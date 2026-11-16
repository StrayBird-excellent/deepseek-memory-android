package com.memoseek.mobile

import com.memoseek.mobile.data.memory.MemoryExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抽取规则的边界。
 *
 * 原则是「宁可漏抽，不可错抽」：错抽会长期污染上下文，
 * 所以疑问句、指代不明的句子都应当被拒绝。
 */
class MemoryExtractorTest {

    @Test
    fun `识别自报姓名`() {
        assertEquals(listOf("用户叫「小明」"), MemoryExtractor.extract("我叫小明"))
        assertEquals(listOf("用户叫「李雷」"), MemoryExtractor.extract("我的名字是李雷"))
    }

    @Test
    fun `识别正在做的项目`() {
        assertTrue(MemoryExtractor.extract("我在做一个桌宠项目").single().contains("桌宠项目"))
        assertTrue(MemoryExtractor.extract("我的项目是深语").single().contains("深语"))
    }

    @Test
    fun `识别偏好`() {
        assertTrue(MemoryExtractor.extract("我喜欢用 Kotlin 写东西").isNotEmpty())
        assertTrue(MemoryExtractor.extract("我习惯用深色模式").single().contains("深色模式"))
    }

    @Test
    fun `疑问句不抽取`() {
        assertTrue(MemoryExtractor.extract("你叫什么名字？").isEmpty())
        assertTrue(MemoryExtractor.extract("我该怎么做一个 App").isEmpty())
    }

    @Test
    fun `过短或过长的输入不抽取`() {
        assertTrue(MemoryExtractor.extract("我叫").isEmpty())
        assertTrue(MemoryExtractor.extract("我".repeat(300)).isEmpty())
    }

    @Test
    fun `一条消息最多产出一条记忆`() {
        val result = MemoryExtractor.extract("我叫小明，我在做一个桌宠项目，我喜欢 Kotlin")
        assertEquals(1, result.size)
    }
}
