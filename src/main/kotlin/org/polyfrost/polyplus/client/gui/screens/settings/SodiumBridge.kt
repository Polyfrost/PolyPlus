package org.polyfrost.polyplus.client.gui.screens.settings

//? if > 1.8.9 {
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.polyfrost.polyplus.PolyPlusConstants
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.function.Consumer
import kotlin.math.roundToInt

internal class SodiumPageRef(
    val id: String,
    val label: String,
    val icon: String,
    val owner: SettingsOwner,
    val page: Any,
    val external: Consumer<Screen>?,
)

internal class SodiumAction(val key: String, val label: String, val hint: String?, val enabled: Boolean, val run: (Screen?) -> Unit)

internal class SodiumGroup(val title: String?, val rows: List<SettingRow>, val actions: List<SodiumAction>)

internal class SodiumBridge private constructor() : SodiumBackend {
    private val config: Field = sodium("client.config.ConfigManager").getField("CONFIG")

    private val configClass = sodium("client.config.structure.Config")
    private val getModOptions = configClass.getMethod("getModOptions")
    private val applyAllOptions = configClass.getMethod("applyAllOptions")
    private val resetAllOptions = configClass.getMethod("resetAllOptionsFromBindings")
    private val anyOptionChanged = configClass.getMethod("anyOptionChanged")
    private val invalidateRebuild = configClass.getMethod("invalidateGlobalRebuildDependents")

    private val modOptionsClass = sodium("client.config.structure.ModOptions")
    private val modConfigId = modOptionsClass.getMethod("configId")
    private val modName = modOptionsClass.getMethod("name")
    private val modPages = modOptionsClass.getMethod("pages")

    private val pageClass = sodium("client.config.structure.Page")
    private val pageName = pageClass.getMethod("name")
    private val pageGroups = pageClass.getMethod("groups")
    private val optionPageClass = sodium("client.config.structure.OptionPage")
    private val externalPageClass = sodium("client.config.structure.ExternalPage")
    private val externalPageConsumer = externalPageClass.getMethod("currentScreenConsumer")

    private val groupClass = sodium("client.config.structure.OptionGroup")
    private val groupName = groupClass.getMethod("name")
    private val groupOptions = groupClass.getMethod("options")

    private val optionClass = sodium("client.config.structure.Option")
    private val optionId: Field = optionClass.getDeclaredField("id").apply { isAccessible = true }
    private val optionName = optionClass.getMethod("getName")
    private val optionEnabled = optionClass.getMethod("isEnabled")
    private val optionTooltip = optionClass.getMethod("getTooltip")
    private val optionImpact = optionClass.getMethod("getImpact")
    private val optionFlags = optionClass.getMethod("getFlags")
    private val optionChanged = optionClass.getMethod("hasChanged")

    private val statefulClass = sodium("client.config.structure.StatefulOption")
    private val validatedValue = statefulClass.getMethod("getValidatedValue")
    private val appliedValue = statefulClass.getMethod("getAppliedValue")
    private val modifyValue = statefulClass.getMethod("modifyValue", Any::class.java)

    private val booleanClass = sodium("client.config.structure.BooleanOption")

    private val integerClass = sodium("client.config.structure.IntegerOption")
    private val integerValidator = integerClass.getMethod("getSteppedValidator")
    private val integerFormat = integerClass.getMethod("formatValue", Int::class.javaPrimitiveType)
    private val validatorClass = sodium("api.config.option.SteppedValidator")
    private val validatorMin = validatorClass.getMethod("min")
    private val validatorMax = validatorClass.getMethod("max")
    private val validatorStep = validatorClass.getMethod("step")

    private val enumClass = sodium("client.config.structure.EnumOption")
    private val enumType = enumClass.getMethod("getEnumClass")
    private val enumAllowed = enumClass.getMethod("isValueAllowed", Enum::class.java)
    private val enumName = enumClass.getMethod("getElementName", Enum::class.java)

    private val buttonClass = sodium("client.config.structure.ExternalButtonOption")
    private val buttonConsumer = buttonClass.getMethod("getCurrentScreenConsumer")

    private val impactName = sodium("api.config.option.OptionImpact").getMethod("getName")

    private val createScreen = sodium("client.gui.VideoSettingsScreen").getMethod("createScreen", Screen::class.java, optionPageClass)

    private val sodiumOptions = sodium("client.SodiumClientMod").getMethod("options")
    private val optionsReadOnly = sodium("client.gui.SodiumOptions").getMethod("isReadOnly")

