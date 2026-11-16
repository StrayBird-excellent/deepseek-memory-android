/**
 * 账户余额。
 *
 * 官方接口：GET https://api.deepseek.com/user/balance
 * 只需要 API Key，与账号登录无关——这个应用的身份就是 Key 本身。
 *
 * 只做读取。充值不在这里做：官方没有开放支付接口，
 * 代收充值款涉及资金合规，客户端只负责把用户送到官方充值页。
 */

import { request } from './client.js'

/** 单个币种的余额；官方目前返回 CNY 与 USD 两种 */
export type BalanceInfo = {
  currency: 'CNY' | 'USD'
  /** 总可用余额，含赠金与充值 */
  total_balance: string
  /** 未过期的赠金余额 */
  granted_balance: string
  /** 充值余额 */
  topped_up_balance: string
}

export type Balance = {
  /** 余额是否足以继续调用 API */
  is_available: boolean
  balance_infos: BalanceInfo[]
}

/**
 * 读取账户余额。
 * 失败一律抛 ApiError，由调用方决定展示文案——
 * 余额查不到不应该影响聊天主流程。
 */
export const fetchBalance = async (apiKey: string, signal?: AbortSignal): Promise<Balance> => {
  const response = await request('https://api.deepseek.com/user/balance', {
    method: 'GET',
    headers: { Authorization: `Bearer ${apiKey}` },
    signal,
  })
  const payload = (await response.json()) as Balance
  return {
    is_available: Boolean(payload?.is_available),
    balance_infos: Array.isArray(payload?.balance_infos) ? payload.balance_infos : [],
  }
}

/** 官方充值页；由客户端用系统浏览器打开，支付全程在官方域名完成 */
export const TOP_UP_URL = 'https://platform.deepseek.com/top_up'

/** 官方用量与账单页 */
export const USAGE_URL = 'https://platform.deepseek.com/usage'

/** 把余额格式化成「¥12.34」这样的展示串 */
export const formatBalance = (info: BalanceInfo | undefined): string => {
  if (!info) {
    return '—'
  }
  const symbol = info.currency === 'USD' ? '$' : '¥'
  const value = Number(info.total_balance)
  if (!Number.isFinite(value)) {
    return `${symbol}${info.total_balance}`
  }
  return `${symbol}${value.toFixed(2)}`
}
