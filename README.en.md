<div align="center">

![M3L](m3l-title.jpg)

</div>

M3L is a progressive rich-text rendering library for Android Jetpack Compose, designed around composition over inheritance. Extracted from [MChat](https://github.com/MChat-code/MChat), it supports rendering and mixing Markdown, LaTeX, code blocks, tables, and more, with theme- and component-level composition, per-block style overrides, syntax highlighting, streaming rendering, and native extension hooks.

**Languages:** [简体中文](README.md) | English

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square\&logo=kotlin\&logoColor=white)
![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2024.12.01-4285F4?style=flat-square\&logo=jetpackcompose\&logoColor=white)
![Android API](https://img.shields.io/badge/Android%20API-26%2B-3DDC84?style=flat-square\&logo=android\&logoColor=white)

[![JitPack](https://jitpack.io/v/M-Foundry/MMixedMarkdownLatex.svg)](https://jitpack.io/#M-Foundry/MMixedMarkdownLatex)

## ✨ Features

- Markdown, tables, blockquotes, dividers, task lists, and basic HTML
- Inline and block LaTeX formulas, including chemistry extensions
- TextMate syntax highlighting with language labels, line numbers, and horizontal scrolling
- Streaming-friendly rendering for incomplete formulas, code fences, and tables
- Progressive API design: start with `Markdown(content = ...)`, then add styles, components, and behavior as needed
- `MarkdownTheme` as a unified entry point for themes, components, link handling, and code block toolbars
- Designed around composition over inheritance, with style objects, slots, per-block overrides, and custom renderers for extension
- Separation of concerns: rendering appearance, host behavior, and native extensions have independent entry points

## 🚀 Quick start

### Use from JitPack

The project is configured for JitPack. Add the repository to the consuming project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency to the app or library module:

```kotlin
dependencies {
    implementation("com.github.M-Foundry:MMixedMarkdownLatex:1.1.0")
}
```

## 📖 Basic usage

```kotlin
import androidx.compose.runtime.Composable
import com.mroldl001.m3l.*

@Composable
fun RendererScreen(text: String, streaming: Boolean) {
    Markdown(content = text, isStreaming = streaming)
}
```

Use `MarkdownTheme` to share colors, styles, components, code block brightness, link handling, and a code block header. Omitted arguments inherit their parent values; direct CompositionLocal injection remains available. M3L does not read `MaterialTheme` or manage page scrolling.

For parsing only, call `parseMarkdownBlocks(content)` to obtain `List<MarkdownBlock>`, with block types represented by `MarkdownBlockType`. The optional `allowUnclosedCodeFence` argument defaults to false.

`content` is a Markdown string. The renderer recognizes Markdown, tables, blockquotes, task lists, basic HTML, fenced code, and LaTeX. It emits a full-width Compose column, so place it inside the host's `LazyColumn`, `Column.verticalScroll`, or another scrolling container:

```kotlin
LazyColumn {
    item {
        MarkdownTheme(
            colors = MarkdownColors(
                primary = Color(0xFF6750A4),
                text = if (isSystemInDarkTheme()) Color.White else Color.Black,
                surface = Color.White,
                outlineVariant = Color(0xFFCAC4D0)
            ),
            darkCodeBlocks = isSystemInDarkTheme(),
            onLinkClick = { url -> openLink(url) }
        ) {
            Markdown(content = markdown)
        }
    }
}
```

### LaTeX and tables

Pass `onFormulaWidthMeasured = { widthDp -> ... }` to `Markdown` to receive formula widths in dp.

Use `$...$` or `\\(...\\)` for inline formulas, and a standalone `$$...$$` or `\\[...\\]` block for display formulas. Formulas are rendered as bitmaps on a background dispatcher and cached by formula, color, size, and display mode. Until a bitmap is ready, the source is shown as monospace fallback text; invalid formulas also remain readable. Wide formulas and tables scroll horizontally.

```markdown
Fermat-style example: $a^n + b^n = c^n$

$$
\\int_0^1 x^2 dx = \\frac{1}{3}
$$

| Name | Value |
| --- | ---: |
| alpha | 1 |
```

### Streaming text

During model output, pass `isStreaming = true`, then switch it to `false` when the response is complete. The renderer stabilizes incomplete formulas, fences, and tables and refreshes the stable text about every 80 ms, avoiding repeated broken intermediate layouts. Backticks protect `$` characters inside code. Keep a stable Compose key when rendering streamed messages in a list.

## 🪡 Host interaction and behavior

Use `MarkdownTheme.codeBlockHeader` or provide `LocalCodeBlockHeader` directly for a custom toolbar. `CodeBlockContent` exposes the raw code, language, highlighted lines, palette, and block bounds. `CodeBlockBody` can be reused in a host-owned fullscreen screen.

Pass link handling directly to the renderer, or use `MarkdownTheme.onLinkClick` for a shared default:

```kotlin
Markdown(content = content, onLinkClick = { url -> openLink(url) })
```

Code fence labels are normalized (`py` to `python`, `js` to `javascript`, `kt` to `kotlin`, `sh` to `shellscript`, and more). TextMate grammars can come from bundled resources or the `grammarProvider` described below; an unknown language still gets readable code, line numbers, and horizontal scrolling. `CodeBlockContent` exposes the raw code, normalized language, highlighted lines, palette, and a lazy `boundsInWindow` lookup. Copy, fullscreen, icons, and clipboard actions remain host responsibilities.

## 🧩 Styling and component extensions

This section covers rendering appearance and component replacement only. Link navigation, copy, fullscreen, clipboard, and notifications remain host behavior as described above.

`Markdown(content = ...)` requires only the content. Use `modifier` for the outer layout; `style` and `components` inherit values from `MarkdownTheme`. Pass `onLinkClick` to handle links directly for this renderer. Text color precedence is `style.body.color`, the `color` argument, then `MarkdownColors.text`, which defaults to black. `Color.Unspecified` inherits the default.

This example removes link underlines, overrides inline code and table colors, and sets native selection handle colors independently:

```kotlin
val style = MarkdownStyle(
    body = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    headings = mapOf(2 to TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)),
    link = MarkdownLinkStyle(color = Color(0xFF1565C0), underline = false),
    inlineCode = MarkdownInlineCodeStyle(
        color = Color(0xFFC62828),
        backgroundColor = Color(0xFFFFF3E0),
        cornerRadius = 6.dp
    ),
    selection = MarkdownSelectionStyle(
        handleColor = Color(0xFF1565C0),
        backgroundColor = Color(0x331565C0)
    ),
    table = MarkdownTableStyle(
        headerBackground = Color(0xFFE3F2FD),
        alternateBackground = Color(0xFFF5F5F5),
        columnAlignments = mapOf(1 to TextAlign.End)
    )
)
Markdown(content = content, color = Color.Black, style = style)
```

Renderer types are in `com.mroldl001.m3l.*`; `TextStyle`, `FontWeight`, `TextAlign`, `Color`, `dp`, and `sp` come from Compose. A parent can provide `CompositionLocalProvider(LocalMarkdownStyle provides style)`; nested blockquotes and table cells inherit the configuration. Use `copy` on the current style to preserve its other settings. Passing a new `MarkdownStyle()` uses that object's complete defaults.

### Style fields

| Type                      | Fields and purpose                                                                                                                                                                                                                                                                                                                                                                 |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MarkdownStyle`           | `body` text typography; `headings` typography by heading level 1–6; `paragraphSpacing`; `listIndent`; `bulletColor`, `bulletWidth`; `streamingIntervalMillis`, a positive streaming refresh interval, default 80                                                                                                                                                                   |
| `MarkdownStyle` children  | `link`, `inlineCode`, `selection`, `codeBlock`, `quote`, `divider`, `table`, `latex`, `image`, and `native`, detailed below                                                                                                                                                                                                                                                        |
| `MarkdownLinkStyle`       | `color`, `underline`, `textStyle: SpanStyle`; `autoLinkMask`, default web URLs and email addresses, 0 to disable automatic detection; `icon: (String) -> Drawable?` supplies an inline icon for each URL, null for none; `iconSize`, default 16.dp; `iconGap`, default 4.dp; `iconPlacement` defaults to `BeforeText` and can be `AfterText` to place the icon after the link text |
| `MarkdownInlineCodeStyle` | `color`, `backgroundColor`, `cornerRadius`, `fontFamily`, `fontWeight`, `fontStyle`, `sizeScale`; defaults include monospace, 4.dp corners, and 0.95 size scale                                                                                                                                                                                                                    |
| `MarkdownSelectionStyle`  | `enabled` controls text selection; `handleColor` and `backgroundColor` override handle and selection colors. Unspecified colors follow the primary theme color. Native handle tinting requires Android API 29+                                                                                                                                                                     |

#### Layout, table, and media styles

| Type                                       | Fields and purpose                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `MarkdownQuoteStyle`                       | `barColor`, `backgroundColor`, `textColor`, `textStyle`; `barWidth`, `barShape`; `spacing` between the bar and content; `padding` and `contentPadding`                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `MarkdownDividerStyle`                     | `color`, `thickness`, `shape`, `padding`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `MarkdownTableStyle` colors and typography | `borderColor`, `headerBackground`, `bodyBackground`, `alternateBackground`, `headerTextColor`, `bodyTextColor`, `headerTextStyle`, `bodyTextStyle`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `MarkdownTableStyle` layout                | `borderWidth`, `cellBorderWidth`, `shape`, `padding`, `cellPadding`; `minColumnWidth`, `maxColumnWidth` bound automatic widths; `columnWidths` supplies widths by zero-based column index, with remaining columns measured automatically; `columnAlignments` overrides text alignment per column; `cellAlignment` mainly controls vertical position, since body text fills the column width and uses `columnAlignments` or `TextStyle.textAlign` for horizontal text alignment; `respectMarkdownAlignment`, default false, enables colon alignment from the Markdown separator row, with explicit `columnAlignments` taking priority |
| `MarkdownImageStyle`                       | `width`, `height`, `cornerRadius`; native `loadingDrawable` and `failureDrawable`; `onClick` receives the image URL; `imageLoader` supplies a Coil `ImageLoader`; `configureRequest` receives `ImageRequest.Builder` and the URL; `onClickWithContext` receives the image position and `TextView`; `verticalOffsetPx` returns a per-image vertical draw offset in pixels; `motionPadding` reserves space above and below a moving image                                                                                                                                                                                              |

### Code blocks and highlighting

| `MarkdownCodeBlockStyle` field                                               | Purpose                                                                                                                                                                                                                           |
| ---------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `palette`                                                                    | Replaces the `CodeBlockPalette`; defaults come from `LocalCodeBlockDark` and the renderer theme                                                                                                                                   |
| `textStyle`, `lineNumberStyle`                                               | Code and line-number typography, measured independently and aligned by each row's baseline, including fallback fonts for Chinese text                                                                                             |
| `showLineNumbers`, `lineNumberGutter`                                        | Line-number visibility and distance from the code                                                                                                                                                                                 |
| `shape`, `contentPadding`, `verticalPadding`                                 | Container shape, code padding, and space above/below the block                                                                                                                                                                    |
| `headerTextStyle`, `headerPadding`, `emptyHeaderHeight`, `showLanguageLabel` | Default language-label typography and spacing; `LocalCodeBlockHeader` gives the host control of the header                                                                                                                        |
| `scrollbarColor`, `showHorizontalBar`                                        | Horizontal scrollbar color and visibility                                                                                                                                                                                         |
| `languageAliases`                                                            | Custom language aliases take priority over built-in mappings                                                                                                                                                                      |
| `grammarProvider`                                                            | `(Context, String) -> String?` returning TextMate JSON for a normalized language or referenced scope name; null falls back to bundled grammars                                                                                    |
| `highlightRules`                                                             | `List<MarkdownHighlightRule>`; each rule has `scopes` and `style: SpanStyle`. A selector matches an identical token scope or a scope starting with `selector.`. Rules merge in list order; later properties override earlier ones |
| `highlighter`                                                                | `MarkdownCodeHighlighter`, a `suspend (List<String>, String?) -> List<AnnotatedString>` callback replacing the highlighting pipeline on a background dispatcher                                                                   |

`CodeBlockPalette` exposes `background`, `text`, `muted`, `keyword`, `string`, `number`, `comment`, `type`, `function`, `operator`, `annotation`, and `property`. The Composable `codeBlockPalette(dark, primaryTint)` creates a base palette that can be adjusted with `copy`:

```kotlin
val palette = codeBlockPalette(dark = false, primaryTint = Color(0xFF1565C0))
val style = LocalMarkdownStyle.current.let { current ->
    current.copy(
        codeBlock = current.codeBlock.copy(
            palette = palette.copy(keyword = Color(0xFFAD1457)),
            showLineNumbers = false,
            languageAliases = mapOf("custom-kotlin" to "kotlin"),
            highlightRules = listOf(
                MarkdownHighlightRule(
                    scopes = listOf("comment"),
                    style = SpanStyle(color = Color(0xFF607D8B), fontStyle = FontStyle.Italic)
                )
            )
        )
    )
}
```

A custom `highlighter` must preserve the input line count and each line's original text. Exceptions or changed text fall back to plain lines. Hosts can reuse `CodeBlockBody` in fullscreen screens and pass `textStyle`, `lineNumberStyle`, `showLineNumbers`, `lineNumberGutter`, `scrollbarColor`, `showHorizontalBar`, `fillHeight`, and `modifier` directly.

### Formulas

`MarkdownLatexStyle` provides `inlineColor`, `blockColor`, `inlineFontSize`, and `blockFontSize`. Both sizes default to 18.sp and require positive, finite sp values. `transform` preprocesses the formula string before rendering, for both inline and block formulas.

Block formulas also support `blockAlignment`, `blockPadding`, `fallbackStyle`, `loading`, `error`, `onClick`, and `contentDescription`. `loading` and `error` are Compose slots receiving the original formula; `onClick` receives that source, and `contentDescription` returns an accessibility description. `blockPadding = null` keeps automatic height-dependent spacing; `compact` removes that default spacing. Inline formulas retain their source while loading or when rendering fails.

### Icons, actions, component replacement, and per-block overrides

`MarkdownComponents` applies to parsed blocks:

| Field                 | Behavior                                                                                                                                                                      |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `leading`, `trailing` | `MarkdownBlockSlot` content before and after the built-in or replacement content, arranged horizontally; suitable for Compose icons                                           |
| `actions`             | `MarkdownBlockSlot` content below the row, such as copy buttons                                                                                                               |
| `blocks`              | Maps `MarkdownBlockType` to `MarkdownBlockRenderer`. It receives a block context and `defaultContent`; fully replace a component or decorate it by calling `defaultContent()` |
| `resolveStyle`        | Receives `MarkdownBlockContext` with inherited styles and returns the current block's complete `MarkdownStyle`. Use `context.style.copy(...)` for selective overrides         |
| `modifier`            | Receives the resolved context and supplies a `Modifier` for the whole block                                                                                                   |

`MarkdownBlockContext` contains `block` (`type`, `content`, `info`), zero-based sibling `index`, `path`, `depth`, and `style`. Slots, renderers, and `modifier` receive the style returned by `resolveStyle`. A `path` starts with the top-level block index; nested blockquotes append block indices, and table cells append row and column indices followed by block indices. Header rows use index 0. `depth = path.size - 1`, including table coordinates. Paths reflect the parsed structure and are not stable IDs across edits.

```kotlin
val decorateFormula: MarkdownBlockRenderer = { _, defaultContent ->
    Column(Modifier.background(Color(0xFFF5F5F5))) {
        defaultContent()
    }
}
val components = MarkdownComponents(
    leading = { context ->
        if (context.block.type == MarkdownBlockType.QUOTE) {
            Image(painter = quoteIcon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    },
    actions = { context ->
        if (context.block.type == MarkdownBlockType.CODE_BLOCK) {
            BasicText("Copy", Modifier.clickable { copyCode(context.block.content) })
        }
    },
    blocks = mapOf(MarkdownBlockType.LATEX_BLOCK to decorateFormula),
    resolveStyle = { context ->
        if (context.block.type == MarkdownBlockType.QUOTE) {
            context.style.copy(quote = context.style.quote.copy(barColor = Color(0xFF1565C0)))
        } else {
            context.style
        }
    }
)
Markdown(content = content, color = Color.Black, components = components)
```

The host supplies `quoteIcon: Painter` and `copyCode`. `defaultContent` calls the current block's built-in renderer directly; child blocks in nested blockquotes and tables still inherit component configuration. `MarkdownBlockType` includes `MARKDOWN`, `TABLE`, `CODE_BLOCK`, `INLINE_CODE`, `LATEX_INLINE`, `LATEX_BLOCK`, `QUOTE`, and `HR`. Inline links, code, and formulas inside ordinary paragraphs are rendered with the Markdown body and use their corresponding style configuration. `CompositionLocalProvider(LocalMarkdownComponents provides components)` configures slots for a subtree.

### Native extensions

Inline link icons and image placeholders use Android `Drawable`; block icons, formula loading/error slots, and action areas use Compose content. `MarkdownImageStyle.imageLoader` can reuse a host Coil loader, and `configureRequest` can set headers, cache policies, and other Coil request options. The host manages the lifecycle of its custom image loader.

`MarkdownNativeConfiguration` provides `configureMarkwon`, `transformSpans`, and `configureTextView`. `configureMarkwon` runs after built-in plugins are registered and before Markwon is built. `transformSpans` runs after formula, heading, link, image, and inline code styling. `configureTextView` runs after view styling and rendered text assignment, so it can override native view settings. Hooks can run again on recomposition or style changes and should tolerate repeated calls. Markwon and Coil types are exposed through the library's public API; Material components are not required.

## 🤝 Development and contribution

See the [contribution guide](CONTRIBUTING.en.md) for the development environment, local verification, and distribution package instructions.

## 📄 License

This project is licensed under Apache-2.0. See `LICENSE` in the project root.
