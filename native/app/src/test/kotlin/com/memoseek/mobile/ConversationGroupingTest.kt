package com.memoseek.mobile

import com.memoseek.mobile.data.model.Conversation
import com.memoseek.mobile.data.model.Message
import com.memoseek.mobile.data.model.Role
import com.memoseek.mobile.ui.sider.groupConversations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 时间分组是侧边栏的核心逻辑，边界一旦算错，
 * 昨天的对话会跑到"7 天内"，或去年的对话跑到"30 天内"。
 */
class ConversationGroupingTest {

    private fun fixedNow(): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 16, 14, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun conversation(id: String, updatedAt: Long) = Conversation(
        id = id,
        title = id,
        messages = listOf(Message(id = "m-$id", role = Role.User, content = id)),
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `空列表返回空分组`() {
        assertTrue(groupConversations(emptyList(), fixedNow()).isEmpty())
    }

    @Test
    fun `按今天昨天七天三十天和更早分组`() {
        val now = fixedNow()
        val day = 24 * 60 * 60 * 1000L

        val result = groupConversations(
            listOf(
                conversation("today", now - 60_000),
                conversation("yesterday", now - day),
                conversation("week", now - 3 * day),
                conversation("month", now - 20 * day),
                conversation("old", now - 200 * day),
            ),
            now,
        )

        assertEquals(listOf("今天", "昨天", "7 天内", "30 天内"), result.take(4).map { it.title })
        assertEquals("today", result[0].conversations.single().id)
        assertEquals("old", result.last().conversations.single().id)
        // 更早的归到「YYYY年M月」
        assertTrue(result.last().title.endsWith("月"))
    }

    @Test
    fun `组内按更新时间倒序`() {
        val now = fixedNow()
        val result = groupConversations(
            listOf(
                conversation("a", now - 3 * 60_000),
                conversation("b", now - 60_000),
                conversation("c", now - 2 * 60_000),
            ),
            now,
        )
        assertEquals(listOf("b", "c", "a"), result.single().conversations.map { it.id })
    }

    @Test
    fun `跨天边界按自然日而不是二十四小时`() {
        val now = fixedNow()
        // 今天 00:10，虽然只过去了不到 24 小时，但属于「今天」
        val earlyToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 10)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val result = groupConversations(listOf(conversation("early", earlyToday)), now)
        assertEquals("今天", result.single().title)
    }
}
