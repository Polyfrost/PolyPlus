@file:Suppress("UnstableApiUsage")

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.atomicfu)
    id("net.fabricmc.fabric-loom")
    id("me.modmuss50.mod-publish-plugin")
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
    mavenLocal()
    mavenCentral()
    google()
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
    maven("https://maven.fabricmc.net/") {
        content { includeGroupAndSubgroups("net.fabricmc") }
    }
    maven("https://maven.terraformersmc.com/releases/") {
        content { includeGroup("com.terraformersmc") }
    }
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://central.sonatype.com/repository/maven-snapshots") {
        content { includeGroup("net.kyori") }
    }
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

    catalogLib("sodium")?.let { compileOnly(it) { isTransitive = false } }

    implementation("org.polyfrost.oneconfig:$mcVersion-fabric:$oneconfigVersion")
    for (module in listOf("commands", "config", "config-impl", "hud", "notifications", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
    }

    implementation(libs.sentry)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.serialization)

    testImplementation(libs.junit.jupiter)
    catalogLib("fabric-loader-junit")?.let { testImplementation(it) }
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
    // Dev: force the PolyPlus user badge onto every player's nametag + tab entry
    // so the render path can be tested solo without live backend presence.
    vmArg("-Dpolyplus.badge.debug=true")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.withType<ProcessResources>().configureEach {
    val modName = project.property("mod.name") as String
    val modVersion = project.property("mod.version") as String
    inputs.property("modId", modId)
    inputs.property("modName", modName)
    inputs.property("modVersion", modVersion)
    inputs.property("minorMcVersion", minecraftPredicate)
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "mod_id" to modId,
                "mod_name" to modName,
                "mod_version" to modVersion,
                "mod_description" to "Various expansions for the OneClient 'modpack'",
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

val modVersion = property("mod.version") as String
val modrinthMinecraftVersionOverride = mapOf(
    "26.1" to listOf("26.1", "26.1.1", "26.1.2")
)
val modrinthId = listOf("oneconfig.publish.modrinth", "publish.modrinth")
    .firstNotNullOfOrNull { findProperty(it) }?.toString()?.takeIf { it.isNotBlank() }
val modrinthToken = listOf("oneconfig.publish.modrinth.token", "publish.modrinth.token", "modrinth.token")
    .firstNotNullOfOrNull { findProperty(it) }?.toString()?.takeIf { it.isNotBlank() }
val minecraftVersion = modrinthMinecraftVersionOverride[mcVersion] ?: listOf(mcVersion)
val publishJarTaskName = if ("remapJar" in tasks.names) "remapJar" else "jar"
val changelogs = rootProject.file("CHANGELOG.md").takeIf { it.exists() }?.readText() ?: "No changelog provided."

publishMods {
    file = tasks.named<AbstractArchiveTask>(publishJarTaskName).flatMap { it.archiveFile }

    displayName = modVersion
    version = "v$modVersion"
    changelog = changelogs
    type = STABLE

    modLoaders.add("fabric")

    dryRun = modrinthId == null || modrinthToken == null

    if (modrinthId != null) {
        modrinth {
            projectId = modrinthId
            accessToken = modrinthToken.orEmpty()

            minecraftVersions.addAll(minecraftVersion)

            requires("oneconfig")
            requires("fabric-language-kotlin")
        }
    }
}
