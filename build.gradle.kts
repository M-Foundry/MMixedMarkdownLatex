plugins {
    id("com.android.library") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("maven-publish")
}

tasks.register("verifyRendererDependencies") {
    group = "verification"
    description = "Verifies that the renderer has no Material or host interaction dependencies."
    doLast {
        val forbidden = configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            .filter {
                it.group == "androidx.compose.material3" ||
                    it.group == "androidx.compose.material" ||
                    (it.group == "androidx.activity" && it.module == "activity-compose")
            }
        check(forbidden.isEmpty()) { "Renderer still depends on host UI: $forbidden" }
    }
}

group = "com.mroldl001"
version = "1.0.1"

android {
    namespace = "com.mroldl001.m3l"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2024.12.01"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    api("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("com.github.jeziellago:Markwon:58aa5aba6a")
    api("io.coil-kt:coil:2.7.0")
    implementation("io.github.ivan-magda:kotlin-textmate-core:0.3.0")
    implementation("com.github.sixgodIT:JLaTexMath-andriod:-SNAPSHOT")
}

publishing {
    repositories {
        maven {
            name = "Renderer"
            url = rootProject.layout.buildDirectory.dir("renderer-repository").get().asFile.toURI()
        }
    }
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            artifactId = "mmixedmarkdownlatex"
            pom {
                name = "MMixedMarkdownLatex"
                description = "Mixed Markdown + LaTeX renderer library"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                        distribution = "repo"
                    }
                }
            }
        }
    }
}

tasks.register<Zip>("bundleRenderer") {
    group = "distribution"
    description = "Packages the renderer Maven repository and license for local use."
    dependsOn("publishReleasePublicationToRendererRepository")
    archiveFileName.set("m3l-renderer-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.buildDirectory.dir("renderer-repository"))
    from("LICENSE")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
