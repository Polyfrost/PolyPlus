package org.polyfrost.polyplus.client.features

import org.polyfrost.polyplus.libs.ktor.client.call.body
import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.metadata.ModOrigin
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import org.polyfrost.polyplus.client.utils.runSuspendCatching

object ModpackDiff {
    private val logger = LogManager.getLogger("PolyPlus/ModpackDiff")

    // Modrinth slug to display name - on a tie, earlier packs win
    private val PACKS = listOf(
        "oneclient-modpack" to "OneClient modpack",
        "oneclient-skyblock" to "OneClient SkyBlock modpack",
    )

    private const val MAX_VERSIONS_CHECKED = 10

    @Serializable
    private data class PackVersion(val version_number: String, val dependencies: List<PackDependency>)

    @Serializable
    private data class PackDependency(val version_id: String? = null)

    @Serializable
    private data class ModVersion(val id: String, val files: List<ModFile>)

    @Serializable
    private data class ModFile(val filename: String, val hashes: Map<String, String>, val primary: Boolean = false)

    data class Diff(val removed: List<String>, val added: List<String>) {
        val size: Int get() = removed.size + added.size
    }

    private data class Match(val pack: String, val version: String, val diff: Diff)

    fun logAsync() {
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            runSuspendCatching { log() }.onFailure { logger.warn("Failed to compare mods against the OneClient modpack", it) }
        }
    }

    fun diff(pack: Map<String, String>, loaded: Map<String, String>): Diff = Diff(
        removed = pack.filterKeys { it !in loaded }.values.sorted(),
        added = loaded.filterKeys { it !in pack }.values.sorted(),
    )

    private suspend fun log() {
        val loader = FabricLoader.getInstance()
        val loaded = modJars()
            .associate { (mod, path) -> sha1(path) to "${mod.metadata.id} ${mod.metadata.version.friendlyString} (${path.fileName})" }

        val mcVersion = loader.getModContainer("minecraft").get().metadata.version.friendlyString
        var best: Match? = null
        for ((slug, name) in PACKS) {
            val versions = PolyPlusClient.HTTP.get("https://api.modrinth.com/v2/project/$slug/version") {
                parameter("game_versions", "[\"$mcVersion\"]")
                parameter("loaders", "[\"fabric\"]")
                parameter("include_changelog", "false")
            }.body<List<PackVersion>>().take(MAX_VERSIONS_CHECKED)
            val jars = fetchModJars(versions.flatMap { it.dependencies }.mapNotNull { it.version_id }.distinct())

            for (version in versions) {
                val diff = diff(version.dependencies.mapNotNull { dep -> dep.version_id?.let(jars::get) }.toMap(), loaded)
                if (best == null || diff.size < best.diff.size) best = Match(name, version.version_number, diff)
                if (diff.removed.isEmpty()) break
            }
        }

        if (best == null) {
            logger.info("No OneClient modpack found for Minecraft {}", mcVersion)
            return
        }
        val (pack, version, diff) = best
        logger.info(buildString {
            append(pack).append(' ').append(version).append(" (Minecraft ").append(mcVersion).append(")")
            if (diff.size == 0) append(": exact match")
            append("\nRemoved mods (").append(diff.removed.size).append("):")
            diff.removed.forEach { append("\n\t").append(it) }
            append("\nExternal mods (").append(diff.added.size).append("):")
            diff.added.forEach { append("\n\t").append(it) }
        })
    }

    fun modJars(): List<Pair<ModContainer, Path>> {
        val loader = FabricLoader.getInstance()
        val modsDir = (System.getProperty("fabric.modsFolder")?.let(Path::of) ?: loader.gameDir.resolve("mods"))
            .toAbsolutePath().normalize()
        return loader.allMods
            .filter { it.containingMod.isEmpty && it.origin.kind == ModOrigin.Kind.PATH }
            .flatMap { mod -> mod.origin.paths.map { mod to it.toAbsolutePath().normalize() } }
            .filter { (_, path) -> path.startsWith(modsDir) && Files.isRegularFile(path) }
    }

    // Never download the .mrpack itself: Modrinth counts every CDN fetch as a download of the pack
    private suspend fun fetchModJars(versionIds: List<String>): Map<String, Pair<String, String>> {
        if (versionIds.isEmpty()) return emptyMap()
        return PolyPlusClient.HTTP.get("https://api.modrinth.com/v2/versions") {
            parameter("ids", PolyPlusClient.JSON.encodeToString(versionIds))
        }.body<List<ModVersion>>().mapNotNull { version ->
            val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull() ?: return@mapNotNull null
            // Packs also embed shaders and resource packs
            if (!file.filename.endsWith(".jar")) return@mapNotNull null
            file.hashes["sha1"]?.let { version.id to (it to file.filename) }
        }.toMap()
    }

    fun sha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        DigestInputStream(Files.newInputStream(path), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        return HexFormat.of().formatHex(digest.digest())
    }
}
