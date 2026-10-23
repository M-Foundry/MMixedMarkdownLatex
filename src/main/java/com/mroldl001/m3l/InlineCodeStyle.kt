package com.mroldl001.m3l

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.widget.TextView
import io.noties.markwon.core.spans.CodeSpan
import io.noties.markwon.core.spans.TextViewSpan
import kotlin.math.max
import kotlin.math.min

internal fun applyInlineCodeStyle(
    textView: TextView,
    backgroundColor: Int,
    textColor: Int,
    cornerRadiusPx: Float = 4f * textView.resources.displayMetrics.density,
    typeface: Typeface = Typeface.MONOSPACE,
    sizeScale: Float = 0.95f
) {
    val rendered = textView.text as? Spanned ?: return
    val styled = (rendered as? Spannable) ?: SpannableString(rendered)
    styleInlineCode(styled, backgroundColor, textColor, cornerRadiusPx, typeface, sizeScale)
    TextViewSpan.applyTo(styled, textView)
    if (styled !== rendered) {
        textView.text = styled
    } else {
        textView.requestLayout()
        textView.invalidate()
    }
}

internal fun styleInlineCode(
    styled: Spannable,
    backgroundColor: Int,
    textColor: Int,
    cornerRadiusPx: Float,
    typeface: Typeface,
    sizeScale: Float
) {
    val codeSpans = styled.getSpans(0, styled.length, CodeSpan::class.java).toList() +
        styled.getSpans(0, styled.length, RoundedInlineCodeSpan::class.java).toList()
    codeSpans.forEach { span ->
        val start = styled.getSpanStart(span)
        val end = styled.getSpanEnd(span)
        val flags = styled.getSpanFlags(span)
        styled.removeSpan(span)
        if (start < end) {
            styled.setSpan(
                RoundedInlineCodeSpan(backgroundColor, textColor, cornerRadiusPx, typeface, sizeScale),
                start, end, flags
            )
        }
    }

}

/** Keep real text spans so long code can wrap and selection/copy retains the original text. */
private class RoundedInlineCodeSpan(
    backgroundColor: Int,
    private val textColor: Int,
    private val radius: Float,
    private val typeface: Typeface,
    private val sizeScale: Float
) : MetricAffectingSpan(), LineBackgroundSpan {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
    private val selectionPath = Path()
    private val selectionRegion = Region()
    private val clipRegion = Region()
    private val bounds = Rect()

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = typeface
        paint.textSize *= sizeScale
    }

    override fun updateDrawState(paint: TextPaint) {
        updateMeasureState(paint)
        paint.color = textColor
        paint.bgColor = Color.TRANSPARENT
    }

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {
        val spanned = text as? Spanned ?: return
        val from = max(start, spanned.getSpanStart(this))
        val to = min(end, spanned.getSpanEnd(this))
        if (from >= to) return
        val layout = TextViewSpan.textViewOf(text)?.layout ?: return
        // Layout resolves wrapping, nested styles and bidirectional text; measuring raw strings cannot.
        selectionPath.reset()
        layout.getSelectionPath(from, to, selectionPath)
        clipRegion.set(left, top, right, bottom)
        selectionRegion.setPath(selectionPath, clipRegion)
        val rectangles = RegionIterator(selectionRegion)
        while (rectangles.next(bounds)) {
            canvas.drawRoundRect(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(),
                radius, radius, backgroundPaint
            )
        }
    }
}
