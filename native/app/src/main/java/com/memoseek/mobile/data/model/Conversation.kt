package com.memoseek.mobile.data.model

import com.memoseek.mobile.ui.i18n.DsText

/**
 * 一次完整会话。
 *
 * 存的是快照而不是增量事件：体积略大，但恢复时不需要重放，
 * 打开会话就是一次 JSON 解析，长对话也不会卡。
 */
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<Message>,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        /** 标题取首条用户消息的前若干字，与官方侧边栏展示一致 */
        fun deriveTitle(messages: List<Message>): String {
            val first = messages.firstOrNull { it.role == Role.User }?.content?.trim().orEmpty()
            return when {
                first.isEmpty() -> DsText.current.newChat
                first.length <= 18 -> first
                else -> first.take(18) + "…"
            }
        }
    }
}
