package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.privacy.LauncherEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/** Discovers compatible client profiles and imports one when the user opts in during onboarding. */
object ClientConfigSync {
    private val logger = LogManager.getLogger("PolyPlus/ClientConfigSync")
    private const val MARKER = ".polyplus-client-config-sync-v5"
    private val previousMarkers = (1..4).map { ".polyplus-client-config-sync-v$it" }

    data class ImportSource(
        val id: String,
        val client: String,
        val profile: String,
        val preview: List<String>,
        val onboardingPreferences: Map<OnboardingPreference, Boolean>,
        internal val paths: List<Path>,
    )

    enum class OnboardingPreference {
        SKIN_LAYERS,
    }

    /** Previous builds wrote nested HUD files but failed to rebind them into OneConfig's live trees. */
    fun needsImportRecovery(): Boolean {
        val gameDir = FabricLoader.getInstance().gameDir
        if (gameDir.resolve(MARKER).exists()) return false
        return previousMarkers.any { markerName ->
            val marker = gameDir.resolve(markerName)
            if (!marker.isRegularFile()) return@any false
            val translatedCount = Regex("(\\d+) translated HUD layout\\(s\\)")
                .find(runCatching { Files.readString(marker) }.getOrDefault(""))
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            translatedCount > 0
        }
    }

    fun dismissImportRecovery() {
        if (!needsImportRecovery()) return
        val gameDir = FabricLoader.getInstance().gameDir
        Files.createDirectories(gameDir)
        Files.writeString(gameDir.resolve(MARKER), "Import recovery skipped by the user.\n")
        logger.info("Marked the previous client config import as dismissed")
    }

    private data class LauncherRoot(
        val name: String,
        val path: Path,
        val sharedGameDirectory: Boolean = false,
    )

    // These entries describe session or installation state, rather than reusable preferences.
    private val excludedOptions = setOf(
        "lastServer",
        "resourcePacks",
        "incompatibleResourcePacks",
        "soundDevice",
        "joinedFirstServer",
        "realmsNotifications",
        "onboarded",
        "tutorialStep",
        "lastLaunchedVersion",
    )
    private val optionFileNames = setOf("options.txt", "optionsLC.txt")
    private val oneConfigHudProfileMods = setOf("evergreenhud", "vanillahud", "vanilla-hud", "chatting")
    private val sharedModConfigs = mapOf(
        "entityculling" to "entityculling.json",
        "immediatelyfast" to "immediatelyfast.json",
        "iris" to "iris.properties",
        "lambdynlights" to "lambdynlights.toml",
        "moreculling" to "moreculling.toml",
        "skinlayers" to "skinlayers.json",
        "sodium" to "sodium-options.json",
        "sodium-extra" to "sodium-extra-options.json",
        "waveycapes" to "waveycapes.json",
        "zoomify" to "zoomify.json",
    )
    /** Returns compatible profiles separately so onboarding can preview and select one. */
    fun availableSources(): List<ImportSource> {
        val gameDir = FabricLoader.getInstance().gameDir
        if (gameDir.resolve(MARKER).exists()) {
            logger.info("Client config discovery skipped; import marker exists at {}", gameDir.resolve(MARKER))
            return emptyList()
        }
        val roots = launcherRoots()
        val grouped = findSources().filter { !it.startsWith(gameDir) && it.isRegularFile() }
            .distinct()
            .groupBy { path ->
                val root = roots.lastOrNull { path.startsWith(it.path) }
                if (root == null) "Other" to (path.parent?.fileName?.toString() ?: "Default")
                else {
                    val relative = root.path.relativize(path)
                    val relativeName = relative.fileName?.toString().orEmpty()
                    val relativeTopLevel = relative.getName(0).toString()
                    val profile = if (
                        root.sharedGameDirectory && relative.nameCount >= 5 && relativeTopLevel == "OneConfig" &&
                        relative.getName(1).toString() == "profiles"
                    ) {
                        relative.getName(2).toString().removeSuffix(" Profile")
                    } else if (relative.nameCount == 1 && relativeName.endsWith(".json")) {
                        relative.fileName.toString().substringBeforeLast('.')
                    } else if (relative.nameCount == 1 && relativeName in (sharedModConfigs.values + optionFileNames)) {
                        "Default"
                    } else if (relative.nameCount > 1 && relativeTopLevel in setOf("config", "huds")) {
                        "Default"
                    } else if (relative.nameCount > 0) {
                        relative.getName(0).toString()
                    } else "Default"
                    root.name to profile
                }
            }

        val results = mergeDawnSyncGroups(grouped).map { (key, paths) ->
            val (client, profile) = key
            val orderedPaths = paths.sortedBy { it.toString() }
            val shortProfile = Regex("(?i)^([0-9a-f]{8})-[0-9a-f-]{27}$")
                .matchEntire(profile)?.groupValues?.get(1) ?: profile
            ImportSource(
                id = orderedPaths.joinToString("|") { it.toAbsolutePath().normalize().toString() },
                client = client,
                profile = shortProfile,
                preview = preview(orderedPaths),
                onboardingPreferences = readOnboardingPreferences(orderedPaths),
                paths = orderedPaths,
            )
        }.sortedWith(compareBy({ it.client.lowercase() }, { it.profile.lowercase() }))
        logger.info(
            "Discovered {} importable client profile(s): {}",
            results.size,
            results.joinToString { "${it.client}/${it.profile} (${it.paths.size} files)" }.ifEmpty { "none" },
        )
        return results
    }

