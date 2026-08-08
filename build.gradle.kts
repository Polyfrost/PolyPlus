@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.kikugie.loomx.LoomCompatDependencyExtension
import dev.kikugie.loomx.LoomCompatProjectExtension
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
    id("dev.kikugie.loom-back-compat")
    id("me.modmuss50.mod-publish-plugin")
}

shadow {
    addShadowJarToAssembleLifecycle = false
}

val stonecutter = extensions.getByName("stonecutter") as StonecutterBuildExtension
val loomx = extensions.getByType<LoomCompatProjectExtension>()
val mcVersion = stonecutter.current.version

run {
    val (version, loader) = stonecutter.current.project.split("-", limit = 2)
    stonecutter.properties.tags(version, loader)
}

val minecraftPredicate = property("mod.mc_compat") as String

/** Reads a `stonecutter.properties.toml` entry that only some versions declare. */
fun optionalProperty(name: String): String? =
    findProperty(name)?.toString()?.takeIf { it.isNotBlank() }

val fabricLoaderVersion = property("deps.fabric_loader") as String

group = property("mod.group") as String
version = "${property("mod.version")}+${stonecutter.current.version}"
base.archivesName = property("mod.id") as String
val oneconfigVersion = property("deps.oneconfig") as String

repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }

    mavenLocal()
    mavenCentral()
    google()
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
    maven("https://maven.fabricmc.net/") {
        content { includeGroupAndSubgroups("net.fabricmc") }
    }
    maven("https://maven.parchmentmc.org") {
        content { includeGroupAndSubgroups("org.parchmentmc") }
    }
    maven("https://central.sonatype.com/repository/maven-snapshots") {
        content { includeGroup("net.kyori") }
    }
    strictMaven("https://maven.terraformersmc.com/releases/", "TerraformersMC", "com.terraformersmc")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
}

val javaVersion = when {
    stonecutter.current.parsed >= "26.1" -> 25
    else -> 21
}

configure<KotlinJvmExtension> {
    jvmToolchain(javaVersion)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
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

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")

    // Layered mappings are rejected outside an obfuscated environment, and Parchment is only
    // published for versions that have one, so its presence selects the mapping strategy.
    // `applyMojangMappings` is a no-op on 26+.
    optionalProperty("deps.parchment")?.also { parchmentDep ->
        mappings(loomExt.layered {
            officialMojangMappings()
            parchment("$parchmentDep@zip")
        })
    } ?: dependencies.extensions.getByType<LoomCompatDependencyExtension>().applyMojangMappings()

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    annotationProcessor(libs.mixin.extras)
    annotationProcessor(libs.mixin.squared)

    modLocalRuntime(libs.devauth.fabric)

    // Use `mod{dependency type}` even on 26+ - loom-back-compat aliases them.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    optionalProperty("deps.sodium")?.let {
        modCompileOnly("maven.modrinth:sodium:$it") { isTransitive = false }
    }

    modImplementation("org.polyfrost.oneconfig:$mcVersion-fabric:$oneconfigVersion")
    for (module in listOf("commands", "config", "config-impl", "hud", "notifications", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
    }

    sentryShade(libs.sentry)
    implementation(files(relocateSentry.flatMap { it.archiveFile }))
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.serialization)

    testImplementation(libs.junit.jupiter)
    testImplementation("net.fabricmc:fabric-loader-junit:$fabricLoaderVersion")
}

run {
    val bundledRoots = libs.bundles.ktor.client.get() +
        libs.bundles.ktor.server.get() +
        libs.bundles.ktor.serialization.get()
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

loomExt.decompilerOptions.named("vineflower") {
    options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
}

loomExt.runs.configureEach {
    ideConfigGenerated(true)
    runDir("../../run") // Shares the run directory between versions
    vmArg("-Dmixin.debug.export=true") // Exports transformed classes for debugging
    vmArg("-Dpolyplus.badge.debug=true")
}
loomExt.runs.named("client") {
    client()
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

// `loomx.mod(Sources)Jar` returns the jar task for the applied loom variant
// (`remapJar` when obfuscated, plain `jar` on 26+).
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

    inputs.property("version", project.property("mod.version"))
    from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
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
val changelogs = rootProject.file("CHANGELOG.md").takeIf { it.exists() }?.readText() ?: "No changelog provided."

publishMods {
    file = loomx.modJar.flatMap { it.archiveFile }

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
