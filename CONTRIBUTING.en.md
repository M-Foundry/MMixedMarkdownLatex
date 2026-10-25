# Contribution guide

## Development environment

- JDK 17
- Android SDK Platform 36
- Android API 26+
- Kotlin 2.0.21
- Compose BOM 2024.12.01
- compileSdk 36

Set the Android SDK path with `ANDROID_HOME` or `sdk.dir` in `local.properties`. The wrapper pins Gradle 8.13.

## Verification

Run from the project root:

```shell
sh ./gradlew compileDebugKotlin verifyRendererDependencies
```

On Windows:

```shell
.\gradlew.bat compileDebugKotlin verifyRendererDependencies
```

`verifyRendererDependencies` checks the complete transitive dependency graph and prevents host UI dependencies from re-entering the renderer.

## Build a distribution package

```shell
sh ./gradlew bundleRenderer
```

On Windows, use `.\gradlew.bat bundleRenderer`. The generated `build/distributions/m3l-renderer-1.1.0.zip` contains a local Maven repository, AARs, source JARs, Gradle metadata, and licenses.

After unpacking it, add the local repository to the consuming project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("/absolute/path/to/unpacked-renderer") }
        google()
        mavenCentral()
    }
}
```

Then add:

```kotlin
implementation("com.mroldl001:m3l:1.1.0")
```
