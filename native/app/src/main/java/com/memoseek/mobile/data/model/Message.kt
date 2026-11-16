package com.memoseek.mobile.data.model

/** 对话角色。目前只有用户与助手两类，工具角色在引入搜索后再补。 */
enum class Role { User, Assistant }

/**
 * 一条消息。
 *
 * thinking 与 content 分开存：官方把思考过程和正文分开展示，
 * 混在一个字段里后续无法还原折叠状态。
 */
data class Message(
    val id: String,
    val role: Role,
    val content: String = "",
    val thinking: String = "",
    /** 是否仍在流式输出中，用于显示光标 */
    val streaming: Boolean = false,
    /** 思考开始时间戳；与 endedAt 一起算出「用时 N 秒」 */
    val thinkingStartedAt: Long? = null,
    val thinkingEndedAt: Long? = null,
    /** 失败原因，非空时该条消息展示错误态 */
    val error: String? = null,
)
