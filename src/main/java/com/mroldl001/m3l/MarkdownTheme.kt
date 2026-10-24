package com.mroldl001.m3l

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun MarkdownTheme(
    colors: MarkdownColors = LocalMarkdownColors.current,
    style: MarkdownStyle = LocalMarkdownStyle.current,
    components: MarkdownComponents = LocalMarkdownComponents.current,
    darkCodeBlocks: Boolean = LocalCodeBlockDark.current,
    onLinkClick: ((String) -> Unit)? = LocalMarkdownLinkHandler.current,
    codeBlockHeader: (@Composable (CodeBlockContent) -> Unit)? = LocalCodeBlockHeader.current,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMarkdownColors provides colors,
        LocalMarkdownStyle provides style,
        LocalMarkdownComponents provides components,
        LocalCodeBlockDark provides darkCodeBlocks,
        LocalMarkdownLinkHandler provides onLinkClick,
        LocalCodeBlockHeader provides codeBlockHeader,
        content = content
    )
}
