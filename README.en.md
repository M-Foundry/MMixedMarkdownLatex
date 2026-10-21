<div align="center">

![M3L](m3l-title.jpg)

</div>

MMixedMarkdown LaTeX（M3L） is an Android Jetpack Compose renderer for Markdown, LaTeX, and syntax-highlighted code, Extracted from [MChat](https://github.com/MChat-code/MChat), M3L focuses on rendering only. 

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
- Host-provided code block toolbar and link handling
- Bundled grammars, formula fonts, and the LaTeX engine

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
    implementation("com.github.M-Foundry:MMixedMarkdownLatex:1.0.0-beta")
}
```

### Build a local distribution

Install JDK 17 and Android SDK Platform 36, and set the SDK path with `ANDROID_HOME` or `sdk.dir` in `local.properties`. The wrapper pins Gradle 8.13. Build the distribution from this project's root:

```shell
sh ./gradlew bundleRenderer
```

On Windows:

```shell
.\gradlew.bat bundleRenderer
```

The generated `build/distributions/m3l-renderer-1.0.0-beta.zip` contains a local Maven repository, AARs, source JARs, Gradle metadata, and licenses. JitPack consumers do not need this task.

Add the unpacked repository to `settings.gradle.kts`, then add:

```kotlin
implementation("com.mroldl001:mmixedmarkdownlatex:1.0.0-beta")
```

## 📖 Basic usage

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mroldl001.m3l.MixedMarkdownLatex

@Composable
fun RendererScreen(text: String, streaming: Boolean) {
    MixedMarkdownLatex(text = text, textColor = Color.Black, isStreaming = streaming)
}
```

Use `LocalMarkdownRendererTheme` for renderer colors and `LocalCodeBlockDark` for code block brightness. M3L does not manage page scrolling.

`text` is a Markdown string. The renderer recognizes Markdown, tables, blockquotes, task lists, basic HTML, fenced code, and LaTeX. It emits a full-width Compose column, so place it inside the host's `LazyColumn`, `Column.verticalScroll`, or another scrolling container:

```kotlin
LazyColumn {
    item {
        CompositionLocalProvider(
            LocalMarkdownRendererTheme provides MarkdownRendererTheme(
                primary = Color(0xFF6750A4),
                surface = Color.White,
                outlineVariant = Color(0xFFCAC4D0)
            ),
            LocalCodeBlockDark provides isSystemInDarkTheme()
        ) {
            MixedMarkdownLatex(
                text = markdown,
                textColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                isStreaming = false
            )
        }
    }
}
```

### LaTeX, tables, and streaming

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

During model output, pass `isStreaming = true`, then switch it to `false` when the response is complete. The renderer stabilizes incomplete formulas, fences, and tables and refreshes the stable text about every 80 ms, avoiding repeated broken intermediate layouts. Backticks protect `$` characters inside code. Keep a stable Compose key when rendering streamed messages in a list.

## 🎨 Host interaction

Use `LocalCodeBlockHeader` to provide a custom toolbar. `CodeBlockContent` exposes the raw code, language, highlighted lines, palette, and block bounds. `CodeBlockBody` can be reused in a host-owned fullscreen screen.

Use `LocalMarkdownLinkHandler` when the host wants to handle links:

```kotlin
CompositionLocalProvider(
    LocalMarkdownLinkHandler provides { url -> openLink(url) }
) {
    MixedMarkdownLatex(text = content)
}
```

Code fence labels are normalized (`py` to `python`, `js` to `javascript`, `kt` to `kotlin`, `sh` to `shellscript`, and more). Bundled TextMate grammars enable highlighting; an unknown language still gets readable code, line numbers, and horizontal scrolling with plain text colors. `CodeBlockContent` exposes the raw code, normalized language, highlighted lines, palette, and a lazy `boundsInWindow` lookup. Copy, fullscreen, icons, and clipboard actions remain host responsibilities.

## 📱 Requirements

- Android API 26+
- Kotlin 2.0.21
- Compose BOM 2024.12.01
- compileSdk 36

## ✅ Verification

```shell
sh ./gradlew compileDebugKotlin verifyRendererDependencies
```

## 📄 License

See `LICENSE` in the project root
