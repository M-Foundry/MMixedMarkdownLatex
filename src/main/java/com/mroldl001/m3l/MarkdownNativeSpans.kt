package com.mroldl001.m3l

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.LocaleList
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LineHeightSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.view.View
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.core.spans.TextViewSpan
import io.noties.markwon.utils.SpanUtils
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class NativeTextStyle(
    val style: SpanStyle,
    val typeface: Typeface?,
    val density: Density,
    val baseSizePx: Float
) {
    fun apply(paint: TextPaint) {
        typeface?.let { paint.typeface = it; paint.isFakeBoldText = false }
        if (style.fontSize.isSpecified) paint.textSize = sizePx(style.fontSize, density, baseSizePx)
        if (style.color.isSpecified) paint.color = style.color.toArgb()
        if (style.background.isSpecified) paint.bgColor = style.background.toArgb()
        if (style.letterSpacing.isSpecified) {
            paint.letterSpacing = if (style.letterSpacing.isEm) style.letterSpacing.value
            else sizePx(style.letterSpacing, density, paint.textSize) / paint.textSize
        }
        style.fontFeatureSettings?.let { paint.fontFeatureSettings = it }
        style.textDecoration?.let {
            paint.isUnderlineText = TextDecoration.Underline in it
            paint.isStrikeThruText = TextDecoration.LineThrough in it
        }
        style.baselineShift?.let { paint.baselineShift += (paint.ascent() * it.multiplier).roundToInt() }
        style.textGeometricTransform?.let {
            paint.textScaleX *= it.scaleX
            paint.textSkewX += it.skewX
        }
        style.localeList?.let { locales ->
            paint.textLocales = LocaleList(*locales.map { Locale.forLanguageTag(it.toLanguageTag()) }.toTypedArray())
        }
        style.shadow?.let {
            paint.setShadowLayer(it.blurRadius, it.offset.x, it.offset.y, it.color.toArgb())
        }
    }
}

internal fun sizePx(size: TextUnit, density: Density, baseSizePx: Float): Float = when {
    size.isSp -> with(density) { size.toPx() }
    size.isEm -> baseSizePx * size.value
    else -> baseSizePx
}

internal class NativeTextStyleSpan(private val style: NativeTextStyle) : MetricAffectingSpan() {
    override fun updateMeasureState(textPaint: TextPaint) = style.apply(textPaint)
    override fun updateDrawState(textPaint: TextPaint) = style.apply(textPaint)
}

internal class NativeLineHeightSpan(
    private val heightPx: Int,
    private val minimumOnly: Boolean = false
) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
        fm: Paint.FontMetricsInt
    ) {
        val naturalHeight = fm.descent - fm.ascent
        val target = if (minimumOnly) maxOf(naturalHeight, heightPx) else heightPx
        val extra = target - naturalHeight
        fm.ascent -= extra / 2
        fm.descent += extra - extra / 2
        fm.top = fm.ascent
        fm.bottom = fm.descent
    }
}

internal class NativeLinkSpan(private val original: URLSpan) : URLSpan(original.url) {
    override fun onClick(widget: View) = original.onClick(widget)
    override fun updateDrawState(ds: TextPaint) = Unit
}

internal class NativeHeadingSpan(
    private val original: HeadingSpan,
    private val style: NativeTextStyle
) : MetricAffectingSpan(), LeadingMarginSpan {
    override fun updateMeasureState(textPaint: TextPaint) {
        original.updateMeasureState(textPaint)
        style.apply(textPaint)
    }

    override fun updateDrawState(textPaint: TextPaint) {
        original.updateDrawState(textPaint)
        style.apply(textPaint)
    }

    override fun getLeadingMargin(first: Boolean): Int = original.getLeadingMargin(first)

    override fun drawLeadingMargin(
        canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int,
        bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean,
        layout: android.text.Layout
    ) = original.drawLeadingMargin(canvas, paint, x, dir, top, baseline, bottom, text, start, end, first, layout)
}

