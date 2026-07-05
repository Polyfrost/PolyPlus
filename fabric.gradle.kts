@file:Suppress("UnstableApiUsage")

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.atomicfu)
    id("net.fabricmc.fabric-loom")
}

val stonecutter = extensions.getByName("stonecutter") as StonecutterBuildExtension
val mcVersion = stonecutter.current.version
val catalogVersion = mcVersion.replace(".", "")

fun versionCatalog(name: String) =
    extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named(name)

val versionedCatalogs = run {
    val catalogs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
    listOf(
        catalogs.named("fabric$catalogVersion"),
        catalogs.named("common$catalogVersion"),
        catalogs.named("fabric"),
        catalogs.named("libs"),
    )
}

fun catalogLib(name: String) =
    versionedCatalogs.firstNotNullOfOrNull { cat -> cat.findLibrary(name).orElse(null) }

fun catalogBundle(name: String) =
    versionedCatalogs.firstNotNullOfOrNull { cat -> cat.findBundle(name).orElse(null) }

group = property("mod.group") as String
version = property("mod.version") as String
val oneconfigVersion = property("oneconfig_version") as String

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
    maven("https://jitpack.io") {
        content { includeGroupAndSubgroups("com.github") }
    }
    maven("https://maven.terraformersmc.com/") {
        content { includeGroup("com.terraformersmc") }
    }
    maven("https://maven.bawnorton.com/releases") {
        content { includeGroup("com.github.bawnorton.mixinsquared") }
    }
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    maven("https://nexus.prsm.wtf/repository/maven-public/maven-repo/releases/")
    maven("https://central.sonatype.com/repository/maven-snapshots") {
        content { includeGroup("net.kyori") }
    }
    google()
}

val javaVersion = if (mcVersion.substringBefore('.').toIntOrNull()?.let { it >= 26 } == true) 25 else 21

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

configure<KotlinJvmExtension> {
    jvmToolchain(javaVersion)
}

val loomExt = extensions.getByName<LoomGradleExtensionAPI>("loom")

dependencies {
    minecraft("com.mojang:minecraft:${versionCatalog("common$catalogVersion").findVersion("minecraft").get()}")

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    annotationProcessor(libs.mixin.extras)
    annotationProcessor(libs.mixin.squared)

    runtimeOnly(libs.devauth.fabric)

    catalogBundle("fabric-api")?.let { implementation(it) { isTransitive = true } }
    catalogLib("fabric-loader")?.let { implementation(it) { isTransitive = true } }

    implementation("org.polyfrost.oneconfig:$mcVersion-fabric:$oneconfigVersion")
    for (module in listOf("commands", "compose-bundle", "config", "config-impl", "hud", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
    }

    implementation(libs.discord.game.sdk4j)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.serialization)

    include(libs.discord.game.sdk4j)
    libs.bundles.ktor.client.get().forEach { include(it) }
    libs.bundles.ktor.server.get().forEach { include(it) }
    libs.bundles.ktor.serialization.get().forEach { include(it) }
}

val modId = property("mod.id") as String
loomExt.mixin {
    defaultRefmapName.set("mixins.$modId.refmap.json")
}
loomExt.runs.named("client") {
    ideConfigGenerated(true)
    client()
    runDir("../../run")
    // Dev: force the PolyPlus user badge onto every player's nametag + tab entry
    // so the render path can be tested solo without live backend presence.
    vmArg("-Dpolyplus.badge.debug=true")
}

tasks.withType<ProcessResources>().configureEach {
    val modName = project.property("mod.name") as String
    val modVersion = project.property("mod.version") as String
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "mod_id" to modId,
                "mod_name" to modName,
                "mod_version" to modVersion,
                "mod_description" to "PolyPlus cosmetics for OneConfig",
                "minor_mc_version" to mcVersion,
            ),
        )
    }
    filesMatching("mixins.*.json") {
        expand("id" to modId)
    }
}

tasks.matching { it.name == "createMinecraftArtifacts" }.configureEach {
    dependsOn("stonecutterGenerate")
}
