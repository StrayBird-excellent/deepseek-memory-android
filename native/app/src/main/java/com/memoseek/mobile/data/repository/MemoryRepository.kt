package com.memoseek.mobile.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.memoseek.mobile.data.model.Memory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.memoryStore: DataStore<Preferences> by preferencesDataStore(name = "memories")

/**
 * 记忆仓库。
 *
 * 与会话一样用 DataStore + JSON：记忆条目通常几十条，全量读写完全够用。
 * 超过千条再考虑换 Room，届时才需要按权重排序的索引。
 */
class MemoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val memories: Flow<List<Memory>> = context.memoryStore.data.map { prefs ->
        val raw = prefs[KEY_LIST] ?: return@map emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(MemoryEntity.serializer()), raw).map { it.toModel() }
        }.getOrDefault(emptyList())
    }

    /**
     * 取当前最该注入的记忆。
     *
     * 排序依据是衰减后的权重而不是原始权重，
     * 这样半年前的强记忆会自然让位给近期的弱记忆。
     */
    suspend fun topMemories(limit: Int = 6): List<Memory> {
        val all = memories.first()
        val now = System.currentTimeMillis()
        return all
            .filter { it.effectiveWeight(now) >= Memory.FORGET_THRESHOLD }
            .sortedByDescending { it.effectiveWeight(now) }
            .take(limit)
    }

    suspend fun add(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        update { current ->
            // 内容重复就不再加一条，只提升权重
            val existing = current.firstOrNull { it.content.equals(trimmed, ignoreCase = true) }
            if (existing != null) {
                current.map { if (it.id == existing.id) it.used() else it }
            } else {
                current + Memory(id = UUID.randomUUID().toString(), content = trimmed)
            }
        }
    }

    suspend fun remove(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    suspend fun touchUsed(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        update { current -> current.map { if (it.id in ids) it.used(now) else it } }
    }

    /** 清理衰减到阈值以下的记忆 */
    suspend fun prune() {
        val now = System.currentTimeMillis()
        update { current -> current.filter { it.effectiveWeight(now) >= Memory.FORGET_THRESHOLD } }
    }

    private suspend fun update(transform: (List<Memory>) -> List<Memory>) {
        context.memoryStore.edit { prefs ->
            val current = prefs[KEY_LIST]
                ?.let { runCatching { json.decodeFromString(ListSerializer(MemoryEntity.serializer()), it) }.getOrNull() }
                .orEmpty()
                .map { it.toModel() }
            val next = transform(current)
            prefs[KEY_LIST] = json.encodeToString(ListSerializer(MemoryEntity.serializer()), next.map { it.toEntity() })
        }
    }

    private companion object {
        val KEY_LIST = stringPreferencesKey("list")
    }
}

@Serializable
internal data class MemoryEntity(
    val id: String,
    val content: String,
    val weight: Float = 0.5f,
    val createdAt: Long,
    val lastUsedAt: Long,
    val useCount: Int = 0,
)

internal fun Memory.toEntity() = MemoryEntity(id, content, weight, createdAt, lastUsedAt, useCount)

internal fun MemoryEntity.toModel() = Memory(id, content, weight, createdAt, lastUsedAt, useCount)