    private fun config(): Any? = config.get(null)

    private fun call(method: Method, target: Any?, vararg args: Any?): Any? = method.invoke(target, *args)

    private fun text(component: Any?): String = (component as? Component)?.string.orEmpty()

    override fun usable(): Boolean = config() != null && call(optionsReadOnly, call(sodiumOptions, null)) != true

    override fun pages(): List<SodiumPageRef> {
        val config = config() ?: return emptyList()
        val out = mutableListOf<SodiumPageRef>()
        (call(getModOptions, config) as List<*>).filterNotNull().forEach { mod ->
            val configId = call(modConfigId, mod) as String
            val owner = owner(configId, call(modName, mod) as? String)
            (call(modPages, mod) as List<*>).filterNotNull().forEachIndexed { index, page ->
                val name = call(pageName, page) as? Component
                val external = if (externalPageClass.isInstance(page)) {
                    @Suppress("UNCHECKED_CAST")
                    call(externalPageConsumer, page) as? Consumer<Screen>
                } else {
                    null
                }
                if (external == null && !optionPageClass.isInstance(page)) return@forEachIndexed
                out += SodiumPageRef("sodium:$configId/$index", text(name).ifBlank { owner.name }, iconFor(name, external != null), owner, page, external)
            }
        }
        return out
    }

    override fun groups(page: Any, parent: () -> Screen?): List<SodiumGroup> =
        (call(pageGroups, page) as List<*>).filterNotNull().map { group ->
            val rows = mutableListOf<SettingRow>()
            val actions = mutableListOf<SodiumAction>()
            (call(groupOptions, group) as List<*>).filterNotNull().forEach { option ->
                val row = runCatching { row(option) }.getOrNull()
                if (row != null) {
                    rows += row
                } else {
                    actions += action(option, page, parent)
                }
            }
            SodiumGroup(text(call(groupName, group)).ifBlank { null }, rows, actions)
        }

    override fun dirty(): Boolean = config()?.let { call(anyOptionChanged, it) as Boolean } ?: false

    override fun begin() {
        val config = config() ?: return
        if (!dirty()) call(resetAllOptions, config)
        call(invalidateRebuild, config)
    }

    override fun apply() {
        val config = config() ?: return
        if (!dirty()) return
        if (pendingRestart(config)) restartPending = true
        call(applyAllOptions, config)
    }

    override fun undo() {
        config()?.let { call(resetAllOptions, it) }
    }

    fun openSodium(page: Any, parent: Screen?) {
        val screen = call(createScreen, null, parent, page) as? Screen ?: return
        SettingsRoutes.pass(screen)
    }

    private fun pendingRestart(config: Any): Boolean = (call(getModOptions, config) as List<*>).filterNotNull().any { mod ->
        (call(modPages, mod) as List<*>).filterNotNull().any { page ->
            (call(pageGroups, page) as List<*>).filterNotNull().any { group ->
                (call(groupOptions, group) as List<*>).filterNotNull().any { option ->
                    call(optionChanged, option) as Boolean && restarts(option)
                }
            }
        }
    }

    private fun restarts(option: Any): Boolean = (call(optionFlags, option) as? Set<*>).orEmpty().any { it.toString() == RESTART_FLAG }

    private fun row(option: Any): SettingRow? {
        val key = optionId.get(option).toString()
        val label = text(call(optionName, option))
        val enabled = call(optionEnabled, option) as Boolean
        val hint = hint(option)
        val kind = when {
            booleanClass.isInstance(option) -> SettingKind.Toggle(call(validatedValue, option) as Boolean) { modify(option, it) }
            integerClass.isInstance(option) -> slider(option)
            enumClass.isInstance(option) -> choice(option)
            else -> return null
        } ?: return null
        return SettingRow(key, label, hint, kind, enabled)
    }

    private fun slider(option: Any): SettingKind? {
        val validator = call(integerValidator, option) ?: return null
        val min = call(validatorMin, validator) as Int
        val max = call(validatorMax, validator) as Int
        val step = (call(validatorStep, validator) as Int).coerceAtLeast(1)
        val current = call(validatedValue, option) as Int
        val format = { value: Int -> text(call(integerFormat, option, value)) }
        if (max <= min) return SettingKind.Choice(listOf(format(current)), 0) {}
        val snap = { value: Float -> (min + ((value - min) / step).roundToInt() * step).coerceIn(min, max) }
        return SettingKind.Range(
            current.toFloat(),
            min.toFloat()..max.toFloat(),
            step.toFloat(),
            true,
            { format(snap(it)) },
        ) { modify(option, snap(it)) }
    }

