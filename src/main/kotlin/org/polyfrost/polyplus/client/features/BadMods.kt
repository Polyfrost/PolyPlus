package org.polyfrost.polyplus.client.features

import org.polyfrost.polyplus.libs.ktor.client.call.body
import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.request.parameter
import org.polyfrost.polyplus.libs.ktor.client.request.post
import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.contentType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.BadModsScreen
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.concurrent.atomic.AtomicBoolean

object BadMods {
    private val logger = LogManager.getLogger("PolyPlus/BadMods")

    private const val DATA_URL = "https://data-v2.polyfrost.org"
    private const val LIST_URL = "$DATA_URL/oneclient/bad-mods.json"
    private const val MODRINTH_API = "https://api.modrinth.com/v2"

    @Serializable
    data class ProjectIds(val modrinth: String? = null, val curseforge: String? = null)

    @Serializable
    data class Alternative(
        val hash: String? = null,
        @SerialName("project-ids") val projectIds: ProjectIds = ProjectIds(),
    )

    @Serializable
    data class Entry(
        val hash: String? = null,
        @SerialName("project-ids") val projectIds: ProjectIds = ProjectIds(),
        val name: String? = null,
        val author: String? = null,
        val explanation: String? = null,
        val alternatives: List<Alternative> = emptyList(),
    )

    @Serializable
    data class BadModList(@SerialName("bad-mods") val badMods: List<Entry> = emptyList())

    data class InstalledMod(
        val id: String,
        val name: String,
        val authors: List<String>,
        val sha1: String,
        val modrinthProjectId: String?,
    )

    data class Detection(val entry: Entry, val mod: InstalledMod)

    data class Warning(
        val id: String,
        val name: String,
        val message: String,
        val alternative: Pair<String, String>?,
    )

    @Serializable
    private data class VersionFilesRequest(val hashes: List<String>, val algorithm: String)

    @Serializable
    private data class ModrinthVersion(val project_id: String)

    @Serializable
    private data class ModrinthProject(val id: String, val title: String, val slug: String)

    private val started = AtomicBoolean(false)

    var warnings by mutableStateOf<List<Warning>>(emptyList())
        private set

    fun dismiss() {
        warnings = emptyList()
    }

    fun ignoreShown() {
        PolyPlusConfig.ignoredBadMods = (ignored() + warnings.map { it.id }).joinToString(",")
        PolyPlusConfig.save()
        dismiss()
    }

    private fun ignored(): Set<String> = PolyPlusConfig.ignoredBadMods.split(',').filter(String::isNotEmpty).toSet()

