package org.polyfrost.polyplus.client.features

//? if wwaypoints
import com.wwaypoints.WaypointsClient
import net.fabricmc.loader.api.FabricLoader
//? if > 1.8.9 {
import net.minecraft.client.KeyMapping
//?} else {
/*import net.minecraft.client.options.KeyBinding as KeyMapping
*///?}
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.render.InputConstants
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

object DefaultSettings {
    private val logger = LogManager.getLogger("PolyPlus/DefaultSettings")

    private const val UNLIMITED_FRAMERATE = 260

    private const val TICK_SCAN_INTERVAL = 20
    private const val RETRY_SCAN_LIMIT = 100

    private val UNBIND_ALL_NAMESPACES = listOf(
        "presencefootsteps",
        "skyocean",
        "modernwarpmenu",
        "iris",
        "viewmodel",
    )

    private val UNBIND_KEYS = listOf(
        "zoomify.key.zoom.secondary",
        "key.optigui.inspect",
        "key.blackbarconcealer.toggle",
        "key.debug.noxesium",
        "Open Mod Configuration",
        "Reload Mod",
    )

    private val POST_LEGACY_UNBINDS = setOf("key.debug.noxesium", "Open Mod Configuration", "Reload Mod")

    private const val BETTER_SCREENS_ID = "betterscreens"
    private const val CONFIRM_DISCONNECT_ID = "confirmdisconnect"
    private const val CONTROLIFY_ID = "controlify"
    private const val BOBBY_ID = "bobby"
    private const val MODMENU_ID = "modmenu"
    private const val IQ_ID = "iqaddons"
    private const val CBH_ID = "custom-block-highlight"
    private const val ITEM_PHYSIC_ID = "itemphysiclite"
    private const val WWAYPOINTS_ID = "wwaypoints"

    private const val IQ_PHASE_THREE_CONFIG = "net.iqaddons.mod.config.categories.PhaseThreeConfig"
    private const val RESOURCEFUL_CONFIGURATIONS = "com.teamresourceful.resourcefulconfig.common.config.Configurations"
    private const val RESOURCEFUL_CONFIG = "com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig"

    private const val BOBBY_CONFIG_FILE = "bobby.conf"
    private const val BOBBY_DYNAMIC_MULTI_WORLD = "dynamic-multi-world"

    private val BOBBY_DYNAMIC_MULTI_WORLD_LINE =
        Regex("""^(\s*)"?$BOBBY_DYNAMIC_MULTI_WORLD"?\s*([=:])\s*.*$""")

    private const val ITEM_PHYSIC_CONFIG = "xyz.tryfle.oitemphysic.config.OSLConfig"
    private const val OSL_CONFIG = "net.ornithemc.osl.config.api.config.Config"
    private const val OSL_CONFIG_MANAGER = "net.ornithemc.osl.config.api.ConfigManager"
    private const val OSL_CONFIG_MANAGER_IMPL = "net.ornithemc.osl.config.impl.ConfigManagerImpl"

    private const val MODMENU_MAIN = "com.terraformersmc.modmenu.ModMenu"
    private const val MODMENU_CONFIG = "com.terraformersmc.modmenu.config.ModMenuConfig"
    private const val MODMENU_CONFIG_MANAGER = "com.terraformersmc.modmenu.config.ModMenuConfigManager"

    private val MODMENU_COUNT_OPTIONS = listOf("COUNT_CHILDREN", "COUNT_LIBRARIES", "COUNT_HIDDEN_MODS")

    private const val BETTER_SCREENS_CONFIG = "dev.microcontrollers.betterscreens.config.BetterScreensConfig"
    private const val CONFIRM_DISCONNECT_CONFIG = "dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig"
    private const val CONTROLIFY_MOD = "dev.isxander.controlify.Controlify"

    private class Task(
        val id: String,
        val label: String,
        val isPresent: () -> Boolean,
        val apply: () -> Unit,
        val coveredByLegacyFlag: Boolean = true,
        val retryable: Boolean = false,
    ) {
        var attempts = 0
    }

