package org.polyfrost.polyplus.client.features

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
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
    )

    private const val ANIMATIUM_CONFIG = "org.visuals.legacy.animatium.config.AnimatiumConfig"
    private const val ANIMATIUM_MOD = "org.visuals.legacy.animatium.Animatium"

    private val ANIMATIUM_STATE = listOf(
        "org.visuals.legacy.animatium.util.config.GeneralConfigUtil",
        "org.visuals.legacy.animatium.util.config.ConfigUtil",
    )
    private val ANIMATIUM_VERSION = listOf(
        "org.visuals.legacy.animatium.util.config.PresetVersion",
        "org.visuals.legacy.animatium.util.config.Version",
    )

    private val ANIMATIUM_PRESET = listOf("VANILLA", "MODERN")

    private const val ANIMATIUM_ID = "animatium"
    private const val BETTER_SCREENS_ID = "betterscreens"
    private const val CONFIRM_DISCONNECT_ID = "confirmdisconnect"
    private const val CONTROLIFY_ID = "controlify"
    private const val BOBBY_ID = "bobby"

    private const val BOBBY_CONFIG_FILE = "bobby.conf"
    private const val BOBBY_DYNAMIC_MULTI_WORLD = "dynamic-multi-world"

    private val BOBBY_DYNAMIC_MULTI_WORLD_LINE =
        Regex("""^(\s*)"?$BOBBY_DYNAMIC_MULTI_WORLD"?\s*([=:])\s*.*$""")

    private class AnimatiumOption(vararg val names: String, val value: Any)

    private val ANIMATIUM_OVERRIDES = mapOf(
        "items" to listOf(
            AnimatiumOption("itemPositions", value = true),
            AnimatiumOption("itemPositionsInThirdPerson", value = true),
            AnimatiumOption("itemUsageSwinging", value = true),
            AnimatiumOption("disableSwingOnUse", value = false),
            AnimatiumOption("itemPickupPosition", value = true),
            AnimatiumOption("fishingRodVersion", value = "V1_7"),
        ),
        "other" to listOf(
            AnimatiumOption("thirdPersonSwordBlockingPosition", value = true),
            AnimatiumOption("damageTintArmor", "entityArmorHurtTint", value = true),
        ),
    )

    private val ANIMATIUM_FIXUPS = mapOf(
        "items" to listOf(
            AnimatiumOption("disableSwingOnUse", value = false),
        ),
    )

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

    private val INIT_TASKS = listOf(
        Task(
            id = "animatium-onboarding",
            label = "Animatium onboarding",
            isPresent = { modLoaded(ANIMATIUM_ID) && findFirstClass(ANIMATIUM_STATE) != null },
            apply = ::markAnimatiumOnboardingSeen,
        ),
    )

    private val TICK_TASKS = buildList {
        add(Task("vanilla-options", "Minecraft options", { true }, ::applyVanillaOptions))
        UNBIND_ALL_NAMESPACES.forEach { namespace ->
            add(unbindTask(namespace) { key -> namespace in key.split('.') })
        }
        UNBIND_KEYS.forEach { key -> add(unbindTask(key) { it == key }) }
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
                id = "animatium-config",
                label = "Animatium",
                isPresent = { modLoaded(ANIMATIUM_ID) && findClass(ANIMATIUM_CONFIG) != null },
                apply = ::applyAnimatiumConfig,
            ),
        )
        add(
            Task(
                id = "animatium-swing-on-use",
                label = "Animatium",
                isPresent = { modLoaded(ANIMATIUM_ID) && findClass(ANIMATIUM_CONFIG) != null },
                apply = ::applyAnimatiumFixups,
                coveredByLegacyFlag = false,
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
                id = "animatium-packs",
                label = "Animatium resource packs",
                isPresent = { modLoaded(ANIMATIUM_ID) && findClass(ANIMATIUM_CONFIG) != null },
                apply = ::disableAnimatiumResourcePacks,
            ),
        )
    }

    private fun unbindTask(id: String, matches: (String) -> Boolean) = Task(
        id = "unbind:$id",
        label = "keybinds",
        isPresent = { keyMappings().any { matches(it.name) } },
        apply = { unbindMatching(matches) },
    )

    private val LEGACY_TASKS = (INIT_TASKS + TICK_TASKS).filter(Task::coveredByLegacyFlag)

    private val pending = TICK_TASKS.toMutableList()

    private val applied = linkedSetOf<String>()

    private val failures = linkedSetOf<String>()
    private var reported = false

    private val classCache = HashMap<String, Class<*>?>()
    private var ticks = 0
    private var done = false

    fun initialize() {
        applied += PolyPlusConfig.appliedDefaults.split(',').filter(String::isNotEmpty)

        if (!PolyPlusConfig.defaultSettingsApplied) runTasks(INIT_TASKS.toMutableList())

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
                    attempt(task.label, task.apply)
                    applied += task.id
                    changed = true
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

    private inline fun attempt(what: String, block: () -> Unit) {
        runCatching(block).onFailure {
            logger.warn("Could not apply default settings for {}", what, it)
            failures += what
        }
    }

    private fun reportFailures() {
        if (reported || failures.isEmpty()) return
        val minecraft = Minecraft.getInstance()
        //? if >= 26.2 {
        if (minecraft.gui.overlay() != null || minecraft.gui.screen() == null) return
        //?} else
        //if (minecraft.overlay != null || minecraft.screen == null) return

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
        options.enableVsync().set(false)
        options.framerateLimit().set(UNLIMITED_FRAMERATE)
        options.entityShadows().set(false)
        options.save()
    }

    private fun keyMappings(): List<KeyMapping> =
        Minecraft.getInstance().options?.keyMappings?.asList().orEmpty()

    private fun unbindMatching(matches: (String) -> Boolean) {
        val options = Minecraft.getInstance().options ?: return
        var changed = false
        options.keyMappings.forEach { mapping ->
            if (!matches(mapping.name) || mapping.isUnbound) return@forEach
            mapping.setKey(InputConstants.UNKNOWN)
            changed = true
            logger.info("Unbound keybind {}", mapping.name)
        }
        if (changed) {
            KeyMapping.resetMapping()
            options.save()
        }
    }

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

    private fun setYaclField(className: String, fieldName: String, value: Boolean) {
        val type = findClass(className) ?: error("$className is missing")
        val handler = type.getField("CONFIG").get(null)
        val instance = handler.javaClass.getMethod("instance").invoke(handler)
        instance.javaClass.getField(fieldName).setBoolean(instance, value)
        handler.javaClass.getMethod("save").invoke(handler)
        logger.info("Set {}#{} to {}", className.substringAfterLast('.'), fieldName, value)
    }

    private fun markAnimatiumOnboardingSeen() {
        val configUtil = findFirstClass(ANIMATIUM_STATE) ?: error("Animatium has none of $ANIMATIUM_STATE")
        runCatching { configUtil.getMethod("load").invoke(null) }
        configUtil.getMethod("put", String::class.java, Boolean::class.javaPrimitiveType)
            .invoke(null, "onboarding", false)
        logger.info("Marked Animatium onboarding as already viewed")
    }

    private fun applyAnimatiumConfig() {
        val configClass = findClass(ANIMATIUM_CONFIG) ?: error("$ANIMATIUM_CONFIG is missing")
        val instance = configClass.getMethod("instance").invoke(null)
        applyAnimatiumPreset(configClass, instance)
        applyAnimatiumOverrides(instance, ANIMATIUM_OVERRIDES)

        configClass.getMethod("save").invoke(null)
        reloadAnimatium()
        logger.info("Applied the Animatium modern-animations preset with PolyPlus overrides")
    }

    private fun applyAnimatiumFixups() {
        val configClass = findClass(ANIMATIUM_CONFIG) ?: error("$ANIMATIUM_CONFIG is missing")
        val instance = configClass.getMethod("instance").invoke(null)
        applyAnimatiumOverrides(instance, ANIMATIUM_FIXUPS)

        configClass.getMethod("save").invoke(null)
        reloadAnimatium()
        logger.info("Corrected outdated Animatium PolyPlus overrides")
    }

    private fun applyAnimatiumOverrides(instance: Any, overrides: Map<String, List<AnimatiumOption>>) {
        overrides.forEach { (name, options) ->
            val category = runCatching { instance.javaClass.getField(name).get(instance) }.getOrNull()
            if (category == null) {
                logger.warn("Animatium has no config category '{}', skipping", name)
                return@forEach
            }
            options.forEach { option -> setAnimatiumField(category, option) }
        }
    }

    private fun applyAnimatiumPreset(configClass: Class<*>, config: Any) {
        val versionClass = findFirstClass(ANIMATIUM_VERSION)
            ?: error("Animatium has none of $ANIMATIUM_VERSION")
        val preset = ANIMATIUM_PRESET.firstNotNullOfOrNull { name ->
            runCatching { enumConstant(versionClass, name) }.getOrNull()
        } ?: error("Animatium has none of the $ANIMATIUM_PRESET presets")

        val legacyApply = runCatching { versionClass.getMethod("apply", configClass) }.getOrNull()
        if (legacyApply != null) legacyApply.invoke(preset, config)
        else versionClass.getMethod("apply").invoke(preset)
    }

    private fun reloadAnimatium() {
        val mod = findClass(ANIMATIUM_MOD) ?: return
        val reload = runCatching { mod.getMethod("reload") }.getOrNull() ?: return
        runCatching { reload.invoke(null) }
            .onFailure { logger.warn("Could not reload Animatium after applying defaults", it) }
    }

    private fun setAnimatiumField(category: Any, option: AnimatiumOption) {
        val field = option.names.firstNotNullOfOrNull { name ->
            runCatching { category.javaClass.getField(name) }.getOrNull()
        }
        if (field == null) {
            logger.warn("Animatium option '{}' is not present in this version, skipping", option.names.first())
            return
        }

        attempt("Animatium") {
            val value = option.value
            when {
                value is Boolean -> field.setBoolean(category, value)
                field.type.isEnum -> field.set(category, enumConstant(field.type, value as String))
                else -> error("Unsupported option type for '${field.name}'")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumConstant(type: Class<*>, name: String): Any =
        java.lang.Enum.valueOf(type as Class<out Enum<*>>, name)

    private fun disableAnimatiumResourcePacks() {
        val minecraft = Minecraft.getInstance()
        val options = minecraft.options ?: return
        val removed = options.resourcePacks.removeAll(::isAnimatiumPack) or
            options.incompatibleResourcePacks.removeAll(::isAnimatiumPack)

        val repository = minecraft.resourcePackRepository
        val selected = repository.selectedIds
        val kept = selected.filterNot(::isAnimatiumPack)
        val wasSelected = kept.size != selected.size
        if (wasSelected) repository.setSelected(kept)

        if (removed || wasSelected) {
            options.save()
            logger.info("Disabled Animatium resource packs")
        }
        if (wasSelected) minecraft.reloadResourcePacks()
    }

    private fun isAnimatiumPack(id: String): Boolean =
        id.substringBefore(':').substringBefore('/').equals(ANIMATIUM_ID, ignoreCase = true)

    private fun modLoaded(id: String): Boolean = FabricLoader.getInstance().isModLoaded(id)

    private fun findClass(name: String): Class<*>? {
        if (name in classCache) return classCache[name]
        val type = runCatching { Class.forName(name, true, javaClass.classLoader) }.getOrNull()
        classCache[name] = type
        return type
    }

    private fun findFirstClass(names: List<String>): Class<*>? = names.firstNotNullOfOrNull(::findClass)
}
