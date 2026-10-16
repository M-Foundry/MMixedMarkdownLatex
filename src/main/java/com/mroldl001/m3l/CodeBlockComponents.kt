package com.mroldl001.m3l

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.textmate.theme.FontStyle as TextMateFontStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

/** Optional host header. The renderer performs no copy, fullscreen or icon actions. */
val LocalCodeBlockHeader = staticCompositionLocalOf<(@Composable (CodeBlockContent) -> Unit)?> { null }
val LocalCodeBlockDark = compositionLocalOf { true }

/** Snapshot for the host toolbar; bounds are read when an action is invoked. */
data class CodeBlockContent(
    val code: String,
    val label: String,
    val language: String,
    val highlightedLines: List<AnnotatedString>,
    val palette: CodeBlockPalette,
    val boundsInWindow: () -> Rect? = { null }
)

@Composable
internal fun CodeBlockView(code: String, info: String?, primaryTint: Color = Color.Black) {
    val context = LocalContext.current
    val darkCodeBlock = LocalCodeBlockDark.current
    val palette = codeBlockPalette(darkCodeBlock, primaryTint)
    val lines = code.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val fenceLabel = info?.trim()?.substringBefore(' ').orEmpty()
    val normalizedLanguage = normalizeCodeLanguage(fenceLabel)
    val language = normalizedLanguage.takeIf { hasGrammar(context, it) }
    val codeLines = lines
    var blockCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val lineCount = codeLines.size
    val maxLineNumberWidth = lineCount.toString().length
    val appContext = context.applicationContext
    val codeHScroll = rememberScrollState()
    val highlightedLines by produceState(
        initialValue = codeLines.map { AnnotatedString(it) },
        codeLines,
        language,
        darkCodeBlock
    ) {
        value = highlightCodeLinesAsync(codeLines, language, darkCodeBlock, appContext)
    }

    val header = LocalCodeBlockHeader.current
    val block = CodeBlockContent(
        code = code,
        label = fenceLabel,
        language = normalizedLanguage,
        highlightedLines = highlightedLines,
        palette = palette,
        boundsInWindow = { blockCoordinates?.takeIf { it.isAttached }?.boundsInWindow() }
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .onGloballyPositioned { blockCoordinates = it }
            .background(palette.background, RoundedCornerShape(8.dp))
    ) {
        if (header != null) {
            header(block)
        } else if (fenceLabel.isNotEmpty()) {
            BasicText(
                text = fenceLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(color = palette.muted, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        CodeBlockBody(
            highlightedLines = highlightedLines,
            maxLineNumberWidth = maxLineNumberWidth,
            textColor = palette.text,
            numberColor = palette.muted,
            horizontalState = codeHScroll,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
        )
    }
}

private suspend fun highlightCodeLinesAsync(
    lines: List<String>,
    language: String?,
    dark: Boolean,
    context: Context
): List<AnnotatedString> = withContext(Dispatchers.Default) {
    try {
        highlightCodeLines(lines, language, dark, context)
    } catch (error: Throwable) {
        lines.map { AnnotatedString(it) }
    }
}

@Composable
fun CodeBlockBody(
    highlightedLines: List<AnnotatedString>,
    maxLineNumberWidth: Int,
    textColor: Color,
    numberColor: Color,
    horizontalState: ScrollState,
    showHorizontalBar: Boolean = true,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hBarVisible by rememberScrollbarVisibility(horizontalState)
    val hBarActive by rememberScrollActivity(horizontalState)
    val numberStyle = codeLineStyle(numberColor)
    val textStyle = codeLineStyle(textColor)
    val scrollbarThumb = if (!textColor.isDark()) {
        Color.White.copy(alpha = 0.4f)
    } else {
        Color.Black.copy(alpha = 0.4f)
    }

    Box(modifier) {
        Column(
            modifier = if (fillHeight) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth()
            }.horizontalScroll(horizontalState)
        ) {
            highlightedLines.forEachIndexed { index, line ->
                Row {
                    BasicText(
                        text = (index + 1).toString().padStart(maxLineNumberWidth, ' '),
                        style = numberStyle,
                        modifier = Modifier
                            .width(numberColumnWidth(maxLineNumberWidth))
                            .alignByBaseline()
                    )
                    BasicText(
                        text = line.ifEmpty { AnnotatedString(" ") },
                        style = textStyle,
                        modifier = Modifier
                            .padding(start = CODE_GUTTER)
                            .alignByBaseline()
                    )
                }
            }
            if (fillHeight) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        if (showHorizontalBar) {
            CodeHorizontalScrollbar(
                state = horizontalState,
                visible = hBarActive && hBarVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 2.dp)
                    .padding(top = SCROLLBAR_GAP),
                thumbColor = scrollbarThumb
            )
        }
    }
}

private fun numberColumnWidth(digits: Int): Dp =
    (CODE_FONT_SIZE.value * 0.62f * digits).dp + CODE_GUTTER

private fun codeLineStyle(color: Color) = TextStyle(
    color = color,
    fontSize = CODE_FONT_SIZE,
    lineHeight = CODE_LINE_HEIGHT,
    fontFamily = FontFamily.Monospace
)

private fun Color.isDark(): Boolean {
    val r = if (red <= 0.04045f) red / 12.92f else ((red + 0.055f) / 1.055f).pow(2.4f)
    val g = if (green <= 0.04045f) green / 12.92f else ((green + 0.055f) / 1.055f).pow(2.4f)
    val b = if (blue <= 0.04045f) blue / 12.92f else ((blue + 0.055f) / 1.055f).pow(2.4f)
    return (0.2126f * r + 0.7152f * g + 0.0722f * b) < 0.5f
}

@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
private fun CodeHorizontalScrollbar(
    state: ScrollState,
    visible: Boolean,
    modifier: Modifier = Modifier,
    thumbColor: Color = SCROLLBAR_THUMB
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(SCROLLBAR_FADE_MILLIS, easing = FastOutSlowInEasing),
        label = "hBarAlpha"
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(SCROLLBAR_TRACK_HEIGHT)
    ) {
        val density = LocalDensity.current
        val viewportDp = maxWidth
        val viewportPx = with(density) { viewportDp.toPx() }
        val contentPx = viewportPx + state.maxValue
        if (contentPx > 0f && viewportPx > 0f) {
            val thumbWidth = viewportPx * (viewportPx / contentPx).coerceAtLeast(SCROLLBAR_MIN_FRACTION)
            val travel = viewportPx - thumbWidth
            val fraction = if (state.maxValue > 0) state.value.toFloat() / state.maxValue else 0f
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha
                        translationX = travel * fraction
                        scaleX = (thumbWidth / viewportPx).coerceAtLeast(SCROLLBAR_MIN_FRACTION)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(color = thumbColor, shape = RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun rememberScrollbarVisibility(state: ScrollState): State<Boolean> {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.maxValue > 0 }.collect { hasRange ->
            visible = hasRange
        }
    }
    return rememberUpdatedState(visible)
}

@Composable
private fun rememberScrollActivity(vararg states: ScrollState): State<Boolean> {
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(*states) {
        var hideJob: Job? = null
        var previous = states.map { it.value }
        snapshotFlow { states.map { it.value to it.isScrollInProgress } }
            .collect { snapshot ->
                val scrolling = snapshot.any { it.second }
                val moved = snapshot.indices.any { snapshot[it].first != previous[it] }
                previous = snapshot.map { it.first }
                if (scrolling || moved) {
                    hideJob?.cancel()
                    hideJob = null
                    if (!active) active = true
                    if (!scrolling) {
                        hideJob = launch { delay(SCROLLBAR_HIDE_DELAY); active = false }
                    }
                } else if (active && hideJob == null) {
                    hideJob = launch { delay(SCROLLBAR_HIDE_DELAY); active = false }
                }
            }
    }
    return rememberUpdatedState(active)
}


private val CODE_FONT_SIZE = 13.sp
private val CODE_LINE_HEIGHT = 22.sp
private val CODE_GUTTER = 14.dp
private const val SCROLLBAR_HIDE_DELAY = 700L
private const val SCROLLBAR_FADE_MILLIS = 220
private val SCROLLBAR_GAP = 8.dp
private val SCROLLBAR_TRACK_HEIGHT = 5.dp
private val SCROLLBAR_THUMB = Color(0x66FFFFFF)
private const val SCROLLBAR_MIN_FRACTION = 0.12f

private fun normalizeCodeLanguage(label: String): String {
    return when (label.lowercase().substringBefore(' ').trim()) {
        "py", "python3" -> "python"
        "js", "mjs", "cjs", "node" -> "javascript"
        "ts", "mts", "cts" -> "typescript"
        "kt", "kts" -> "kotlin"
        "c++", "cc", "cxx", "hpp" -> "cpp"
        "c#", "cs" -> "csharp"
        "golang" -> "go"
        "rs" -> "rust"
        "sh", "zsh", "shell", "shellscript" -> "shellscript"
        "bash", "pwsh", "ps1" -> "powershell"
        "htm" -> "html"
        "yml" -> "yaml"
        "pl", "perl" -> "perl"
        "hs" -> "haskell"
        "ex", "exs" -> "elixir"
        "erl" -> "erlang"
        "clj", "cljs" -> "clojure"
        "jl" -> "julia"
        "sol" -> "solidity"
        "gql" -> "graphql"
        "f90", "f95", "f03" -> "fortran-free-form"
        "cr" -> "crystal"
        "ml", "mli" -> "ocaml"
        "fs", "fsi", "fsx" -> "fsharp"
        "rkt" -> "racket"
        "lisp", "cl", "el" -> "common-lisp"
        "adb", "ads" -> "ada"
        "purs", "rescript" -> "purescript"
        "hx" -> "haxe"
        "wl", "nb", "mathematica" -> "wolfram"
        "tex" -> "latex"
        "md", "mkd" -> "markdown"
        "dockerfile" -> "docker"
        ".env", "dotenv" -> "dotenv"
        "tf", "tfvars" -> "terraform"
        "el", "emacs", "gnuemacs" -> "emacs-lisp"
        "proto" -> "proto"
        "make" -> "make"
        "objc" -> "objective-c"
        "objcpp" -> "objective-cpp"
        "coffee", "coffeescript" -> "coffee"
        "htmles" -> "html-derivative"
        "cshtml" -> "razor"
        "restructuredtext", "rst" -> "rst"
        "f#" -> "fsharp"
        "vb", "vbnet" -> "vb"
        "plaintext", "txt" -> "text"
        else -> label.lowercase().substringBefore(' ').trim()
    }
}

data class CodeBlockPalette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val type: Color,
    val function: Color,
    val operator: Color,
    val annotation: Color,
    val property: Color
)

internal val DarkCodeBlockPalette = CodeBlockPalette(
    background = Color.Black,
    text = Color.White,
    muted = Color.Gray,
    keyword = Color(0xFFC792EA),
    string = Color(0xFFC3E88D),
    number = Color(0xFFF78C6C),
    comment = Color(0xFF7C8495),
    type = Color(0xFFFFCB6B),
    function = Color(0xFF82AAFF),
    operator = Color(0xFF89DDFF),
    annotation = Color(0xFFFF5370),
    property = Color(0xFFF07178)
)

internal val LightCodeBlockPalette = CodeBlockPalette(
    background = Color.White,
    text = Color(0xFF1F2328),
    muted = Color(0xFF6B7280),
    keyword = Color(0xFFCF222E),
    string = Color(0xFF0A3069),
    number = Color(0xFF0550AE),
    comment = Color(0xFF6E7781),
    type = Color(0xFF953800),
    function = Color(0xFF8250DF),
    operator = Color(0xFF0550AE),
    annotation = Color(0xFF953800),
    property = Color(0xFF116329)
)

// 浅色用 primary 6% 叠白而非纯白：跟主题联动，又不压过语法高亮
@Composable
fun codeBlockPalette(dark: Boolean, primaryTint: Color): CodeBlockPalette {
    return if (dark) {
        DarkCodeBlockPalette
    } else {
        LightCodeBlockPalette.copy(
            background = primaryTint
                .copy(alpha = 0.06f)
                .compositeOver(Color.White)
        )
    }
}

private fun highlightCodeLines(
    lines: List<String>,
    language: String?,
    dark: Boolean,
    context: Context
): List<AnnotatedString> {
    val plain = lines.map { AnnotatedString(it) }
    if (language == null) return plain
    return try {
        val palette = if (dark) DarkCodeBlockPalette else LightCodeBlockPalette
        val grammar = codeGrammar(context, language) ?: return plain
        val tokens = codeTokens(grammar, lines)
        val theme = codeTheme(dark, palette)
        val defaultColor = palette.text

        lines.mapIndexed { index, line ->
            if (line.isEmpty()) return@mapIndexed AnnotatedString("")
            val resolved = tokens.getOrNull(index).orEmpty().map { token ->
                Pair(token, resolveStyle(theme, token.scopes))
            }
            buildAnnotatedString {
                append(line)
                resolved.forEach { (token, style) ->
                    val start = token.start.coerceIn(0, line.length)
                    val end = token.end.coerceIn(start, line.length)
                    if (end <= start) return@forEach
                    val resolvedColor = style?.foreground
                        ?.takeIf { it != 0L }
                        ?.let { Color(textMateForegroundColor(it)) }
                    val italic = style?.fontStyle?.any { it == TextMateFontStyle.ITALIC } == true
                    if (resolvedColor == null && !italic) return@forEach
                    addStyle(
                        SpanStyle(
                            color = resolvedColor ?: defaultColor,
                            fontStyle = if (italic) FontStyle.Italic else null
                        ),
                        start,
                        end
                    )
                }
            }
        }
    } catch (error: Throwable) {
        plain
    }
}
