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
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.textmate.theme.FontStyle as TextMateFontStyle
import kotlinx.coroutines.CancellationException
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
@SuppressLint("ProduceStateDoesNotAssignValue")
internal fun CodeBlockView(
    code: String,
    info: String?,
    primaryTint: Color = Color.Black,
    style: MarkdownCodeBlockStyle = LocalMarkdownStyle.current.codeBlock
) {
    val context = LocalContext.current
    val darkCodeBlock = LocalCodeBlockDark.current
    val palette = style.palette ?: codeBlockPalette(darkCodeBlock, primaryTint)
    val lines = code.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val fenceLabel = info?.trim()?.substringBefore(' ').orEmpty()
    val normalizedLanguage = normalizeCodeLanguage(fenceLabel, style.languageAliases)
    val language = normalizedLanguage.takeIf { it.isNotEmpty() }
    val codeLines = lines
    var blockCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val lineCount = codeLines.size
    val maxLineNumberWidth = lineCount.toString().length
    val appContext = context.applicationContext
    val codeHScroll = rememberScrollState()
    val highlightedLines by key(codeLines, language, palette, style) {
        produceState(initialValue = codeLines.map { AnnotatedString(it) }) {
            value = highlightCodeLinesAsync(codeLines, language, palette, style, appContext)
        }
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
            .padding(vertical = style.verticalPadding)
            .onGloballyPositioned { blockCoordinates = it }
            .background(palette.background, style.shape)
    ) {
        if (header != null) {
            header(block)
        } else if (style.showLanguageLabel && fenceLabel.isNotEmpty()) {
            BasicText(
                text = fenceLabel,
                modifier = Modifier.padding(style.headerPadding),
                style = TextStyle(color = palette.muted).merge(style.headerTextStyle)
            )
        } else {
            Spacer(Modifier.height(style.emptyHeaderHeight))
        }
        CodeBlockBody(
            highlightedLines = highlightedLines,
            maxLineNumberWidth = maxLineNumberWidth,
            textColor = palette.text,
            numberColor = palette.muted,
            horizontalState = codeHScroll,
            showHorizontalBar = style.showHorizontalBar,
            modifier = Modifier.fillMaxWidth().padding(style.contentPadding),
            textStyle = style.textStyle,
            lineNumberStyle = style.lineNumberStyle,
            showLineNumbers = style.showLineNumbers,
            lineNumberGutter = style.lineNumberGutter,
            scrollbarColor = style.scrollbarColor
        )
    }
}