    /**
     * Dawn profiles play inside a shared synced folder (`.dawn/auto-sync/groups/<group>`), which holds the real
     * options and Feather HUD layouts; the profile folder itself only has a stub `options.txt`. Fold each group's
     * files into its member profiles so picking a profile imports everything, and drop the bare group entry.
     */
    private fun mergeDawnSyncGroups(grouped: Map<Pair<String, String>, List<Path>>): Map<Pair<String, String>, List<Path>> {
        val groupsFile = System.getenv("APPDATA")?.let { Path.of(it, ".dawn", "auto-sync", "groups.json") }
            ?.takeIf { it.isRegularFile() } ?: return grouped
        val memberships = runCatching {
            Json.parseToJsonElement(Files.readString(groupsFile)).jsonObject["groups"]?.jsonArray.orEmpty().mapNotNull { group ->
                val groupObject = group.jsonObject
                val id = groupObject["groupId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                id to groupObject["members"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            }
        }.onFailure { logger.debug("Could not read Dawn sync groups from {}", groupsFile, it) }.getOrNull() ?: return grouped

        val result = grouped.toMutableMap()
        memberships.forEach { (groupId, members) ->
            val groupPaths = grouped["Dawn" to groupId] ?: return@forEach
            val merged = members.filter { ("Dawn" to it) in grouped }
            merged.forEach { member -> result["Dawn" to member] = grouped.getValue("Dawn" to member) + groupPaths }
            if (merged.isNotEmpty()) {
                result.remove("Dawn" to groupId)
                logger.info("Merged Dawn sync group '{}' into profile(s) {}", groupId, merged.joinToString())
            }
        }
        return result
    }

    fun apply(source: ImportSource) {
        val gameDir = FabricLoader.getInstance().gameDir
        val marker = gameDir.resolve(MARKER)
        if (marker.exists()) {
            logger.info("Client config import skipped; import marker already exists at {}", marker)
            return
        }

        val target = gameDir.resolve("options.txt")
        val sources = source.paths.filter { !it.startsWith(gameDir) && it.isRegularFile() }
            .sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }

        if (sources.isEmpty()) {
            logger.warn("Selected client profile {}/{} contains no readable config files", source.client, source.profile)
            Files.createDirectories(gameDir)
            Files.writeString(marker, "No compatible client config files were found.\n")
            return
        }

        logger.info(
            "Importing client profile {}/{} from {}",
            source.client,
            source.profile,
            sources.joinToString { it.toString() },
        )
        Files.createDirectories(gameDir)
        val optionSources = sources.filter { it.fileName.toString() in optionFileNames }
            .sortedBy { if (it.fileName.toString() == "optionsLC.txt") 0 else 1 }
        val importedOptions = if (optionSources.isEmpty()) 0 else mergeOptions(optionSources, target)
        val importedConfigs = importSharedModConfigs(sources)
        val importedHudFiles = importOneConfigHudConfigs(sources)
        val importedProfileHudFiles = importOneConfigProfileHudConfigs(sources)
        val importedHudConfigs = importedHudFiles.size + importedProfileHudFiles
        val translatedHudLayouts = NoRiskHudTranslator.import(sources, importedHudFiles) +
            LunarHudTranslator.import(sources, importedHudFiles) +
            FeatherHudTranslator.import(sources, importedHudFiles)
        val refreshedHudConfigs = reloadOneConfigProfileHudConfigs(sources)
        if (importedHudFiles.isNotEmpty() || translatedHudLayouts > 0) OneConfigHudInstances.reloadFromDisk()
        Files.writeString(
            marker,
            "Imported $importedOptions Minecraft settings, $importedConfigs shared mod config(s), " +
                "$importedHudConfigs OneConfig HUD config(s), and $translatedHudLayouts translated HUD layout(s).\n",
        )
        logger.info(
            "Imported {} Minecraft settings, {} shared mod config(s), {} OneConfig HUD config(s), " +
                "{} translated HUD layout(s), and refreshed {} live HUD config(s)",
            importedOptions,
            importedConfigs,
            importedHudConfigs,
            translatedHudLayouts,
            refreshedHudConfigs,
        )
    }

    private fun launcherRoots(): List<LauncherRoot> {
        val roaming = System.getenv("APPDATA")?.let(Path::of)
        val local = System.getenv("LOCALAPPDATA")?.let(Path::of)
        val userHome = System.getProperty("user.home")?.let(Path::of)
        val windowsUserHome = System.getenv("USERPROFILE")?.let(Path::of)

        return buildList {
            if (roaming != null) {
                // Prism profiles are consulted only when this launch is actually running OneClient.
                if (LauncherEnvironment.isOneClient) {
                    add(LauncherRoot("Prism Launcher", roaming.resolve("PrismLauncher/instances")))
                }
                addAll(
                    listOf(
                        LauncherRoot("MultiMC", roaming.resolve("MultiMC/instances")),
                        LauncherRoot("Modrinth", roaming.resolve("ModrinthApp/profiles")),
                        LauncherRoot("ATLauncher", roaming.resolve("ATLauncher/instances")),
                        LauncherRoot("Badlion Client", roaming.resolve("Badlion Client")),
                        LauncherRoot("LabyMod", roaming.resolve("LabyMod/instances")),
                        LauncherRoot("NoRisk Client", roaming.resolve("norisk/NoRiskClientV3/data/profiles")),
                        LauncherRoot("Dawn", roaming.resolve(".dawn/profiles")),
                        // Dawn's synced game folders, which hold the bundled Feather client's HUD profiles.
                        LauncherRoot("Dawn", roaming.resolve(".dawn/auto-sync/groups")),
                    ),
                )
            }
            addAll(
                listOfNotNull(
                    userHome?.resolve(".lunarclient/offline/multiver")?.let { LauncherRoot("Lunar Client", it) },
                    windowsUserHome?.resolve(".lunarclient/offline/multiver")?.let { LauncherRoot("Lunar Client", it) },
                    userHome?.resolve(".lunarclient/settings/game")?.let { LauncherRoot("Lunar Client", it) },
                    windowsUserHome?.resolve(".lunarclient/settings/game")?.let { LauncherRoot("Lunar Client", it) },
                    local?.resolve("LunarClient/offline/multiver")?.let { LauncherRoot("Lunar Client", it) },
                ),
            )
            val sharedMinecraftDirs = listOfNotNull(
                roaming?.resolve(".minecraft"),
                userHome?.resolve(".minecraft"),
                userHome?.resolve("Library/Application Support/minecraft"),
            ).distinct().filter { directory ->
                directory.resolve(".lunar-instance.lock").isRegularFile() ||
                    directory.resolve("optionsLC.txt").isRegularFile()
            }
            sharedMinecraftDirs.forEach { add(LauncherRoot("Lunar Client", it, sharedGameDirectory = true)) }
        }
    }

    private fun preview(paths: List<Path>): List<String> = buildList {
        val options = paths.filter { it.fileName?.toString() in optionFileNames }
            .sortedBy { if (it.fileName?.toString() == "optionsLC.txt") 0 else 1 }
            .flatMap { path -> runCatching { readOptionEntries(path) }.getOrDefault(emptyList()) }
            .filter { it.first !in excludedOptions }
            .distinctBy { it.first }
        if (options.isNotEmpty()) {
            add("Minecraft options · ${options.size} settings")
        }
        readOnboardingPreferences(paths).forEach { (preference, enabled) ->
            when (preference) {
                OnboardingPreference.SKIN_LAYERS ->
                    add("3D Skin Layers · ${if (enabled) "Enabled" else "Disabled"} (from client)")
            }
        }

        val configNames = paths.mapNotNull { path ->
            sharedModConfigs.entries.firstOrNull { it.value == path.fileName?.toString() && FabricLoader.getInstance().isModLoaded(it.key) }
                ?.key?.toPreviewLabel()
        }
        if (configNames.isNotEmpty()) add("Mod configs · ${configNames.distinct().joinToString(", ")}")

        val hudCount = importableHudFiles(paths).size
        if (hudCount > 0) add("HUD layouts · $hudCount")
        val profileHudCount = paths.count { path ->
            oneConfigProfileHudMod(path)?.let(::isHudModLoaded) == true
        }
        if (profileHudCount > 0) add("HUD elements · $profileHudCount compatible layouts")
        val translatedHudElements = NoRiskHudTranslator.preview(paths)
        if (translatedHudElements.isNotEmpty()) {
            add("Matching HUD elements · ${translatedHudElements.joinToString(", ")}")
        } else if (paths.any { it.parent?.fileName?.toString() == "profiles" && it.fileName?.toString()?.endsWith(".json") == true }) {
            add("No matching HUD elements installed in this instance")
        }
        val lunarHudElements = LunarHudTranslator.preview(paths)
        if (lunarHudElements.isNotEmpty()) {
            add("Lunar HUD elements · ${lunarHudElements.joinToString(", ")}")
        }
        val featherHudElements = FeatherHudTranslator.preview(paths)
        if (featherHudElements.isNotEmpty()) {
            add("Feather HUD elements · ${featherHudElements.joinToString(", ")}")
        }
        options.take(2).forEach { (key, value) -> add("${key.toPreviewLabel()}: $value") }
        if (isEmpty()) add("Compatible settings found")
    }

    private fun String.toPreviewLabel(): String =
        replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('-', ' ')
            .replaceFirstChar { it.uppercase() }

    private fun findSources(): Sequence<Path> = sequence {
        val launcherRoots = launcherRoots()

        for (root in launcherRoots) {
            if (!Files.isDirectory(root.path)) continue
            if (root.sharedGameDirectory) {
                val candidates = buildList {
                    add(root.path.resolve("options.txt"))
                    add(root.path.resolve("optionsLC.txt"))
                    sharedModConfigs.values.forEach { add(root.path.resolve("config").resolve(it)) }
                }
                candidates.filter { it.isRegularFile() }.forEach { yield(it) }
                listOf(root.path.resolve("huds"), root.path.resolve("config/huds")).filter(Files::isDirectory).forEach { dir ->
                    Files.list(dir).use { files ->
                        files.iterator().asSequence()
                            .filter { it.fileName.toString().endsWith(".json") && it.isRegularFile() }
                            .toList()
                    }.forEach { yield(it) }
                }
                val oneConfigProfiles = root.path.resolve("OneConfig/profiles")
                if (Files.isDirectory(oneConfigProfiles)) {
                    Files.walk(oneConfigProfiles, 4).use { profilePaths ->
                        val iterator = profilePaths.iterator()
                        while (iterator.hasNext()) {
                            val path = iterator.next()
                            if (oneConfigProfileHudMod(path) != null && !isBackupPath(path)) yield(path)
                        }
                    }
                }
                continue
            }
            try {
                Files.walk(root.path, 6).use { paths ->
                    val iterator = paths.iterator()
                    var scanned = 0
                    while (iterator.hasNext() && scanned < 20_000) {
                        val path = iterator.next()
                        scanned++
                        val fileName = path.fileName?.toString()
                        val parentName = path.parent?.fileName?.toString()
                        val isConfig = parentName == "config" && fileName in sharedModConfigs.values
                        val isHudConfig = parentName == "huds" && fileName?.endsWith(".json") == true
                        val isLunarHudConfig = root.name == "Lunar Client" && fileName == "mods.json" &&
                            path.parent?.parent?.fileName?.toString() == "game"
                        val isNoRiskProfile = root.name == "NoRisk Client" &&
                            fileName?.endsWith(".json") == true
                        val isFeatherProfile = root.name == "Dawn" && FeatherHudTranslator.isFeatherProfile(path)
                        if ((fileName in optionFileNames || isConfig || isHudConfig || isLunarHudConfig || isNoRiskProfile ||
                                isFeatherProfile) &&
                            Files.isRegularFile(path) && !isBackupPath(path)
                        ) {
                            yield(path)
                        }
                    }
                }
            } catch (error: Exception) {
                logger.debug("Could not scan client options under {}", root.path, error)
            }
        }
    }

    /** OneConfig HUD instance files from another instance whose HUD exists here. */
    private fun importableHudFiles(paths: List<Path>): Map<Path, String> =
        paths.filter { it.parent?.fileName?.toString() == "huds" && it.fileName?.toString()?.endsWith(".json") == true }
            .distinctBy { it.fileName.toString() }
            .mapNotNull { path ->
                // Files from older OneConfig builds have no hudClass; OneConfig's HUD loader fails on them.
                val hudClass = OneConfigHudInstances.hudClassOf(path) ?: return@mapNotNull null
                if (!OneConfigHudInstances.isHudClassAvailable(hudClass)) return@mapNotNull null
                path to hudClass
            }.toMap()

    private fun importOneConfigHudConfigs(sources: List<Path>): Set<String> {
        val targetDir = OneConfigHudInstances.hudDirectory()
        val files = importableHudFiles(sources)
        if (files.isEmpty()) return emptySet()
        Files.createDirectories(targetDir)
        // Existing instances per HUD type, so imported ones replace them instead of adding duplicates.
        val existing = Files.list(targetDir).use { stream ->
            stream.iterator().asSequence()
                .filter { it.fileName.toString().endsWith(".json") && it.isRegularFile() }
                .sortedBy { it.fileName.toString() }
                .mapNotNull { path -> OneConfigHudInstances.hudClassOf(path)?.let { it to path } }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toMutableList() }
                .toMutableMap()
        }
        val imported = mutableSetOf<String>()
        files.forEach { (source, hudClass) ->
            val sameName = targetDir.resolve(source.fileName.toString())
            val target = existing[hudClass]?.let { matches ->
                (matches.firstOrNull { it == sameName } ?: matches.firstOrNull())?.also { matches.remove(it) }
            } ?: sameName
            backup(target)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            imported += target.fileName.toString()
            logger.info("Imported OneConfig HUD {} from {} into {}", hudClass, source, target)
        }
        return imported
    }