    private fun choice(option: Any): SettingKind? {
        val type = call(enumType, option) as? Class<*> ?: return null
        val current = call(validatedValue, option) as Enum<*>
        val allowed = type.enumConstants.orEmpty().filterIsInstance<Enum<*>>().filter { call(enumAllowed, option, it) as Boolean }
        val values = if (current in allowed) allowed else listOf(current) + allowed
        if (values.isEmpty()) return null
        return SettingKind.Choice(values.map { text(call(enumName, option, it)).ifBlank { it.name } }, values.indexOf(current)) { index ->
            values.getOrNull(index)?.let { modify(option, it) }
        }
    }

    private fun action(option: Any, page: Any, parent: () -> Screen?): SodiumAction {
        val key = runCatching { optionId.get(option).toString() }.getOrDefault(option.toString())
        val label = runCatching { text(call(optionName, option)) }.getOrDefault(key)
        val enabled = runCatching { call(optionEnabled, option) as Boolean }.getOrDefault(true)
        val hint = runCatching { hint(option) }.getOrNull()
        if (buttonClass.isInstance(option)) {
            @Suppress("UNCHECKED_CAST")
            val consumer = call(buttonConsumer, option) as? Consumer<Screen>
            if (consumer != null) return SodiumAction(key, label, hint, enabled) { host -> host?.let(consumer::accept) }
        }
        return SodiumAction(key, label, hint ?: "Opens in Sodium's own settings screen.", enabled) { host -> openSodium(page, host) }
    }

    private fun modify(option: Any, value: Any) {
        val applied = call(appliedValue, option)
        call(modifyValue, option, if (applied == value) applied else value)
    }

    private fun hint(option: Any): String? {
        val parts = mutableListOf<String>()
        text(call(optionTooltip, option)).takeIf { it.isNotBlank() }?.let { parts += it }
        call(optionImpact, option)?.let { impact ->
            parts += Component.translatable("sodium.options.performance_impact_string", call(impactName, impact) as Component).string
        }
        if (restarts(option)) parts += "Needs a game restart to take effect."
        return parts.joinToString("\n\n").ifBlank { null }
    }

    private fun owner(configId: String, name: String?): SettingsOwner =
        if (FabricLoader.getInstance().isModLoaded(configId)) SettingsOwner.mod(configId) else SettingsOwner(name ?: configId, null, tinted = false)

    private fun iconFor(name: Component?, external: Boolean): String {
        val key = (name?.contents as? TranslatableContents)?.key.orEmpty() + " " + name?.string.orEmpty().lowercase()
        return when {
            "general" in key -> "hud"
            "quality" in key -> "sun"
            "performance" in key -> "lightning-01"
            "advanced" in key -> "cog"
            "shader" in key -> "paintbrush"
            external -> "external-link"
            else -> "layers"
        }
    }

    companion object {
        private const val SODIUM_ID = "sodium"
        private const val RESTART_FLAG = "sodium:builtin_option_flag.requires_game_restart"
        private val logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/sodium-settings")

        var restartPending = false
            internal set

        private var failed = false

        private val instance: SodiumBackend? by lazy {
            if (!FabricLoader.getInstance().isModLoaded(SODIUM_ID)) return@lazy null
            runCatching { if (SodiumLegacyBridge.supported() && !modern()) SodiumLegacyBridge() else SodiumBridge() }.onFailure { fail(it) }.getOrNull()
        }

        fun get(): SodiumBackend? = if (failed) null else instance

        fun <T> guard(fallback: T, block: (SodiumBackend) -> T): T {
            val bridge = get() ?: return fallback
            return try {
                block(bridge)
            } catch (t: Throwable) {
                fail(t)
                fallback
            }
        }

        private fun fail(t: Throwable) {
            if (failed) return
            failed = true
            logger.warn("Could not read Sodium's options, video settings will open Sodium's own screen", t)
        }

        private fun modern(): Boolean =
            runCatching { Class.forName("net.caffeinemc.mods.sodium.client.config.structure.Config", false, SodiumBridge::class.java.classLoader) }.isSuccess

        private fun sodium(name: String): Class<*> = Class.forName("net.caffeinemc.mods.sodium.$name")
    }
}
//?}
