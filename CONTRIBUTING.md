# 贡献指南

**语言：** 简体中文 | [English](CONTRIBUTING.en.md)

## 开发环境

- JDK 17
- Android SDK Platform 36
- Android API 26+
- Kotlin 2.0.21
- Compose BOM 2024.12.01
- compileSdk 36

通过 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir` 指定 Android SDK。Wrapper 固定为 Gradle 8.13。

## 验证

在项目根目录运行：

```shell
sh ./gradlew compileDebugKotlin verifyRendererDependencies
```

Windows 使用：

```shell
.\gradlew.bat compileDebugKotlin verifyRendererDependencies
```

`verifyRendererDependencies` 会检查完整传递依赖图，阻止宿主 UI 依赖重新进入渲染器。

## 构建发行包

```shell
sh ./gradlew bundleRenderer
```

Windows 使用 `.\gradlew.bat bundleRenderer`。生成的 `build/distributions/m3l-renderer-1.1.0.zip` 包含本地 Maven 仓库、AAR、sources JAR、Gradle 元数据和许可证。

解压后，在目标项目的 `settings.gradle.kts` 添加本地仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("/absolute/path/to/unpacked-renderer") }
        google()
        mavenCentral()
    }
}
```

然后添加依赖：

```kotlin
implementation("com.mroldl001:m3l:1.1.0")
```
