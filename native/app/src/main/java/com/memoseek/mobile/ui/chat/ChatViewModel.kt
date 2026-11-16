package com.memoseek.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memoseek.mobile.data.memory.MemoryExtractor
import com.memoseek.mobile.data.model.Conversation
import com.memoseek.mobile.data.model.Message
import com.memoseek.mobile.data.model.Role
import com.memoseek.mobile.data.remote.ChatEvent
import com.memoseek.mobile.data.repository.ChatRepository
import com.memoseek.mobile.data.repository.ConversationRepository
import com.memoseek.mobile.data.repository.MemoryRepository
import com.memoseek.mobile.data.repository.SettingsRepository
import com.memoseek.mobile.ui.i18n.DsText
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 聊天页的完整 UI 状态。单一数据源，界面只读它。 */
data class ChatUiState(
    val title: String = DsText.current.newChat,
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val thinking: Boolean = false,
    val search: Boolean = false,
    val streaming: Boolean = false,
    val apiKeyReady: Boolean = false,
    val errorBanner: String? = null,
)

/**
 * 聊天状态机。
 *
 * 三条关键设计：
 * 1. 流式只改最后一条消息的引用，其余保持原对象，LazyColumn 才能精准复用。
 * 2. 停止生成 = 取消协程；Flow 是冷的，取消会连带取消底层 SSE 请求。
 * 3. 落盘只在本轮结束做一次——每个 token 都写 DataStore 会把 IO 打满。
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    private var conversationId: String = UUID.randomUUID().toString()
    private var conversationCreatedAt: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            val key = settingsRepository.apiKey.first()
            _state.update { it.copy(apiKeyReady = key.isNotBlank()) }
        }
    }

    fun onInputChange(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun toggleThinking() {
        _state.update { it.copy(thinking = !it.thinking) }
    }

    fun toggleSearch() {
        _state.update { it.copy(search = !it.search) }
    }

    fun dismissError() {
        _state.update { it.copy(errorBanner = null) }
    }

    /** 载入一条历史会话 */
    fun openConversation(conversation: Conversation) {
        conversationId = conversation.id
        conversationCreatedAt = conversation.createdAt
        _state.update {
            it.copy(
                title = conversation.title,
                messages = conversation.messages,
                input = "",
                errorBanner = null,
            )
        }
    }

    /** 开新对话：换一个新 id，避免覆盖上一条会话 */
    fun newConversation() {
        conversationId = UUID.randomUUID().toString()
        conversationCreatedAt = System.currentTimeMillis()
        _state.update {
            it.copy(title = DsText.current.newChat, messages = emptyList(), input = "", errorBanner = null)
        }
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.streaming) return
        val text = snapshot.input.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val apiKey = settingsRepository.apiKey.first()
            val modelId = settingsRepository.modelId.first()
            val memories = memoryRepository.topMemories()
            if (apiKey.isBlank()) {
                _state.update { it.copy(errorBanner = DsText.current.errorNoApiKey) }
                return@launch
            }

            // 抽取与发送并行：抽取失败不能影响这次对话
            launch {
                MemoryExtractor.extract(text).forEach { runCatching { memoryRepository.add(it) } }
            }

            val userMessage = Message(id = UUID.randomUUID().toString(), role = Role.User, content = text)
            val assistantMessage = Message(
                id = UUID.randomUUID().toString(),
                role = Role.Assistant,
                streaming = true,
            )
            val history = snapshot.messages + userMessage

            val nextTitle = if (snapshot.messages.isEmpty()) {
                Conversation.deriveTitle(listOf(userMessage))
            } else {
                snapshot.title
            }
            _state.update {
                it.copy(
                    title = nextTitle,
                    messages = it.messages + userMessage + assistantMessage,
                    input = "",
                    streaming = true,
                    apiKeyReady = true,
                    errorBanner = null,
                )
            }

            sendJob = launch {
                try {
                    chatRepository.send(
                        apiKey = apiKey,
                        history = history,
                        model = modelId,
                        memories = memories.map { it.content },
                        thinking = snapshot.thinking,
                        search = snapshot.search,
                    ).collect { event ->
                        when (event) {
                            is ChatEvent.Text -> patchMessage(assistantMessage.id) {
                                it.copy(content = it.content + event.delta)
                            }
                            is ChatEvent.Thinking -> patchMessage(assistantMessage.id) { current ->
                                current.copy(
                                    thinking = current.thinking + event.delta,
                                    thinkingStartedAt = current.thinkingStartedAt
                                        ?: System.currentTimeMillis(),
                                )
                            }
                            is ChatEvent.Done -> patchMessage(assistantMessage.id) { current ->
                                current.copy(
                                    streaming = false,
                                    thinkingEndedAt = current.thinkingEndedAt
                                        ?: System.currentTimeMillis().takeIf { current.thinking.isNotEmpty() },
                                )
                            }
                            is ChatEvent.Failed -> {
                                val reason = event.localizedReason()
                                patchMessage(assistantMessage.id) {
                                    it.copy(streaming = false, error = reason)
                                }
                                _state.update { it.copy(errorBanner = reason) }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 用户主动停止：保留已生成内容，并给思考计时封口
                    patchMessage(assistantMessage.id) { current ->
                        current.copy(
                            streaming = false,
                            thinkingEndedAt = current.thinkingEndedAt
                                ?: System.currentTimeMillis().takeIf { current.thinking.isNotEmpty() },
                        )
                    }
                    throw e
                } finally {
                    _state.update { it.copy(streaming = false) }
                    persist()
                    // 本轮引用过的记忆提升权重，没人用的靠衰减自然淘汰
                    runCatching { memoryRepository.touchUsed(memories.map { it.id }) }
                }
            }
        }
    }

    /**
     * 重试上一次失败的回答。
     *
     * 做法是把「失败的助手消息 + 对应的用户消息」一起摘掉，
     * 再把用户那句放回输入框重发——这样不会在历史里留下半截消息。
     */
    fun retryLast() {
        val messages = _state.value.messages
        val lastAssistant = messages.lastOrNull { it.role == Role.Assistant } ?: return
        if (lastAssistant.error == null) return
        val lastUser = messages.lastOrNull { it.role == Role.User } ?: return

        _state.update {
            it.copy(
                messages = messages.dropLast(2),
                input = lastUser.content,
                errorBanner = null,
            )
        }
        send()
    }

    /** 当前会话 id，删除会话时用来判断是否正在查看它 */
    fun currentConversationId(): String = conversationId

    /**
     * 重新生成上一条回答。
     *
     * 与 retryLast 的区别：重试针对失败，会连用户消息一起摘掉；
     * 重新生成针对成功的回答，同样摘掉一问一答再发一遍，保证上下文干净。
     */
    fun regenerate() {
        val messages = _state.value.messages
        val lastAssistant = messages.lastOrNull { it.role == Role.Assistant } ?: return
        if (lastAssistant.streaming || messages.any { it.streaming }) return
        val lastUser = messages.lastOrNull { it.role == Role.User } ?: return

        _state.update {
            it.copy(
                messages = messages.dropLast(2),
                input = lastUser.content,
                errorBanner = null,
            )
        }
        send()
    }

    fun stop() {
        sendJob?.cancel()
        sendJob = null
        _state.update { it.copy(streaming = false) }
    }

    /** 只替换目标那条消息，其余保持原引用，LazyColumn 才能精准复用 */
    private fun patchMessage(id: String, transform: (Message) -> Message) {
        _state.update { current ->
            val index = current.messages.indexOfFirst { it.id == id }
            if (index < 0) {
                current
            } else {
                val updated = current.messages.toMutableList().apply {
                    this[index] = transform(this[index])
                }
                current.copy(messages = updated)
            }
        }
    }

    /** 本轮结束后整体落盘一次 */
    private fun persist() {
        val messages = _state.value.messages
        if (messages.isEmpty()) return
        val conversation = Conversation(
            id = conversationId,
            title = Conversation.deriveTitle(messages),
            // streaming 标记不落盘，否则重启后会显示成一直在生成
            messages = messages.map { it.copy(streaming = false) },
            createdAt = conversationCreatedAt,
            updatedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            runCatching { conversationRepository.save(conversation) }
        }
    }

    class Factory(
        private val chatRepository: ChatRepository,
        private val settingsRepository: SettingsRepository,
        private val conversationRepository: ConversationRepository,
        private val memoryRepository: MemoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(chatRepository, settingsRepository, conversationRepository, memoryRepository) as T
    }
}

/**
 * 把 HTTP 状态码翻成当前语言的提示。
 * 放在 UI 层而不是网络层，是因为网络层不该知道界面语言。
 */
private fun ChatEvent.Failed.localizedReason(): String = when (code) {
    401 -> DsText.current.errorInvalidKey
    402 -> DsText.current.errorInsufficientBalance
    429 -> DsText.current.errorRateLimited
    null -> DsText.current.errorNetwork
    else -> String.format(DsText.current.errorHttpFormat, code)
}
