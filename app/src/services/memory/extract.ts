/**
 * 记忆抽取。
 *
 * 每轮回复结束后在后台跑一次：把这轮对话交给 flash 模型，让它判断哪些内容
 * 值得长期记住，输出结构化的候选记忆。失败一律静默——抽取属于增强，
 * 不能因为一次后台请求失败就影响对话本身。
 *
 * 用 flash 而不是当前会话的模型：抽取是分类 + 改写任务，flash 足够，
 * 成本约为 v4-pro 的 1/4，且不占用对话模型的并发额度。
 */

import { createId } from '../../utils/id.js'
import { completeMessage } from '../api/client.js'
import type { ChatModel } from '../api/types.js'
import { initialStability } from './ebbinghaus.js'
import type { ExtractedMemory, MemoryEntry, MemoryKind } from './types.js'

/** 抽取用模型：固定 flash，与对话模型解耦 */
const EXTRACT_MODEL: ChatModel = 'deepseek-flash'
/** 单次最多抽取条数，过多会让记忆层迅速膨胀 */
const MAX_PER_ROUND = 4
/** 低于这个长度的对话不值得抽取，直接跳过以省 token */
const MIN_USER_CHARS = 4
const MIN_REPLY_CHARS = 40

const EXTRACT_SYSTEM = `你是一个记忆整理器。阅读一轮对话，提炼出值得长期记住的信息。

判断标准：这条信息在几天后、几十轮对话之后是否仍然有用。
值得记住：
- 对方的身份、职业、所处环境、背景
- 稳定的偏好、习惯、审美、表达方式上的要求
- 明确的角色扮演设定、称呼、关系、语气约定
- 双方约定好的规则或长期任务
- 发生过、且后续可能被提起的事

不值得记住：
- 一次性的提问与答案
- 临时的情绪、寒暄、客套
- 通用知识、你自己刚给出的解释
- 你自己的自我描述

输出要求：
- 每条用第三人称中文陈述，一句话说清，不超过 60 字
- 直接写事实本身，不要写"用户说""我记住了"这类元描述
- kind 取 fact（身份背景）、preference（偏好习惯）、persona（设定约定）、event（事件）
- importance 0-1：会明显影响后续对话走向的给 0.7 以上
- difficulty 0-1：越琐碎、越偶然提到的给越高
- keywords：2-5 个中文检索词

只输出 JSON 数组，不要任何解释、不要代码块标记。没有值得记住的内容就输出 []。
示例：[{"kind":"fact","text":"对方是一名前端工程师，主要使用 TypeScript","keywords":["职业","前端","TypeScript"],"importance":0.7,"difficulty":0.3}]`

const VALID_KINDS: Exclude<MemoryKind, 'summary'>[] = ['fact', 'preference', 'persona', 'event']

/** 从模型输出里剥出 JSON 数组：容忍代码块包裹与前后杂字 */
const parseArray = (raw: string): unknown[] => {
  const text = raw.trim()
  const start = text.indexOf('[')
  const end = text.lastIndexOf(']')
  if (start < 0 || end <= start) {
    return []
  }
  try {
    const parsed: unknown = JSON.parse(text.slice(start, end + 1))
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const clamp01 = (value: unknown): number => {
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? Math.min(1, Math.max(0, n)) : 0.5
}

const asString = (value: unknown): string =>
  typeof value === 'string' ? value.trim() : ''

/** 校验并规范化单条候选；结构不合格的直接丢弃 */
const toCandidate = (raw: unknown): ExtractedMemory | null => {
  if (typeof raw !== 'object' || raw === null) {
    return null
  }
  const item = raw as Record<string, unknown>
  const text = asString(item.text)
  if (!text || text.length < 4 || text.length > 200) {
    return null
  }
  const kindRaw = asString(item.kind) as Exclude<MemoryKind, 'summary'>
  const kind = VALID_KINDS.includes(kindRaw) ? kindRaw : 'fact'
  const keywords = Array.isArray(item.keywords)
    ? item.keywords
        .map(asString)
        .filter((word) => word.length > 0 && word.length <= 16)
        .slice(0, 6)
    : []
  return {
    kind,
    text,
    keywords,
    importance: clamp01(item.importance),
    difficulty: clamp01(item.difficulty),
  }
}

/** 把候选转成可入库的条目 */
export const toEntry = (
  candidate: ExtractedMemory,
  sessionId: string,
  sourceMessageIds: string[],
  now: number
): MemoryEntry => ({
  id: createId(),
  sessionId,
  kind: candidate.kind,
  text: candidate.text,
  keywords: candidate.keywords,
  stability: initialStability(candidate.difficulty),
  difficulty: candidate.difficulty,
  reps: 0,
  lapses: 0,
  createdAt: now,
  lastReviewAt: now,
  sourceMessageIds,
  pinned: false,
  archived: false,
  manual: false,
  importance: candidate.importance,
})

/**
 * 抽取一轮对话中的记忆。
 * 返回空数组表示这轮没有值得记住的内容，或者抽取失败。
 */
export const extractFromRound = async ({
  apiKey,
  sessionId,
  userText,
  replyText,
  sourceMessageIds,
  signal,
}: {
  apiKey: string
  sessionId: string
  userText: string
  replyText: string
  sourceMessageIds: string[]
  signal?: AbortSignal
}): Promise<MemoryEntry[]> => {
  // 过短的往来通常是寒暄，抽不出东西，直接省掉这次请求
  if (userText.trim().length < MIN_USER_CHARS && replyText.trim().length < MIN_REPLY_CHARS) {
    return []
  }
  const transcript = [
    `用户：${userText.trim().slice(0, 2000)}`,
    `助手：${replyText.trim().slice(0, 3000)}`,
  ].join('\n\n')

  try {
    const raw = await completeMessage(
      apiKey,
      {
        model: EXTRACT_MODEL,
        system: EXTRACT_SYSTEM,
        messages: [{ role: 'user', content: transcript }],
        thinking: { type: 'disabled' },
        max_tokens: 900,
        temperature: 0.2,
      },
      signal
    )
    const now = Date.now()
    return parseArray(raw)
      .map(toCandidate)
      .filter((candidate): candidate is ExtractedMemory => candidate !== null)
      .slice(0, MAX_PER_ROUND)
      .map((candidate) => toEntry(candidate, sessionId, sourceMessageIds, now))
  } catch (error) {
    // eslint-disable-next-line no-console
    console.warn('[memory] extraction skipped', error)
    return []
  }
}