    private val TICK_TASKS = buildList {
        add(Task("vanilla-options", "Minecraft options", { true }, ::applyVanillaOptions))
        //? if >= 1.21.11 {
        add(
            Task(
                id = "vanilla-texture-filtering",
                label = "Minecraft texture filtering",
                isPresent = { true },
                apply = ::disableTextureFiltering,
                coveredByLegacyFlag = false,
            ),
        )
        //?}
        UNBIND_ALL_NAMESPACES.forEach { namespace ->
            add(unbindTask(namespace) { key -> namespace in key.split('.') })
        }
        UNBIND_KEYS.forEach { key -> add(unbindTask(key) { it == key }) }
        //? if wwaypoints {
        add(
            Task(
                id = "unbind:wwaypoints-create-waypoint",
                label = "keybinds",
                isPresent = { modLoaded(WWAYPOINTS_ID) },
                apply = ::unbindWWaypointsCreateKeys,
                coveredByLegacyFlag = false,
            ),
        )
        //?}
        add(
            Task(
                id = "better-screens",
                label = "Better Screens",
                isPresent = { modLoaded(BETTER_SCREENS_ID) && findClass(BETTER_SCREENS_CONFIG) != null },
                apply = ::applyBetterScreens,
            ),
        )
        add(
            Task(
                id = "confirm-disconnect",
                label = "Confirm Disconnect",
                isPresent = { modLoaded(CONFIRM_DISCONNECT_ID) && findClass(CONFIRM_DISCONNECT_CONFIG) != null },
                apply = ::applyConfirmDisconnect,
            ),
        )
        add(
            Task(
                id = "controlify-keyboard-movement",
                label = "Controlify keyboard-like movement",
                isPresent = { modLoaded(CONTROLIFY_ID) && controlifyGlobalSettings() != null },
                apply = ::applyControlifyKeyboardMovement,
                retryable = true,
            ),
        )
        add(
            Task(
                id = "bobby-dynamic-multi-world",
                label = "Bobby",
                isPresent = { modLoaded(BOBBY_ID) && bobbyConfigPath().exists() },
                apply = ::applyBobbyConfig,
                coveredByLegacyFlag = false,
                retryable = true,
            ),
        )
        add(
            Task(
                id = "modmenu-mod-count",
                label = "Mod Menu mod count",
                isPresent = { modLoaded(MODMENU_ID) && findClass(MODMENU_CONFIG) != null },
                apply = ::applyModMenuModCount,
                coveredByLegacyFlag = false,
            ),
        )
        add(
            Task(
                id = "iq-block-useless-perks",
                label = "IQ Addons",
                isPresent = { modLoaded(IQ_ID) && findClass(IQ_PHASE_THREE_CONFIG) != null },
                apply = ::disableIqBlockUselessPerks,
                coveredByLegacyFlag = false,
            ),
        )
        add(
            Task(
                id = "custom-block-highlight",
                label = "Custom Block Highlight",
                isPresent = { modLoaded(CBH_ID) && BlockHighlightPresets.available },
                apply = ::applyCustomBlockHighlight,
                coveredByLegacyFlag = false,
            ),
        )
        add(
            Task(
                id = "item-physic-lite",
                label = "ItemPhysicLite",
                isPresent = { modLoaded(ITEM_PHYSIC_ID) && findClass(ITEM_PHYSIC_CONFIG) != null },
                apply = ::disableItemPhysic,
                coveredByLegacyFlag = false,
            ),
        )
        add(
            Task(
                id = "custom-block-highlight-stale-onboarding",
                label = "Custom Block Highlight",
                isPresent = { modLoaded(CBH_ID) && BlockHighlightPresets.available },
                apply = ::repairCustomBlockHighlight,
                coveredByLegacyFlag = false,
            ),
        )
    }

    private fun unbindTask(id: String, matches: (String) -> Boolean) = Task(
        id = "unbind:$id",
        label = "keybinds",
        isPresent = { keyMappings().any { matches(it.name) } },
        apply = { unbindMatching { matches(it.name) } },
        coveredByLegacyFlag = id !in POST_LEGACY_UNBINDS,
    )

