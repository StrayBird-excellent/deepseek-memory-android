package com.memoseek.mobile

import com.memoseek.mobile.ui.chat.MdBlock
import com.memoseek.mobile.ui.chat.parseBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 解析是正文渲染的基础。
 * 流式输出时文本是不完整的，解析器必须在任何前缀上都稳定，不能崩。
 */
class MarkdownParseTest {

    @Test
    fun `空输入返回空列表`() {
        assertTrue(parseBlocks("").isEmpty())
        assertTrue(parseBlocks("   \n  ").isEmpty())
    }

    @Test
    fun `标题与段落分开`() {
        val blocks = parseBlocks("## 小标题\n\n正文内容")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals("小标题", (blocks[0] as MdBlock.Heading).text)
        assertTrue(blocks[1] is MdBlock.Paragraph)
    }

    @Test
    fun `无序列表逐条成块`() {
        val blocks = parseBlocks("- 第一项\n- 第二项")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MdBlock.Bullet })
        assertEquals("第一项", (blocks[0] as MdBlock.Bullet).text)
    }

    @Test
    fun `代码块内容原样保留`() {
        val blocks = parseBlocks("```\nval a = 1\nprintln(a)\n```")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Code)
        assertTrue((blocks[0] as MdBlock.Code).code.contains("println(a)"))
    }

    @Test
    fun `未闭合的代码块不丢内容`() {
        // 流式输出中途常见：围栏已出现但结尾还没到
        val blocks = parseBlocks("```\nval a = 1")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Code)
        assertTrue((blocks[0] as MdBlock.Code).code.contains("val a = 1"))
    }

    @Test
    fun `多行段落合并为一个块`() {
        val blocks = parseBlocks("第一行\n第二行")
        assertEquals(1, blocks.size)
        val text = (blocks[0] as MdBlock.Paragraph).text
        assertTrue(text.contains("第一行"))
        assertTrue(text.contains("第二行"))
    }
}
