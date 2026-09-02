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
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    id("com.gradleup.shadow")
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

fun optionalProperty(name: String): String? =
    findProperty(name)?.toString()?.takeIf { it.isNotBlank() }

val fabricLoaderVersion = property("deps.fabric_loader") as String
val fabricLanguageKotlinVersion = property("deps.fabric_language_kotlin") as String
val kotlinVersion = property("deps.kotlin") as String
val ktorVersion = property("deps.ktor") as String
val sentryVersion = property("deps.sentry") as String
val mixinExtrasVersion = property("deps.mixin_extras") as String
val mixinSquaredVersion = property("deps.mixin_squared") as String
val devauthVersion = property("deps.devauth") as String
val junitVersion = property("deps.junit") as String

val ktorModules = listOf(
    "io.ktor:ktor-client-core",
    "io.ktor:ktor-client-cio",
    "io.ktor:ktor-client-content-negotiation",
    "io.ktor:ktor-server-websockets",
    "io.ktor:ktor-serialization-kotlinx-json"
).map { "$it:$ktorVersion" }

val flkProvidedModules = setOf(
    "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm",
    "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm",
    "org.jetbrains.kotlinx:kotlinx-serialization-cbor-jvm",
    "org.jetbrains.kotlinx:kotlinx-io-core-jvm",
    "org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm",
)

group = property("mod.group") as String
version = "${property("mod.version")}+${stonecutter.current.version}"
base.archivesName = property("mod.id") as String
val oneconfigVersion = property("deps.oneconfig") as String

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }

    mavenLocal()
    mavenCentral()
    google()
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
    maven("https://maven.cloverclient.com/releases")
    maven("https://jitpack.io") {
        content { includeGroupAndSubgroups("com.github") }
    }
    maven("https://maven.fabricmc.net/") {
        content { includeGroupAndSubgroups("net.fabricmc") }
    }
    maven("https://central.sonatype.com/repository/maven-snapshots") {
        content { includeGroup("net.kyori") }
    }
    strictMaven("https://maven.bawnorton.com/releases", "Bawnorton", "com.github.bawnorton.mixinsquared")
    strictMaven("https://maven.terraformersmc.com/releases/", "TerraformersMC", "com.terraformersmc")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://maven.maxhenkel.de/repository/public", "MaxHenkel", "de.maxhenkel.voicechat")
}

val flkProvidedVersions: Map<String, String> = run {
    configurations.detachedConfiguration(
        dependencies.create("net.fabricmc:fabric-language-kotlin:$fabricLanguageKotlinVersion"),
    ).resolvedConfiguration.resolvedArtifacts
        .map { it.moduleVersion.id }
        .filter { "${it.group}:${it.name}" in flkProvidedModules }
        .associate { "${it.group}:${it.name}" to it.version }
        .also { resolved ->
            val missing = flkProvidedModules - resolved.keys
            check(missing.isEmpty()) {
                "fabric-language-kotlin $fabricLanguageKotlinVersion no longer ships $missing; " +
                    "drop them from flkProvidedModules so they get bundled again"
            }
        }
}

configurations.all {
    resolutionStrategy.eachDependency {
        flkProvidedVersions["${requested.group}:${requested.name}"]?.let { useVersion(it) }
    }
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
    isTransitive = false // io.sentry:sentry has zero runtime dependencies
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

    dependencies.extensions.getByType<LoomCompatDependencyExtension>().applyMojangMappings()

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")
    annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-common:$mixinSquaredVersion")

    modLocalRuntime("me.djtheredstoner:DevAuth-fabric:$devauthVersion")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    // This is a library, not a traditional mod. It must not use modRuntimeOnly,
    // or it does not get properly loaded into the test environment on 1.21.x.
    runtimeOnly("net.fabricmc:fabric-language-kotlin:$fabricLanguageKotlinVersion")

    optionalProperty("deps.sodium")?.let {
        modCompileOnly("maven.modrinth:sodium:$it") { isTransitive = false }
    }

    modCompileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.20") { isTransitive = false }

    compileOnly("maven.modrinth:debugify:26.2.0.0") { isTransitive = false }

    compileOnly("com.nikoverflow:exploit-preventer-api:1.0.0")

    modImplementation("org.polyfrost.oneconfig:$mcVersion-fabric:$oneconfigVersion")
    for (module in listOf("commands", "config", "config-impl", "hud", "notifications", "poly-compose", "utils", "internal", "ui", "events")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
    }

    sentryShade("io.sentry:sentry:$sentryVersion")
    implementation(files(relocateSentry.flatMap { it.archiveFile }))
    for (module in ktorModules) implementation(module)

    implementation(include("gg.sona:eos:2.0.2")!!)

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("net.fabricmc:fabric-loader-junit:$fabricLoaderVersion")
}

run {
    val closure = configurations.detachedConfiguration(
        *ktorModules.map { dependencies.create(it) }.toTypedArray()
    )
    closure.resolvedConfiguration.resolvedArtifacts.forEach { art ->
        val id = art.moduleVersion.id
        if (id.group == "org.jetbrains.kotlin") return@forEach
        if ("${id.group}:${id.name}" in flkProvidedModules) return@forEach
        dependencies.include("${id.group}:${id.name}:${id.version}")
    }
}

val modId = property("mod.id") as String
loomExt.mixin {
    defaultRefmapName.set("mixins.$modId.refmap.json")
}

loomExt.decompilerOptions.named("vineflower") {
    options.put("mark-corresponding-synthetics", "1") // Adds names to lambdas - useful for mixins
}

val modOutputGroup = sourceSets.main.get().output
    .let { files(it.classesDirs, it.resourcesDir) }
    .joinToString(File.pathSeparator) { it.absolutePath }

loomExt.runs.configureEach {
    ideConfigGenerated(true)
    runDir("../../run") // Shares the run directory between versions
    vmArg("-Dpolyplus.badge.debug=true")
    vmArg("-Dfabric.classPathGroups=$modOutputGroup")
}
loomExt.runs.named("client") {
    client()
}

tasks.test {
    useJUnitPlatform()
    systemProperty("fabric.classPathGroups", modOutputGroup)
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
