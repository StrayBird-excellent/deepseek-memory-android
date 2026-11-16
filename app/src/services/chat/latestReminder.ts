import type { Locale } from '../settings.js'
import { formatDate } from '../../utils/format.js'
import { translate } from '../../i18n/index.js'

/**
 * 模型约定的「最新提醒」信息，格式固定为 5 个逗号分隔字段：
 *   <date>,<weekday>,<location>,<platform>,<language>
 *
 * 日期与星期使用浏览器本地时区，避免 UTC 日期跨日。
 *
 * 位置字段必须保留占位：DeepSeek API 会严格校验字段数量（至少 5 个），
 * 直接删掉这一位会导致请求被拒（"must have at least 5 comma-separated fields"）。
 * 用户不需要选择位置，因此这里始终传空字符串——API 文档明确允许 location 为空。
 * 参数仍接收整个 settings，调用方无需改动。
 */
export const createLatestReminder = (
  { locale }: { locale: Locale },
  now = new Date()
): string => {
  return [
    formatDate(now),
    new Intl.DateTimeFormat(locale, { weekday: 'long' }).format(now),
    '', // location：保留字段占位，值为空
    'Web',
    translate('latestReminderLanguage'),
  ].join(',')
}
