package com.memoseek.mobile.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeepSeek Chat Completions 的请求/响应模型。
 *
 * 字段名严格对齐官方 API，不做任何"更名美化"——
 * 保持与文档一一对应，排查线上问题时能直接对照。
 */

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<MessageDto>,
    val stream: Boolean = true,
    /** 深度思考开关；关闭时不发送该字段，与官方行为一致 */
    val thinking: ThinkingConfig? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
)

@Serializable
data class ThinkingConfig(
    val type: String = "enabled",
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String,
)

// ---------- 流式响应 ----------

@Serializable
data class StreamChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: StreamDelta = StreamDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class StreamDelta(
    /** 正文增量 */
    val content: String? = null,
    /** 思考过程增量，仅在开启深度思考时下发 */
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

// ---------- 余额 ----------

@Serializable
data class BalanceResponse(
    @SerialName("is_available") val isAvailable: Boolean = false,
    @SerialName("balance_infos") val balanceInfos: List<BalanceInfoDto> = emptyList(),
)

@Serializable
data class BalanceInfoDto(
    val currency: String = "CNY",
    @SerialName("total_balance") val totalBalance: String = "0",
    @SerialName("granted_balance") val grantedBalance: String = "0",
    @SerialName("topped_up_balance") val toppedUpBalance: String = "0",
)

// ---------- 错误 ----------

@Serializable
data class ApiError(
    val error: ApiErrorBody? = null,
)

@Serializable
data class ApiErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
