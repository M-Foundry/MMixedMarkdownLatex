package com.mroldl001.m3l

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
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
    afterSetMarkdown: (TextView) -> Unit
) {
    val context = LocalContext.current
    val linkHandler = LocalMarkdownLinkHandler.current
    val density = LocalDensity.current
    val textSizePx = with(density) { style.fontSize.toPx() }
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val markwon = remember(context, linkColor, linkifyMask, linkHandler) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create(linkifyMask))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> linkHandler?.invoke(link) }
                }

                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(linkColor.toArgb())
                }
            })
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                includeFontPadding = false
            }
        },
        update = { view ->
            view.setTextColor(style.color.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            view.setLineSpacing((lineHeightPx - view.paint.fontSpacing).coerceAtLeast(0f), 1f)
            view.highlightColor = textSelectionColors.backgroundColor.toArgb()
            view.setTextIsSelectable(isTextSelectable)
            view.movementMethod = LinkMovementMethod.getInstance()
            if (view.tag != (markdown to markwon)) {
                markwon.setMarkdown(view, markdown)
                view.tag = markdown to markwon
            }
            afterSetMarkdown(view)
        }
    )
}
