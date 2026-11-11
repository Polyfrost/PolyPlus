package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.streams.asSequence

/**
 * Writes imported HUD layouts as OneConfig HUD instances.
 *
 * OneConfig only shows a HUD when an instance file with a `hudClass` exists in the profile's `huds` folder.
 * HUDs that are off by default (FPS, coordinates, ...) never get one until the user adds them in the editor,
 * so translators update the matching instance or create one; the HUD reload after onboarding reads them from disk.
 */
internal object OneConfigHudInstances {
    private val logger = LogManager.getLogger("PolyPlus/ClientConfigSync/Hud")
    private val json = Json { prettyPrint = true }

    /** [fileName] is fixed for single-instance HUDs; otherwise OneConfig's `<random>-<name>` pattern is used. */
    data class Target(val modId: String, val hudClass: String, val name: String, val fileName: String? = null)

    private const val EVERGREEN = "org.polyfrost.evergreenhud.client.hud"
    private const val VANILLA = "org.polyfrost.vanillahud.hud"

    val FPS = Target("evergreenhud", "$EVERGREEN.FpsHud", "fps")
    val COORDINATES = Target("evergreenhud", "$EVERGREEN.PositionHud", "position")
    val DIRECTION = Target("evergreenhud", "$EVERGREEN.direction.DirectionHud", "direction")
    val DAY = Target("evergreenhud", "$EVERGREEN.DayHud", "day")
    val ARMOR = Target("evergreenhud", "$EVERGREEN.ArmorHud", "armor")
    val POTIONS = Target("evergreenhud", "$EVERGREEN.potion.PotionEffectsHud", "potions")
    val KEYSTROKES = Target("evergreenhud", "$EVERGREEN.keystrokes.KeystrokesHud", "keystrokes")
    val CPS = Target("evergreenhud", "$EVERGREEN.CpsHud", "cps")
    val PING = Target("evergreenhud", "$EVERGREEN.PingHud", "ping")
    val TPS = Target("evergreenhud", "$EVERGREEN.TpsHud", "tps")
    val MEMORY = Target("evergreenhud", "$EVERGREEN.MemoryHud", "memory")
    val COMBO = Target("evergreenhud", "$EVERGREEN.ComboHud", "combo")
    val REACH = Target("evergreenhud", "$EVERGREEN.ReachHud", "reach")
    val SPEED = Target("evergreenhud", "$EVERGREEN.SpeedHud", "speed")
    val SERVER = Target("evergreenhud", "$EVERGREEN.ServerAddressHud", "serverip")
    val RESOURCE_PACK = Target("evergreenhud", "$EVERGREEN.ResourcePackHud", "resourcepack")
    val CLOCK = Target("evergreenhud", "$EVERGREEN.clock.ClockHud", "clock")
    val ITEM_COUNTER = Target("evergreenhud", "$EVERGREEN.ItemCounterHud", "itemcounter")
    val INVENTORY = Target("evergreenhud", "$EVERGREEN.InventoryHud", "inventory")
    val PLAYTIME = Target("evergreenhud", "$EVERGREEN.PlayTimeHud", "playtime")
    val MEDIA = Target("evergreenhud", "$EVERGREEN.media.MediaHud", "media")
    val SATURATION = Target("evergreenhud", "$EVERGREEN.SaturationHud", "saturation")
    val MOUSE_STROKES =Target("evergreenhud", "$EVERGREEN.mouse.MouseStrokesHud", "mousestrokes")
    val SCOREBOARD = Target("vanillahud", "$VANILLA.ScoreboardHud", "scoreboard", "vanillahud-scoreboard.json")
    val ACTION_BAR = Target("vanillahud", "$VANILLA.ActionBarHud", "actionbar", "vanillahud-actionbar.json")
    val TITLES = Target("vanillahud", "$VANILLA.TitleHud", "title", "vanillahud-title.json")
    val BOSS_BAR = Target("vanillahud", "$VANILLA.BossBarHud", "bossbar", "vanillahud-bossbar.json")
    val HOTBAR = Target("vanillahud", "$VANILLA.HotbarHud", "hotbar", "vanillahud-hotbar.json")
    val TAB_LIST = Target("vanillahud", "$VANILLA.TabListHud", "tab", "vanillahud-tab.json")
    val ITEM_TOOLTIP = Target("vanillahud", "$VANILLA.HeldItemTooltipHud", "itemtooltip", "vanillahud-itemtooltip.json")

