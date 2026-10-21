package com.mroldl001.m3l

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.scilab.forge.jlatexmath.core.AjLatexMath
import org.scilab.forge.jlatexmath.core.Insets
import org.scilab.forge.jlatexmath.core.TeXConstants
import org.scilab.forge.jlatexmath.core.TeXFormula
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object LatexBitmapRenderer {
    private const val MaxCacheBytes = 24 * 1024 * 1024
    private const val MaxBitmapDimension = 4096f
    private val initialized = AtomicBoolean(false)
    private val renderLock = Any()

    private data class CacheKey(
        val latex: String,
        val textColor: Int,
        val textSizePx: Float,
        val density: Float,
        val displayMode: Boolean
    )

    private val cache = object : LruCache<CacheKey, LatexRenderResult>(MaxCacheBytes) {
        override fun sizeOf(key: CacheKey, value: LatexRenderResult): Int = value.bitmap.byteCount
    }

    suspend fun render(
        context: Context,
        latex: String,
        textColor: Int,
        textSizePx: Float,
        displayMode: Boolean
    ): LatexRenderResult? = withContext(Dispatchers.Default) {
        val normalized = normalizeLatex(latex)
        if (normalized.isBlank()) return@withContext null
        val density = context.resources.displayMetrics.density
        if (!textSizePx.isFinite() || textSizePx <= 0f || !density.isFinite() || density <= 0f) {
            return@withContext null
        }
        val key = CacheKey(normalized, textColor, textSizePx, density, displayMode)
        cache.get(key)?.let { return@withContext it }

        synchronized(renderLock) {
            cache.get(key)?.let { return@withContext it }
            try {
                if (!initialized.get()) {
                    AjLatexMath.init(context.applicationContext)
                    initialized.set(true)
                }
                AjLatexMath.setColor(textColor)
                val formula = TeXFormula(normalized)
                val textSizeSp = textSizePx / density
                val style = if (displayMode) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT
                val icon = formula.createTeXIcon(style, textSizeSp)
                icon.setInsets(Insets(0, 0, 0, 0))

                val sourceWidth = icon.iconWidth
                val sourceHeight = icon.iconHeight
                if (sourceWidth <= 0 || sourceHeight <= 0) return@withContext null
                val scale = min(1f, MaxBitmapDimension / max(sourceWidth, sourceHeight).toFloat())
                val bitmapWidth = max(1, (sourceWidth * scale).roundToInt())
                val bitmapHeight = max(1, (sourceHeight * scale).roundToInt())
                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.TRANSPARENT)
                canvas.scale(scale, scale)
                icon.paintIcon(canvas, 0, 0)

                val result = LatexRenderResult(
                    bitmap = bitmap,
                    baselinePx = ((icon.iconHeight - icon.iconDepth) * scale).roundToInt(),
                    depthPx = (icon.iconDepth * scale).roundToInt()
                )
                cache.put(key, result)
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun normalizeLatex(latex: String): String {
        val trimmed = latex.trim()
        val unwrapped = when {
            trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4 ->
                trimmed.substring(2, trimmed.length - 2).trim()
            trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 4 ->
                trimmed.substring(2, trimmed.length - 2).trim()
            trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length > 2 ->
                trimmed.substring(1, trimmed.length - 1).trim()
            trimmed.startsWith("\\(") && trimmed.endsWith("\\)") && trimmed.length > 4 ->
                trimmed.substring(2, trimmed.length - 2).trim()
            else -> trimmed
        }
        return unwrapped
    }
}

data class LatexRenderResult(
    val bitmap: Bitmap,
    val baselinePx: Int,
    val depthPx: Int
)
