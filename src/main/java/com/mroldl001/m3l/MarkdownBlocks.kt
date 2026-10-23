package com.mroldl001.m3l

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
internal fun MarkdownBlocks(
    segments: List<ContentSegment>,
    textColor: Color,
    compact: Boolean,
    onLatexWidthMeasured: ((Float) -> Unit)?,
    style: MarkdownStyle,
    components: MarkdownComponents
) {
    val parentPath = LocalMarkdownPath.current
    segments.forEachIndexed { index, segment ->
        key(index, segment.type) {
            val path = parentPath + index
            val inherited = MarkdownBlockContext(segment, index, path, path.size - 1, style)
            val resolved = components.resolveStyle?.invoke(inherited) ?: style
            val context = inherited.copy(style = resolved)
            CompositionLocalProvider(
                LocalMarkdownStyle provides resolved,
                LocalMarkdownPath provides path
            ) {
                Column(Modifier.fillMaxWidth().then(components.modifier?.invoke(context) ?: Modifier)) {
                    Row(Modifier.fillMaxWidth()) {
                        components.leading?.invoke(context)
                        Box(Modifier.weight(1f)) {
                            val defaultContent: @Composable () -> Unit = {
                                DefaultMarkdownBlock(segment, textColor, compact, onLatexWidthMeasured)
                            }
                            val renderer = components.blocks[segment.type]
                            if (renderer == null) defaultContent() else renderer(context, defaultContent)
                        }
                        components.trailing?.invoke(context)
                    }
                    components.actions?.invoke(context)
                }
            }
        }
    }
}

@Composable
private fun DefaultMarkdownBlock(
    segment: ContentSegment,
    textColor: Color,
    compact: Boolean,
    onLatexWidthMeasured: ((Float) -> Unit)?
) {
    val style = LocalMarkdownStyle.current
    val theme = LocalMarkdownRendererTheme.current
    when (segment.type) {
        ContentType.MARKDOWN, ContentType.INLINE_CODE -> MarkdownText(
            markdown = segment.content,
            modifier = Modifier.fillMaxWidth(),
            linkColor = style.link.color.markdownColorOr(theme.primary),
            linkifyMask = style.link.autoLinkMask,
            textSelectionColors = TextSelectionColors(
                handleColor = style.selection.handleColor.markdownColorOr(theme.primary),
                backgroundColor = style.selection.backgroundColor.markdownColorOr(theme.primary.copy(alpha = 0.35f))
            ),
            style = TextStyle(color = textColor, fontSize = 15.sp, lineHeight = 22.sp).merge(style.body),
            isTextSelectable = style.selection.enabled,
            afterSetMarkdown = {},
            onWidthMeasured = onLatexWidthMeasured,
            rendererStyle = style
        )
        ContentType.TABLE -> MarkdownTableView(segment.content, textColor)
        ContentType.CODE_BLOCK -> CodeBlockView(segment.content, segment.info, theme.primary)
        ContentType.LATEX_BLOCK -> LatexBlockImage(
            latex = segment.content,
            textColor = style.body.color.markdownColorOr(textColor),
            compact = compact,
            onWidthMeasured = onLatexWidthMeasured
        )
        ContentType.LATEX_INLINE -> LatexInlineLine(segment.content, textColor, onLatexWidthMeasured)
        ContentType.QUOTE -> QuoteBlockView(
            content = segment.content,
            textColor = textColor,
            compact = compact,
            onLatexWidthMeasured = onLatexWidthMeasured
        )
        ContentType.HR -> HrView()
    }
}
