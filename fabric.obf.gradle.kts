@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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
    alias(libs.plugins.shadow)
    id("net.fabricmc.fabric-loom-remap")
    id("me.modmuss50.mod-publish-plugin")
}

shadow {
    addShadowJarToAssembleLifecycle = false
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
    maven("https://maven.parchmentmc.org") {
        content { includeGroupAndSubgroups("org.parchmentmc") }
    }
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://central.sonatype.com/repository/maven-snapshots") {
        content { includeGroup("net.kyori") }
    }
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

val sentryRelocatedPackage = "org.polyfrost.polyplus.libs.sentry"

val sentryShade: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false // io.sentry:sentry:7.18.0 has zero runtime dependencies
}

val relocateSentry = tasks.register<ShadowJar>("relocateSentry") {
    group = "build"
    description = "Relocates io.sentry into $sentryRelocatedPackage"

    configurations = listOf(sentryShade)
    relocate("io.sentry", sentryRelocatedPackage)

    exclude(
        "META-INF/native-image/**",
        "META-INF/INDEX.LIST",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "module-info.class",
    )

    archiveBaseName = "sentry-relocated"
    archiveVersion = ""
    archiveClassifier = ""
    destinationDirectory = layout.buildDirectory.dir("relocated")

    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    from(zipTree(relocateSentry.flatMap { it.archiveFile })) {
        exclude("META-INF/MANIFEST.MF")
    }
}

val serializationRelocatedPackage = "org.polyfrost.polyplus.libs.serialization"

val serializationShade: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val shadedArchiveBaseName = property("mod.id") as String
val shadedArchiveVersion = version.toString()

val relocateSerialization = tasks.register<ShadowJar>("relocateSerialization") {
    group = "build"
    description = "Rewrites kotlinx.serialization to $serializationRelocatedPackage across the mod"

    from(tasks.jar.map { zipTree(it.archiveFile) })
    configurations = listOf(serializationShade)
    relocate("kotlinx.serialization", serializationRelocatedPackage)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    exclude(
        "META-INF/MANIFEST.MF",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/versions/**/module-info.class",
        "module-info.class",
    )

    archiveBaseName = shadedArchiveBaseName
    archiveVersion = shadedArchiveVersion
    archiveClassifier = "shaded"
    destinationDirectory = layout.buildDirectory.dir("libs")

    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    inputFile = relocateSerialization.flatMap { it.archiveFile }
}

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

    catalogLib("fabric-api")?.let { modImplementation(it) { isTransitive = true } }
    catalogLib("fabric-loader")?.let { modImplementation(it) { isTransitive = true } }

    catalogLib("sodium")?.let { modCompileOnly(it) { isTransitive = false } }

    modImplementation("org.polyfrost.oneconfig:$mcVersion-fabric:$oneconfigVersion")
    for (module in listOf("commands", "config", "config-impl", "hud", "notifications", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
    }

    sentryShade(libs.sentry)
    implementation(files(relocateSentry.flatMap { it.archiveFile }))
    serializationShade(libs.bundles.serialization.shade)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.serialization)

    testImplementation(libs.junit.jupiter)
    catalogLib("fabric-loader-junit")?.let { testImplementation(it) }
}

run {
    val bundledRoots = libs.bundles.ktor.client.get() +
        libs.bundles.ktor.server.get() +
        libs.bundles.ktor.serialization.get()
    val closure = configurations.detachedConfiguration(
        *bundledRoots.map { dependencies.create(it) }.toTypedArray()
    )

    fun key(group: String?, name: String) = "$group:${name.removeSuffix("-jvm")}"
    val relocated = serializationShade.dependencies.map { key(it.group, it.name) }.toSet()

    closure.resolvedConfiguration.resolvedArtifacts.forEach { art ->
        val id = art.moduleVersion.id
        if (id.group == "org.jetbrains.kotlin") return@forEach
        if (key(id.group, id.name) in relocated) return@forEach
        dependencies.include("${id.group}:${id.name}:${id.version}")
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