    private val LEGACY_TASKS = TICK_TASKS.filter(Task::coveredByLegacyFlag)

    private val pending = TICK_TASKS.toMutableList()

    private val applied = linkedSetOf<String>()

    private val failures = linkedSetOf<String>()
    private var reported = false

    private val classCache = HashMap<String, Class<*>?>()
    private var ticks = 0
    private var done = false

    fun initialize() {
        applied += PolyPlusConfig.appliedDefaults.split(',').filter(String::isNotEmpty)

        eventHandler { _: TickEvent.End ->
            if (!done && ticks++ % TICK_SCAN_INTERVAL == 0) scan()
        }
    }

    private fun scan() {
        migrateLegacyFlag()
        runTasks(pending)
        reportFailures()
        done = pending.isEmpty() && !PolyPlusConfig.defaultSettingsApplied && (reported || failures.isEmpty())
    }

    private fun migrateLegacyFlag() {
        if (!PolyPlusConfig.defaultSettingsApplied) return
        var settled = true
        LEGACY_TASKS.forEach { task ->
            if (task.id in applied) return@forEach
            if (isPresent(task)) applied += task.id
            else if (task in pending) settled = false
        }
        if (!settled) return

        PolyPlusConfig.defaultSettingsApplied = false
        persist()
        logger.info("Migrated legacy default settings flag to {}", applied)
    }

