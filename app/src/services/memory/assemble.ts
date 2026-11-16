/**
 * 记忆的检索、排序与提示词注入。
 *
 * 注入方式：不新增消息轮次，而是把选中的记忆拼进 system prompt 末尾。
 * 这样做的好处是——记忆不占用对话轮次的位置，也不会在历史里留下痕迹，
 * 模型看到的是「它本来就知道这些事」，而不是「有人递给它一份档案」。
 */

import { priorityScore, retention, relevance, review } from './ebbinghaus.js'
import { estimateTokens, truncateToBudget } from './tokenEstimate.js'
import type { MemoryConfig, MemoryEntry, MemoryKind } from './types.js'

/** 官方 chat-demo 的助手身份说明，作为系统提示的固定前缀 */
export const BASE_SYSTEM_PROMPT = '该助手为DeepSeek，由深度求索公司创造。'

/** 分类在提示词里的中文标签，顺序即渲染顺序 */
const KIND_ORDER: Exclude<MemoryKind, 'summary'>[] = ['persona', 'fact', 'preference', 'event']

const KIND_LABEL: Record<Exclude<MemoryKind, 'summary'>, string> = {
  persona: '约定与设定',
  fact: '身份与背景',
  preference: '偏好与习惯',
  event: '过往事件',
}

/** 排序后的选中结果，附带复习后的条目，供调用方持久化 */
export type MemorySelection = {
  /** 本次实际注入的条目 */
  selected: MemoryEntry[]
  /** 已执行复习的条目（需写回存储）；未被选中的不复习 */
  reviewed: MemoryEntry[]
  /** 注入用的系统提示文本；没有记忆时为空串 */
  block: string
}

const EMPTY_SELECTION: MemorySelection = { selected: [], reviewed: [], block: '' }

/**
 * 按「保留率 × 重要性 × 相关度」挑出本轮要注入的记忆。
 *
 * 预算按 token 计算：先放锁定条目，再按分数从高到低装入，
 * 装不下的直接丢弃——宁可少带，也不让记忆挤占正文的生成空间。
 */
export const selectMemories = (
  entries: MemoryEntry[],
  query: string,
  config: MemoryConfig,
  now: number
): MemorySelection => {
  const usable = entries.filter((entry) => !entry.archived || entry.pinned)
  if (usable.length === 0) {
    return EMPTY_SELECTION
  }

  const scored = usable
    .map((entry) => ({
      entry,
      score: priorityScore(entry, now, relevance(entry, query)),
    }))
    .sort((a, b) => b.score - a.score)

  const selected: MemoryEntry[] = []
  let used = 0
  const overhead = 60 // 包裹标签本身的占用
  for (const { entry } of scored) {
    if (selected.length >= config.maxEntries) {
      break
    }
    const cost = estimateTokens(entry.text) + 8
    if (used + cost + overhead > config.tokenBudget) {
      // 锁定条目必须保留，其余在预算耗尽后停止
      if (!entry.pinned) {
        continue
      }
    }
    selected.push(entry)
    used += cost
  }

  if (selected.length === 0) {
    return EMPTY_SELECTION
  }

  return {
    selected,
    reviewed: selected.map((entry) => review(entry, now)),
    block: renderMemoryBlock(selected, config.tokenBudget),
  }
}

/** 把选中的记忆渲染成提示词片段；超预算时整体截断 */
export const renderMemoryBlock = (entries: MemoryEntry[], tokenBudget: number): string => {
  if (entries.length === 0) {
    return ''
  }
  const sections: string[] = []
  for (const kind of KIND_ORDER) {
    const items = entries.filter((entry) => entry.kind === kind)
    if (items.length === 0) {
      continue
    }
    sections.push(`[${KIND_LABEL[kind]}]\n${items.map((entry) => `- ${entry.text}`).join('\n')}`)
  }
  const summaries = entries.filter((entry) => entry.kind === 'summary')
  if (summaries.length > 0) {
    sections.push(
      `[早期对话摘要]\n${summaries.map((entry) => `- ${entry.text}`).join('\n')}`
    )
  }
  if (sections.length === 0) {
    return ''
  }
  const body = truncateToBudget(sections.join('\n\n'), tokenBudget)
  return [
    '<long_term_memory>',
    '以下是你与对方长期相处中已经知道的事情。自然地运用它们，不要主动提及这些内容本身，',
    '也不要说"根据我的记忆""记录显示"之类的话。',
    '',
    body,
    '</long_term_memory>',
  ].join('\n')
}

/**
 * 组装最终的 system prompt：固定身份 + 记忆块。
 * 没有记忆时返回原始身份说明，与官方行为完全一致。
 */
export const buildSystemPrompt = (memoryBlock: string): string =>
  memoryBlock ? `${BASE_SYSTEM_PROMPT}\n\n${memoryBlock}` : BASE_SYSTEM_PROMPT

/**
 * 记忆数量与强度的概览，供按钮角标与面板头部使用。
 * 只统计未归档的条目——归档的已经不再影响对话。
 */
export const memoryOverview = (entries: MemoryEntry[], now: number) => {
  const live = entries.filter((entry) => !entry.archived || entry.pinned)
  const strengths = live.map((entry) => retention(entry, now))
  const avg = strengths.length === 0 ? 0 : strengths.reduce((a, b) => a + b, 0) / strengths.length
  return {
    count: live.length,
    archived: entries.filter((entry) => entry.archived && !entry.pinned).length,
    averageRetention: avg,
  }
}
