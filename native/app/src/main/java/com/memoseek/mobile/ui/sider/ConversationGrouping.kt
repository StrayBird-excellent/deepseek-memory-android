package com.memoseek.mobile.ui.sider

import com.memoseek.mobile.data.model.Conversation
import com.memoseek.mobile.ui.i18n.DsStrings
import com.memoseek.mobile.ui.i18n.DsText
import java.util.Calendar

/**
 * 会话按时间分组。
 *
 * 分组口径对齐官方侧边栏：昨天 / 7 天内 / 30 天内 / 具体年月。
 * 用自然日边界而不是「24 小时」，否则凌晨一点会把前一晚的对话算成今天。
 */
data class ConversationGroup(
    val title: String,
    val conversations: List<Conversation>,
)

fun groupConversations(
    conversations: List<Conversation>,
    now: Long = System.currentTimeMillis(),
    strings: DsStrings = DsText.current,
): List<ConversationGroup> {
    if (conversations.isEmpty()) return emptyList()

    val todayStart = startOfDay(now)
    val yesterdayStart = todayStart - DAY_MILLIS
    val weekStart = todayStart - 6 * DAY_MILLIS
    val monthStart = todayStart - 29 * DAY_MILLIS

    val buckets = linkedMapOf<String, MutableList<Conversation>>()
    conversations.sortedByDescending { it.updatedAt }.forEach { item ->
        val key = when {
            item.updatedAt >= todayStart -> strings.groupToday
            item.updatedAt >= yesterdayStart -> strings.groupYesterday
            item.updatedAt >= weekStart -> strings.groupWeek
            item.updatedAt >= monthStart -> strings.groupMonth
            else -> formatMonth(item.updatedAt, strings)
        }
        buckets.getOrPut(key) { mutableListOf() }.add(item)
    }

    return buckets.map { (title, list) -> ConversationGroup(title, list) }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatMonth(timestamp: Long, strings: DsStrings): String = Calendar.getInstance().apply {
    timeInMillis = timestamp
}.let {
    String.format(strings.groupYearMonth, it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
}
