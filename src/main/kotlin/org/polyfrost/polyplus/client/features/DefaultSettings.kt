package org.polyfrost.polyplus.client.features

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig

object DefaultSettings {
    private val logger = LogManager.getLogger("PolyPlus/DefaultSettings")

    private const val UNLIMITED_FRAMERATE = 260

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

    private const val ANIMATIUM_PRESET = "MODERN"

    private class AnimatiumOption(vararg val names: String, val value: Any)

    private val ANIMATIUM_OVERRIDES = mapOf(
        "items" to listOf(
            AnimatiumOption("itemPositions", value = true),
            AnimatiumOption("itemPositionsInThirdPerson", value = true),
            AnimatiumOption("itemUsageSwinging", value = true),
            AnimatiumOption("disableSwingOnUse", value = true),
            AnimatiumOption("itemPickupPosition", value = true),
            AnimatiumOption("fishingRodVersion", value = "V1_7"),
        ),
        "other" to listOf(
            AnimatiumOption("thirdPersonSwordBlockingPosition", value = true),
            AnimatiumOption("damageTintArmor", "entityArmorHurtTint", value = true),
        ),
    )
    private const val BETTER_SCREENS_CONFIG = "dev.microcontrollers.betterscreens.config.BetterScreensConfig"
    private const val CONFIRM_DISCONNECT_CONFIG = "dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig"

    private class Task(
        val id: String,
        val label: String,
        val isPresent: () -> Boolean,
        val apply: () -> Unit,
    )

    private val INIT_TASKS = listOf(
        Task(
            id = "animatium-onboarding",
            label = "Animatium onboarding",
            isPresent = { findFirstClass(ANIMATIUM_STATE) != null },
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
                isPresent = { findClass(BETTER_SCREENS_CONFIG) != null },
                apply = ::applyBetterScreens,
            ),
        )
        add(
            Task(
                id = "confirm-disconnect",
                label = "Confirm Disconnect",
                isPresent = { findClass(CONFIRM_DISCONNECT_CONFIG) != null },
                apply = ::applyConfirmDisconnect,
            ),
        )
        add(
            Task(
                id = "animatium-config",
                label = "Animatium",
                isPresent = { findClass(ANIMATIUM_CONFIG) != null },
                apply = ::applyAnimatiumConfig,
            ),
        )
        add(
            Task(
                id = "animatium-packs",
                label = "Animatium resource packs",
                isPresent = { findClass(ANIMATIUM_CONFIG) != null },
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

    private val applied = linkedSetOf<String>()

    private val failures = linkedSetOf<String>()
    private var reported = false

    fun initialize() {
        applied += PolyPlusConfig.appliedDefaults.split(',').filter(String::isNotEmpty)

        if (!PolyPlusConfig.defaultSettingsApplied) runTasks(INIT_TASKS)

        eventHandler { _: TickEvent.End ->
            migrateLegacyFlag()
            runTasks(TICK_TASKS)
            reportFailures()
        }
    }

    private fun migrateLegacyFlag() {
        if (!PolyPlusConfig.defaultSettingsApplied) return
        (INIT_TASKS + TICK_TASKS).forEach { task ->
            if (isPresent(task)) applied += task.id
        }
        PolyPlusConfig.defaultSettingsApplied = false
        persist()
        logger.info("Migrated legacy default settings flag to {}", applied)
    }

    private fun runTasks(tasks: List<Task>) {
        var changed = false
        tasks.forEach { task ->
            if (task.id in applied || !isPresent(task)) return@forEach
            attempt(task.label, task.apply)
            applied += task.id
            changed = true
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
        /*if (minecraft.gui.overlay() != null || minecraft.gui.screen() == null) return
        *///?} else
        if (minecraft.overlay != null || minecraft.screen == null) return

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

        ANIMATIUM_OVERRIDES.forEach { (name, overrides) ->
            val category = runCatching { instance.javaClass.getField(name).get(instance) }.getOrNull()
            if (category == null) {
                logger.warn("Animatium has no config category '{}', skipping", name)
                return@forEach
            }
            overrides.forEach { option -> setAnimatiumField(category, option) }
        }

        configClass.getMethod("save").invoke(null)
        reloadAnimatium()
        logger.info("Applied the Animatium {} preset with PolyPlus overrides", ANIMATIUM_PRESET)
    }

    private fun applyAnimatiumPreset(configClass: Class<*>, config: Any) {
        val versionClass = findFirstClass(ANIMATIUM_VERSION)
            ?: error("Animatium has none of $ANIMATIUM_VERSION")
        val preset = enumConstant(versionClass, ANIMATIUM_PRESET)

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
        id.substringBefore(':').equals("animatium", ignoreCase = true)

    private fun findClass(name: String): Class<*>? =
        runCatching { Class.forName(name, true, javaClass.classLoader) }.getOrNull()

    private fun findFirstClass(names: List<String>): Class<*>? = names.firstNotNullOfOrNull(::findClass)
}
