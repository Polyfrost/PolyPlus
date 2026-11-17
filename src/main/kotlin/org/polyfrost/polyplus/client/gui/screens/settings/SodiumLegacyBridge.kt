package org.polyfrost.polyplus.client.gui.screens.settings

//? if > 1.8.9 {
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.function.Consumer
import java.util.function.IntSupplier
import kotlin.math.roundToInt
//? if >= 1.21.1 && < 1.21.11 {
/*import net.minecraft.client.Minecraft
*///?}

internal interface SodiumBackend {
    fun usable(): Boolean
    fun pages(): List<SodiumPageRef>
    fun groups(page: Any, parent: () -> Screen?): List<SodiumGroup>
    fun dirty(): Boolean
    fun begin()
    fun apply()
    fun undo()
}

internal class SodiumLegacyBridge : SodiumBackend {
    private val pageFactories: List<Method> = sodium("client.gui.SodiumGameOptionPages").let { owner -> PAGES.map { owner.getMethod(it) } }

    private val pageClass = sodium("client.gui.options.OptionPage")
    private val pageName = pageClass.getMethod("getName")
    private val pageGroups = pageClass.getMethod("getGroups")
    private val pageOptions = pageClass.getMethod("getOptions")

    private val groupOptions = sodium("client.gui.options.OptionGroup").getMethod("getOptions")

    private val optionClass = sodium("client.gui.options.Option")
    private val optionName = optionClass.getMethod("getName")
    private val optionTooltip = optionClass.getMethod("getTooltip")
    private val optionImpact = optionClass.getMethod("getImpact")
    private val optionControl = optionClass.getMethod("getControl")
    private val optionValue = optionClass.getMethod("getValue")
    private val optionSetValue = optionClass.getMethod("setValue", Any::class.java)
    private val optionReset = optionClass.getMethod("reset")
    private val optionStorage = optionClass.getMethod("getStorage")
    private val optionAvailable = optionClass.getMethod("isAvailable")
    private val optionChanged = optionClass.getMethod("hasChanged")
    private val optionApply = optionClass.getMethod("applyChanges")
    private val optionFlags = optionClass.getMethod("getFlags")

    private val storageSave = sodium("client.gui.options.storage.OptionStorage").getMethod("save")

    private val impactName = sodium("client.gui.options.OptionImpact").getMethod("getLocalizedName")

    private val tickBoxClass = sodium("client.gui.options.control.TickBoxControl")

    private val sliderClass = sodium("client.gui.options.control.SliderControl")
    private val sliderFields: List<Field> = SLIDER_FIELDS.map { declared(sliderClass, it) }
    private val dynamicSliderClass: Class<*>? = runCatching { sodium("client.gui.options.control.DynamicMaxSliderControl") }.getOrNull()
    private val dynamicSliderFields: List<Field>? = dynamicSliderClass?.let { owner -> SLIDER_FIELDS.map { declared(owner, it) } }
    private val formatterFormat = sodium("client.gui.options.control.ControlValueFormatter").getMethod("format", Int::class.javaPrimitiveType)

    private val cyclingClass = sodium("client.gui.options.control.CyclingControl")
    private val cyclingValues: Field = declared(cyclingClass, "allowedValues")
    private val cyclingNames: Field = declared(cyclingClass, "names")

    private val createScreen = sodium("client.gui.SodiumOptionsGUI").getMethod("createScreen", Screen::class.java)

    private val sodiumOptions = sodium("client.SodiumClientMod").getMethod("options")
    private val optionsReadOnly = sodium("client.gui.SodiumGameOptions").getMethod("isReadOnly")

    private val guiPages: Pair<Constructor<*>, Field>? = runCatching {
        val gui = sodium("client.gui.SodiumOptionsGUI")
        gui.getDeclaredConstructor(Screen::class.java).apply { isAccessible = true } to declared(gui, "pages")
    }.getOrNull()

    private val donationSeen: Pair<Field, Field>? = runCatching {
        sodium("client.gui.SodiumGameOptions").getField("notifications") to sodium("client.gui.SodiumGameOptions\$NotificationSettings").getField("hasSeenDonationPrompt")
    }.getOrNull()

    private val shaderPackScreen: Constructor<*>? by lazy {
        if (!FabricLoader.getInstance().isModLoaded(IRIS_ID)) return@lazy null
        runCatching { Class.forName(IRIS_SCREEN).getConstructor(Screen::class.java) }.getOrNull()
    }

    private var session: List<Any>? = null

    private fun call(method: Method, target: Any?, vararg args: Any?): Any? = method.invoke(target, *args)

    private fun text(component: Any?): String = (component as? Component)?.string.orEmpty()

    private fun session(): List<Any> = session ?: load().also { session = it }

