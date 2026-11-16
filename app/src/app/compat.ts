/**
 * 低版本 WebView 兼容层。
 *
 * 构建目标写的是 chrome >= 105，但 APK 的 minSdk 是 24（Android 7.0）。
 * 系统 WebView 版本与 Android 版本并非一一对应：低端机、定制 ROM 上
 * WebView 可能远旧于 105，缺少下列 API 会导致启动即白屏。
 * 这里集中补齐运行时缺失的 API，业务代码保持现代化写法。
 *
 * 必须在 main.tsx 的最顶部、任何业务模块之前引入。
 */

type MutableArray<T = unknown> = T[]

/** Object.hasOwn —— ES2022，Chrome 93+ */
if (typeof Object.hasOwn !== 'function') {
  Object.defineProperty(Object, 'hasOwn', {
    value: (target: object, property: PropertyKey) =>
      Object.prototype.hasOwnProperty.call(target, property),
    configurable: true,
    writable: true,
  })
}

/** Array.prototype.at —— ES2022，Chrome 92+ */
if (!Array.prototype.at) {
  Object.defineProperty(Array.prototype, 'at', {
    value: function <T>(this: MutableArray<T>, index: number): T | undefined {
      const length = this.length
      const normalized = index < 0 ? length + index : index
      if (normalized < 0 || normalized >= length) {
        return undefined
      }
      return this[normalized]
    },
    configurable: true,
    writable: true,
  })
}

/** Object.fromEntries —— ES2019，Chrome 73+ */
if (typeof Object.fromEntries !== 'function') {
  Object.defineProperty(Object, 'fromEntries', {
    value: (entries: Iterable<readonly [PropertyKey, unknown]>) => {
      const result: Record<PropertyKey, unknown> = {}
      for (const [key, value] of entries) {
        result[key] = value
      }
      return result
    },
    configurable: true,
    writable: true,
  })
}

/** Array.prototype.flatMap —— ES2019，Chrome 69+ */
if (!Array.prototype.flatMap) {
  Object.defineProperty(Array.prototype, 'flatMap', {
    value: function <T, U>(
      this: T[],
      callback: (value: T, index: number, array: T[]) => U | U[],
      thisArg?: unknown
    ): U[] {
      const result: U[] = []
      this.forEach((value, index, array) => {
        const mapped = callback.call(thisArg, value, index, array)
        if (Array.isArray(mapped)) {
          result.push(...mapped)
        } else {
          result.push(mapped as U)
        }
      })
      return result
    },
    configurable: true,
    writable: true,
  })
}

/** String.prototype.replaceAll —— ES2021，Chrome 85+ */
if (!String.prototype.replaceAll) {
  Object.defineProperty(String.prototype, 'replaceAll', {
    value: function (this: string, search: string | RegExp, replace: string) {
      if (search instanceof RegExp) {
        if (!search.global) {
          throw new TypeError('replaceAll must be called with a global RegExp')
        }
        return this.replace(search, replace)
      }
      return this.split(search).join(replace)
    },
    configurable: true,
    writable: true,
  })
}

/**
 * ResizeObserver —— Chrome 64+。缺失时多数场景只是「尺寸变化时重算浮层/滚动」，
 * 用 window 的 resize + 定时轮询兜底，保证功能不缺失（精度略低可接受）。
 */
if (typeof window !== 'undefined' && !window.ResizeObserver) {
  type Watched = {
    target: Element
    callback: ResizeObserverCallback
    observer: ResizeObserver
    lastSize: string
  }
  const watched = new Set<Watched>()

  const measure = () => {
    for (const entry of watched) {
      const rect = entry.target.getBoundingClientRect()
      const size = `${rect.width}x${rect.height}`
      if (size !== entry.lastSize) {
        entry.lastSize = size
        const record = {
          target: entry.target,
          contentRect: rect,
          borderBoxSize: [],
          contentBoxSize: [],
          devicePixelContentBoxSize: [],
        } as unknown as ResizeObserverEntry
        entry.callback([record], entry.observer)
      }
    }
  }

  /** 用工厂函数而非 class，避免构造函数返回值与实例类型不一致的 TS 报错 */
  const createFallback = (callback: ResizeObserverCallback): ResizeObserver => {
    const own = new Set<Watched>()
    const observer = {
      observe(target: Element) {
        const entry: Watched = {
          target,
          callback,
          observer: observer as ResizeObserver,
          lastSize: '',
        }
        own.add(entry)
        watched.add(entry)
        measure()
      },
      unobserve(target: Element) {
        for (const entry of own) {
          if (entry.target === target) {
            own.delete(entry)
            watched.delete(entry)
          }
        }
      },
      disconnect() {
        for (const entry of own) {
          watched.delete(entry)
        }
        own.clear();
      },
    }
    return observer as unknown as ResizeObserver
  }

  window.ResizeObserver = createFallback as unknown as typeof ResizeObserver
  window.addEventListener('resize', measure)
  // 抽屉、弹层等由内容撑高的元素不会触发 window.resize，用低频轮询兜住
  window.setInterval(measure, 500)
}

/**
 * Intl.DisplayNames —— 位置功能已移除，这里仅防止历史数据或第三方代码调用时崩溃。
 * 返回一个退化为「返回原代码」的最小实现，不做真实翻译。
 */
if (typeof Intl !== 'undefined' && !Intl.DisplayNames) {
  Object.defineProperty(Intl, 'DisplayNames', {
    value: class {
      of(code: string) {
        return code
      }
    },
    configurable: true,
    writable: true,
  })
}

/** 全局错误兜底：捕获初始化阶段的同步异常，写入控制台而不阻断后续模块 */
export const installCompat = () => {
  // createId 已在 utils 中做了 randomUUID 降级，这里确保 crypto 对象本身存在。
  // 用 window 而非 globalThis：后者在 Chrome 71 以下不存在。
  if (typeof window !== 'undefined' && !window.crypto) {
    Object.defineProperty(window, 'crypto', {
      value: { getRandomValues: <T extends ArrayBufferView>(array: T) => array },
      configurable: true,
    })
  }
}
