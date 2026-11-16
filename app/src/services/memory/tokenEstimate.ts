/**
 * 请求前的 token 估算。
 *
 * 目的不是精确计费，而是判断「上下文还剩多少余量」：
 * 中文按字计、英文按 3.6 字符计，误差在 15% 以内，
 * 足以支撑压缩阈值的判断，且无需引入分词器依赖（本地包体积敏感）。
 */

/** CJK 统一表意文字、假名、谚文：一字约一 token */
const CJK =
  /[\u3000-\u303f\u3040-\u309f\u30a0-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\uac00-\ud7af]/

/** 单条消息的固定开销：role、分隔符等 */
const MESSAGE_OVERHEAD = 4

export const estimateTokens = (text: string): number => {
  if (!text) {
    return 0
  }
  let cjk = 0
  for (const char of text) {
    if (CJK.test(char)) {
      cjk += 1
    }
  }
  const rest = text.length - cjk
  return Math.ceil(cjk + rest / 3.6)
}

/** 估算整段对话的 token；messages 只需提供可序列化的文本负载 */
export const estimateMessages = (
  messages: { role: string; text: string }[]
): number =>
  messages.reduce((sum, message) => sum + estimateTokens(message.text) + MESSAGE_OVERHEAD, 0)

/** 上下文占用比例：用于判断是否触及压缩阈值 */
export const contextUsage = (used: number, window: number): number =>
  window <= 0 ? 0 : used / window

/**
 * 把 token 预算换算成可注入的字符量。
 * 中文一字约一 token，英文约 3.6 字符，取保守值 2 字符/token。
 */
export const budgetToChars = (tokens: number): number => Math.max(200, tokens * 2)

/** 按字符预算截断文本，保留完整句子边界 */
export const truncateToBudget = (text: string, tokenBudget: number): string => {
  const limit = budgetToChars(tokenBudget)
  if (text.length <= limit) {
    return text
  }
  const slice = text.slice(0, limit)
  const cut = Math.max(
    slice.lastIndexOf('。'),
    slice.lastIndexOf('！'),
    slice.lastIndexOf('？'),
    slice.lastIndexOf('\n'),
    slice.lastIndexOf('. ')
  )
  return `${cut > limit * 0.6 ? slice.slice(0, cut + 1) : slice}…`
}
