package com.mroldl001.m3l

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
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
    modifier: Modifier = Modifier,
    style: MarkdownStyle = LocalMarkdownStyle.current,
    components: MarkdownComponents = LocalMarkdownComponents.current
) {
    val latestText by rememberUpdatedState(text)
    var renderedText by remember {
        mutableStateOf(if (isStreaming) stabilizeStreamingMarkdown(text) else text)
    }
    LaunchedEffect(text, isStreaming) {
        if (!isStreaming) renderedText = text
    }
    LaunchedEffect(isStreaming, style.streamingIntervalMillis) {
        if (!isStreaming) return@LaunchedEffect
        while (true) {
            val stableText = stabilizeStreamingMarkdown(latestText)
            if (stableText != renderedText) renderedText = stableText
            delay(style.streamingIntervalMillis)
        }
    }
    val displayText = if (isStreaming) renderedText else text
    val segments by remember(displayText) {
        derivedStateOf { splitContent(normalizeHtmlToMarkdown(displayText), allowUnclosedCodeFence = true) }
    }

    CompositionLocalProvider(
        LocalMarkdownStyle provides style,
        LocalMarkdownComponents provides components
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            MarkdownBlocks(segments, textColor, compact, onLatexWidthMeasured, style, components)
        }
    }
}

@Composable
internal fun QuoteBlockView(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onLatexWidthMeasured: ((Float) -> Unit)? = null
) {
    val theme = LocalMarkdownRendererTheme.current
    val style = LocalMarkdownStyle.current
    val quoteStyle = style.quote
    val quoteBar = quoteStyle.barColor.markdownColorOr(theme.primary.copy(alpha = 0.6f))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(quoteStyle.padding)
            .background(quoteStyle.backgroundColor)
    ) {
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.width(quoteStyle.barWidth).fillMaxHeight()
                    .clip(quoteStyle.barShape).background(quoteBar)
            )
        }
        Box(
            Modifier.fillMaxWidth().padding(start = quoteStyle.barWidth + quoteStyle.spacing)
                .padding(quoteStyle.contentPadding)
        ) {
            val body = style.body.merge(quoteStyle.textStyle).let {
                if (quoteStyle.textColor == Color.Unspecified) it else it.copy(color = quoteStyle.textColor)
            }
            MixedMarkdownLatex(
                text = content,
                textColor = quoteStyle.textColor.markdownColorOr(textColor),
                compact = compact,
                onLatexWidthMeasured = onLatexWidthMeasured,
                style = style.copy(body = body)
            )
        }
    }
}

@Composable
internal fun HrView() {
    val theme = LocalMarkdownRendererTheme.current
    val style = LocalMarkdownStyle.current.divider
    val hrColor = style.color.markdownColorOr(theme.primary.copy(alpha = 0.4f))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(style.padding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(style.thickness)
                .clip(style.shape)
                .background(hrColor)
        )
    }
}

