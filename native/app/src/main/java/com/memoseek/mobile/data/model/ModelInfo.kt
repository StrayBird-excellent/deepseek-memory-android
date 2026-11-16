package com.memoseek.mobile.data.model

/**
 * 可选模型。
 *
 * 官方目前只有这两款，直接内置而不是每次启动拉 /models：
 * 列表变动极低频，内置可以省一次网络请求，也让首屏无需等待。
 * 将来模型增多时再改成「内置兜底 + 接口刷新」。
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val tagline: String,
    /** 是否支持（或强制）深度思考 */
    val supportsThinking: Boolean,
)

val AvailableModels = listOf(
    ModelInfo(
        id = "deepseek-chat",
        name = "DeepSeek V3",
        tagline = "通用对话，响应快",
        supportsThinking = true,
    ),
    ModelInfo(
        id = "deepseek-reasoner",
        name = "DeepSeek R1",
        tagline = "深度推理，结论更严谨",
        supportsThinking = true,
    ),
)

val DefaultModel: ModelInfo = AvailableModels.first()

fun modelById(id: String): ModelInfo =
    AvailableModels.firstOrNull { it.id == id } ?: DefaultModel
