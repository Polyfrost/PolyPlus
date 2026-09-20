package org.polyfrost.polyplus.client.features

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipInputStream

object ModpackDiff {
    private val logger = LogManager.getLogger("PolyPlus/ModpackDiff")

    private const val VERSIONS_URL = "https://api.modrinth.com/v2/project/oneclient-modpack/version"

    private const val MAX_VERSIONS_CHECKED = 10

    @Serializable
    private data class PackVersion(val version_number: String, val files: List<PackFile>)

    @Serializable
    private data class PackFile(val url: String, val primary: Boolean = false)

    @Serializable
    private data class PackIndex(val files: List<IndexFile>)

    @Serializable
    private data class IndexFile(val path: String, val hashes: Map<String, String>, val env: Map<String, String>? = null)

    data class Diff(val removed: List<String>, val added: List<String>)

    fun logAsync() {
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            runCatching { log() }.onFailure { logger.warn("Failed to compare mods against the OneClient modpack", it) }
        }
    }

    fun diff(pack: Map<String, String>, loaded: Map<String, String>): Diff = Diff(
        removed = pack.filterKeys { it !in loaded }.values.sorted(),
        added = loaded.filterKeys { it !in pack }.values.sorted(),
    )

    private suspend fun log() {
        val loader = FabricLoader.getInstance()
        val modsDir = loader.gameDir.resolve("mods").toAbsolutePath().normalize()
        val loaded = loader.allMods
            .filter { it.containingMod.isEmpty && it.origin.kind == ModOrigin.Kind.PATH }
            .flatMap { mod -> mod.origin.paths.map { mod to it.toAbsolutePath().normalize() } }
            .filter { (_, path) -> path.startsWith(modsDir) && Files.isRegularFile(path) }
            .associate { (mod, path) -> sha1(path) to "${mod.metadata.id} ${mod.metadata.version.friendlyString} (${path.fileName})" }

        val mcVersion = loader.getModContainer("minecraft").get().metadata.version.friendlyString
        val versions = PolyPlusClient.HTTP.get(VERSIONS_URL) {
            parameter("game_versions", "[\"$mcVersion\"]")
            parameter("loaders", "[\"fabric\"]")
            parameter("include_changelog", "false")
        }.body<List<PackVersion>>()

        var best: Pair<String, Diff>? = null
        for (version in versions.take(MAX_VERSIONS_CHECKED)) {
            val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull() ?: continue
            val pack = fetchPackMods(file.url)
            val diff = diff(pack, loaded)
            if (best == null || diff.removed.size + diff.added.size < best.second.removed.size + best.second.added.size) {
                best = version.version_number to diff
            }
            if (diff.removed.isEmpty()) break
        }

        if (best == null) {
            logger.info("No OneClient modpack found for Minecraft {}", mcVersion)
            return
        }
        val (version, diff) = best
        logger.info(buildString {
            append("OneClient modpack ").append(version).append(" (Minecraft ").append(mcVersion).append(")")
            if (diff.removed.isEmpty() && diff.added.isEmpty()) append(": exact match")
            append("\nRemoved mods (").append(diff.removed.size).append("):")
            diff.removed.forEach { append("\n\t").append(it) }
            append("\nExternal mods (").append(diff.added.size).append("):")
            diff.added.forEach { append("\n\t").append(it) }
        })
    }

    private suspend fun fetchPackMods(url: String): Map<String, String> {
        val bytes = PolyPlusClient.HTTP.get(url).body<ByteArray>()
        return ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "modrinth.index.json" } ?: return emptyMap()
            PolyPlusClient.JSON.decodeFromString<PackIndex>(zip.readBytes().decodeToString()).files
                .filter { it.path.startsWith("mods/") && it.env?.get("client") != "unsupported" }
                .mapNotNull { file -> file.hashes["sha1"]?.let { it to file.path.removePrefix("mods/") } }
                .toMap()
        }
    }

    private fun sha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        DigestInputStream(Files.newInputStream(path), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        return HexFormat.of().formatHex(digest.digest())
    }
}