internal class LinkIconSpan(
    private val icon: Drawable,
    private val size: Int,
    private val gap: Int,
    private val placement: MarkdownLinkIconPlacement
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        paint.getFontMetricsInt(fm)
        fm?.let {
            it.ascent = minOf(it.ascent, -size)
            it.top = minOf(it.top, it.ascent)
        }
        return size + gap + ceil(paint.measureText(text, start, end).toDouble()).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val rtl = TextViewSpan.textViewOf(text)?.layout?.isRtlCharAt(start)
            ?: (Character.getDirectionality(text[start]) in setOf(
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
            ))
        val textWidth = paint.measureText(text, start, end)
        val iconFirst = (placement == MarkdownLinkIconPlacement.BeforeText) != rtl
        val iconX = if (iconFirst) x else x + textWidth + gap
        val save = canvas.save()
        canvas.translate(iconX, y - size.toFloat())
        icon.setBounds(0, 0, size, size)
        icon.draw(canvas)
        canvas.restoreToCount(save)
        canvas.drawText(text, start, end, if (iconFirst) x + size + gap else x, y.toFloat(), paint)
    }
}

internal class StyledMarkdownImageSpan(
    theme: MarkwonTheme,
    private val original: AsyncDrawableSpan,
    private val radius: Float,
    private val reference: MarkdownImageReference,
    private val verticalOffsetPx: ((MarkdownImageReference) -> Float)?,
    private val motionPaddingPx: Int
) : AsyncDrawableSpan(theme, original.drawable, ALIGN_BOTTOM, false) {
    private val clip = Path()

    override fun getSize(
        paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        val width = original.getSize(paint, text, start, end, fm)
        if (motionPaddingPx > 0) fm?.let {
            it.ascent -= motionPaddingPx
            it.top = minOf(it.top, it.ascent)
            it.descent += motionPaddingPx
            it.bottom = maxOf(it.bottom, it.descent)
        }
        return width
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float,
        top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val save = canvas.save()
        val actual = (text as? Spanned)?.let { spanned ->
            val startIndex = spanned.getSpanStart(this)
            val endIndex = spanned.getSpanEnd(this)
            if (startIndex >= 0 && endIndex > startIndex) {
                reference.copy(start = startIndex, end = endIndex)
            } else reference
        } ?: reference
        val offset = verticalOffsetPx?.invoke(actual)?.takeIf { it.isFinite() } ?: 0f
        canvas.translate(0f, offset)
        val imageBottom = bottom - motionPaddingPx
        if (radius <= 0f || !drawable.hasResult()) {
            original.draw(canvas, text, start, end, x, top, y, imageBottom, paint)
            canvas.restoreToCount(save)
            return
        }
        drawable.initWithKnownDimensions(SpanUtils.width(canvas, text), paint.textSize)
        val bounds = drawable.bounds
        clip.reset()
        clip.addRoundRect(
            RectF(x, imageBottom - bounds.height().toFloat(), x + bounds.width(), imageBottom.toFloat()),
            radius, radius, Path.Direction.CW
        )
        canvas.clipPath(clip)
        canvas.translate(x, imageBottom - bounds.bottom.toFloat())
        drawable.draw(canvas)
        canvas.restoreToCount(save)
    }
}

internal fun <T : Any> SpannableStringBuilder.replaceNativeSpan(original: T, replacement: Any) {
    val start = getSpanStart(original)
    val end = getSpanEnd(original)
    val flags = getSpanFlags(original)
    removeSpan(original)
    if (start >= 0 && end > start) setSpan(replacement, start, end, flags)
}

internal fun SpannableStringBuilder.styleParagraphGaps(heightPx: Int) {
    for (index in 1 until length) {
        if (this[index - 1] == '\n' && this[index] == '\n') {
            setSpan(NativeLineHeightSpan(heightPx), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
