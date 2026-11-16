package com.memoseek.mobile.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoseek.mobile.ui.theme.DsColors
import com.memoseek.mobile.ui.theme.DsTypography

/**
 * 轻量 Markdown 渲染。
 *
 * 只覆盖模型实际会输出的子集：标题、段落、粗体、行内代码、无序列表、代码块。
 * 不引第三方 Markdown 库——它们体积大且样式难对齐官方。
 *
 * 解析策略是"按空行切块"，逐块判断类型，够用且可预测；
 * 流式输出时最后一块会不断变化，这样切分不会让已渲染内容抖动。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = block.text,
                    style = DsTypography.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = DsColors.TextPrimary,
                )

                is MdBlock.Code -> Text(
                    text = block.code.trimEnd(),
                    style = DsTypography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                    color = DsColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DsColors.SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )

                is MdBlock.Bullet -> Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = DsColors.TextTertiary)) { append("•  ") }
                        appendInline(block.text)
                    },
                    style = DsTypography.bodyLarge,
                    color = DsColors.TextPrimary,
                )

                is MdBlock.Paragraph -> Text(
                    text = buildAnnotatedString { appendInline(block.text) },
                    style = DsTypography.bodyLarge,
                    color = DsColors.TextPrimary,
                )
            }
        }
    }
}

internal sealed interface MdBlock {
    data class Heading(val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Code(val code: String) : MdBlock
}

private val CODE_FENCE = "```"
private val HEADING_PREFIXES = listOf("### ", "## ", "# ")
private val BULLET_PREFIXES = listOf("- ", "* ")

internal fun parseBlocks(source: String): List<MdBlock> {
    if (source.isBlank()) return emptyList()

    val blocks = mutableListOf<MdBlock>()
    val buffer = StringBuilder()
    var inCode = false
    var codeBuffer = StringBuilder()

    fun flushParagraph() {
        val text = buffer.toString().trim()
        if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
        buffer.clear()
    }

    source.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()

        when {
            line.startsWith(CODE_FENCE) -> {
                if (inCode) {
                    blocks += MdBlock.Code(codeBuffer.toString())
                    codeBuffer = StringBuilder()
                    inCode = false
                } else {
                    flushParagraph()
                    inCode = true
                }
            }

            inCode -> codeBuffer.appendLine(rawLine)

            line.isBlank() -> flushParagraph()

            else -> {
                val heading = HEADING_PREFIXES.firstOrNull { line.startsWith(it) }
                val bullet = BULLET_PREFIXES.firstOrNull { line.startsWith(it) }
                when {
                    heading != null -> {
                        flushParagraph()
                        blocks += MdBlock.Heading(line.removePrefix(heading))
                    }

                    bullet != null -> {
                        flushParagraph()
                        blocks += MdBlock.Bullet(line.removePrefix(bullet))
                    }

                    else -> {
                        if (buffer.isNotEmpty()) buffer.append('\n')
                        buffer.append(line)
                    }
                }
            }
        }
    }

    if (inCode) blocks += MdBlock.Code(codeBuffer.toString())
    flushParagraph()
    return blocks
}

/**
 * 行内样式：粗体、行内代码。
 * 用最小的状态机扫描，避免正则回溯在大文本上卡顿。
 */
private fun AnnotatedString.Builder.appendInline(text: String) {
    var index = 0
    while (index < text.length) {
        val boldStart = text.indexOf("**", index)
        val codeStart = text.indexOf('`', index)

        val next = listOfNotNull(
            boldStart.takeIf { it >= 0 }?.let { it to true },
            codeStart.takeIf { it >= 0 }?.let { it to false },
        ).minByOrNull { it.first }

        if (next == null) {
            append(text.substring(index))
            return
        }

        if (next.first > index) append(text.substring(index, next.first))

        if (next.second) {
            val end = text.indexOf("**", next.first + 2)
            if (end < 0) {
                append(text.substring(next.first))
                return
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(text.substring(next.first + 2, end))
            }
            index = end + 2
        } else {
            val end = text.indexOf('`', next.first + 1)
            if (end < 0) {
                append(text.substring(next.first))
                return
            }
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = DsColors.Brand,
                    fontSize = 15.sp,
                )
            ) {
                append(text.substring(next.first + 1, end))
            }
            index = end + 1
        }
    }
}
