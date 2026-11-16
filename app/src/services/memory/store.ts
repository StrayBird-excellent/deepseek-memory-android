/**
 * 记忆层的状态与编排。
 *
 * 对外只暴露两类东西：
 * 1. 对话链路要用的钩子——请求前取记忆块、回复后抽取、按需压缩；
 * 2. 面板要用的状态与操作——列表、编辑、置顶、恢复、配置。
 *
 * 记忆不跨会话共享，所有读写都以 sessionId 为边界。
 */

import { create } from 'zustand'
import { useSettings } from '../settings.js'
import { buildSystemPrompt, selectMemories } from './assemble.js'
import { getProgress, type MemoryProgress } from './db.js'
import * as db from './db.js'
import { archive, initialStability, memoryState, mergeExtracted, restore, retention, review } from './ebbinghaus.js'
import { extractFromRound } from './extract.js'
import { runCompaction, planCompaction, CONTEXT_WINDOW } from './compact.js'
import { memoryConfigFor, type MemoryConfig, type MemoryEntry, type MemoryView } from './types.js'
import { createId } from '../../utils/id.js'

type MemoryState = {
  /** 面板是否展开 */
  panelOpen: boolean
  /** 面板当前对应的会话 */
  sessionId: string | null
  entries: MemoryEntry[]
  config: MemoryConfig
  progress: MemoryProgress
  loading: boolean
  /** 正在后台抽取的会话，用于面板显示「正在整理」 */
  extracting: Record<string, boolean>
}

const emptyConfig = memoryConfigFor('')
const emptyProgress: MemoryProgress = { sessionId: '', compactedMessageIds: [], updatedAt: 0 }

export const useMemoryStore = create<MemoryState>(() => ({
  panelOpen: false,
  sessionId: null,
  entries: [],
  config: emptyConfig,
  progress: emptyProgress,
  loading: false,
  extracting: {},
}))

const { getState: get, setState: set } = useMemoryStore

/** 每个会话的抽取串行化，避免连续发言时并发请求同一会话 */
const extractQueues = new Map<string, Promise<void>>()

/** 读取某会话的全部记忆与配置 */
export const loadMemory = async (sessionId: string): Promise<void> => {
  const config = await db.getConfig(sessionId, memoryConfigFor(sessionId))
  const [entries, progress] = await Promise.all([
    db.listMemories(sessionId),
    getProgress(sessionId),
  ])
  // 打开瞬间把跌到阈值的条目落一次归档标记，避免每次渲染都重算
  const now = Date.now()
  const stale = entries.filter(
    (entry) => !entry.archived && !entry.pinned && retention(entry, now) < 0.2
  )
  if (stale.length > 0) {
    const archived = stale.map(archive)
    await db.putMemories(archived)
    const byId = new Map(archived.map((entry) => [entry.id, entry]))
    for (let i = 0; i < entries.length; i++) {
      const next = byId.get(entries[i].id)
      if (next) {
        entries[i] = next
      }
    }
  }
  set({ sessionId, entries: [...entries], config, progress, loading: false })
}

/** 面板视图：带上实时强度与剩余天数 */
export const memoryViews = (entries: MemoryEntry[], now: number): MemoryView[] =>
  entries
    .map((entry) => {
      const r = retention(entry, now)
      return {
        entry,
        retention: r,
        state: entry.archived ? ('forgotten' as const) : memoryState(r),
        daysLeft: 0,
      }
    })
    .sort((a, b) => {
      // 锁定优先，其余按保留率降序，已淡忘沉底
      if (a.entry.pinned !== b.entry.pinned) {
        return a.entry.pinned ? -1 : 1
      }
      return b.retention - a.retention
    })

export const openMemoryPanel = async (sessionId: string) => {
  set({ panelOpen: true, sessionId, loading: true })
  await loadMemory(sessionId)
}

export const closeMemoryPanel = () => set({ panelOpen: false })

export const refreshMemory = async () => {
  const { sessionId } = get()
  if (sessionId) {
    await loadMemory(sessionId)
  }
}

/** 手动新增一条记忆：强度从高位起步，因为是用户明确写下的 */
export const addManualMemory = async (text: string, kind: MemoryEntry['kind'] = 'fact') => {
  const { sessionId, config } = get()
  const trimmed = text.trim()
  if (!sessionId || !trimmed) {
    return
  }
  const now = Date.now()
  const entry: MemoryEntry = {
    id: createId(),
    sessionId,
    kind,
    text: trimmed.slice(0, 300),
    keywords: [],
    stability: initialStability(0.15),
    difficulty: 0.15,
    reps: 0,
    lapses: 0,
    createdAt: now,
    lastReviewAt: now,
    sourceMessageIds: [],
    pinned: false,
    archived: false,
    manual: true,
    importance: 0.9,
  }
  await db.putMemory(entry)
  set((state) => ({ entries: [...state.entries, entry], config }))
}

export const updateMemoryText = async (id: string, text: string) => {
  const trimmed = text.trim()
  if (!trimmed) {
    return
  }
  const target = get().entries.find((entry) => entry.id === id)
  if (!target) {
    return
  }
  // 用户亲手改过即视为重新确认，按复习处理，强度回升
  const next = { ...review(target, Date.now()), text: trimmed.slice(0, 300), manual: true }
  await db.putMemory(next)
  set((state) => ({ entries: state.entries.map((entry) => (entry.id === id ? next : entry)) }))
}

export const removeMemory = async (id: string) => {
  await db.deleteMemory(id)
  set((state) => ({ entries: state.entries.filter((entry) => entry.id !== id) }))
}