private suspend fun highlightCodeLinesAsync(
    lines: List<String>,
    language: String?,
    palette: CodeBlockPalette,
    style: MarkdownCodeBlockStyle,
    context: Context
): List<AnnotatedString> = withContext(Dispatchers.Default) {
    try {
        val highlighted = style.highlighter?.invoke(lines.toList(), language)
            ?: highlightCodeLines(lines, language, palette, context, style.grammarProvider, style.highlightRules)
        if (highlighted.size == lines.size && highlighted.indices.all { highlighted[it].text == lines[it] }) {
            highlighted
        } else {
            lines.map { AnnotatedString(it) }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
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
    showHorizontalBar: Boolean = LocalMarkdownStyle.current.codeBlock.showHorizontalBar,
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalMarkdownStyle.current.codeBlock.textStyle,
    lineNumberStyle: TextStyle = LocalMarkdownStyle.current.codeBlock.lineNumberStyle,
    showLineNumbers: Boolean = LocalMarkdownStyle.current.codeBlock.showLineNumbers,
    lineNumberGutter: Dp = LocalMarkdownStyle.current.codeBlock.lineNumberGutter,
    scrollbarColor: Color = LocalMarkdownStyle.current.codeBlock.scrollbarColor
) {
    val hBarVisible by rememberScrollbarVisibility(horizontalState)
    val hBarActive by rememberScrollActivity(horizontalState)
    val resolvedTextStyle = TextStyle(color = textColor).merge(textStyle)
    val numberStyle = resolvedTextStyle.copy(color = numberColor).merge(lineNumberStyle)
    val lineBaselines = remember(highlightedLines.size) {
        List(highlightedLines.size) { HorizontalAlignmentLine { old, new -> minOf(old, new) } }
    }
    val scrollbarThumb = if (scrollbarColor != Color.Unspecified) {
        scrollbarColor
    } else if (!resolvedTextStyle.color.isDark()) {
        Color.White.copy(alpha = 0.4f)
    } else {
        Color.Black.copy(alpha = 0.4f)
    }

    Box(modifier) {
        SubcomposeLayout(
            modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        ) { constraints ->
            val numberPlaceables = subcompose("numbers") {
                if (showLineNumbers) {
                    highlightedLines.forEachIndexed { index, _ ->
                        BasicText(
                            text = (index + 1).toString().padStart(maxLineNumberWidth.coerceAtLeast(0), ' '),
                            style = numberStyle,
                            softWrap = false
                        )
                    }
                }
            }.map { it.measure(Constraints()) }
            val numberWidth = if (showLineNumbers) {
                ((numberPlaceables.maxOfOrNull { it.width } ?: 0) + lineNumberGutter.roundToPx().coerceAtLeast(0))
                    .coerceAtMost(constraints.maxWidth)
            } else {
                0
            }
            val codePlaceable = subcompose("code") {
                Layout(
                    modifier = Modifier.horizontalScroll(horizontalState),
                    content = {
                        highlightedLines.forEach { line ->
                            BasicText(
                                text = line.ifEmpty { AnnotatedString(" ") },
                                style = resolvedTextStyle,
                                softWrap = false
                            )
                        }
                    }
                ) { measurables, codeConstraints ->
                    val lineConstraints = codeConstraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
                    val textPlaceables = measurables.map { it.measure(lineConstraints) }
                    val textOffsets = IntArray(textPlaceables.size)
                    val baselines = IntArray(textPlaceables.size)
                    var contentHeight = 0
                    textPlaceables.forEachIndexed { index, text ->
                        val number = numberPlaceables.getOrNull(index)
                        val textBaseline = text[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: text.height
                        val numberBaseline = number?.let {
                            it[FirstBaseline].takeUnless { baseline -> baseline == AlignmentLine.Unspecified } ?: it.height
                        } ?: 0
                        val baseline = maxOf(textBaseline, numberBaseline)
                        baselines[index] = contentHeight + baseline
                        textOffsets[index] = baselines[index] - textBaseline
                        contentHeight += baseline + maxOf(
                            text.height - textBaseline,
                            (number?.height ?: 0) - numberBaseline
                        )
                    }
                    layout(
                        width = codeConstraints.constrainWidth(textPlaceables.maxOfOrNull { it.width } ?: 0),
                        height = codeConstraints.constrainHeight(contentHeight),
                        alignmentLines = lineBaselines.indices.associate { lineBaselines[it] to baselines[it] }
                    ) {
                        textPlaceables.forEachIndexed { index, text ->
                            text.placeRelative(0, textOffsets[index])
                        }
                    }
                }
            }.single().measure(constraints.copy(minWidth = 0).offset(horizontal = -numberWidth))
            layout(
                width = constraints.constrainWidth(numberWidth + codePlaceable.width),
                height = constraints.constrainHeight(codePlaceable.height)
            ) {
                codePlaceable.placeRelative(numberWidth, 0)
                numberPlaceables.forEachIndexed { index, number ->
                    val baseline = number[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: number.height
                    number.placeRelative(0, codePlaceable[lineBaselines[index]] - baseline)
                }
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


private const val SCROLLBAR_HIDE_DELAY = 700L
private const val SCROLLBAR_FADE_MILLIS = 220
private val SCROLLBAR_GAP = 8.dp
private val SCROLLBAR_TRACK_HEIGHT = 5.dp
private val SCROLLBAR_THUMB = Color(0x66FFFFFF)
private const val SCROLLBAR_MIN_FRACTION = 0.12f

private fun normalizeCodeLanguage(label: String, aliases: Map<String, String>): String {
    val language = label.lowercase().substringBefore(' ').trim()
    aliases.entries.firstOrNull { it.key.lowercase().trim() == language }?.value?.let {
        return it.lowercase().trim()
    }
    return when (language) {
        "py", "python3" -> "python"
        "js", "mjs", "cjs", "node" -> "javascript"
        "ts", "mts", "cts" -> "typescript"
        "kt", "kts" -> "kotlin"
        "c++", "cc", "cxx", "hpp" -> "cpp"
        "c#", "cs" -> "csharp"
        "golang" -> "go"
        "rs" -> "rust"
        "sh", "bash", "zsh", "shell", "shellscript" -> "shellscript"
        "pwsh", "ps1" -> "powershell"
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
        "lisp", "cl" -> "common-lisp"
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
        else -> language
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
    palette: CodeBlockPalette,
    context: Context,
    grammarProvider: ((Context, String) -> String?)?,
    highlightRules: List<MarkdownHighlightRule>
): List<AnnotatedString> {
    val plain = lines.map { AnnotatedString(it) }
    if (language == null) return plain
    return try {
        val grammar = codeGrammar(context, language, grammarProvider) ?: return plain
        val tokens = codeTokens(grammar, lines)
        val theme = codeTheme(palette)
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
                    val resolvedColor = style?.foreground?.let { Color(textMateForegroundColor(it)) }
                    val fontStyles = style?.fontStyle.orEmpty()
                    val decorations = buildList {
                        if (TextMateFontStyle.UNDERLINE in fontStyles) add(TextDecoration.Underline)
                        if (TextMateFontStyle.STRIKETHROUGH in fontStyles) add(TextDecoration.LineThrough)
                    }
                    var tokenStyle = SpanStyle(
                        color = resolvedColor ?: defaultColor,
                        fontStyle = if (TextMateFontStyle.ITALIC in fontStyles) FontStyle.Italic else null,
                        fontWeight = if (TextMateFontStyle.BOLD in fontStyles) FontWeight.Bold else null,
                        textDecoration = decorations.takeIf { it.isNotEmpty() }?.let { TextDecoration.combine(it) }
                    )
                    highlightRules.forEach { rule ->
                        if (rule.scopes.any { selector ->
                            token.scopes.any { scope -> scope == selector || scope.startsWith("$selector.") }
                        }) {
                            tokenStyle = tokenStyle.merge(rule.style)
                        }
                    }
                    addStyle(tokenStyle, start, end)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        plain
    }
}
