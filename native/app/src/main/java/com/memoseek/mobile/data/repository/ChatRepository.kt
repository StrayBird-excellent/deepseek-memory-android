package com.memoseek.mobile.data.repository

import com.memoseek.mobile.data.model.Message
import com.memoseek.mobile.data.model.Role
import com.memoseek.mobile.data.remote.ChatEvent
import com.memoseek.mobile.data.remote.ChatRequest
import com.memoseek.mobile.data.remote.DeepSeekClient
import com.memoseek.mobile.data.remote.MessageDto
import com.memoseek.mobile.data.model.DefaultModel
import com.memoseek.mobile.data.remote.ThinkingConfig
import kotlinx.coroutines.flow.Flow

/**
 * 对话数据源。
 *
 * 只做「领域模型 → 请求 DTO」的翻译，不掺任何 UI 逻辑：
 * 这样换模型、换接口版本时只改这一层，ViewModel 不受影响。
 */
class ChatRepository(private val client: DeepSeekClient = DeepSeekClient()) {

    fun send(
        apiKey: String,
        history: List<Message>,
        model: String = DefaultModel.id,
        thinking: Boolean = false,
        search: Boolean = false,
        memories: List<String> = emptyList(),
    ): Flow<ChatEvent> {
        // 有记忆时补一条 system 消息；没有就完全不发，避免空 system 影响模型表现
        val systemMessage = memories.takeIf { it.isNotEmpty() }?.let {
            MessageDto(role = "system", content = buildMemoryPrompt(it))
        }
        val payload = ChatRequest(
            model = model,
            messages = listOfNotNull(systemMessage) +
                history.map { MessageDto(role = it.role.wireName, content = it.content) },
            stream = true,
            thinking = if (thinking) ThinkingConfig() else null,
        )
        return client.streamChat(apiKey, payload)
    }

    /**
     * 记忆注入模板。
     *
     * 明确要求「不要复述记忆」——否则模型会开头来一句
     * 「我记得你叫小明」，这在多轮对话里非常出戏。
     */
    private fun buildMemoryPrompt(memories: List<String>): String = buildString {
        appendLine("以下是与用户相关的稳定信息，请在需要时自然地运用，不要主动复述这些内容，也不要提及「记忆」这件事：")
        memories.forEach { appendLine("- $it") }
    }

    companion object {
        private val Role.wireName: String
            get() = when (this) {
                Role.User -> "user"
                Role.Assistant -> "assistant"
            }
    }
}
