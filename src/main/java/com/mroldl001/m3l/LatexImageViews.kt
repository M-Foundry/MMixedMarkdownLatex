package com.mroldl001.m3l

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import java.util.UUID
import kotlin.math.roundToInt

private const val LatexTextSizeSp = 18f

internal data class InlineFormula(
    val token: String,
    val latex: String
)

internal data class TokenizedInlineLatex(
    val markdown: String,
    val formulas: List<InlineFormula>
)

@Composable
internal fun rememberLatexRenderResults(
    context: Context,
    formulas: List<String>,
    textColor: Int,
    textSizePx: Float,
    displayMode: Boolean = false
): State<List<LatexRenderResult?>> = key(
    context,
    formulas,
    textColor,
    textSizePx,
    displayMode,
    context.resources.displayMetrics.density
) {
    produceState(initialValue = List<LatexRenderResult?>(formulas.size) { null }) {
        value = formulas.map { formula ->
            LatexBitmapRenderer.render(context, formula, textColor, textSizePx, displayMode)
        }
    }
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
internal fun LatexBlockImage(
    latex: String,
    textColor: Color,
    compact: Boolean = false,
    onWidthMeasured: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textSizePx = with(density) { LatexTextSizeSp.sp.toPx() }
    val results by rememberLatexRenderResults(context, listOf(latex), textColor.toArgb(), textSizePx, true)
    val result = results.firstOrNull()
    LaunchedEffect(result) {
        result?.let {
            onWidthMeasured?.invoke(with(density) { it.bitmap.width.toDp().value })
        }
    }
    val blockVerticalPadding = if (compact) {
        0.dp
    } else {
        result?.let {
            with(density) {
                (it.bitmap.height * 0.12f).toDp().coerceIn(8.dp, 18.dp)
            }
        } ?: 10.dp
    }

    val rendered = result ?: run {
        Box(
            modifier = modifier
                .padding(vertical = blockVerticalPadding)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            BasicText(
                text = latex,
                style = TextStyle(
                    color = textColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
        return
    }
    val image = rendered.bitmap
    Box(
        modifier = modifier
            .padding(vertical = blockVerticalPadding)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = latex,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(with(density) { image.width.toDp() })
                .height(with(density) { image.height.toDp() })
        )
    }
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
internal fun LatexInlineLine(
    line: String,
    textColor: Color,
    onWidthMeasured: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textSizePx = with(density) { 15.sp.toPx() }
    val latexTextSizePx = with(density) { LatexTextSizeSp.sp.toPx() }
    val colorArgb = textColor.toArgb()
    val tokenized = remember(line) { tokenizeInlineLatex(line) }
    val results by rememberLatexRenderResults(
        context,
        tokenized.formulas.map { it.latex },
        colorArgb,
        latexTextSizePx
    )
    LaunchedEffect(results) {
        val widest = results.filterNotNull().maxOfOrNull { it.bitmap.width } ?: 0
        if (widest > 0) {
            onWidthMeasured?.invoke(with(density) { widest.toDp().value })
        }
    }
    val linkHandler = LocalMarkdownLinkHandler.current
    val markwon = remember(context, linkHandler) {
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> linkHandler?.invoke(link) }
                }

                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.headingBreakHeight(0)
                }
            })
            .build()
    }
    val minInlineVerticalPaddingPx = with(density) { 2.dp.roundToPx() }
    val spanned = remember(tokenized, results, colorArgb, minInlineVerticalPaddingPx) {
        replaceInlineLatex(
            markwon.toMarkdown(tokenized.markdown),
            tokenized,
            results,
            minInlineVerticalPaddingPx
        )
    }

    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(colorArgb)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setLineSpacing(0f, 1f)
                gravity = Gravity.START
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
                includeFontPadding = false
            }
        },
        update = { textView ->
            textView.setTextColor(colorArgb)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            textView.text = spanned
        },
        modifier = modifier.fillMaxWidth()
    )
}

internal fun tokenizeInlineLatex(markdown: String): TokenizedInlineLatex {
    val output = StringBuilder(markdown.length)
    val formulas = mutableListOf<InlineFormula>()
    var tokenPrefix = "\uE000LTX${UUID.randomUUID().toString().replace("-", "")}"
    while (markdown.contains(tokenPrefix)) tokenPrefix += '\uE000'
    var cursor = 0
    var bracketDepth = 0
    while (cursor < markdown.length) {
        if (!isEscapedLatex(markdown, cursor)) {
            val markupEnd = when (markdown[cursor]) {
                '<' -> findRawMarkupEnd(markdown, cursor)
                '[' -> {
                    val definitionEnd = findReferenceDefinitionEnd(markdown, cursor)
                    if (definitionEnd < 0) bracketDepth++
                    definitionEnd
                }
                ']' -> if (bracketDepth > 0) {
                    bracketDepth--
                    if (markdown.getOrNull(cursor + 1) == '(') {
                        findLinkDestinationEnd(markdown, cursor + 1)
                    } else {
                        -1
                    }
                } else {
                    -1
                }
                else -> -1
            }
            if (markupEnd >= 0) {
                output.append(markdown, cursor, markupEnd)
                cursor = markupEnd
                continue
            }
        }
        if (markdown[cursor] == '`' && !isEscapedLatex(markdown, cursor)) {
            var ticks = 1
            while (cursor + ticks < markdown.length && markdown[cursor + ticks] == '`') ticks++
            val closing = findClosingBackticks(markdown, cursor + ticks, ticks)
            val end = if (closing >= 0) closing + ticks else cursor + ticks
            output.append(markdown, cursor, end)
            cursor = end
            continue
        }
        val delimiter = when {
            markdown.startsWith("\\(", cursor) && !isEscapedLatex(markdown, cursor) -> "\\)" to "\\("
            markdown[cursor] == '$' && !markdown.startsWith("$$", cursor) &&
                markdown.getOrNull(cursor - 1) != '$' && !isEscapedLatex(markdown, cursor) -> "$" to "$"
            else -> null
        }
        if (delimiter == null) {
            output.append(markdown[cursor++])
            continue
        }
        val contentStart = cursor + delimiter.second.length
        val closing = findLatexDelimiter(markdown, delimiter.first, contentStart)
        if (closing < 0) {
            output.append(markdown[cursor++])
            continue
        }
        val token = "$tokenPrefix${formulas.size}\uE001"
        formulas += InlineFormula(token, markdown.substring(contentStart, closing).trim())
        output.append(token)
        cursor = closing + delimiter.first.length
    }
    return TokenizedInlineLatex(output.toString(), formulas)
}