@Composable
internal fun MarkdownTableView(
    markdown: String,
    textColor: Color
) {
    val theme = LocalMarkdownRendererTheme.current
    val style = LocalMarkdownStyle.current
    val tableStyle = style.table
    val parentPath = LocalMarkdownPath.current
    val tableLines = remember(markdown) {
        markdown.lineSequence()
            .map { it.removeSuffix("\r").trim() }
            .filter { it.isNotBlank() }
            .toList()
    }
    val rows = remember(tableLines) {
        tableLines.filterNot(::isMarkdownTableDelimiter).map(::markdownTableCells)
    }
    val markdownAlignments = remember(tableLines) {
        tableLines.firstOrNull(::isMarkdownTableDelimiter)?.let(::markdownTableCells)?.map {
            when {
                it.startsWith(':') && it.endsWith(':') -> TextAlign.Center
                it.endsWith(':') -> TextAlign.Right
                else -> TextAlign.Left
            }
        }.orEmpty()
    }

    if (rows.isEmpty()) return

    val borderColor = tableStyle.borderColor.markdownColorOr(theme.outlineVariant.copy(alpha = 0.58f))
    val headerColor = tableStyle.headerBackground.markdownColorOr(theme.primary.copy(alpha = 0.12f))
    val bodyColor = tableStyle.bodyBackground.markdownColorOr(theme.surface)
    val maxColumnCount = rows.maxOf { it.size }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    val renderedFormulaWidths = remember(markdown, style, density.density, density.fontScale) {
        mutableStateMapOf<Int, Float>()
    }
    val horizontalPadding = tableStyle.cellPadding.calculateLeftPadding(layoutDirection) +
        tableStyle.cellPadding.calculateRightPadding(layoutDirection)
    val columnWidths = List(maxColumnCount) { columnIndex ->
        val widestCell = rows.mapIndexed { rowIndex, row ->
            val cellText = stripLatexForTableWidth(row.getOrNull(columnIndex).orEmpty())
            textMeasurer.measure(
                text = cellText,
                style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp).merge(style.body)
                    .merge(if (rowIndex == 0) tableStyle.headerTextStyle else tableStyle.bodyTextStyle),
                softWrap = false,
                maxLines = 1
            ).size.width
        }.maxOrNull() ?: 0
        val estimatedWidth = with(density) { widestCell.toDp() } + horizontalPadding
        val renderedWidth = (renderedFormulaWidths[columnIndex] ?: 0f).dp + horizontalPadding
        tableStyle.columnWidths.getOrNull(columnIndex) ?: maxOf(estimatedWidth, renderedWidth)
            .coerceIn(tableStyle.minColumnWidth, tableStyle.maxColumnWidth)
    }
    val tableWidth = columnWidths.fold(0.dp) { total, width -> total + width }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tableStyle.padding)
            .horizontalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .width(tableWidth)
                .clip(tableStyle.shape)
                .border(BorderStroke(tableStyle.borderWidth, borderColor), tableStyle.shape)
        ) {
            rows.forEachIndexed { rowIndex, cells ->
                val background = when {
                    rowIndex == 0 -> headerColor
                    rowIndex % 2 == 0 -> tableStyle.alternateBackground.markdownColorOr(bodyColor)
                    else -> bodyColor
                }
                Box {
                    Row(Modifier.matchParentSize()) {
                        columnWidths.forEach { width ->
                            Box(Modifier.width(width).fillMaxHeight().background(background)
                                .border(BorderStroke(tableStyle.cellBorderWidth, borderColor)))
                        }
                    }
                    Layout(content = {
                        repeat(maxColumnCount) { columnIndex ->
                            val alignment = tableStyle.columnAlignments[columnIndex]
                                ?: markdownAlignments.getOrNull(columnIndex).takeIf { tableStyle.respectMarkdownAlignment }
                            val foreground = if (rowIndex == 0) tableStyle.headerTextColor else tableStyle.bodyTextColor
                            val cellTextStyle = style.body
                                .merge(if (rowIndex == 0) tableStyle.headerTextStyle else tableStyle.bodyTextStyle)
                                .let { if (foreground == Color.Unspecified) it else it.copy(color = foreground) }
                                .let { if (alignment == null) it else it.copy(textAlign = alignment) }
                            CompositionLocalProvider(LocalMarkdownPath provides (parentPath + rowIndex + columnIndex)) {
                                Box(Modifier.padding(tableStyle.cellPadding)) {
                                    MixedMarkdownLatex(
                                        text = cells.getOrNull(columnIndex).orEmpty().replace("\\|", "|"),
                                        textColor = foreground.markdownColorOr(textColor),
                                        compact = true,
                                        style = style.copy(body = cellTextStyle),
                                        onLatexWidthMeasured = { width ->
                                            val previous = renderedFormulaWidths[columnIndex] ?: 0f
                                            if (width > previous) renderedFormulaWidths[columnIndex] = width
                                        }
                                    )
                                }
                            }
                        }
                    }) { measurables, constraints ->
                        val widths = columnWidths.map { it.roundToPx() }
                        val placeables = measurables.mapIndexed { index, measurable ->
                            measurable.measure(Constraints.fixedWidth(widths[index]))
                        }
                        val height = placeables.maxOfOrNull { it.height } ?: 0
                        layout(constraints.constrainWidth(widths.sum()), constraints.constrainHeight(height)) {
                            var x = 0
                            placeables.forEachIndexed { index, placeable ->
                                val offset = tableStyle.cellAlignment.align(
                                    IntSize(placeable.width, placeable.height),
                                    IntSize(widths[index], height), layoutDirection
                                )
                                placeable.placeRelative(x + offset.x, offset.y)
                                x += widths[index]
                            }
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
