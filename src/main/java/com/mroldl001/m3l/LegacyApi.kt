package com.mroldl001.m3l

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Deprecated("Use MarkdownColors", ReplaceWith("MarkdownColors"))
typealias MarkdownRendererTheme = MarkdownColors

@Deprecated("Use LocalMarkdownColors", ReplaceWith("LocalMarkdownColors"))
val LocalMarkdownRendererTheme = LocalMarkdownColors

@Deprecated("Use parseMarkdownBlocks", ReplaceWith("parseMarkdownBlocks(text, allowUnclosedCodeFence)"))
fun splitContent(text: String, allowUnclosedCodeFence: Boolean = false): List<ContentSegment> =
    parseMarkdownBlocks(text, allowUnclosedCodeFence)

@Deprecated("Use Markdown(content = text, color = textColor) and onFormulaWidthMeasured")
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
    Markdown(
        content = text,
        modifier = modifier,
        color = textColor,
        style = style,
        components = components,
        isStreaming = isStreaming,
        compact = compact,
        onFormulaWidthMeasured = onLatexWidthMeasured
    )
}