internal fun replaceInlineLatex(
    spanned: CharSequence,
    tokenized: TokenizedInlineLatex,
    results: List<LatexRenderResult?>,
    minVerticalPaddingPx: Int
): Spanned {
    val builder = SpannableStringBuilder(spanned)
    tokenized.formulas.forEachIndexed { index, formula ->
        val start = builder.indexOf(formula.token)
        if (start < 0) return@forEachIndexed
        builder.replace(start, start + formula.token.length, formula.latex)
        results.getOrNull(index)?.let { result ->
            if (formula.latex.isNotEmpty()) {
                builder.setSpan(
                    LatexReplacementSpan(result, minVerticalPaddingPx),
                    start,
                    start + formula.latex.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
    return builder
}

private fun findClosingBackticks(text: String, start: Int, ticks: Int): Int {
    var cursor = start
    while (cursor < text.length) {
        if (text[cursor] != '`') {
            cursor++
            continue
        }
        val runStart = cursor
        while (cursor < text.length && text[cursor] == '`') cursor++
        if (cursor - runStart == ticks) return runStart
    }
    return -1
}

private fun findRawMarkupEnd(text: String, start: Int): Int {
    val next = text.getOrNull(start + 1) ?: return -1
    if (!next.isLetter() && next != '/' && next != '!' && next != '?') return -1
    if (text.startsWith("<!--", start)) {
        val closing = text.indexOf("-->", start + 4)
        return if (closing >= 0) closing + 3 else text.length
    }
    var quote: Char? = null
    var cursor = start + 1
    while (cursor < text.length) {
        val character = text[cursor]
        if (quote == null) {
            if (character == '>') return cursor + 1
            if (character == '\'' || character == '"') quote = character
        } else if (character == quote) {
            quote = null
        }
        cursor++
    }
    return -1
}

private fun findLinkDestinationEnd(text: String, start: Int): Int {
    var depth = 1
    var quote: Char? = null
    var cursor = start + 1
    while (cursor < text.length) {
        val character = text[cursor]
        if (isEscapedLatex(text, cursor)) {
            cursor++
            continue
        }
        if (quote != null) {
            if (character == quote) quote = null
        } else {
            when (character) {
                '\'', '"' -> if (depth == 1 && text[cursor - 1].isWhitespace()) quote = character
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return cursor + 1
                }
                '<' -> {
                    val closing = text.indexOf('>', cursor + 1)
                    if (closing >= 0) cursor = closing
                }
            }
        }
        cursor++
    }
    return -1
}

private fun findReferenceDefinitionEnd(text: String, start: Int): Int {
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    if (start - lineStart > 3 || text.substring(lineStart, start).any { it != ' ' }) return -1
    val lineEnd = text.indexOf('\n', start).takeIf { it >= 0 } ?: text.length
    var cursor = start + 1
    while (cursor < lineEnd) {
        if (text[cursor] == ']' && !isEscapedLatex(text, cursor)) {
            return if (cursor > start + 1 && text.getOrNull(cursor + 1) == ':') lineEnd else -1
        }
        cursor++
    }
    return -1
}

private fun findLatexDelimiter(text: String, delimiter: String, start: Int): Int {
    val lineEnd = text.indexOfAny(charArrayOf('\n', '\r'), start)
    var cursor = start
    while (cursor <= text.length - delimiter.length) {
        val index = text.indexOf(delimiter, cursor)
        if (index < 0 || (lineEnd >= 0 && index >= lineEnd)) return -1
        if (!isEscapedLatex(text, index)) return index
        cursor = index + delimiter.length
    }
    return -1
}

private fun isEscapedLatex(text: String, index: Int): Boolean {
    var slashCount = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        slashCount++
        cursor--
    }
    return slashCount % 2 != 0
}

private class LatexReplacementSpan(
    private val result: LatexRenderResult,
    private val minVerticalPaddingPx: Int
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        fm?.let { metrics ->
            // Keep a small, height-aware gap around tall formulas so the
            // following line never sits directly against the bitmap.
            val verticalPadding = (result.bitmap.height * 0.06f)
                .roundToInt()
                .coerceAtLeast(minVerticalPaddingPx)
            val ascent = -(result.baselinePx + verticalPadding)
            val descent = result.depthPx + verticalPadding
            metrics.ascent = minOf(metrics.ascent, ascent)
            metrics.top = minOf(metrics.top, ascent)
            metrics.descent = maxOf(metrics.descent, descent)
            metrics.bottom = maxOf(metrics.bottom, descent)
        }
        return result.bitmap.width
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        canvas.drawBitmap(
            result.bitmap,
            x,
            (y - result.baselinePx).toFloat(),
            paint
        )
    }
}
