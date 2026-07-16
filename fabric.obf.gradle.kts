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
    id("net.fabricmc.fabric-loom-remap")
}

val stonecutter = extensions.getByName("stonecutter") as StonecutterBuildExtension
val mcVersion = stonecutter.current.version
val catalogVersion = mcVersion.replace(".", "")

run {
    val (version, loader) = stonecutter.current.project.split("-", limit = 2)
    stonecutter.properties.tags(version, loader)
}

val minecraftPredicate = property("mod.mc_compat") as String

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
version = "${property("mod.version")}+${stonecutter.current.version}"
base.archivesName = property("mod.id") as String
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
    maven("https://maven.parchmentmc.org") {
        content { includeGroupAndSubgroups("org.parchmentmc") }
    }
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    maven("https://nexus.prsm.wtf/repository/maven-public/maven-repo/releases/")
    google()
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configure<KotlinJvmExtension> {
    jvmToolchain(21)
}

val loomExt = extensions.getByName<LoomGradleExtensionAPI>("loom")

dependencies {
    minecraft("com.mojang:minecraft:${versionCatalog("common$catalogVersion").findVersion("minecraft").get()}")

    mappings(loomExt.layered {
        officialMojangMappings()
        catalogLib("parchment")?.let { parchmentDep ->
            parchment(variantOf(parchmentDep) { artifactType("zip") })
        }
    })

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    annotationProcessor(libs.mixin.extras)
    annotationProcessor(libs.mixin.squared)

    modLocalRuntime(libs.devauth.fabric)

    catalogBundle("fabric-api")?.let { modImplementation(it) { isTransitive = true } }
    catalogLib("fabric-loader")?.let { modImplementation(it) { isTransitive = true } }

    modImplementation("org.polyfrost.oneconfig:$mcVersion-fabric:$oneconfigVersion")
    for (module in listOf("commands", "compose-bundle", "config", "config-impl", "hud", "notifications", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
    }

    implementation(libs.discord.game.sdk4j)
    implementation(libs.sentry)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.serialization)

    include(libs.discord.game.sdk4j)
}

run {
    val bundledRoots = libs.bundles.ktor.client.get() +
        libs.bundles.ktor.server.get() +
        libs.bundles.ktor.serialization.get() +
        libs.sentry.get()
    val closure = configurations.detachedConfiguration(
        *bundledRoots.map { dependencies.create(it) }.toTypedArray()
    )
    closure.resolvedConfiguration.resolvedArtifacts.forEach { art ->
        val id = art.moduleVersion.id
        if (id.group != "org.jetbrains.kotlin") {
            dependencies.include("${id.group}:${id.name}:${id.version}")
        }
    }
}

val modId = property("mod.id") as String
loomExt.mixin {
    defaultRefmapName.set("mixins.$modId.refmap.json")
}
loomExt.runs.named("client") {
    ideConfigGenerated(true)
    client()
    runDir("../../run")
}

// Every version node declares its own `runClient`/`runServer` + `downloadAssets`, all
// pointing at the shared `run/` dir. A bare `./gradlew runClient` matches the run task
// in every version, dragging in each version's `downloadAssets` — which all write to the
// same directory the active client reads, a hard "implicit dependency" validation
// failure on Gradle 9 (and a needless download of every MC version's assets). Only the
// Stonecutter-active version can actually be launched, so disable these tasks on the
// rest; the bare command then resolves to just the active node.
if (!stonecutter.current.isActive) {
    tasks.matching {
        it.name == "runClient" || it.name == "runServer" || it.name == "downloadAssets"
    }.configureEach {
        enabled = false
    }
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
                "minor_mc_version" to minecraftPredicate,
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