    /**
     * One imported HUD. [x]/[y] are offsets from [section] already in OneConfig HUD units (see [screen]).
     * [style] holds extra OneConfig HUD properties, such as colors, copied straight into the instance.
     */
    data class Request(
        val source: String,
        val target: Target,
        val enabled: Boolean?,
        val section: String? = null,
        val x: Double? = null,
        val y: Double? = null,
        val scale: Double? = null,
        val style: Map<String, JsonElement> = emptyMap(),
    ) {
        val hasPlacement get() = section != null || x != null || y != null
        val hasChanges get() = enabled != null || hasPlacement || scale != null || style.isNotEmpty()
    }

    /** OneConfig's HUD screen size; offsets are stored relative to it and rescaled when the window changes. */
    fun screen(): Pair<Double, Double> {
        val width = HudManager.guiScreenWidth.toDouble()
        val height = HudManager.guiScreenHeight.toDouble()
        return if (width > 0 && height > 0) width to height else 1280.0 to 720.0
    }

    /**
     * Factors that turn another client's offsets in Minecraft scaled-GUI pixels (at the GUI scale from its
     * imported options) into OneConfig HUD units for the current window, so HUDs keep the same visual spacing.
     */
    fun guiPixelFactors(sources: List<Path>, client: String, guiScaleSetting: Int? = null): Pair<Double, Double> = runCatching {
        val (refW, refH) = screen()
        val window = Minecraft.getInstance().window
        val width = window.width
        val height = window.height
        if (width <= 0 || height <= 0) return@runCatching 1.0 to 1.0
        val scale = guiScale(width, height, guiScaleSetting ?: importedGuiScale(sources))
        logger.info(
            "Scaling {} HUD offsets for a {}x{} window at GUI scale {} into {}x{} HUD units",
            client, width, height, scale, refW, refH,
        )
        refW / (width.toDouble() / scale) to refH / (height.toDouble() / scale)
    }.onFailure { logger.warn("Could not read the window size; {} HUD offsets are left unscaled", client, it) }
        .getOrDefault(1.0 to 1.0)

    /** Minecraft's own GUI scale selection; 0 (auto) picks the largest scale that still fits 320x240. */
    private fun guiScale(width: Int, height: Int, setting: Int): Int {
        var scale = 1
        while (scale != setting && scale < width && scale < height &&
            width / (scale + 1) >= 320 && height / (scale + 1) >= 240
        ) scale++
        return scale
    }

    /** The other client's GUI scale from its imported options (Lunar's JSON options first). */
    private fun importedGuiScale(sources: List<Path>): Int {
        sources.firstOrNull { it.fileName?.toString() == "optionsLC.txt" }?.let { path ->
            runCatching {
                json.parseToJsonElement(Files.readString(path)).jsonObject["guiScale"]?.jsonPrimitive?.content?.toIntOrNull()
            }.getOrNull()?.let { return it }
        }
        sources.firstOrNull { it.fileName?.toString() == "options.txt" }?.let { path ->
            runCatching {
                Files.readAllLines(path).firstOrNull { it.startsWith("guiScale:") }?.substringAfter(':')?.trim()?.toIntOrNull()
            }.getOrNull()?.let { return it }
        }
        return 0
    }

    fun apply(requests: List<Request>, alreadyImported: Set<String>): Int {
        val hudDir = hudDirectory()
        val (refW, refH) = screen()
        var applied = 0
        var unplaced = 0
        val handled = mutableSetOf<String>()

        requests.forEach { request ->
            runCatching {
                val target = request.target
                if (!isAvailable(target) || !handled.add(target.hudClass)) return@runCatching
                val existing = findInstance(hudDir, target)
                if (existing != null) {
                    if (existing.fileName.toString() in alreadyImported || !request.hasChanges) return@runCatching
                    val root = json.parseToJsonElement(Files.readString(existing)) as? JsonObject ?: return@runCatching
                    ClientConfigSync.backup(existing)
                    write(existing, root, request, refW, refH, null)
                    applied++
                    logger.info("Applied {} HUD settings to {}", request.source, existing)
                    return@runCatching
                }

                // Only create HUDs the other client actually shows; a disabled one needs no instance.
                if (request.enabled != true) return@runCatching
                Files.createDirectories(hudDir)
                val path = hudDir.resolve(target.fileName ?: "${(1000..9999).random()}-${target.name}.json")
                // The client left this HUD at its default spot, so stack these down the left edge instead of overlapping.
                val fallbackY = if (request.hasPlacement) null else 4.0 + 14.0 * unplaced++
                write(path, JsonObject(mapOf("hudClass" to JsonPrimitive(target.hudClass))), request, refW, refH, fallbackY)
                applied++
                logger.info("Created {} HUD from {} at {}", target.hudClass, request.source, path)
            }.onFailure { logger.warn("Could not apply imported {} HUD", request.source, it) }
        }
        return applied
    }