    private fun importOneConfigProfileHudConfigs(sources: List<Path>): Int {
        val targetProfile = ConfigManager.active().folder
        var imported = 0
        sources.forEach { source ->
            val modId = oneConfigProfileHudMod(source) ?: return@forEach
            if (!isHudModLoaded(modId)) return@forEach
            val target = targetProfile.resolve(modId).resolve(source.fileName.toString())
            Files.createDirectories(target.parent)
            backup(target)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            imported++
            logger.info("Imported {} HUD element settings from {}", modId, source)
        }
        return imported
    }

    /** Rebind imported profile files into OneConfig's live trees; its watcher only watches the profile root. */
    private fun reloadOneConfigProfileHudConfigs(sources: List<Path>): Int {
        val manager = ConfigManager.active()
        var refreshed = 0
        sources.forEach { source ->
            val modId = oneConfigProfileHudMod(source) ?: return@forEach
            if (!isHudModLoaded(modId)) return@forEach
            val configId = "$modId/${source.fileName}"
            runCatching {
                val importedTree = manager.load(configId)
                if (importedTree == null) {
                    logger.warn("Could not load imported HUD config '{}' into the active profile", configId)
                    return@runCatching
                }
                importedTree.setID(configId)
                val result = manager.register(importedTree)
                refreshed++
                logger.info("Refreshed live OneConfig HUD config '{}' (registration state {})", configId, result.state)
            }.onFailure {
                logger.warn("Could not refresh imported HUD config '{}' in the active profile", configId, it)
            }
        }
        return refreshed
    }

