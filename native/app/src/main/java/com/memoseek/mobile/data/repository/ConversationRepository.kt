package com.memoseek.mobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.memoseek.mobile.data.model.Conversation
import com.memoseek.mobile.data.model.Message
import com.memoseek.mobile.data.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.conversationStore: DataStore<Preferences> by preferencesDataStore(name = "conversations")

/**
 * 会话持久化。
 *
 * 用 DataStore + JSON 而不是 Room：
 * 会话是「整体读写」的文档型数据，不需要按字段查询或关联，
 * 上 Room 会引入 KSP、Entity/Dao/迁移三套模板，收益不抵成本。
 *
 * 如果将来要做全文搜索（官方侧边栏有搜索框），再迁移到 Room 的 FTS。
 */
class ConversationRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val conversations: Flow<List<Conversation>> = context.conversationStore.data.map { prefs ->
        val raw = prefs[KEY_LIST] ?: return@map emptyList()
        runCatching {
            json.decodeFromString(ListSerializer, raw).map { it.toModel() }
        }.getOrDefault(emptyList())
    }

    suspend fun save(conversation: Conversation) {
        context.conversationStore.edit { prefs ->
            val current = prefs[KEY_LIST]
                ?.let { runCatching { json.decodeFromString(ListSerializer, it) }.getOrNull() }
                .orEmpty()
                .filterNot { it.id == conversation.id }
            val updated = (current + conversation.toEntity()).sortedByDescending { it.updatedAt }
            prefs[KEY_LIST] = json.encodeToString(ListSerializer, updated)
        }
    }

    suspend fun delete(id: String) {
        context.conversationStore.edit { prefs ->
            val current = prefs[KEY_LIST]
                ?.let { runCatching { json.decodeFromString(ListSerializer, it) }.getOrNull() }
                .orEmpty()
                .filterNot { it.id == id }
            prefs[KEY_LIST] = json.encodeToString(ListSerializer, current)
        }
    }

    private companion object {
        val KEY_LIST = stringPreferencesKey("list")
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(ConversationEntity.serializer())
    }
}

// ---------- 持久化实体（与领域模型分离，便于后续改存储而不动业务） ----------

@Serializable
internal data class ConversationEntity(
    val id: String,
    val title: String,
    val messages: List<MessageEntity>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
internal data class MessageEntity(
    val id: String,
    val role: String,
    val content: String = "",
    val thinking: String = "",
    val thinkingStartedAt: Long? = null,
    val thinkingEndedAt: Long? = null,
)

internal fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    messages = messages.map {
        MessageEntity(it.id, it.role.name, it.content, it.thinking, it.thinkingStartedAt, it.thinkingEndedAt)
    },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ConversationEntity.toModel() = Conversation(
    id = id,
    title = title,
    messages = messages.map {
        Message(
            id = it.id,
            role = runCatching { Role.valueOf(it.role) }.getOrDefault(Role.Assistant),
            content = it.content,
            thinking = it.thinking,
            thinkingStartedAt = it.thinkingStartedAt,
            thinkingEndedAt = it.thinkingEndedAt,
        )
    },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
