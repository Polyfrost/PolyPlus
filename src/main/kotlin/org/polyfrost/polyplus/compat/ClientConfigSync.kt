package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.apache.logging.log4j.LogManager
//? if = 1.8.9 {
/*import org.lwjgl.opengl.Display
*///?}
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

    /** What importing a source would change here, for the onboarding summary box. */
    data class ImportSummary(
        val gameSettings: Int,
        val modSettings: List<String>,
        val huds: List<String>,
        /** The client has a HUD layout, but none of its HUDs exist in this instance. */
        val hasUnmatchedHuds: Boolean,
    ) {
        fun isEmpty(): Boolean = gameSettings == 0 && modSettings.isEmpty() && huds.isEmpty()
    }

    data class ImportSource(
        val id: String,
        val client: String,
        val profile: String,
        val summary: ImportSummary,
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
        "version",
        "lastServer",
        "resourcePacks",
        "incompatibleResourcePacks",
        "soundDevice",
        "joinedFirstServer",
        "realmsNotifications",
        "onboarded",
        "tutorialStep",
        "lastLaunchedVersion",
        "startedCleanly",
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
                // The most specific folder wins, e.g. Badlion's profiles inside the shared .minecraft folder.
                val root = roots.filter { path.startsWith(it.path) }.maxByOrNull { it.path.nameCount }
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
                    } else if (relative.nameCount == 1 && (relativeName.endsWith(".json") || relativeName.endsWith(".zip"))) {
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
            val summary = summarize(orderedPaths)
            ImportSource(
                id = orderedPaths.joinToString("|") { it.toAbsolutePath().normalize().toString() },
                client = client,
                profile = shortProfile,
                summary = summary,
                preview = summary.previewLines(),
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
        if (importedOptions > 0) reloadGameOptions()
        val importedConfigs = importSharedModConfigs(sources)
        val importedHudFiles = importOneConfigHudConfigs(sources)
        val importedProfileHudFiles = importOneConfigProfileHudConfigs(sources)
        val importedHudConfigs = importedHudFiles.size + importedProfileHudFiles
        val translatedHudLayouts = NoRiskHudTranslator.import(sources, importedHudFiles) +
            LunarHudTranslator.import(sources, importedHudFiles) +
            FeatherHudTranslator.import(sources, importedHudFiles) +
            BadlionHudTranslator.import(sources, importedHudFiles)
        val importedFeatures = ModConfigPatcher.apply(ClientFeatureTranslator.patches(sources))
        val refreshedHudConfigs = reloadOneConfigProfileHudConfigs(sources)
        if (importedHudFiles.isNotEmpty() || translatedHudLayouts > 0) OneConfigHudInstances.reloadFromDisk()
        Files.writeString(
            marker,
            "Imported $importedOptions Minecraft settings, $importedConfigs shared mod config(s), " +
                "$importedFeatures mod feature config(s), $importedHudConfigs OneConfig HUD config(s), " +
                "and $translatedHudLayouts translated HUD layout(s).\n",
        )
        logger.info(
            "Imported {} Minecraft settings, {} shared mod config(s), {} mod feature config(s), " +
                "{} OneConfig HUD config(s), {} translated HUD layout(s), and refreshed {} live HUD config(s)",
            importedOptions,
            importedConfigs,
            importedFeatures,
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
                        // Badlion keeps its mod profiles (HUD layouts included) as zips inside .minecraft.
                        LauncherRoot("Badlion Client", roaming.resolve(".minecraft/BLClient-Mod-Profiles")),
                        // LabyMod import is disabled for now.
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

    /** Short preview lines for a source card; onboarding shows only a few, so one line per category. */
    private fun ImportSummary.previewLines(): List<String> = buildList {
        if (gameSettings > 0) add("Game settings · $gameSettings options")
        if (modSettings.isNotEmpty()) add("Mod settings · ${modSettings.joinToString(", ")}")
        when {
            huds.isNotEmpty() -> {
                val shown = huds.take(PREVIEW_HUD_NAMES)
                val more = huds.size - shown.size
                add("HUDs · ${huds.size}: ${shown.joinToString(", ")}${if (more > 0) ", +$more more" else ""}")
            }
            hasUnmatchedHuds -> add("HUDs · none of this client's HUDs have a match here")
        }
        if (isEmpty()) add("Compatible settings found")
    }

    private fun summarize(paths: List<Path>): ImportSummary {
        val options = paths.filter { it.fileName?.toString() in optionFileNames }
            .sortedBy { if (it.fileName?.toString() == "optionsLC.txt") 0 else 1 }
            .flatMap { path -> runCatching { readOptionEntries(path) }.getOrDefault(emptyList()) }
            .filter { it.first !in excludedOptions && isOptionKey(it.first) }
            .distinctBy { it.first }

        val modSettings = buildList {
            paths.forEach { path ->
                sharedModConfigs.entries.firstOrNull {
                    it.value == path.fileName?.toString() && FabricLoader.getInstance().isModLoaded(it.key)
                }?.let { add(it.key.toPreviewLabel()) }
            }
            readOnboardingPreferences(paths).forEach { (preference, enabled) ->
                when (preference) {
                    OnboardingPreference.SKIN_LAYERS -> add("3D skin layers ${if (enabled) "on" else "off"}")
                }
            }
            addAll(ClientFeatureTranslator.preview(paths))
        }.distinct()

        val huds = buildList {
            importableHudFiles(paths).values.forEach { add(it.substringAfterLast('.').removeSuffix("Hud")) }
            paths.filter { oneConfigProfileHudMod(it)?.let(::isHudModLoaded) == true }
                .forEach { add(it.fileName.toString().removeSuffix(".json")) }
            addAll(LunarHudTranslator.preview(paths))
            addAll(NoRiskHudTranslator.preview(paths))
            addAll(FeatherHudTranslator.preview(paths))
            addAll(BadlionHudTranslator.preview(paths))
        }.map { it.toHudLabel() }.distinct()
        val hasHudLayout = paths.any {
            BadlionHudTranslator.isBadlionProfile(it) || FeatherHudTranslator.isFeatherProfile(it) ||
                it.fileName?.toString() == "mods.json" ||
                it.parent?.fileName?.toString() == "profiles" && it.fileName?.toString()?.endsWith(".json") == true
        }
        return ImportSummary(
            gameSettings = options.size,
            modSettings = modSettings,
            huds = huds,
            hasUnmatchedHuds = huds.isEmpty() && hasHudLayout,
        )
    }

    private const val PREVIEW_HUD_NAMES = 5
    private val previewAcronyms = setOf("fps", "cps", "tps")

    private fun String.toPreviewLabel(): String =
        replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('-', ' ')
            .replaceFirstChar { it.uppercase() }

    /** `DAY_COUNTER`, `comboDisplay` and `reach display` all become sentence-case labels like "Day counter". */
    private fun String.toHudLabel(): String {
        val words = replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').trim().lowercase()
        if (words in previewAcronyms) return words.uppercase()
        return words.replaceFirstChar { it.uppercase() }
    }

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
                        val isBadlionProfile = root.name == "Badlion Client" && BadlionHudTranslator.isBadlionProfile(path)
                        if ((fileName in optionFileNames || isConfig || isHudConfig || isLunarHudConfig || isNoRiskProfile ||
                                isFeatherProfile || isBadlionProfile) &&
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

    internal fun mergeOptions(sources: List<Path>, target: Path): Int {
        val currentLines = if (Files.isRegularFile(target)) Files.readAllLines(target) else emptyList()
        val values = linkedMapOf<String, String>()
        //? if = 1.8.9 {
        /*val legacyOptions = true
        *///?} else {
        val legacyOptions = false
        //?}
        //? if >= 26.3 {
        val sdlKeys = true
        //?} else {
        /*val sdlKeys = false
        *///?}
        sources.forEach { source ->
            // Lunar's optionsLC.txt is JSON, so read it as such rather than as `key:value` lines.
            // The translator reads the source's `version` to tell its format, so exclusions come after it.
            GameOptionsTranslator.translate(readOptionEntries(source).filter { isOptionKey(it.first) }, legacyOptions, sdlKeys)
                .filter { (key, _) -> key !in excludedOptions }
                .forEach { (key, value) -> values.putIfAbsent(key, value) }
        }
        if (values.isEmpty()) return 0

        val seen = mutableSetOf<String>()
        // Drop the JSON line an earlier build pasted in from optionsLC.txt.
        val merged = currentLines.filter { line -> optionEntry(line)?.first?.let(::isOptionKey) != false }.map { line ->
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

    /**
     * Makes the running game read the merged `options.txt`. It only reads the file at startup, so without this the
     * imported options would not apply this session and its next options save would write the old ones back.
     */
    private fun reloadGameOptions() {
        val mc = Minecraft.getInstance()
        val reload = Runnable {
            runCatching {
                mc.options.load()
                // Loading only stores the values; screens opened from here on use the imported GUI scale.
                //? if >= 26.1 {
                mc.resizeGui()
                //?} elif > 1.8.9 {
                /*mc.resizeDisplay()
                *///?} else {
                /*Display.setVSyncEnabled(mc.options.vsync)
                *///?}
                logger.info("Reloaded the game options after the import")
            }.onFailure { logger.warn("Could not reload the game options; the import applies after a restart", it) }
        }
        //? if > 1.8.9 {
        if (mc.isSameThread) reload.run() else mc.execute(reload)
        //?} else {
        /*if (mc.isOnSameThread) reload.run() else mc.executeTask(reload)
        *///?}
    }

    /** Real option keys are plain names such as `guiScale` or `key_key.attack`, never JSON. */
    private fun isOptionKey(key: String): Boolean = key.none { it == '{' || it == '"' || it == '}' }

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
                    if (value == JsonNull) return@mapNotNull null
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
        }
        if (lunarMods == null) {
            NoRiskHudTranslator.skinLayersEnabled(paths)?.let { put(OnboardingPreference.SKIN_LAYERS, it) }
            return@buildMap
        }
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