    private fun oneConfigProfileHudMod(path: Path): String? {
        if (path.fileName?.toString()?.endsWith(".json") != true) return null
        val modId = path.parent?.fileName?.toString() ?: return null
        if (modId !in oneConfigHudProfileMods) return null
        val profileDirectory = path.parent.parent ?: return null
        val profilesDirectory = profileDirectory.parent ?: return null
        val oneConfigDirectory = profilesDirectory.parent ?: return null
        return modId.takeIf {
            profilesDirectory.fileName?.toString() == "profiles" &&
                oneConfigDirectory.fileName?.toString() == "OneConfig"
        }
    }

    internal fun isHudModLoaded(modId: String): Boolean =
        FabricLoader.getInstance().isModLoaded(modId) ||
            modId == "vanilla-hud" && FabricLoader.getInstance().isModLoaded("oneconfig")

    internal fun backup(target: Path) {
        if (!Files.exists(target)) return
        val parent = target.parent
        // OneConfig reads every file in a huds folder as a HUD, so keep those backups beside it instead.
        val backup = if (parent?.fileName?.toString() == "huds") {
            parent.resolveSibling("huds.polyplus-import-backup").also(Files::createDirectories).resolve(target.fileName.toString())
        } else {
            target.resolveSibling("${target.fileName}.polyplus-import-backup")
        }
        if (!Files.exists(backup)) Files.copy(target, backup)
    }

