/**
 * 上下文压缩：把早期轮次熬成记忆。
 *
 * 触发条件：估算的上下文占用超过窗口的 compactThreshold（默认 80%）。
 * 动作：从最早的轮次开始，把足够多的旧轮次交给 flash 总结成一段摘要，
 *       写成一条 kind 为 summary 的记忆，并把这批消息标记为「已归档」。
 *
 * 关键点：原文不删除。消息仍完整留在本地会话里，用户翻历史照样能看，
 * 只是不再参与每一轮请求——这才是「有损记忆」的正确形态：
 * 丢的是推理时的可见性，不是用户的数据。
 */

import { createId } from '../../utils/id.js'
import { completeMessage } from '../api/client.js'
import type { ChatModel } from '../api/types.js'
import { initialStability } from './ebbinghaus.js'
import { estimateTokens } from './tokenEstimate.js'
import type { MemoryEntry } from './types.js'

/**
 * 模型上下文窗口。两个模型的文档标称均为 1M；
 * 压缩阈值按比例计算，因此这里只需保证数量级正确。
 */
export const CONTEXT_WINDOW: Record<ChatModel, number> = {
  'deepseek-flash': 1_000_000,
  'deepseek-v4-pro': 1_000_000,
}

/** 压缩后希望降到的占用比例：留出余量给下一轮生成 */
const TARGET_RATIO = 0.55
/** 单次压缩至少归档的轮数，避免频繁触发小额压缩 */
const MIN_ROUNDS = 4
/** 进入摘要的对话正文上限，防止把超长内容一次性塞给模型 */
const MAX_TRANSCRIPT_CHARS = 24_000

const COMPACT_SYSTEM = `你是一个对话档案员。把一段较早的对话压缩成摘要。

保留：
- 讨论过的主要话题和得出的结论
- 双方达成的共识、做出的决定、约定好的事
- 出现过的重要细节：名字、数字、地点、偏好、设定

忽略：
- 寒暄与重复内容
- 已经被推翻或过时的中间过程
- 你自己给出的通用解释

输出一段 300 字以内的中文摘要，用第三人称，不要分点，不要任何前缀或标题。
只输出摘要正文。`

/** 一轮对话在请求里的近似 token 占用 */
export const roundTokens = (userText: string, replyText: string): number =>
  estimateTokens(userText) + estimateTokens(replyText) + 12

/** 需要压缩时返回归档计划；不需要时返回 null */
export type CompactPlan = {
  /** 本轮要归档的消息 id */
  messageIds: string[]
  /** 归档后预计的上下文占用 */
  projected: number
  transcript: string
}

/**
 * 规划一次压缩：从最早的一轮开始累计，直到剩余占用降到目标比例。
 *
 * 入参 rounds 必须按时间正序，且已排除此前归档过的轮次。
 */
export const planCompaction = (
  rounds: { userId: string; assistantId: string; userText: string; replyText: string }[],
  currentTokens: number,
  window: number,
  threshold: number
): CompactPlan | null => {
  if (window <= 0 || currentTokens <= window * threshold) {
    return null
  }
  const target = window * TARGET_RATIO
  const messageIds: string[] = []
  const parts: string[] = []
  let remaining = currentTokens
  let chars = 0
  let index = 0
  for (; index < rounds.length - MIN_ROUNDS; index++) {
    const round = rounds[index]
    const cost = roundTokens(round.userText, round.replyText)
    remaining -= cost
    messageIds.push(round.userId, round.assistantId)
    if (chars < MAX_TRANSCRIPT_CHARS) {
      const segment = `用户：${round.userText.trim()}\n助手：${round.replyText.trim()}`
      parts.push(segment)
      chars += segment.length
    }
    if (remaining <= target) {
      break
    }
  }
  // 归档量太小没有意义：要么真的压下去了，要么说明该调阈值而不是硬压
  if (messageIds.length < MIN_ROUNDS * 2 || remaining > window * threshold) {
    return null
  }
  return { messageIds, projected: remaining, transcript: parts.join('\n\n') }
}

/**
 * 执行压缩：调用 flash 生成摘要，返回要写入的记忆条目。
 * 失败返回 null，调用方保持原状（下一轮还会再判断）。
 */
export const runCompaction = async ({
  apiKey,
  sessionId,
  plan,
  signal,
}: {
  apiKey: string
  sessionId: string
  plan: CompactPlan
  signal?: AbortSignal
}): Promise<MemoryEntry | null> => {
  if (!plan.transcript.trim()) {
    return null
  }
  try {
    const summary = await completeMessage(
      apiKey,
      {
        model: 'deepseek-flash',
        system: COMPACT_SYSTEM,
        messages: [{ role: 'user', content: plan.transcript }],
        thinking: { type: 'disabled' },
        max_tokens: 700,
        temperature: 0.3,
      },
      signal
    )
    const text = summary.trim()
    if (text.length < 10) {
      return null
    }
    const now = Date.now()
    return {
      id: createId(),
      sessionId,
      kind: 'summary',
      text: text.slice(0, 600),
      keywords: [],
      // 摘要承载了被移出上下文的全部信息，起点强度给高，让它成为稳定的底座
      stability: initialStability(0.2),
      difficulty: 0.2,
      reps: 0,
      lapses: 0,
      createdAt: now,
      lastReviewAt: now,
      sourceMessageIds: plan.messageIds.slice(),
      pinned: false,
      archived: false,
      manual: false,
      importance: 0.85,
    }
  } catch (error) {
    // eslint-disable-next-line no-console
    console.warn('[memory] compaction skipped', error)
    return null
  }
}
