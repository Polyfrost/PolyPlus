pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        maven("https://maven.fabricmc.net/")
        maven("https://repo.polyfrost.org/releases")
        mavenCentral()
    }

    plugins {
        kotlin("jvm") version "2.4.10"
        kotlin("plugin.serialization") version "2.4.10"
        kotlin("plugin.compose") version "2.4.10"
        id("org.jetbrains.kotlinx.atomicfu") version "0.33.0"
        id("com.gradleup.shadow") version "9.6.1"
        id("me.modmuss50.mod-publish-plugin") version "2.2.0"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    // Applies the loom variant matching each version and puts it on the buildscript
    // classpath, using `loomx.loom_version` from stonecutter.properties.toml
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.polyfrost.org/releases")
        maven("https://repo.polyfrost.org/snapshots")
        maven("https://jitpack.io")
        maven("https://maven.bawnorton.com/releases")
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
        google()
    }
}

stonecutter.create(rootProject) {
    // Per-version dependencies live in stonecutter.properties.toml
    val mcVersions = listOf("1.21.1", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11", "26.1", "26.2")
    versions(mcVersions.associateBy { "$it-fabric" })
    vcsVersion = "26.2-fabric"
}

rootProject.name = "PolyPlus"
