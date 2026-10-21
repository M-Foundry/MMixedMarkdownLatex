<div align="center">

![M3L](m3l-title.jpg)

</div>

MMixedMarkdown LaTeX（M3L）是一个面向 Android Jetpack Compose 的 Markdown、LaTeX 与代码高亮渲染器，它从 [MChat](https://github.com/MChat-code/MChat) 抽离而来，专注于内容渲染。

**语言：** 简体中文 | [English](README.en.md)

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square\&logo=kotlin\&logoColor=white)
![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2024.12.01-4285F4?style=flat-square\&logo=jetpackcompose\&logoColor=white)
![Android API](https://img.shields.io/badge/Android%20API-26%2B-3DDC84?style=flat-square\&logo=android\&logoColor=white)

[![JitPack](https://jitpack.io/v/M-Foundry/MMixedMarkdownLatex.svg)](https://jitpack.io/#M-Foundry/MMixedMarkdownLatex)

## ✨ 特性

- Markdown、表格、引用、分隔线、任务列表与基础 HTML
- 行内与块级 LaTeX 公式，包含化学公式等扩展
- TextMate 代码语法高亮、语言标签、行号和横向滚动
- 支持流式文本，暂缓显示未闭合公式、代码块和表格
- 代码块工具栏与链接处理由宿主注入
- 语法文件、公式字体和 LaTeX 引擎随库发布

## 🚀 快速开始

### 从 JitPack 使用

项目已配置为可由 JitPack 构建。目标项目在 `settings.gradle.kts` 中加入：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

然后在模块的 `build.gradle.kts` 中加入：

```kotlin
dependencies {
    implementation("com.github.M-Foundry:MMixedMarkdownLatex:1.0.1")
}
```

### 本地构建发行包

安装 JDK 17 和 Android SDK Platform 36，通过 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir` 指定 SDK 路径，然后在项目根目录运行（Wrapper 固定为 Gradle 8.13）：

```shell
sh ./gradlew bundleRenderer
```

Windows 使用 `.\gradlew.bat bundleRenderer`。生成的 `build/distributions/m3l-renderer-1.0.0-beta.zip` 包含本地 Maven 仓库、AAR、sources JAR、Gradle 元数据和许可证。JitPack 用户不需要执行这个任务。

解压后，在目标项目的 `settings.gradle.kts` 添加：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("/absolute/path/to/unpacked-renderer") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

然后添加依赖：

```kotlin
implementation("com.mroldl001:mmixedmarkdownlatex:1.0.1")
```

## 📖 基本用法

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mroldl001.m3l.MixedMarkdownLatex

@Composable
fun RendererScreen(text: String, streaming: Boolean) {
    MixedMarkdownLatex(
        text = text,
        textColor = Color.Black,
        isStreaming = streaming
    )
}
```

`text` 可以直接是 Markdown 字符串。渲染器会识别 Markdown、表格、引用、任务列表、基础 HTML、代码围栏和 LaTeX；渲染结果本身是一个填满父容器宽度的 `Column`。

主题通过 `LocalMarkdownRendererTheme` 注入，代码块明暗色通过 `LocalCodeBlockDark` 注入。库不读取 `MaterialTheme`，也不管理页面滚动，因此通常把它放在宿主的 `LazyColumn`、`Column.verticalScroll` 或其他滚动容器中：

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

### LaTeX 与表格

行内公式使用 `$...$` 或 `\\(...\\)`，块级公式使用独占一行的 `$$...$$` 或 `\\[...\\]`。公式会在后台生成 Bitmap，并按文字颜色、字号和显示模式缓存；首次生成前会暂时显示等宽文本，解析失败时也会保留可读的原文。超宽公式和表格可横向滚动。

```markdown
费马定理：$a^n + b^n = c^n$

$$
\\int_0^1 x^2 dx = \\frac{1}{3}
$$

| 名称 | 值 |
| --- | ---: |
| alpha | 1 |
```

避免在行内公式中使用未配对的 `$`；反引号代码中的 `$` 不会被当作公式。表格单元格中的公式也会参与宽度测量，内容过宽时由表格自身横向滚动。

### 流式文本

接收模型输出时将 `isStreaming` 设为 `true`，结束后切换为 `false`：

```kotlin
MixedMarkdownLatex(
    text = partialResponse,
    textColor = Color.Black,
    isStreaming = responseIsRunning
)
```

流式期间渲染器会暂缓未闭合的 LaTeX、代码围栏和表格，内部每约 80 ms 更新一次稳定文本；这样不会因为模型尚未输出结束分隔符而反复创建错误的中间布局。停止流式后会立即使用完整文本。宿主仍应使用稳定的 `key`，避免在列表中频繁销毁该 Composable。

## 🎨 宿主交互

通过 `LocalCodeBlockHeader` 提供代码块工具栏：

```kotlin
CompositionLocalProvider(
    LocalCodeBlockHeader provides { block ->
        Row {
            BasicText(block.label)
            BasicText("复制", Modifier.clickable { onCopy(block.code) })
            BasicText("全屏", Modifier.clickable { onFullscreen(block) })
        }
    }
) {
    MixedMarkdownLatex(text = text, textColor = Color.Black)
}
```

`CodeBlockContent` 提供原始代码、语言、已高亮行、配色和代码块位置；全屏页面可复用 `CodeBlockBody`。

代码围栏的语言标签会做常见别名归一化，例如 `py`→`python`、`js`→`javascript`、`kt`→`kotlin`、`sh`→`shellscript`。随库发布的 TextMate grammar 才会启用高亮；没有对应 grammar 时仍显示代码、行号和横向滚动，只是不着色。工具栏回调可以读取 `block.code`、`block.language` 和 `block.boundsInWindow()`，复制、全屏和图标行为必须由宿主实现。

默认不会自动打开链接，需要时注入：

```kotlin
CompositionLocalProvider(
    LocalMarkdownLinkHandler provides { url -> openLink(url) }
) {
    MixedMarkdownLatex(text = content)
}
```

剪贴板、通知、全屏界面、返回手势和外部链接 Intent 均由宿主负责。

## 📱 环境要求

- Android API 26+
- Kotlin 2.0.21
- Compose BOM 2024.12.01
- compileSdk 36

## ✅ 验证

```shell
sh ./gradlew compileDebugKotlin verifyRendererDependencies
```

`verifyRendererDependencies` 会检查完整传递依赖图，阻止宿主 UI 依赖重新进入渲染器。

## 📄 许可证

本项目使用 Apache-2.0 许可证，详见根目录 `LICENSE`
