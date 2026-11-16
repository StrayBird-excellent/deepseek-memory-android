import type { Locale } from '../settings.js'
import { formatDate } from '../../utils/format.js'
import { translate } from '../../i18n/index.js'

/**
 * 模型约定的「最新提醒」信息：日期、星期、平台、语言。
 * 日期与星期使用浏览器本地时区，避免 UTC 日期跨日。
 *
 * 位置信息已移除：它既不是回答问题的必要条件，还要求用户授权选择省市，
 * 对绝大多数对话没有价值。参数仍接收整个 settings，调用方无需改动。
 */
export const createLatestReminder = (
  { locale }: { locale: Locale },
  now = new Date()
): string => {
  return [
    formatDate(now),
    new Intl.DateTimeFormat(locale, { weekday: 'long' }).format(now),
    'Web',
    translate('latestReminderLanguage'),
  ].join(',')
}
