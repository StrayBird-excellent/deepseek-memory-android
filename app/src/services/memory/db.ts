/**
 * 记忆层的本地存储。
 *
 * 使用独立的 IndexedDB 数据库，而不是往官方 chat-demo 的库里加表：
 * 官方库的版本升级逻辑（历史导入、暂存替换）与记忆层互不干扰，
 * 后续同步官方更新时只需覆盖原文件，不必合并 schema 变更。
 *
 * 所有数据只存在本机，不上传服务端。
 */

import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import type { MemoryConfig, MemoryEntry } from './types.js'

const DB_NAME = 'deepseek-memory'
const DB_VERSION = 1

/** 上下文压缩进度：记录哪些轮次已经并入记忆层、不再进入请求 */
export type MemoryProgress = {
  sessionId: string
  /** 已移出上下文的消息 id，按时间顺序 */
  compactedMessageIds: string[]
  /** 压缩发生的时间 */
  updatedAt: number
}

interface MemoryDB extends DBSchema {
  memories: {
    key: string
    value: MemoryEntry
    indexes: { sessionId: string }
  }
  configs: {
    key: string
    value: MemoryConfig
  }
  progress: {
    key: string
    value: MemoryProgress
  }
}

let dbPromise: Promise<IDBPDatabase<MemoryDB>> | null = null

/** 存储不可用（隐私模式、配额耗尽）时的内存兜底，保证当次会话功能不中断 */
const memoryFallback = {
  memories: new Map<string, MemoryEntry>(),
  configs: new Map<string, MemoryConfig>(),
  progress: new Map<string, MemoryProgress>(),
}
let usingFallback = false

export const isFallback = () => usingFallback

const getDB = () =>
  (dbPromise ??= openDB<MemoryDB>(DB_NAME, DB_VERSION, {
    upgrade(db) {
      if (!db.objectStoreNames.contains('memories')) {
        const store = db.createObjectStore('memories', { keyPath: 'id' })
        store.createIndex('sessionId', 'sessionId')
      }
      if (!db.objectStoreNames.contains('configs')) {
        db.createObjectStore('configs', { keyPath: 'sessionId' })
      }
      if (!db.objectStoreNames.contains('progress')) {
        db.createObjectStore('progress', { keyPath: 'sessionId' })
      }
    },
  }).catch((error: unknown) => {
    // 打开失败后允许重试，同时标记降级，调用方继续走内存态
    dbPromise = null
    usingFallback = true
    // eslint-disable-next-line no-console
    console.error('[memory] IndexedDB unavailable, using in-memory fallback', error)
    throw error
  }))

/** 统一包一层：存储不可用时退回内存实现，不向上抛错打断对话 */
const withFallback = async <T>(task: () => Promise<T>, fallback: () => T): Promise<T> => {
  try {
    return await task()
  } catch {
    return fallback()
  }
}

const loadAll = async (sessionId: string): Promise<MemoryEntry[]> =>
  withFallback(
    async () => (await (await getDB()).getAllFromIndex('memories', 'sessionId', sessionId)),
    () =>
      [...memoryFallback.memories.values()]
        .filter((entry) => entry.sessionId === sessionId)
        .sort((a, b) => a.createdAt - b.createdAt)
  )

export const listMemories = async (sessionId: string): Promise<MemoryEntry[]> => {
  const entries = await loadAll(sessionId)
  return entries.sort((a, b) => a.createdAt - b.createdAt)
}

export const putMemory = async (entry: MemoryEntry): Promise<void> =>
  withFallback(
    async () => {
      await (await getDB()).put('memories', entry)
    },
    () => {
      memoryFallback.memories.set(entry.id, entry)
    }
  )

/** 批量写入，抽取与压缩都会用到 */
export const putMemories = async (entries: MemoryEntry[]): Promise<void> => {
  if (entries.length === 0) {
    return
  }
  await withFallback(
    async () => {
      const db = await getDB()
      const tx = db.transaction('memories', 'readwrite')
      await Promise.all([...entries.map((entry) => tx.store.put(entry)), tx.done])
    },
    () => {
      for (const entry of entries) {
        memoryFallback.memories.set(entry.id, entry)
      }
    }
  )
}

export const deleteMemory = async (id: string): Promise<void> =>
  withFallback(
    async () => {
      await (await getDB()).delete('memories', id)
    },
    () => {
      memoryFallback.memories.delete(id)
    }
  )

export const clearSessionMemories = async (sessionId: string): Promise<void> =>
  withFallback(
    async () => {
      const db = await getDB()
      const tx = db.transaction(['memories', 'progress'], 'readwrite')
      const index = tx.objectStore('memories').index('sessionId')
      // 逐条删除，避免 keys 全量驻留内存
      for (let cursor = await index.openCursor(sessionId); cursor; cursor = await cursor.continue()) {
        await cursor.delete()
      }
      await tx.objectStore('progress').delete(sessionId)
      await tx.done
    },
    () => {
      for (const [id, entry] of memoryFallback.memories) {
        if (entry.sessionId === sessionId) {
          memoryFallback.memories.delete(id)
        }
      }
      memoryFallback.progress.delete(sessionId)
    }
  )

export const getConfig = async (
  sessionId: string,
  defaults: MemoryConfig
): Promise<MemoryConfig> =>
  withFallback(
    async () => (await (await getDB()).get('configs', sessionId)) ?? defaults,
    () => memoryFallback.configs.get(sessionId) ?? defaults
  )

export const putConfig = async (config: MemoryConfig): Promise<void> =>
  withFallback(
    async () => {
      await (await getDB()).put('configs', config)
    },
    () => {
      memoryFallback.configs.set(config.sessionId, config)
    }
  )

const emptyProgress = (sessionId: string): MemoryProgress => ({
  sessionId,
  compactedMessageIds: [],
  updatedAt: 0,
})

export const getProgress = async (sessionId: string): Promise<MemoryProgress> =>
  withFallback(
    async () => (await (await getDB()).get('progress', sessionId)) ?? emptyProgress(sessionId),
    () => memoryFallback.progress.get(sessionId) ?? emptyProgress(sessionId)
  )

export const putProgress = async (progress: MemoryProgress): Promise<void> =>
  withFallback(
    async () => {
      await (await getDB()).put('progress', progress)
    },
    () => {
      memoryFallback.progress.set(progress.sessionId, progress)
    }
  )

/** 统计：面板顶部展示总数、清晰数等 */
export const summarize = (entries: MemoryEntry[], now: number) => {
  void now
  return {
    total: entries.length,
    pinned: entries.filter((entry) => entry.pinned).length,
    archived: entries.filter((entry) => entry.archived).length,
  }
}
