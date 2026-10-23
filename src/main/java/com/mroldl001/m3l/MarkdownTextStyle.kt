package com.mroldl001.m3l

import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import io.noties.markwon.Markwon

data class MarkdownLinkStyle(
    val color: Color = Color.Unspecified,
    val underline: Boolean = true,
    val textStyle: SpanStyle = SpanStyle(),
    val autoLinkMask: Int = Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES,
    val icon: ((String) -> Drawable?)? = null,
    val iconSize: Dp = 16.dp,
    val iconGap: Dp = 4.dp,
    val iconPlacement: MarkdownLinkIconPlacement = MarkdownLinkIconPlacement.BeforeText
) {
    init {
        require(iconSize >= 0.dp && iconGap >= 0.dp)
    }
}

enum class MarkdownLinkIconPlacement {
    BeforeText,
    AfterText
}

data class MarkdownInlineCodeStyle(
    val color: Color = Color.Unspecified,
    val backgroundColor: Color = Color.Unspecified,
    val cornerRadius: Dp = 4.dp,
    val fontFamily: FontFamily = FontFamily.Monospace,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val sizeScale: Float = 0.95f
) {
    init {
        require(cornerRadius >= 0.dp && sizeScale > 0f && sizeScale.isFinite())
    }
}

data class MarkdownSelectionStyle(
    val enabled: Boolean = true,
    val handleColor: Color = Color.Unspecified,
    val backgroundColor: Color = Color.Unspecified
)

data class MarkdownImageStyle(
    val width: Dp? = null,
    val height: Dp? = null,
    val cornerRadius: Dp = 0.dp,
    val loadingDrawable: Drawable? = null,
    val failureDrawable: Drawable? = null,
    val onClick: ((String) -> Unit)? = null,
    val imageLoader: ImageLoader? = null,
    val configureRequest: ((ImageRequest.Builder, String) -> Unit)? = null,
    val onClickWithContext: ((MarkdownImageClickContext) -> Unit)? = null,
    val verticalOffsetPx: ((MarkdownImageReference) -> Float)? = null,
    val motionPadding: Dp = 0.dp
) {
    init {
        require(width == null || width > 0.dp)
        require(height == null || height > 0.dp)
        require(cornerRadius >= 0.dp)
        require(motionPadding >= 0.dp)
    }
}

data class MarkdownImageReference(
    val destination: String,
    val start: Int,
    val end: Int
)

data class MarkdownImageClickContext(
    val image: MarkdownImageReference,
    val textView: TextView
)

data class MarkdownNativeConfiguration(
    val configureMarkwon: ((Markwon.Builder) -> Unit)? = null,
    val configureTextView: ((TextView) -> Unit)? = null,
    val transformSpans: ((SpannableStringBuilder) -> Unit)? = null
)
