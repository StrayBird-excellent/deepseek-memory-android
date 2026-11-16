package com.memoseek.mobile.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * 流式对话事件。
 *
 * 用密封接口而不是回调，是为了让 ViewModel 侧能用 when 穷举，
 * 新增事件类型时编译器会强制补齐处理分支。
 */
sealed interface ChatEvent {
    /** 正文增量 */
    data class Text(val delta: String) : ChatEvent

    /** 思考过程增量（深度思考开启时才有） */
    data class Thinking(val delta: String) : ChatEvent

    /** 正常结束，附带 token 用量 */
    data class Done(val usage: Usage?) : ChatEvent

    /** 失败：网络、鉴权、限流等 */
    data class Failed(val message: String, val code: Int? = null) : ChatEvent
}

/**
 * DeepSeek API 客户端。
 *
 * SSE 用 Square 官方的 okhttp-sse（EventSources），不是手写分帧解析：
 * 断流、重连、事件分块都由库处理，减少自研解析器的边界 bug。
 *
 * 读超时给到 5 分钟：长回答 + 思考过程可能连续几十秒没有新数据，
 * 用默认 10 秒会误判断流。
 */
class DeepSeekClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    /**
     * 发起一次流式对话。
     *
     * 注意：Flow 是冷的，收集才开始请求；取消收集会自动取消底层 EventSource，
     * 这正是「停止生成」按钮需要的语义。
     */
    fun streamChat(apiKey: String, request: ChatRequest): Flow<ChatEvent> = callbackFlow {
        val payload = json.encodeToString(ChatRequest.serializer(), request)
        val httpRequest = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data.isBlank() || data == DONE_SENTINEL) {
                    return
                }
                val chunk = runCatching {
                    json.decodeFromString(StreamChunk.serializer(), data)
                }.getOrNull() ?: return

                val delta = chunk.choices.firstOrNull()?.delta
                delta?.reasoningContent?.takeIf { it.isNotEmpty() }?.let {
                    trySend(ChatEvent.Thinking(it))
                }
                delta?.content?.takeIf { it.isNotEmpty() }?.let {
                    trySend(ChatEvent.Text(it))
                }
                chunk.choices.firstOrNull()?.finishReason?.let {
                    trySend(ChatEvent.Done(chunk.usage))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val code = response?.code
                // 文案交给 UI 层按 code 翻译；这里只保证一定有一条可读的兜底信息
                val message = t?.message ?: response?.message ?: "network error"
                trySend(ChatEvent.Failed(message, code))
                channel.close()
            }
        }

        val source = EventSources.createFactory(httpClient).newEventSource(httpRequest, listener)

        // 收集方取消时同步取消网络请求，避免后台白发流量
        awaitClose { source.cancel() }
    }

    /** 查询余额，失败抛异常由调用方处理 */
    suspend fun fetchBalance(apiKey: String): BalanceResponse {
        val request = Request.Builder()
            .url("$BASE_URL/user/balance")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.decodeFromString(ApiError.serializer(), body).error?.message
                }.getOrNull() ?: "HTTP ${response.code}"
                throw DeepSeekException(message, response.code)
            }
            return json.decodeFromString(BalanceResponse.serializer(), body)
        }
    }

    companion object {
        const val BASE_URL = "https://api.deepseek.com"

        private const val DONE_SENTINEL = "[DONE]"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // 流式响应可能长时间无新数据，读写超时必须放宽
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

class DeepSeekException(message: String, val code: Int? = null) : Exception(message)