    fun checkAsync() {
        if (!PolyPlusConfig.badModWarnings || !PrivacyConsent.allowsOnlineServices()) return
        if (!started.compareAndSet(false, true)) return
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            runSuspendCatching { check() }.onFailure { logger.warn("Failed to check for problematic mods", it) }
        }
    }

    @JvmStatic
    fun detect(list: BadModList, installed: List<InstalledMod>): List<Detection> =
        list.badMods.flatMap { entry -> installed.filter { matches(entry, it) }.map { Detection(entry, it) } }

    @JvmStatic
    fun matches(entry: Entry, mod: InstalledMod): Boolean {
        if (entry.hash != null && entry.hash.equals(mod.sha1, ignoreCase = true)) return true
        val modrinth = entry.projectIds.modrinth
        if (modrinth != null && modrinth == mod.modrinthProjectId) return true
        // CurseForge fingerprints need an API key, so entries without a hash or Modrinth ID fall back to name and author
        if (entry.hash != null || modrinth != null) return false
        val name = entry.name ?: return false
        if (!name.equals(mod.name, ignoreCase = true)) return false
        val author = entry.author ?: return true
        return mod.authors.any { it.equals(author, ignoreCase = true) }
    }

    private suspend fun check() {
        val list = PolyPlusClient.JSON.decodeFromString<BadModList>(PolyPlusClient.HTTP.get(LIST_URL).bodyAsText())
        if (list.badMods.isEmpty()) return

        val jars = ModpackDiff.modJars().map { (mod, path) -> mod to ModpackDiff.sha1(path) }
        val projects = runSuspendCatching { modrinthProjects(jars.map { it.second }) }
            .onFailure { logger.warn("Could not look up installed mods on Modrinth", it) }
            .getOrDefault(emptyMap())
        val installed = jars.map { (mod, sha1) ->
            InstalledMod(
                id = mod.metadata.id,
                name = mod.metadata.name,
                authors = mod.metadata.authors.map { it.name },
                sha1 = sha1,
                modrinthProjectId = projects[sha1],
            )
        }

        val detections = detect(list, installed).distinctBy { it.mod.id }
        detections.forEach { logger.warn("Problematic mod installed: {} ({})", it.entry.name ?: it.mod.name, it.mod.id) }
        val ignored = ignored()
        val unseen = detections.filter { it.mod.id !in ignored }
        if (unseen.isEmpty()) return

        val alternativeIds = unseen.flatMap { it.entry.alternatives }.mapNotNull { it.projectIds.modrinth }.distinct()
        val alternatives = runSuspendCatching { projectNames(alternativeIds) }
            .onFailure { logger.warn("Could not look up alternative mods on Modrinth", it) }
            .getOrDefault(emptyMap())

        warnings = unseen.map { detection ->
            val name = detection.entry.name ?: detection.mod.name
            val alternative = detection.entry.alternatives.firstNotNullOfOrNull { alt ->
                alt.projectIds.modrinth?.let { alternatives[it] }
            }
            Warning(
                id = detection.mod.id,
                name = name,
                message = buildString {
                    append(explanation(detection.entry) ?: "$name is known to cause problems with OneClient. We recommend removing it.")
                    if (alternative != null) append(" Try ").append(alternative.first).append(" instead.")
                },
                alternative = alternative,
            )
        }
        val handler: (FramebufferRenderEvent.End) -> Boolean = { showWhenReady() }
        eventHandler(handler)
    }

    private fun showWhenReady(): Boolean {
        if (warnings.isEmpty()) return true
        return when (val screen = ClientPlatform.currentScreen()) {
            is PolyPlusMainMenuScreen -> true
            is TitleScreen -> {
                if (!PolyPlusMainMenuConfig.useVanillaMainMenu) return false
                //? if > 1.8.9 {
                Minecraft.getInstance().execute { ClientPlatform.setScreen(BadModsScreen(screen)) }
                //?} else {
                /*Minecraft.getInstance().tell { ClientPlatform.setScreen(BadModsScreen(screen)) }
                *///?}
                true
            }
            else -> false
        }
    }

    private suspend fun modrinthProjects(hashes: List<String>): Map<String, String> {
        if (hashes.isEmpty()) return emptyMap()
        return PolyPlusClient.HTTP.post("$MODRINTH_API/version_files") {
            contentType(ContentType.Application.Json)
            setBody(VersionFilesRequest(hashes, "sha1"))
        }.body<Map<String, ModrinthVersion>>().mapValues { it.value.project_id }
    }

    private suspend fun projectNames(ids: List<String>): Map<String, Pair<String, String>> {
        if (ids.isEmpty()) return emptyMap()
        return PolyPlusClient.HTTP.get("$MODRINTH_API/projects") {
            parameter("ids", PolyPlusClient.JSON.encodeToString(ids))
        }.body<List<ModrinthProject>>().associate { it.id to (it.title to it.slug) }
    }

    private suspend fun explanation(entry: Entry): String? {
        val path = entry.explanation ?: return null
        return runSuspendCatching { PolyPlusClient.HTTP.get(DATA_URL + path).bodyAsText() }
            .onFailure { logger.warn("Could not fetch the explanation for {}", entry.name, it) }
            .getOrNull()
            ?.let(::plainText)
            ?.ifBlank { null }
    }

    @JvmStatic
    fun plainText(markdown: String): String = markdown.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .joinToString(" ")
        .replace(Regex("""\[([^]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""[*`]"""), "")
}
