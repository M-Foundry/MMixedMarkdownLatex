package com.mroldl001.m3l

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextDirectionHeuristics
import android.text.method.LinkMovementMethod
import android.text.Layout
import android.text.style.AlignmentSpan
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.view.Gravity
import android.widget.TextView
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.Coil
import coil.request.Disposable
import coil.request.ImageRequest
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImageSizeResolverDef
import io.noties.markwon.linkify.LinkifyPlugin

/** Android text adapter with explicit styling; no Material theme or controls. */
@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier,
    linkColor: Color,
    linkifyMask: Int,
    textSelectionColors: TextSelectionColors,
    style: TextStyle,
    isTextSelectable: Boolean,
    afterSetMarkdown: (TextView) -> Unit,
    onWidthMeasured: ((Float) -> Unit)? = null,
    rendererStyle: MarkdownStyle = LocalMarkdownStyle.current
) {
    val context = LocalContext.current
    val theme = LocalMarkdownColors.current
    val linkHandler = LocalMarkdownLinkHandler.current
    val density = LocalDensity.current
    val effectiveStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp).merge(style)
    val textSizePx = sizePx(effectiveStyle.fontSize, density, with(density) { 15.sp.toPx() })
    val lineHeightPx = sizePx(effectiveStyle.lineHeight, density, textSizePx)
    val bodyTypeface = resolveMarkdownTypeface(effectiveStyle.toSpanStyle())
    val link = rendererStyle.link
    val resolvedLinkColor = link.color.takeOrElse { linkColor }
    val linkDecoration = buildList {
        if (link.underline) add(TextDecoration.Underline)
        if (link.textStyle.textDecoration?.contains(TextDecoration.LineThrough) == true) {
            add(TextDecoration.LineThrough)
        }
    }.let { if (it.isEmpty()) TextDecoration.None else TextDecoration.combine(it) }
    val linkSpanStyle = link.textStyle.copy(
        color = link.textStyle.color.takeOrElse { resolvedLinkColor },
        textDecoration = linkDecoration
    )
    val nativeLinkStyle = NativeTextStyle(
        linkSpanStyle,
        resolveMarkdownTypeface(linkSpanStyle, effectiveStyle.toSpanStyle(), onlyIfSpecified = true),
        density,
        textSizePx
    )
    val headingStyles = rendererStyle.headings.mapValues { (_, headingStyle) ->
        val spanStyle = headingStyle.toSpanStyle()
        NativeTextStyle(
            spanStyle,
            resolveMarkdownTypeface(
                spanStyle,
                effectiveStyle.toSpanStyle().copy(fontWeight = FontWeight.Bold),
                onlyIfSpecified = true
            ),
            density,
            textSizePx
        )
    }
    val code = rendererStyle.inlineCode
    val codeTypeface = resolveMarkdownTypeface(
        SpanStyle(fontFamily = code.fontFamily, fontWeight = code.fontWeight, fontStyle = code.fontStyle)
    ) ?: Typeface.MONOSPACE
    val tokenized = remember(markdown) { tokenizeInlineLatex(markdown) }
    val latexStyle = rendererStyle.latex
    val formulas = remember(tokenized, latexStyle.transform) {
        tokenized.formulas.map { latexStyle.transform?.invoke(it.latex) ?: it.latex }
    }
    val latexSizePx = with(density) { latexStyle.inlineFontSize.toPx() }
    val results by rememberLatexRenderResults(
        context,
        formulas,
        latexStyle.inlineColor.takeOrElse { effectiveStyle.color }.toArgb(),
        latexSizePx
    )
    val markwon = remember(context, rendererStyle, resolvedLinkColor, linkifyMask, linkHandler, density) {
        val image = rendererStyle.image
        val imageLoader = image.imageLoader ?: Coil.imageLoader(context)
        val imagePlugin = CoilImagesPlugin.create(object : CoilImagesPlugin.CoilStore {
            override fun load(drawable: AsyncDrawable): ImageRequest = ImageRequest.Builder(context)
                .data(drawable.destination)
                .apply {
                    image.loadingDrawable?.let { placeholder(it.freshDrawable(context)) }
                    image.failureDrawable?.let { error(it.freshDrawable(context)) }
                    image.configureRequest?.invoke(this, drawable.destination)
                }
                .build()

            override fun cancel(disposable: Disposable) = disposable.dispose()
        }, imageLoader)
        val builder = Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create(linkifyMask))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(imagePlugin)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> linkHandler?.invoke(link) }
                    if (image.width != null || image.height != null) {
                        builder.imageSizeResolver(object : ImageSizeResolverDef() {
                            override fun resolveImageSize(drawable: AsyncDrawable): Rect {
                                val original = super.resolveImageSize(drawable)
                                val width = image.width?.let { with(density) { it.roundToPx() } }
                                val height = image.height?.let { with(density) { it.roundToPx() } }
                                val ratio = if (original.width() > 0 && original.height() > 0) {
                                    original.width().toFloat() / original.height()
                                } else 1f
                                return Rect(
                                    0, 0,
                                    width ?: ((height ?: 1) * ratio).toInt().coerceAtLeast(1),
                                    height ?: ((width ?: 1) / ratio).toInt().coerceAtLeast(1)
                                )
                            }
                        })
                    }
                }

                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(resolvedLinkColor.toArgb())
                    builder.isLinkUnderlined(link.underline)
                    builder.headingBreakHeight(0)
                    rendererStyle.listIndent?.let { builder.blockMargin(with(density) { it.roundToPx() }) }
                    if (rendererStyle.bulletColor.isSpecified) {
                        builder.listItemColor(rendererStyle.bulletColor.toArgb())
                    }
                    rendererStyle.bulletWidth?.let { builder.bulletWidth(with(density) { it.roundToPx() }) }
                }
            })
        rendererStyle.native.configureMarkwon?.invoke(builder)
        builder.build()
    }
    val minVerticalPaddingPx = with(density) { 2.dp.roundToPx() }
    val spanned = remember(markwon, tokenized, results, minVerticalPaddingPx, nativeLinkStyle, headingStyles, codeTypeface, effectiveStyle, theme) {
        val parsed = SpannableStringBuilder(replaceInlineLatex(
            markwon.toMarkdown(normalizeEscapedEmphasis(tokenized.markdown)), tokenized, results, minVerticalPaddingPx
        ))
        parsed.getSpans(0, parsed.length, HeadingSpan::class.java).forEach { heading ->
            headingStyles[heading.level]?.let { headingSpanStyle ->
                val start = parsed.getSpanStart(heading)
                val end = parsed.getSpanEnd(heading)
                parsed.replaceNativeSpan(heading, NativeHeadingSpan(heading, headingSpanStyle))
                val headingStyle = rendererStyle.headings.getValue(heading.level)
                if (headingStyle.lineHeight.isSpecified) {
                    val headingSize = sizePx(headingStyle.fontSize, density, textSizePx)
                    parsed.setSpan(
                        NativeLineHeightSpan(sizePx(headingStyle.lineHeight, density, headingSize).toInt()),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                headingStyle.textAlign.toNativeAlignment(parsed, start, end)?.let { alignment ->
                    parsed.setSpan(AlignmentSpan.Standard(alignment), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        parsed.getSpans(0, parsed.length, URLSpan::class.java).forEach { original ->
            val start = parsed.getSpanStart(original)
            val end = parsed.getSpanEnd(original)
            parsed.replaceNativeSpan(original, NativeLinkSpan(original))
            if (start >= 0 && end > start) {
                parsed.setSpan(NativeTextStyleSpan(nativeLinkStyle), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                link.icon?.invoke(original.url)?.let { icon ->
                    val cluster = BreakIterator.getCharacterInstance().run {
                        setText(parsed.subSequence(start, end).toString())
                        if (link.iconPlacement == MarkdownLinkIconPlacement.BeforeText) {
                            first()
                            start to (start + next().coerceAtLeast(1))
                        } else {
                            last()
                            (start + previous().coerceAtLeast(0)) to end
                        }
                    }
                    parsed.setSpan(
                        LinkIconSpan(
                            icon.freshDrawable(context),
                            with(density) { link.iconSize.roundToPx() },
                            with(density) { link.iconGap.roundToPx() },
                            link.iconPlacement
                        ),
                        cluster.first, cluster.second.coerceAtMost(end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        parsed.getSpans(0, parsed.length, AsyncDrawableSpan::class.java).forEach { imageSpan ->
            val start = parsed.getSpanStart(imageSpan)
            val imageEnd = parsed.getSpanEnd(imageSpan)
            val imageStyle = rendererStyle.image
            val imageReference = MarkdownImageReference(imageSpan.drawable.destination, start, imageEnd)
            if (imageStyle.cornerRadius > 0.dp || imageStyle.verticalOffsetPx != null || imageStyle.motionPadding > 0.dp) {
                parsed.replaceNativeSpan(
                    imageSpan,
                    StyledMarkdownImageSpan(
                        markwon.configuration().theme(),
                        imageSpan,
                        with(density) { imageStyle.cornerRadius.toPx() },
                        imageReference,
                        imageStyle.verticalOffsetPx,
                        with(density) { imageStyle.motionPadding.roundToPx() }
                    )
                )
            }
            if (start >= 0 && imageEnd > start &&
                (imageStyle.onClick != null || imageStyle.onClickWithContext != null)
            ) {
                parsed.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        imageStyle.onClick?.invoke(imageReference.destination)
                        if (widget is TextView) {
                            val rendered = widget.text as? Spanned
                            val currentStart = rendered?.getSpanStart(this)?.takeIf { it >= 0 } ?: imageReference.start
                            val currentEnd = rendered?.getSpanEnd(this)?.takeIf { it > currentStart } ?: imageReference.end
                            imageStyle.onClickWithContext?.invoke(
                                MarkdownImageClickContext(imageReference.copy(start = currentStart, end = currentEnd), widget)
                            )
                        }
                    }

                    override fun updateDrawState(ds: android.text.TextPaint) = Unit
                }, start, imageEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        rendererStyle.paragraphSpacing?.let { parsed.styleParagraphGaps(with(density) { it.roundToPx() }) }
        styleInlineCode(
            parsed,
            code.backgroundColor.takeOrElse { theme.primary.copy(alpha = 0.15f) }.toArgb(),
            code.color.takeOrElse { theme.primary }.toArgb(),
            with(density) { code.cornerRadius.toPx() },
            codeTypeface,
            code.sizeScale
        )
        rendererStyle.native.transformSpans?.invoke(parsed)
        parsed
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            MarkdownTextView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                includeFontPadding = false
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) = AsyncDrawableScheduler.schedule(this@apply)
                    override fun onViewDetachedFromWindow(view: View) = AsyncDrawableScheduler.unschedule(this@apply)
                })
            }
        },
        onReset = null,
        onRelease = { AsyncDrawableScheduler.unschedule(it) },
        update = { view ->
            view.typeface = bodyTypeface
            view.paint.isFakeBoldText = false
            view.paint.isUnderlineText = false
            view.paint.isStrikeThruText = false
            view.paint.baselineShift = 0
            view.paint.bgColor = android.graphics.Color.TRANSPARENT
            view.paint.textLocales = context.resources.configuration.locales
            view.paint.textSkewX = 0f
            view.textScaleX = 1f
            view.letterSpacing = 0f
            view.fontFeatureSettings = null
            view.paint.clearShadowLayer()
            view.setTextColor(effectiveStyle.color.toArgb())
            view.setLinkTextColor(resolvedLinkColor.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            NativeTextStyle(effectiveStyle.toSpanStyle(), bodyTypeface, density, textSizePx).apply(view.paint)
            view.setLineSpacing((lineHeightPx - view.paint.fontSpacing).coerceAtLeast(0f), 1f)
            view.highlightColor = rendererStyle.selection.backgroundColor.takeOrElse { textSelectionColors.backgroundColor }.toArgb()
            view.setTextIsSelectable(isTextSelectable && rendererStyle.selection.enabled)
            tintSelectionHandles(view, rendererStyle.selection.handleColor.takeOrElse { textSelectionColors.handleColor }.toArgb())
            view.movementMethod = LinkMovementMethod.getInstance()
            view.gravity = Gravity.TOP or when (effectiveStyle.textAlign) {
                TextAlign.Left -> Gravity.LEFT
                TextAlign.Right -> Gravity.RIGHT
                else -> Gravity.START
            }
            view.justificationMode = if (effectiveStyle.textAlign == TextAlign.Justify) {
                Layout.JUSTIFICATION_MODE_INTER_WORD
            } else Layout.JUSTIFICATION_MODE_NONE
            view.textAlignment = when (effectiveStyle.textAlign) {
                TextAlign.Center -> android.view.View.TEXT_ALIGNMENT_CENTER
                TextAlign.End -> android.view.View.TEXT_ALIGNMENT_TEXT_END
                TextAlign.Left, TextAlign.Right -> android.view.View.TEXT_ALIGNMENT_GRAVITY
                else -> android.view.View.TEXT_ALIGNMENT_TEXT_START
            }
            if (view.renderedMarkdown !== spanned) {
                markwon.setParsedMarkdown(view, spanned)
                view.renderedMarkdown = spanned
            }
            afterSetMarkdown(view)
            rendererStyle.native.configureTextView?.invoke(view)
            if (results.any { it != null }) {
                onWidthMeasured?.invoke(Layout.getDesiredWidth(view.text, view.paint) / density.density)
            }
        }
    )
}

@Composable
private fun resolveMarkdownTypeface(
    style: SpanStyle,
    fallback: SpanStyle = SpanStyle(),
    onlyIfSpecified: Boolean = false
): Typeface? {
    if (onlyIfSpecified && style.fontFamily == null && style.fontWeight == null && style.fontStyle == null) return null
    return LocalFontFamilyResolver.current.resolve(
        style.fontFamily ?: fallback.fontFamily ?: FontFamily.Default,
        style.fontWeight ?: fallback.fontWeight ?: FontWeight.Normal,
        style.fontStyle ?: fallback.fontStyle ?: FontStyle.Normal,
        style.fontSynthesis ?: fallback.fontSynthesis ?: FontSynthesis.All
    ).value as Typeface
}

private class MarkdownTextView(context: Context) : TextView(context) {
    var renderedMarkdown: Spanned? = null
}

private fun android.graphics.drawable.Drawable.freshDrawable(context: Context): android.graphics.drawable.Drawable =
    (constantState?.newDrawable(context.resources) ?: this).mutate()

private fun TextAlign.toNativeAlignment(text: CharSequence, start: Int, end: Int): Layout.Alignment? {
    val rtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, start, end - start)
    return when (this) {
        TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
        TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
        TextAlign.Start -> Layout.Alignment.ALIGN_NORMAL
        TextAlign.Right -> if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
        TextAlign.Left -> if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        else -> null
    }
}