    private fun load(): List<Any> =
        runCatching { constructed() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: pageFactories.mapNotNull { call(it, null) }

    private fun constructed(): List<Any>? {
        val (constructor, pages) = guiPages ?: return null
        val (notifications, seen) = donationSeen ?: return null
        val settings = notifications.get(call(sodiumOptions, null)) ?: return null
        val before = seen.getBoolean(settings)
        seen.setBoolean(settings, true)
        try {
            val screen = constructor.newInstance(null as Screen?)
            return (pages.get(screen) as? List<*>)?.filterNotNull()?.toList()
        } finally {
            seen.setBoolean(settings, before)
        }
    }

    private fun options(): List<Any> = session().flatMap { (call(pageOptions, it) as List<*>).filterNotNull() }

    override fun usable(): Boolean = call(optionsReadOnly, call(sodiumOptions, null)) != true && session().isNotEmpty()

    override fun pages(): List<SodiumPageRef> {
        val owner = SettingsOwner.mod(SODIUM_ID)
        val shaders = shaderPacks()
        return session().mapIndexedNotNull { index, page ->
            if ((call(pageGroups, page) as List<*>).isEmpty()) return@mapIndexedNotNull null
            val name = call(pageName, page) as? Component
            if (shaders != null && name?.string == shaders.label) return@mapIndexedNotNull null
            val id = PAGE_PREFIX + index
            SodiumPageRef(id, text(name).ifBlank { owner.name }, iconFor(name), owner, id, null)
        } + listOfNotNull(shaders)
    }

    private fun iconFor(name: Component?): String {
        val key = (name?.contents as? TranslatableContents)?.key.orEmpty() + " " + name?.string.orEmpty().lowercase()
        return ICONS.entries.firstOrNull { it.key in key }?.value ?: "layers"
    }

    private fun shaderPacks(): SodiumPageRef? {
        val screen = shaderPackScreen ?: return null
        val open = Consumer<Screen> { parent -> (screen.newInstance(parent) as? Screen)?.let(SettingsRoutes::pass) }
        return SodiumPageRef(IRIS_PAGE, Component.translatable(IRIS_TITLE_KEY).string, "paintbrush", SettingsOwner.mod(IRIS_ID), IRIS_PAGE, open)
    }

    override fun groups(page: Any, parent: () -> Screen?): List<SodiumGroup> {
        val id = page as? String ?: return emptyList()
        val target = id.removePrefix(PAGE_PREFIX).toIntOrNull()?.let { session().getOrNull(it) } ?: return emptyList()
        return (call(pageGroups, target) as List<*>).filterNotNull().mapIndexed { groupIndex, group ->
            val rows = mutableListOf<SettingRow>()
            val actions = mutableListOf<SodiumAction>()
            (call(groupOptions, group) as List<*>).filterNotNull().forEachIndexed { optionIndex, option ->
                val key = "$id/$groupIndex/$optionIndex"
                val row = runCatching { row(key, option) }.getOrNull()
                if (row != null) rows += row else actions += action(key, option)
            }
            SodiumGroup(null, rows, actions)
        }
    }

    override fun dirty(): Boolean = session?.let { _ -> options().any { call(optionChanged, it) as Boolean } } ?: false

    override fun begin() {
        if (session != null && !dirty()) undo()
    }

    override fun apply() {
        val changed = options().filter { call(optionChanged, it) as Boolean }
        if (changed.isEmpty()) return
        val flags = mutableSetOf<String>()
        val storages = LinkedHashSet<Any>()
        changed.forEach { option ->
            call(optionApply, option)
            (call(optionFlags, option) as? Collection<*>).orEmpty().filterNotNull().forEach { flags += it.toString() }
            call(optionStorage, option)?.let { storages += it }
        }
        sideEffects(flags)
        storages.forEach { call(storageSave, it) }
    }

    override fun undo() {
        options().forEach { call(optionReset, it) }
    }

    //? if >= 1.21.1 && < 1.21.11 {
    /*private fun sideEffects(flags: Set<String>) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level != null) {
            if (RENDERER_RELOAD in flags) {
                minecraft.levelRenderer.allChanged()
            } else if (RENDERER_UPDATE in flags) {
                minecraft.levelRenderer.needsUpdate()
            }
        }
        if (ASSET_RELOAD in flags) {
            minecraft.updateMaxMipLevel(minecraft.options.mipmapLevels().get())
            minecraft.delayTextureReload()
        }
        if (VIDEOMODE_RELOAD in flags) minecraft.window.changeFullscreenVideoMode()
        if (GAME_RESTART in flags) SodiumBridge.restartPending = true
    }
    *///?} else {
    private fun sideEffects(flags: Set<String>) {
        if (GAME_RESTART in flags) SodiumBridge.restartPending = true
    }
    //?}

    private fun row(key: String, option: Any): SettingRow? {
        val control = call(optionControl, option) ?: return null
        val dynamic = dynamicSliderFields
        val kind = when {
            tickBoxClass.isInstance(control) -> SettingKind.Toggle(call(optionValue, option) as Boolean) { set(option, it) }
            sliderClass.isInstance(control) -> sliderFields.let { (min, max, interval, mode) ->
                slider(option, min.getInt(control), max.getInt(control), interval.getInt(control), mode.get(control))
            }
            dynamic != null && dynamicSliderClass?.isInstance(control) == true -> dynamic.let { (min, max, interval, mode) ->
                slider(option, min.getInt(control), (max.get(control) as IntSupplier).asInt, interval.getInt(control), mode.get(control))
            }
            cyclingClass.isInstance(control) -> choice(option, control)
            else -> null
        } ?: return null
        return SettingRow(key, text(call(optionName, option)), hint(option), kind, call(optionAvailable, option) as Boolean)
    }

    private fun slider(option: Any, min: Int, max: Int, interval: Int, formatter: Any?): SettingKind {
        val step = interval.coerceAtLeast(1)
        val current = call(optionValue, option) as Int
        val format = { value: Int -> formatter?.let { text(call(formatterFormat, it, value)) }?.ifBlank { null } ?: value.toString() }
        if (max <= min) return SettingKind.Choice(listOf(format(current)), 0) {}
        val snap = { value: Float -> (min + ((value - min) / step).roundToInt() * step).coerceIn(min, max) }
        return SettingKind.Range(
            current.toFloat(),
            min.toFloat()..max.toFloat(),
            step.toFloat(),
            true,
            { format(snap(it)) },
        ) { set(option, snap(it)) }
    }

    private fun choice(option: Any, control: Any): SettingKind? {
        val allowed = (cyclingValues.get(control) as? Array<*>)?.filterIsInstance<Enum<*>>().orEmpty()
        if (allowed.isEmpty()) return null
        val names = (cyclingNames.get(control) as? Array<*>)?.toList().orEmpty()
        val current = call(optionValue, option) as? Enum<*> ?: return null
        val values = if (current in allowed) allowed else listOf(current) + allowed
        val labels = values.map { value -> text(names.getOrNull(value.ordinal)).ifBlank { value.name } }
        return SettingKind.Choice(labels, values.indexOf(current)) { index ->
            values.getOrNull(index)?.let { set(option, it) }
        }
    }

    private fun action(key: String, option: Any): SodiumAction {
        val label = runCatching { text(call(optionName, option)) }.getOrDefault(key)
        val enabled = runCatching { call(optionAvailable, option) as Boolean }.getOrDefault(true)
        val hint = runCatching { hint(option) }.getOrNull()
        return SodiumAction(key, label, hint ?: "Opens in Sodium's own settings screen.", enabled) { host -> openSodium(host) }
    }

    private fun openSodium(parent: Screen?) {
        val screen = call(createScreen, null, parent) as? Screen ?: return
        SettingsRoutes.pass(screen)
    }

    private fun set(option: Any, value: Any) {
        call(optionSetValue, option, value)
    }

    private fun restarts(option: Any): Boolean = (call(optionFlags, option) as? Collection<*>).orEmpty().any { it.toString() == GAME_RESTART }

    private fun hint(option: Any): String? {
        val parts = mutableListOf<String>()
        text(call(optionTooltip, option)).takeIf { it.isNotBlank() }?.let { parts += it }
        call(optionImpact, option)?.let { impact ->
            parts += Component.translatable(IMPACT_KEY, call(impactName, impact) as Component).string
        }
        if (restarts(option)) parts += "Needs a game restart to take effect."
        return parts.joinToString("\n\n").ifBlank { null }
    }

    companion object {
        private const val SODIUM_ID = "sodium"
        private const val PAGE_PREFIX = "sodium:sodium/"
        private const val IMPACT_KEY = "sodium.options.performance_impact_string"
        private const val RENDERER_RELOAD = "REQUIRES_RENDERER_RELOAD"
        private const val RENDERER_UPDATE = "REQUIRES_RENDERER_UPDATE"
        private const val ASSET_RELOAD = "REQUIRES_ASSET_RELOAD"
        private const val VIDEOMODE_RELOAD = "REQUIRES_VIDEOMODE_RELOAD"
        private const val GAME_RESTART = "REQUIRES_GAME_RESTART"
        private const val IRIS_ID = "iris"
        private const val IRIS_PAGE = "sodium:iris/0"
        private const val IRIS_TITLE_KEY = "options.iris.shaderPackSelection"
        private const val IRIS_SCREEN = "net.irisshaders.iris.gui.screen.ShaderPackScreen"
        private val PAGES = listOf("general", "quality", "performance", "advanced")
        private val ICONS = mapOf("general" to "hud", "quality" to "sun", "performance" to "lightning-01", "advanced" to "cog", "shader" to "paintbrush")
        private val SLIDER_FIELDS = listOf("min", "max", "interval", "mode")

        fun supported(): Boolean = present("client.gui.SodiumGameOptionPages") && present("client.gui.options.OptionPage")

        private fun present(name: String): Boolean =
            runCatching { Class.forName("net.caffeinemc.mods.sodium.$name", false, SodiumLegacyBridge::class.java.classLoader) }.isSuccess

        private fun declared(owner: Class<*>, name: String): Field = owner.getDeclaredField(name).apply { isAccessible = true }

        private fun sodium(name: String): Class<*> = Class.forName("net.caffeinemc.mods.sodium.$name")
    }
}
//?}
