package com.mroldl001.m3l

import android.text.util.Linkify
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 渲染库自带的主题参数。调用方用 CompositionLocalProvider(LocalMarkdownRendererTheme provides ...)
 * 注入 primary / surface / outlineVariant，渲染库不读取 MaterialTheme。
 */
data class MarkdownRendererTheme(
    val primary: Color,
    val surface: Color,
    val outlineVariant: Color
)

val LocalMarkdownRendererTheme = compositionLocalOf {
    MarkdownRendererTheme(
        primary = Color.Black,
        surface = Color.White,
        outlineVariant = Color.LightGray
    )
}

/** The host decides how to open links. No external activity is launched by default. */
val LocalMarkdownLinkHandler = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
fun MixedMarkdownLatex(
    text: String,
    textColor: Color,
    isStreaming: Boolean = false,
    compact: Boolean = false,
    onLatexWidthMeasured: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownRendererTheme.current
    val inlineCodeBackground by rememberUpdatedState(theme.primary.copy(alpha = 0.15f).toArgb())
    val inlineCodeText by rememberUpdatedState(theme.primary.toArgb())
    val markdownLinkColor = theme.primary
    val markdownSelectionColors = TextSelectionColors(
        handleColor = theme.primary,
        backgroundColor = theme.primary.copy(alpha = 0.35f)
    )
    val latestText by rememberUpdatedState(text)
    var renderedText by remember {
        mutableStateOf(if (isStreaming) stabilizeStreamingMarkdown(text) else text)
    }
    LaunchedEffect(text, isStreaming) {
        if (!isStreaming) renderedText = text
    }
    LaunchedEffect(isStreaming) {
        if (!isStreaming) return@LaunchedEffect
        while (true) {
            val stableText = stabilizeStreamingMarkdown(latestText)
            if (stableText != renderedText) renderedText = stableText
            delay(80)
        }
    }
    val displayText = if (isStreaming) renderedText else text
    val segments by remember(displayText) {
        derivedStateOf { splitContent(normalizeHtmlToMarkdown(displayText), allowUnclosedCodeFence = true) }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        segments.forEach { segment ->
            when (segment.type) {
                ContentType.MARKDOWN -> {
                    val markdown = segment.content
                    key(markdownLinkColor, textColor) {
                        MarkdownText(
                            markdown = markdown,
                            modifier = Modifier.fillMaxWidth(),
                            linkColor = markdownLinkColor,
                            linkifyMask = Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES,
                            textSelectionColors = markdownSelectionColors,
                            style = TextStyle(
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            isTextSelectable = true,
                            afterSetMarkdown = { textView ->
                                applyInlineCodeStyle(
                                    textView,
                                    inlineCodeBackground,
                                    inlineCodeText
                                )
                            },
                            onWidthMeasured = onLatexWidthMeasured
                        )
                    }
                }
                ContentType.TABLE -> {
                    Box(modifier = Modifier.padding(vertical = 12.dp)) {
                        MarkdownTableView(
                            markdown = segment.content,
                            textColor = textColor
                        )
                    }
                }
                ContentType.CODE_BLOCK -> {
                    CodeBlockView(code = segment.content, info = segment.info, primaryTint = theme.primary)
                }
                ContentType.LATEX_BLOCK -> {
                    LatexBlockImage(
                        latex = segment.content,
                        textColor = textColor,
                        compact = compact,
                        onWidthMeasured = onLatexWidthMeasured
                    )
                }
                ContentType.LATEX_INLINE -> {
                    LatexInlineLine(
                        line = segment.content,
                        textColor = textColor,
                        onWidthMeasured = onLatexWidthMeasured
                    )
                }
                ContentType.QUOTE -> {
                    QuoteBlockView(content = segment.content, textColor = textColor)
                }
                ContentType.HR -> {
                    HrView()
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun QuoteBlockView(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val theme = LocalMarkdownRendererTheme.current
    val quoteBar = theme.primary.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(quoteBar)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            MixedMarkdownLatex(text = content, textColor = textColor)
        }
    }
}

@Composable
private fun HrView() {
    val theme = LocalMarkdownRendererTheme.current
    val hrColor = theme.primary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hrColor)
        )
    }
}

@Composable
private fun MarkdownTableView(
    markdown: String,
    textColor: Color
) {
    val theme = LocalMarkdownRendererTheme.current
    val rows = remember(markdown) {
        markdown.lineSequence()
            .map { it.removeSuffix("\r").trim() }
            .filter { it.isNotBlank() && !isMarkdownTableDelimiter(it) }
            .map { line ->
                markdownTableCells(line)
            }
            .toList()
    }

    if (rows.isEmpty()) return

    val tableShape = RoundedCornerShape(10.dp)
    val borderColor = theme.outlineVariant.copy(alpha = 0.58f)
    val headerColor = theme.primary.copy(alpha = 0.12f)
    val maxColumnCount = rows.maxOf { it.size }
    val density = LocalDensity.current
    val renderedFormulaWidths = remember(markdown, density.density, density.fontScale) {
        mutableStateMapOf<Int, Float>()
    }
    val columnWidths = List(maxColumnCount) { columnIndex ->
        val widestCell = rows.fold(0) { widest, row ->
            val cellText = stripLatexForTableWidth(row.getOrNull(columnIndex).orEmpty())
            val cellWidth = cellText.fold(0) { width, character ->
                width + if (character.code > 0x7F) 14 else 8
            }
            maxOf(widest, cellWidth)
        }
        val estimatedWidth = (widestCell + 28).coerceIn(96, 260).dp
        val renderedWidth = (renderedFormulaWidths[columnIndex] ?: 0f).dp + 28.dp
        maxOf(estimatedWidth, renderedWidth)
    }
    val tableWidth = columnWidths.fold(0.dp) { total, width -> total + width }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .width(tableWidth)
                .clip(tableShape)
                .border(BorderStroke(1.dp, borderColor), tableShape)
        ) {
            rows.forEachIndexed { rowIndex, cells ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    repeat(maxColumnCount) { columnIndex ->
                        val cell = cells.getOrNull(columnIndex).orEmpty()
                        Box(
                            modifier = Modifier
                                .width(columnWidths[columnIndex])
                                .fillMaxHeight()
                                .background(if (rowIndex == 0) headerColor else theme.surface)
                                .border(BorderStroke(0.5.dp, borderColor))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            MixedMarkdownLatex(
                                text = cell.replace("\\|", "|").let {
                                    if (rowIndex == 0 && !it.startsWith("**")) "**$it**" else it
                                },
                                textColor = textColor,
                                compact = true,
                                onLatexWidthMeasured = { width ->
                                    val previous = renderedFormulaWidths[columnIndex] ?: 0f
                                    if (width > previous) {
                                        renderedFormulaWidths[columnIndex] = width
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val LATEX_BLOCK_DOLLAR = Regex("\\$\\$[\\s\\S]*?\\$\\$")
private val LATEX_INLINE_DOLLAR = Regex("(?<!\\\\)\\$(?!\\$)[^$]*?\\$")
private val LATEX_BRACKET = Regex("""\\\[[\s\S]*?\\\]""")
private val LATEX_PAREN = Regex("""\\\([\s\S]*?\\\)""")

internal fun stripLatexForTableWidth(cell: String): String {
    return cell
        .replace(LATEX_BLOCK_DOLLAR, "")
        .replace(LATEX_INLINE_DOLLAR, "")
        .replace(LATEX_BRACKET, "")
        .replace(LATEX_PAREN, "")
        .trim()
}
