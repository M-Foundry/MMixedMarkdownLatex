package com.mroldl001.m3l

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class MarkdownLatexStyle(
    val inlineColor: Color = Color.Unspecified,
    val blockColor: Color = Color.Unspecified,
    val inlineFontSize: TextUnit = 18.sp,
    val blockFontSize: TextUnit = 18.sp,
    val blockAlignment: Alignment.Horizontal = Alignment.Start,
    val blockPadding: PaddingValues? = null,
    val fallbackStyle: TextStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
    val loading: (@Composable (String) -> Unit)? = null,
    val error: (@Composable (String) -> Unit)? = null,
    val onClick: ((String) -> Unit)? = null,
    val contentDescription: (String) -> String = { it },
    val transform: ((String) -> String)? = null
) {
    init {
        require(inlineFontSize.isSp && inlineFontSize.value.isFinite() && inlineFontSize.value > 0f)
        require(blockFontSize.isSp && blockFontSize.value.isFinite() && blockFontSize.value > 0f)
    }
}
