package com.mroldl001.m3l

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

typealias MarkdownCodeHighlighter = suspend (List<String>, String?) -> List<AnnotatedString>

data class MarkdownHighlightRule(
    val scopes: List<String>,
    val style: SpanStyle
)

data class MarkdownCodeBlockStyle(
    val palette: CodeBlockPalette? = null,
    val textStyle: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 22.sp,
        fontFamily = FontFamily.Monospace
    ),
    val lineNumberStyle: TextStyle = TextStyle.Default,
    val showLineNumbers: Boolean = true,
    val lineNumberGutter: Dp = 14.dp,
    val shape: Shape = RoundedCornerShape(8.dp),
    val contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
    val verticalPadding: Dp = 12.dp,
    val headerTextStyle: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    val headerPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    val emptyHeaderHeight: Dp = 12.dp,
    val showLanguageLabel: Boolean = true,
    val scrollbarColor: Color = Color.Unspecified,
    val showHorizontalBar: Boolean = true,
    val languageAliases: Map<String, String> = emptyMap(),
    val grammarProvider: ((Context, String) -> String?)? = null,
    val highlightRules: List<MarkdownHighlightRule> = emptyList(),
    val highlighter: MarkdownCodeHighlighter? = null
) {
    init {
        require(lineNumberGutter >= 0.dp)
        require(verticalPadding >= 0.dp)
        require(emptyHeaderHeight >= 0.dp)
    }
}