    private fun runTasks(tasks: MutableList<Task>) {
        var changed = false
        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            when {
                task.id in applied -> iterator.remove()
                isPresent(task) -> {
                    if (attempt(task.label, task.apply)) {
                        applied += task.id
                        changed = true
                    }
                    iterator.remove()
                }
                !task.retryable || ++task.attempts > RETRY_SCAN_LIMIT -> iterator.remove()
            }
        }
        if (changed) persist()
    }

    private fun isPresent(task: Task): Boolean =
        runCatching(task.isPresent).onFailure {
            logger.warn("Could not tell whether '{}' applies, assuming not", task.id, it)
        }.getOrDefault(false)

    private fun persist() {
        PolyPlusConfig.appliedDefaults = applied.joinToString(",")
        PolyPlusConfig.save()
    }

    private inline fun attempt(what: String, block: () -> Unit): Boolean =
        runCatching(block).onFailure {
            logger.warn("Could not apply default settings for {}", what, it)
            failures += what
        }.isSuccess

    private fun reportFailures() {
        if (reported || failures.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        //? if >= 26.2 {
        if (minecraft.gui.overlay() != null || minecraft.gui.screen() == null) return
        //?} elif > 1.8.9 {
        /*if (minecraft.overlay != null || minecraft.screen == null) return
        *///?} else
        //if (minecraft.screen == null) return

        reported = true
        runCatching {
            Notifications.error(
                "PolyPlus",
                "Couldn't apply default settings for ${failures.joinToString(", ")}. See the log for details.",
            )
        }.onFailure { logger.error("Could not show the default settings failure notification", it) }
    }

    private fun applyVanillaOptions() {
        val options = Minecraft.getInstance().options ?: return
        //? if > 1.8.9 {
        options.enableVsync().set(false)
        options.framerateLimit().set(UNLIMITED_FRAMERATE)
        options.entityShadows().set(false)
        //?} else {
        /*options.vsync = false
        org.lwjgl.opengl.Display.setVSyncEnabled(false)
        options.fpsLimit = UNLIMITED_FRAMERATE
        options.entityShadows = false
        *///?}
        options.save()
    }

    //? if >= 1.21.11 {
    private fun disableTextureFiltering() {
        val options = Minecraft.getInstance().options ?: return
        if (options.textureFiltering().get() != net.minecraft.client.TextureFilteringMethod.RGSS) return
        options.textureFiltering().set(net.minecraft.client.TextureFilteringMethod.NONE)
        options.save()
        logger.info("Turned off RGSS texture filtering")
    }
    //?}

    //? if > 1.8.9 {
    private fun keyMappings(): List<KeyMapping> =
        Minecraft.getInstance().options?.keyMappings?.asList().orEmpty()
    //?} else {
    /*private fun keyMappings(): List<KeyMapping> =
        Minecraft.getInstance().options?.keyBindings?.asList().orEmpty()

    private val KeyMapping.isUnbound: Boolean get() = keyCode == 0
    *///?}

    private fun unbindMatching(matches: (KeyMapping) -> Boolean) {
        val options = Minecraft.getInstance().options ?: return
        var changed = false
        //? if > 1.8.9 {
        options.keyMappings.forEach { mapping ->
        //?} else {
        /*options.keyBindings.forEach { mapping ->
        *///?}
            if (!matches(mapping) || mapping.isUnbound) return@forEach
            mapping.setKey(InputConstants.UNKNOWN)
            changed = true
            logger.info("Unbound keybind {}", mapping.name)
        }
        if (changed) {
            KeyMapping.resetMapping()
            options.save()
        }
    }

    //? if wwaypoints {
    // wWaypoints registers a create key per label preset and only binds the default preset's out of the box
    private fun unbindWWaypointsCreateKeys() {
        val createKeys = WaypointsClient.getPresetCreateWaypointKeys().map { it.value }
        unbindMatching { it in createKeys && it.isDefault }
    }
    //?}

    private fun applyBetterScreens() {
        setYaclField(BETTER_SCREENS_CONFIG, "preventClosingScreens", true)
    }

    private fun applyConfirmDisconnect() {
        setYaclField(CONFIRM_DISCONNECT_CONFIG, "confirmEnabled", false)
    }

    private fun controlifyConfig(): Any? {
        val mod = findClass(CONTROLIFY_MOD) ?: return null
        val instance = mod.getMethod("instance").invoke(null) ?: return null
        return runCatching { instance.javaClass.getMethod("config").invoke(instance) }.getOrNull()
    }

    private fun controlifyGlobalSettings(): Any? {
        val config = controlifyConfig() ?: return null
        return runCatching { config.javaClass.getMethod("globalSettings").invoke(config) }.getOrNull()
    }

    private fun applyControlifyKeyboardMovement() {
        val config = controlifyConfig() ?: error("Controlify config is unavailable")
        val globalSettings = config.javaClass.getMethod("globalSettings").invoke(config)
            ?: error("Controlify global settings are unavailable")
        globalSettings.javaClass.getField("alwaysKeyboardMovement").setBoolean(globalSettings, true)
        config.javaClass.getMethod("save").invoke(config)
        logger.info("Enabled Controlify keyboard-like movement")
    }

    private fun bobbyConfigPath(): Path =
        FabricLoader.getInstance().configDir.resolve(BOBBY_CONFIG_FILE)

    private fun applyBobbyConfig() {
        val path = bobbyConfigPath()
        var found = false
        val lines = path.readLines().map { line ->
            val match = BOBBY_DYNAMIC_MULTI_WORLD_LINE.matchEntire(line) ?: return@map line
            found = true
            "${match.groupValues[1]}$BOBBY_DYNAMIC_MULTI_WORLD${match.groupValues[2]}true"
        }
        val updated = if (found) lines else lines + "$BOBBY_DYNAMIC_MULTI_WORLD=true"

        path.writeText(updated.joinToString("\n", postfix = "\n"))
        logger.info("Enabled Bobby dynamic multi-world")
    }

    private fun applyCustomBlockHighlight() {
        if (BlockHighlightPresets.markerPath().exists()) return
        BlockHighlightPresets.apply(BlockHighlightPresets.DEFAULT)
        logger.info("Applied the Custom Block Highlight vanilla preset and skipped its first-open presets screen")
    }

    // Onboarding could snapshot CBH before the vanilla task ran (1.8.9 builds the title screen before
    // the first tick) and save the mod's own defaults back. No preset or onboarding edit produces
    // exactly those, so treat them as that bug and redo the vanilla preset once.
    private fun repairCustomBlockHighlight() {
        if (!BlockHighlightPresets.isModDefault()) return
        BlockHighlightPresets.apply(BlockHighlightPresets.DEFAULT)
        PolyPlusConfig.onboardingBlockHighlightConfig = BlockHighlightPresets.currentJson()
        logger.info("Replaced Custom Block Highlight's built-in defaults left by onboarding with the vanilla preset")
    }

    private fun applyModMenuModCount() {
        val config = findClass(MODMENU_CONFIG) ?: error("$MODMENU_CONFIG is missing")
        MODMENU_COUNT_OPTIONS.forEach { name ->
            val option = config.getField(name).get(null)
            option.javaClass.getMethod("setValue", Boolean::class.javaPrimitiveType).invoke(option, false)
        }
        findClass(MODMENU_CONFIG_MANAGER)?.getMethod("save")?.invoke(null)
        findClass(MODMENU_MAIN)?.getMethod("clearModCountCache")?.invoke(null)
        logger.info("Limited the Mod Menu mod count to non-library mods in the mods folder")
    }

    private fun disableIqBlockUselessPerks() {
        val category = findClass(IQ_PHASE_THREE_CONFIG) ?: error("$IQ_PHASE_THREE_CONFIG is missing")
        category.getField("blockUselessPerks").setBoolean(null, false)

        val registry = findClass(RESOURCEFUL_CONFIGURATIONS) ?: error("$RESOURCEFUL_CONFIGURATIONS is missing")
        val configs = registry.getField("INSTANCE").get(null)
        val config = registry.getMethod("getConfig", String::class.java).invoke(configs, IQ_ID)
            ?: error("IQ Addons has not registered its '$IQ_ID' config")
        val configType = findClass(RESOURCEFUL_CONFIG) ?: error("$RESOURCEFUL_CONFIG is missing")
        configType.getMethod("save").invoke(config)
        logger.info("Disabled IQ Addons Block Useless Perks")
    }

    private fun disableItemPhysic() {
        val type = findClass(ITEM_PHYSIC_CONFIG) ?: error("$ITEM_PHYSIC_CONFIG is missing")
        val toggled = type.getField("toggled").get(null)
        toggled.javaClass.getMethod("set", Any::class.java).invoke(toggled, false)

        val managers = findClass(OSL_CONFIG_MANAGER_IMPL)?.getDeclaredField("MANAGERS")
            ?.apply { isAccessible = true }?.get(null) as? Map<*, *> ?: error("$OSL_CONFIG_MANAGER_IMPL is missing")
        val config = managers.values.filterNotNull().firstNotNullOfOrNull { manager ->
            (manager.javaClass.getMethod("getConfigs").invoke(manager) as Collection<*>).firstOrNull(type::isInstance)
        } ?: error("ItemPhysicLite has not registered its config")
        findClass(OSL_CONFIG_MANAGER)!!.getMethod("save", findClass(OSL_CONFIG)).invoke(null, config)
        logger.info("Disabled ItemPhysicLite")
    }

    private fun setYaclField(className: String, fieldName: String, value: Boolean) {
        val type = findClass(className) ?: error("$className is missing")
        //? if > 1.8.9 {
        val handler = type.getField("CONFIG").get(null)
        val instance = handler.javaClass.getMethod("instance").invoke(handler)
        instance.javaClass.getField(fieldName).setBoolean(instance, value)
        handler.javaClass.getMethod("save").invoke(handler)
        //?} else {
        /*val instance = type.getField("INSTANCE").get(null)
        type.getField(fieldName).setBoolean(null, value)
        instance.javaClass.getMethod("save").invoke(instance)
        *///?}
        logger.info("Set {}#{} to {}", className.substringAfterLast('.'), fieldName, value)
    }

    private fun modLoaded(id: String): Boolean = FabricLoader.getInstance().isModLoaded(id)

    private fun findClass(name: String): Class<*>? {
        if (name in classCache) return classCache[name]
        val type = runCatching { Class.forName(name, true, javaClass.classLoader) }.getOrNull()
        classCache[name] = type
        return type
    }
}
