package com.mroldl001.m3l

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MarkdownStyle(
    val body: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    val headings: Map<Int, TextStyle> = emptyMap(),
    val paragraphSpacing: Dp? = null,
    val listIndent: Dp? = null,
    val bulletColor: Color = Color.Unspecified,
    val bulletWidth: Dp? = null,
    val link: MarkdownLinkStyle = MarkdownLinkStyle(),
    val inlineCode: MarkdownInlineCodeStyle = MarkdownInlineCodeStyle(),
    val selection: MarkdownSelectionStyle = MarkdownSelectionStyle(),
    val codeBlock: MarkdownCodeBlockStyle = MarkdownCodeBlockStyle(),
    val quote: MarkdownQuoteStyle = MarkdownQuoteStyle(),
    val divider: MarkdownDividerStyle = MarkdownDividerStyle(),
    val table: MarkdownTableStyle = MarkdownTableStyle(),
    val latex: MarkdownLatexStyle = MarkdownLatexStyle(),
    val image: MarkdownImageStyle = MarkdownImageStyle(),
    val native: MarkdownNativeConfiguration = MarkdownNativeConfiguration(),
    val streamingIntervalMillis: Long = 80L
) {
    init {
        require(streamingIntervalMillis > 0)
    }
}

data class MarkdownQuoteStyle(
    val barColor: Color = Color.Unspecified,
    val backgroundColor: Color = Color.Transparent,
    val textColor: Color = Color.Unspecified,
    val textStyle: TextStyle = TextStyle.Default,
    val barWidth: Dp = 3.dp,
    val barShape: Shape = RoundedCornerShape(1.5.dp),
    val spacing: Dp = 12.dp,
    val padding: PaddingValues = PaddingValues(vertical = 6.dp),
    val contentPadding: PaddingValues = PaddingValues(0.dp)
)

data class MarkdownDividerStyle(
    val color: Color = Color.Unspecified,
    val thickness: Dp = 1.dp,
    val shape: Shape = RoundedCornerShape(0.dp),
    val padding: PaddingValues = PaddingValues(vertical = 8.dp)
)

data class MarkdownTableStyle(
    val borderColor: Color = Color.Unspecified,
    val headerBackground: Color = Color.Unspecified,
    val bodyBackground: Color = Color.Unspecified,
    val alternateBackground: Color = Color.Unspecified,
    val headerTextColor: Color = Color.Unspecified,
    val bodyTextColor: Color = Color.Unspecified,
    val headerTextStyle: TextStyle = TextStyle(fontWeight = FontWeight.Bold),
    val bodyTextStyle: TextStyle = TextStyle.Default,
    val borderWidth: Dp = 1.dp,
    val cellBorderWidth: Dp = 0.5.dp,
    val shape: Shape = RoundedCornerShape(10.dp),
    val padding: PaddingValues = PaddingValues(vertical = 12.dp),
    val cellPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    val minColumnWidth: Dp = 96.dp,
    val maxColumnWidth: Dp = 260.dp,
    val columnWidths: List<Dp> = emptyList(),
    val columnAlignments: Map<Int, TextAlign> = emptyMap(),
    val cellAlignment: Alignment = Alignment.CenterStart,
    val respectMarkdownAlignment: Boolean = false
) {
    init {
        require(minColumnWidth >= 0.dp && maxColumnWidth >= minColumnWidth)
        require(columnWidths.all { it >= 0.dp })
    }
}

val LocalMarkdownStyle = compositionLocalOf { MarkdownStyle() }

internal fun Color.markdownColorOr(fallback: Color): Color = if (this == Color.Unspecified) fallback else this
