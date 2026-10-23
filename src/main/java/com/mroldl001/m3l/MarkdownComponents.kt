package com.mroldl001.m3l

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

data class MarkdownBlockContext(
    val segment: ContentSegment,
    val index: Int,
    val path: List<Int>,
    val depth: Int,
    val style: MarkdownStyle
)

typealias MarkdownBlockSlot = @Composable (MarkdownBlockContext) -> Unit
typealias MarkdownBlockRenderer = @Composable (MarkdownBlockContext, @Composable () -> Unit) -> Unit

data class MarkdownComponents(
    val leading: MarkdownBlockSlot? = null,
    val trailing: MarkdownBlockSlot? = null,
    val actions: MarkdownBlockSlot? = null,
    val blocks: Map<ContentType, MarkdownBlockRenderer> = emptyMap(),
    val resolveStyle: ((MarkdownBlockContext) -> MarkdownStyle)? = null,
    val modifier: ((MarkdownBlockContext) -> Modifier)? = null
)

val LocalMarkdownComponents = compositionLocalOf { MarkdownComponents() }

internal val LocalMarkdownPath = compositionLocalOf { emptyList<Int>() }
