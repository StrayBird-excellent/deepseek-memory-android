import { create } from 'zustand'
import type { ChatModel } from './api/types.js'
import type { TtsVoice } from './api/speech.js'

export type Locale = 'zh-CN' | 'en-US'

/** 主题偏好；默认跟随系统 */
export type ThemePreference = 'light' | 'dark' | 'system'

/**
 * 本机设置，存 localStorage：API Key、语言、主题、朗读音色、深度思考与智能搜索开关、侧边栏收起状态，
 * 以及本应用新增的：当前模型、是否完成首次引导、上下文窗口容量。
 */
type Settings = {
  apiKey: string | null
  locale: Locale
  themePreference: ThemePreference
  ttsVoice: TtsVoice
  thinking: boolean
  search: boolean
  siderCollapsed: boolean
  /** 对话使用的模型；v4-pro 更适合角色扮演，flash 支持图片输入且成本低 */
  model: ChatModel
  /** 首次引导是否已完成；未完成时强制停留在引导页 */
  onboarded: boolean
  /** 上下文窗口容量，用于计算压缩阈值；按模型标称值预置，可手动调低以控制成本 */
  contextWindow: number
}

const KEYS = {
  apiKey: 'chat-demo:api-key',
  locale: 'chat-demo:locale',
  themePreference: 'chat-demo:theme-preference',
  ttsVoice: 'chat-demo:tts-voice',
  thinking: 'chat-demo:thinking',
  search: 'chat-demo:search',
  siderCollapsed: 'chat-demo:sider-collapsed',
  model: 'chat-demo:model',
  onboarded: 'chat-demo:onboarded',
  contextWindow: 'chat-demo:context-window',
} as const

const read = (key: string) => {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

const write = (key: string, value: string | null) => {
  try {
    if (value === null) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, value)
    }
  } catch {
    // 存储不可用时只在内存中生效
  }
}

/** 位置选择模式跟随浏览器语言，与应用内选择的界面语言无关。 */
export const isChineseBrowser = (): boolean =>
  (typeof navigator === 'undefined' ? 'zh' : navigator.language).toLowerCase().startsWith('zh')

const initialLocale = (): Locale => {
  const saved = read(KEYS.locale)
  if (saved === 'zh-CN' || saved === 'en-US') {
    return saved
  }
  return isChineseBrowser() ? 'zh-CN' : 'en-US'
}

/** 未保存过时跟随系统 */
const initialThemePreference = (): ThemePreference => {
  const saved = read(KEYS.themePreference)
  return saved === 'light' || saved === 'dark' ? saved : 'system'
}

/** 未保存过时使用服务端默认音色 stella（海星） */
const initialTtsVoice = (): TtsVoice => (read(KEYS.ttsVoice) === 'tide' ? 'tide' : 'stella')

/** 未选择时用 flash：支持图片输入，单价约为 v4-pro 的四分之一 */
const initialModel = (): ChatModel =>
  read(KEYS.model) === 'deepseek-v4-pro' ? 'deepseek-v4-pro' : 'deepseek-flash'

/** 上下文窗口：默认按模型标称的 1M 计，允许调低以提前触发归档、控制成本 */
const initialContextWindow = (): number => {
  const value = Number(read(KEYS.contextWindow))
  return Number.isFinite(value) && value >= 10_000 ? value : 1_000_000
}

export const useSettings = create<Settings>(() => ({
  apiKey: read(KEYS.apiKey),
  locale: initialLocale(),
  themePreference: initialThemePreference(),
  ttsVoice: initialTtsVoice(),
  thinking: read(KEYS.thinking) === 'true',
  search: read(KEYS.search) === 'true',
  siderCollapsed: read(KEYS.siderCollapsed) === 'true',
  model: initialModel(),
  onboarded: read(KEYS.onboarded) === 'true',
  contextWindow: initialContextWindow(),
}))

export const setLocale = (locale: Locale) => {
  write(KEYS.locale, locale)
  useSettings.setState({ locale })
}

export const setThemePreference = (themePreference: ThemePreference) => {
  write(KEYS.themePreference, themePreference)
  useSettings.setState({ themePreference })
}

export const setTtsVoice = (ttsVoice: TtsVoice) => {
  write(KEYS.ttsVoice, ttsVoice)
  useSettings.setState({ ttsVoice })
}

export const setApiKey = (apiKey: string | null) => {
  write(KEYS.apiKey, apiKey)
  useSettings.setState({ apiKey })
}

export const setThinking = (thinking: boolean) => {
  write(KEYS.thinking, String(thinking))
  useSettings.setState({ thinking })
}

export const setSearch = (search: boolean) => {
  write(KEYS.search, String(search))
  useSettings.setState({ search })
}

export const setSiderCollapsed = (siderCollapsed: boolean) => {
  write(KEYS.siderCollapsed, String(siderCollapsed))
  useSettings.setState({ siderCollapsed })
}

export const setModel = (model: ChatModel) => {
  write(KEYS.model, model)
  useSettings.setState({ model })
}

/** 完成首次引导；写入后不再拦截进入主界面 */
export const setOnboarded = (onboarded: boolean) => {
  write(KEYS.onboarded, String(onboarded))
  useSettings.setState({ onboarded })
}

export const setContextWindow = (contextWindow: number) => {
  const value = Number.isFinite(contextWindow) && contextWindow >= 10_000 ? contextWindow : 1_000_000
  write(KEYS.contextWindow, String(value))
  useSettings.setState({ contextWindow: value })
}