export const toggleMemoryPin = async (id: string) => {
  const target = get().entries.find((entry) => entry.id === id)
  if (!target) {
    return
  }
  const now = Date.now()
  const next: MemoryEntry = {
    ...target,
    pinned: !target.pinned,
    // 锁定前先复习一次，解锁后不会立刻掉到遗忘区
    lastReviewAt: now,
    archived: false,
  }
  await db.putMemory(next)
  set((state) => ({ entries: state.entries.map((entry) => (entry.id === id ? next : entry)) }))
}

export const restoreMemory = async (id: string) => {
  const target = get().entries.find((entry) => entry.id === id)
  if (!target) {
    return
  }
  const next = restore(target, Date.now())
  await db.putMemory(next)
  set((state) => ({ entries: state.entries.map((entry) => (entry.id === id ? next : entry)) }))
}

export const updateMemoryConfig = async (patch: Partial<MemoryConfig>) => {
  const { sessionId, config } = get()
  if (!sessionId) {
    return
  }
  const next = { ...config, ...patch, sessionId }
  await db.putConfig(next)
  set({ config: next })
}

export const clearAllMemory = async () => {
  const { sessionId } = get()
  if (!sessionId) {
    return
  }
  await db.clearSessionMemories(sessionId)
  set({ entries: [], progress: emptyProgress })
}

/**
 * 请求前：取出本轮要注入的记忆。
 * 返回拼接好的 system prompt 与需要从上下文里剔除的消息 id。
 */
export const memoryForRequest = async (
  sessionId: string,
  query: string
): Promise<{ system: string; skippedIds: Set<string> }> => {
  const config = await db.getConfig(sessionId, memoryConfigFor(sessionId))
  const [entries, progress] = await Promise.all([
    db.listMemories(sessionId),
    getProgress(sessionId),
  ])
  const now = Date.now()
  const selection = selectMemories(entries, query, config, now)
  if (selection.reviewed.length > 0) {
    // 命中即复习：这是遗忘曲线里唯一的强化入口
    await db.putMemories(selection.reviewed)
  }
  return {
    system: buildSystemPrompt(selection.block),
    skippedIds: new Set(progress.compactedMessageIds),
  }
}

/**
 * 回复结束后：抽取本轮记忆并合并进记忆层。
 * 挂在队列上串行执行，同一会话不会并发写。
 */
export const memoryAfterRound = ({
  apiKey,
  sessionId,
  userId,
  assistantId,
  userText,
  replyText,
}: {
  apiKey: string
  sessionId: string
  userId: string
  assistantId: string
  userText: string
  replyText: string
}): Promise<void> => {
  const previous = extractQueues.get(sessionId) ?? Promise.resolve()
  const task = previous
    .then(async () => {
      const config = await db.getConfig(sessionId, memoryConfigFor(sessionId))
      if (!config.autoExtract || !apiKey) {
        return
      }
      set((state) => ({ extracting: { ...state.extracting, [sessionId]: true } }))
      try {
        const candidates = await extractFromRound({
          apiKey,
          sessionId,
          userText,
          replyText,
          sourceMessageIds: [userId, assistantId],
        })
        if (candidates.length === 0) {
          return
        }
        const existing = await db.listMemories(sessionId)
        const now = Date.now()
        let next = existing
        for (const candidate of candidates) {
          next = mergeExtracted(next, candidate, now).entries
        }
        const touched = next.filter(
          (entry) => !existing.some((old) => old.id === entry.id && old.lastReviewAt === entry.lastReviewAt)
        )
        await db.putMemories(touched)
        if (get().sessionId === sessionId) {
          set({ entries: next })
        }
      } finally {
        set((state) => ({ extracting: { ...state.extracting, [sessionId]: false } }))
      }
    })
    .catch((error: unknown) => {
      // eslint-disable-next-line no-console
      console.warn('[memory] after-round task failed', error)
    })
  extractQueues.set(sessionId, task)
  return task
}

/**
 * 检查并按需压缩上下文。
 * 返回已归档的消息 id 集合，供下一轮请求剔除。
 */
export const maybeCompact = async ({
  apiKey,
  sessionId,
  model,
  rounds,
  currentTokens,
}: {
  apiKey: string
  sessionId: string
  model: 'deepseek-flash' | 'deepseek-v4-pro'
  rounds: { userId: string; assistantId: string; userText: string; replyText: string }[]
  currentTokens: number
}): Promise<void> => {
  const config = await db.getConfig(sessionId, memoryConfigFor(sessionId))
  const progress = await getProgress(sessionId)
  const archived = new Set(progress.compactedMessageIds)
  const pending = rounds.filter((round) => !archived.has(round.userId))
  if (pending.length === 0) {
    return
  }
  // 窗口容量优先取用户设置，未设置时按模型标称值
  const window = useSettings.getState().contextWindow || CONTEXT_WINDOW[model]
  const plan = planCompaction(pending, currentTokens, window, config.compactThreshold)
  if (!plan) {
    return
  }
  const summary = await runCompaction({ apiKey, sessionId, plan })
  if (!summary) {
    return
  }
  await db.putMemory(summary)
  const nextProgress: MemoryProgress = {
    sessionId,
    compactedMessageIds: [...progress.compactedMessageIds, ...plan.messageIds],
    updatedAt: Date.now(),
  }
  await db.putProgress(nextProgress)
  if (get().sessionId === sessionId) {
    set((state) => ({
      entries: [...state.entries, summary],
      progress: nextProgress,
    }))
  }
}
