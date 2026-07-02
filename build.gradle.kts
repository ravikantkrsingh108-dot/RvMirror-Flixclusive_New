import com.android.build.gradle.BaseExtension
import com.flixclusive.gradle.FlixclusiveProviderExtension
import com.flixclusive.gradle.getFlixclusive

buildscript {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("com.github.flixclusiveorg.core-gradle:core-gradle:1.2.7")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }

    configurations.all {
        resolutionStrategy.force("com.github.vidstige:jadb:9083b5096f")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

fun Project.flxProvider(configuration: FlixclusiveProviderExtension.() -> Unit) =
    extensions.getFlixclusive().configuration()

@Suppress("UnstableApiUsage")
fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    val projectName = name.lowercase().replace("-", "_")

    apply(plugin = "flx-provider")
    apply(plugin = "kotlin-android")

    flxProvider {
        author(
            name = "ravikantkrsingh108-dot",
            socialLink = "https://github.com/ravikantkrsingh108-dot",
            image = "https://github.com/ravikantkrsingh108-dot.png",
        )

        setRepository("https://github.com/ravikantkrsingh108-dot/RvMirror-Flixclusive_New")
        id = "rvmirror-$projectName"
    }

    android {
        namespace = if (name == "RvMirror") {
            "com.rvmirror.provider"
        } else {
            "com.rvmirror.$projectName"
        }
    }

    dependencies {
        val implementation by configurations
        val compileOnly by configurations
        val testImplementation by configurations
        val coreLibraryDesugaring by configurations

        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
        implementation("androidx.annotation:annotation:1.9.1")
        implementation("androidx.compose.runtime:runtime")
        implementation("org.jsoup:jsoup:1.22.2")
        compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

        val coreStubs = "com.github.flixclusiveorg.core-stubs:provider:1.2.5"
        implementation(coreStubs)
        testImplementation(coreStubs)
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
        testImplementation("junit:junit:4.13.2")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}