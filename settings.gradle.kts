@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        maven("https://maven.fabricmc.net/")
        maven("https://repo.polyfrost.org/releases")
        mavenCentral()
    }

    plugins {
        kotlin("jvm") version "2.3.0"
        kotlin("plugin.serialization") version "2.3.0"
        kotlin("plugin.compose") version "2.3.0"
        id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
        id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT"
        id("org.jetbrains.kotlinx.atomicfu") version "0.27.0"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "PolyPlus"

val mcVersions = listOf("1.21.1", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11", "26.1", "26.2")
val loaders = listOf("fabric")

/** 1.21.11 and below: remapping Loom + Mojang/Parchment mappings. 26.1+ is unobfuscated (no mappings). */
fun usesFabricObfLoom(mc: String): Boolean {
    val major = mc.substringBefore('.').toIntOrNull() ?: return true
    return major < 26
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.polyfrost.org/releases")
        maven("https://repo.polyfrost.org/snapshots")
        maven("https://jitpack.io")
        maven("https://maven.terraformersmc.com/releases") {
            content { includeGroup("com.terraformersmc") }
        }
        maven("https://maven.bawnorton.com/releases")
        maven("https://maven.parchmentmc.org")
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
        google()
    }

    versionCatalogs {
        create("fabric") {
            from(files("gradle/fabric.versions.toml"))
        }

        for (mc in mcVersions) {
            val commonName = "common${mc.replace(".", "")}"
            create(commonName) {
                from(files("gradle/common/$mc.versions.toml"))
            }
            for (loader in loaders) {
                val catalogName = "$loader${mc.replace(".", "")}"
                val file = file("gradle/$loader/$mc.versions.toml")
                if (file.exists() && file.length() > 0) {
                    create(catalogName) {
                        from(files(file))
                    }
                } else {
                    create(catalogName) {
                        from(files("gradle/common/$mc.versions.toml"))
                    }
                }
            }
        }
    }
}

stonecutter.create(rootProject) {
    vcsVersion = "1.21.8-fabric"

    for (mc in mcVersions) {
        for (loader in loaders) {
            val projectName = "$mc-$loader"
            val buildscript = if (usesFabricObfLoom(mc)) "fabric.obf.gradle.kts" else "fabric.gradle.kts"
            version(projectName, mc).buildscript = buildscript
        }
    }
}