    /** Whether [request] would change anything on import; used for onboarding previews. */
    fun wouldApply(request: Request): Boolean =
        isAvailable(request.target) && request.enabled != false &&
            (request.enabled == true || findInstance(hudDirectory(), request.target) != null && request.hasChanges)

    private fun write(path: Path, root: JsonObject, request: Request, refW: Double, refH: Double, fallbackY: Double?) {
        val values = root.toMutableMap()
        values["posSchema"] = JsonPrimitive(1)
        val section = request.section ?: if (fallbackY != null) "TopLeft" else null
        if (section != null) {
            values["section"] = JsonObject(
                mapOf(
                    "class" to JsonPrimitive("org.polyfrost.oneconfig.api.hud.v1.Section"),
                    "value" to JsonPrimitive(section),
                ),
            )
        }
        val x = request.x ?: if (fallbackY != null) 4.0 else null
        val y = request.y ?: fallbackY
        if (section != null || x != null || y != null) {
            values["relativeX"] = JsonPrimitive(x ?: 0.0)
            values["relativeY"] = JsonPrimitive(y ?: 0.0)
            values["layoutRefW"] = JsonPrimitive(refW)
            values["layoutRefH"] = JsonPrimitive(refH)
        }
        request.enabled?.let { values["hidden"] = JsonPrimitive(!it) }
        if (request.enabled == null && !root.containsKey("hidden")) values["hidden"] = JsonPrimitive(false)
        request.scale?.takeIf { it.isFinite() && it in 0.25..4.0 }?.let { values["customScale"] = JsonPrimitive(it) }
        values.putAll(request.style)
        Files.writeString(path, json.encodeToString(JsonElement.serializer(), JsonObject(values)))
    }

    /** Finds the first HUD instance file in the active profile whose `hudClass` is this target's. */
    private fun findInstance(hudDir: Path, target: Target): Path? {
        target.fileName?.let { fixed ->
            val path = hudDir.resolve(fixed)
            if (Files.isRegularFile(path)) return path
        }
        if (!Files.isDirectory(hudDir)) return null
        return Files.list(hudDir).use { files ->
            files.asSequence()
                .filter { it.fileName.toString().endsWith(".json") && Files.isRegularFile(it) }
                .sortedBy { it.fileName.toString() }
                .firstOrNull { hudClassOf(it) == target.hudClass }
        }
    }

    /**
     * Makes OneConfig re-read the `huds` folder now, the same way it does on a profile switch, without first
     * saving the live HUDs (which would write over the imported files). OneConfig has no public call for this.
     */
    fun reloadFromDisk() {
        runCatching {
            val manager = HudManager::class.java
            @Suppress("UNCHECKED_CAST")
            val pending = manager.getMethod("access\$getPendingProfileReload\$p").invoke(null) as AtomicReference<Any?>
            val trees = manager.getMethod("access\$gatherHudTrees", manager).invoke(null, HudManager)
            val reload = Class.forName("${manager.name}\$ProfileReload")
                .getDeclaredConstructor(String::class.java, Boolean::class.javaPrimitiveType, Collection::class.java)
                .apply { isAccessible = true }
                .newInstance(ConfigManager.activeProfile(), false, trees)
            pending.set(reload)
            manager.getMethod("access\$applyPendingProfileReload", manager).invoke(null, HudManager)
            logger.info("Asked OneConfig to reload HUDs from disk after the import")
        }.onFailure {
            logger.warn("Could not reload OneConfig HUDs; imported HUDs will appear after a restart", it)
        }
    }

    fun hudDirectory(): Path = ConfigManager.active().folder.resolve("huds")

    fun hudClassOf(path: Path): String? = runCatching {
        json.parseToJsonElement(Files.readString(path)).jsonObject["hudClass"]?.jsonPrimitive?.content
    }.getOrNull()

    fun isHudClassAvailable(hudClass: String): Boolean =
        runCatching { Class.forName(hudClass, false, OneConfigHudInstances::class.java.classLoader) }.isSuccess

    private fun isAvailable(target: Target): Boolean =
        FabricLoader.getInstance().isModLoaded(target.modId) && isHudClassAvailable(target.hudClass)
}