    private fun importSharedModConfigs(sources: List<Path>): Int {
        val configDir = FabricLoader.getInstance().configDir
        var imported = 0
        sharedModConfigs.forEach { (modId, fileName) ->
            if (!FabricLoader.getInstance().isModLoaded(modId)) return@forEach
            val source = sources.firstOrNull { it.fileName?.toString() == fileName } ?: return@forEach
            val target = configDir.resolve(fileName)
            Files.createDirectories(configDir)
            backup(target)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            imported++
            logger.info("Imported {} settings from {}", modId, source)
        }
        return imported
    }

    private fun mergeOptions(sources: List<Path>, target: Path): Int {
        val currentLines = if (Files.isRegularFile(target)) Files.readAllLines(target) else emptyList()
        val values = linkedMapOf<String, String>()
        sources.forEach { source ->
            Files.readAllLines(source).mapNotNull(::optionEntry)
                .filter { (key, _) -> key !in excludedOptions }
                .forEach { (key, value) -> values.putIfAbsent(key, value) }
        }
        if (values.isEmpty()) return 0

        val seen = mutableSetOf<String>()
        val merged = currentLines.map { line ->
            val entry = optionEntry(line) ?: return@map line
            val importedValue = values[entry.first] ?: return@map line
            seen += entry.first
            "${entry.first}:${importedValue}"
        }.toMutableList()

        values.forEach { (key, value) ->
            if (key !in seen) merged += "$key:$value"
        }

        val temporary = target.resolveSibling("${target.fileName}.polyplus-import")
        Files.write(temporary, merged)
        runCatching {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return values.size
    }

    private fun optionEntry(line: String): Pair<String, String>? {
        if (line.isBlank() || line.startsWith("#")) return null
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        val key = line.substring(0, separator).trim()
        if (key.isEmpty()) return null
        return key to line.substring(separator + 1)
    }

    private fun readOptionEntries(path: Path): List<Pair<String, String>> =
        if (path.fileName?.toString() == "optionsLC.txt") {
            runCatching {
                Json.parseToJsonElement(Files.readString(path)).jsonObject.mapNotNull { (key, element) ->
                    val value = element as? JsonPrimitive ?: return@mapNotNull null
                    if (value == JsonNull || key in excludedOptions) return@mapNotNull null
                    key to value.content
                }
            }.onFailure { logger.debug("Could not read Lunar options from {}", path, it) }
                .getOrDefault(emptyList())
        } else {
            Files.readAllLines(path).mapNotNull(::optionEntry)
        }

    private fun isBackupPath(path: Path): Boolean =
        generateSequence(path.parent) { it.parent }.any { directory ->
            directory.fileName?.toString()?.let { name ->
                name.contains("backup", ignoreCase = true) || name.contains("not used by the game", ignoreCase = true)
            } == true
        }

    private fun readOnboardingPreferences(paths: List<Path>): Map<OnboardingPreference, Boolean> = buildMap {
        val lunarMods = paths.firstOrNull {
            it.fileName?.toString() == "mods.json" && it.parent?.parent?.fileName?.toString() == "game"
        } ?: return@buildMap
        val skinLayersEnabled = runCatching {
            Json.parseToJsonElement(Files.readString(lunarMods))
                .jsonObject["3D_SKINS"]
                ?.jsonObject
                ?.get("enabled")
                ?.jsonPrimitive
                ?.booleanOrNull
        }.onFailure { logger.debug("Could not read Lunar 3D skin setting from {}", lunarMods, it) }
            .getOrNull()
        skinLayersEnabled?.let { put(OnboardingPreference.SKIN_LAYERS, it) }
    }
}
