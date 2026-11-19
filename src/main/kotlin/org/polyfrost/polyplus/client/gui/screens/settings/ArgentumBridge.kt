package org.polyfrost.polyplus.client.gui.screens.settings

//? if = 1.8.9 {
/*import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.resource.language.I18n
import org.polyfrost.polyplus.PolyPlusConstants
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import kotlin.math.roundToInt

internal class ArgentumPageRef(val id: String, val label: String, val icon: String, val owner: SettingsOwner)

internal class ArgentumAction(val key: String, val label: String, val hint: String?, val enabled: Boolean, val run: (Screen?) -> Unit)

internal class ArgentumGroup(val title: String?, val rows: List<SettingRow>, val actions: List<ArgentumAction>)

internal class ArgentumBridge private constructor() {
    private val createPages: Method = Class.forName(PAGES_CLASS).getMethod("create")

    private val pageClass = celeritas("api.options.structure.OptionPage")
    private val pageId = pageClass.getMethod("getId")
    private val pageName = pageClass.getMethod("getName")
    private val pageGroups = pageClass.getMethod("getGroups")
    private val pageOptions = pageClass.getMethod("getOptions")

    private val identifierClass = celeritas("api.options.OptionIdentifier")
    private val identifierModId = identifierClass.getMethod("getModId")
    private val identifierPath = identifierClass.getMethod("getPath")

    private val groupClass = celeritas("api.options.structure.OptionGroup")
    private val groupOptions = groupClass.getMethod("getOptions")

    private val optionClass = celeritas("api.options.structure.Option")
    private val optionId = optionClass.getMethod("getId")
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

    private val storageSave = celeritas("api.options.structure.OptionStorage").getMethod("save", Set::class.java)

    private val impactName = celeritas("api.options.structure.OptionImpact").getMethod("getLocalizedName")

    private val tickBoxClass = celeritas("api.options.control.TickBoxControl")

    private val sliderClass = celeritas("api.options.control.SliderControl")
    private val sliderMin: Field = declared(sliderClass, "min")
    private val sliderMax: Field = declared(sliderClass, "max")
    private val sliderInterval: Field = declared(sliderClass, "interval")
    private val sliderFormatter: Field = declared(sliderClass, "mode")
    private val formatterFormat = celeritas("api.options.control.ControlValueFormatter").getMethod("format", Int::class.javaPrimitiveType)

    private val cyclingClass = celeritas("api.options.control.CyclingControl")
    private val cyclingValues: Field = declared(cyclingClass, "allowedValues")
    private val cyclingNames = cyclingClass.getMethod("getNames")

    private val textClass = embeddium("gui.framework.TextComponent")
    private val literalClass = embeddium("gui.framework.TextComponent\$Literal")
    private val literalText = literalClass.getMethod("text")
    private val translatableClass = embeddium("gui.framework.TextComponent\$Translatable")
    private val translatableKeys = translatableClass.getMethod("keys")
    private val translatableArgs = translatableClass.getMethod("args")
    private val styledClass = embeddium("gui.framework.TextComponent\$Styled")
    private val styledInner = styledClass.getMethod("inner")

    private val postConstruction: ((MutableList<Any>) -> Unit)? = runCatching {
        val eventClass = celeritas("api.OptionGUIConstructionEvent")
        val constructor = eventClass.getConstructor(List::class.java)
        val bus = eventClass.getField("BUS").get(null)
        val post = bus.javaClass.getMethod("post", celeritas("api.eventbus.EmbeddiumEvent"))
        val poster: (MutableList<Any>) -> Unit = { pages -> post.invoke(bus, constructor.newInstance(pages)) }
        poster
    }.getOrNull()

    private var session: List<Any>? = null

    private fun call(method: Method, target: Any?, vararg args: Any?): Any? = method.invoke(target, *args)

    private fun session(): List<Any> = session ?: collectPages().also { session = it }

    private fun collectPages(): List<Any> {
        val pages = (call(createPages, null) as List<*>).filterNotNull().toMutableList()
        val base = pages.toList()
        postConstruction?.let { post -> runCatching { post(pages) }.onFailure { pages.retainAll(base.toSet()) } }
        return pages
    }

    private fun options(): List<Any> = session().flatMap { (call(pageOptions, it) as List<*>).filterNotNull() }

    fun usable(): Boolean = session().isNotEmpty()

    fun pages(): List<ArgentumPageRef> = session().mapNotNull { page ->
        if ((call(pageGroups, page) as List<*>).isEmpty()) return@mapNotNull null
        val identifier = call(pageId, page)
        val modId = call(identifierModId, identifier) as String
        val path = call(identifierPath, identifier) as String
        val owner = owner(modId)
        ArgentumPageRef(idOf(page), text(call(pageName, page)).ifBlank { owner.name }, iconFor(path), owner)
    }

    fun groups(id: String): List<ArgentumGroup>? {
        val page = session().firstOrNull { idOf(it) == id } ?: return null
        return (call(pageGroups, page) as List<*>).filterNotNull().map { group ->
            val rows = mutableListOf<SettingRow>()
            val actions = mutableListOf<ArgentumAction>()
            (call(groupOptions, group) as List<*>).filterNotNull().forEach { option ->
                val row = runCatching { row(option) }.getOrNull()
                if (row != null) rows += row else actions += action(option)
            }
            ArgentumGroup(null, rows, actions)
        }
    }

    fun dirty(): Boolean = session?.let { _ -> options().any { call(optionChanged, it) as Boolean } } ?: false

    fun begin() {
        if (!dirty()) session = null
    }

    fun apply() {
        val changed = options().filter { call(optionChanged, it) as Boolean }
        if (changed.isEmpty()) return
        val flags = mutableSetOf<Any>()
        val storages = LinkedHashSet<Any>()
        changed.forEach { option ->
            call(optionApply, option)
            (call(optionFlags, option) as Collection<*>).filterNotNullTo(flags)
            call(optionStorage, option)?.let { storages += it }
        }
        val saved = Collections.unmodifiableSet(flags)
        storages.forEach { call(storageSave, it, saved) }
        sideEffects(flags.map { it.toString() }.toSet())
    }

    fun undo() {
        options().forEach { call(optionReset, it) }
    }

    private fun sideEffects(flags: Set<String>) {
        val minecraft = Minecraft.getInstance()
        if (RENDERER_RELOAD in flags) minecraft.worldRenderer?.reload()
        if (RENDERER_UPDATE in flags) minecraft.worldRenderer?.onViewChanged()
        if (ASSET_RELOAD in flags) minecraft.reloadResources()
        if (GAME_RESTART in flags) restartPending = true
    }

    private fun idOf(page: Any): String {
        val identifier = call(pageId, page)
        return "argentum:" + call(identifierModId, identifier) + "/" + call(identifierPath, identifier)
    }

    private fun row(option: Any): SettingRow? {
        val key = call(optionId, option).toString()
        val label = text(call(optionName, option))
        val enabled = call(optionAvailable, option) as Boolean
        val control = call(optionControl, option) ?: return null
        val kind = when {
            tickBoxClass.isInstance(control) -> SettingKind.Toggle(call(optionValue, option) as Boolean) { set(option, it) }
            sliderClass.isInstance(control) -> slider(option, control)
            cyclingClass.isInstance(control) -> choice(option, control)
            else -> null
        } ?: return null
        return SettingRow(key, label, hint(option), kind, enabled)
    }

    private fun slider(option: Any, control: Any): SettingKind {
        val min = sliderMin.getInt(control)
        val max = sliderMax.getInt(control)
        val step = sliderInterval.getInt(control).coerceAtLeast(1)
        val formatter = sliderFormatter.get(control)
        val current = call(optionValue, option) as Int
        val format = { value: Int -> formatter?.let { text(call(formatterFormat, it, value)) } ?: value.toString() }
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
        val allowed = (cyclingValues.get(control) as? Array<*>)?.toList().orEmpty()
        if (allowed.isEmpty()) return null
        val names = (call(cyclingNames, control) as? Array<*>)?.toList().orEmpty()
        val current = call(optionValue, option)
        val index = allowed.indexOf(current)
        val labels = allowed.mapIndexed { i, value -> names.getOrNull(i)?.let { text(it) }?.ifBlank { null } ?: value.toString() }
        val values = if (index >= 0) allowed else listOf(current) + allowed
        val shown = if (index >= 0) labels else listOf(current.toString()) + labels
        return SettingKind.Choice(shown, values.indexOf(current)) { i ->
            values.getOrNull(i)?.let { set(option, it) }
        }
    }

    private fun action(option: Any): ArgentumAction {
        val key = runCatching { call(optionId, option).toString() }.getOrDefault(option.toString())
        val label = runCatching { text(call(optionName, option)) }.getOrDefault(key)
        val enabled = runCatching { call(optionAvailable, option) as Boolean }.getOrDefault(true)
        val hint = runCatching { hint(option) }.getOrNull()
        return ArgentumAction(key, label, hint ?: "Opens in Argentum's own settings screen.", enabled) { host -> openArgentum(host) }
    }

    private fun set(option: Any, value: Any) {
        call(optionSetValue, option, value)
    }

    private fun hint(option: Any): String? {
        val parts = mutableListOf<String>()
        text(call(optionTooltip, option), strict = true).takeIf { it.isNotBlank() }?.let { parts += it }
        call(optionImpact, option)?.let { impact ->
            val name = text(call(impactName, impact))
            val line = I18n.translate(IMPACT_KEY, name)
            parts += if (line == IMPACT_KEY) "Performance impact: $name" else line
        }
        if ((call(optionFlags, option) as? Collection<*>).orEmpty().any { it.toString() == GAME_RESTART }) parts += "Needs a game restart to take effect."
        return parts.joinToString("\n\n").ifBlank { null }
    }

    private fun text(component: Any?, strict: Boolean = false): String = when {
        component == null -> ""
        literalClass.isInstance(component) -> call(literalText, component) as? String ?: ""
        translatableClass.isInstance(component) -> translate(component, strict)
        styledClass.isInstance(component) -> text(call(styledInner, component), strict)
        else -> component.toString()
    }.replace(FORMATTING, "")

    private fun translate(component: Any, strict: Boolean): String {
        val keys = (call(translatableKeys, component) as List<*>).filterIsInstance<String>()
        val args = (call(translatableArgs, component) as List<*>).map { if (textClass.isInstance(it)) text(it) else it }.toTypedArray()
        val key = keys.firstOrNull { I18n.translate(it) != it } ?: if (strict) return "" else keys.firstOrNull() ?: return ""
        return I18n.translate(key, *args)
    }

    private fun owner(modId: String): SettingsOwner =
        if (FabricLoader.getInstance().isModLoaded(modId)) SettingsOwner.mod(modId) else SettingsOwner.mod(ARGENTUM_ID)

    private fun iconFor(path: String): String = when {
        "general" in path -> "hud"
        "quality" in path -> "sun"
        "performance" in path -> "lightning-01"
        "advanced" in path -> "cog"
        "shader" in path -> "paintbrush"
        else -> "layers"
    }

    companion object {
        private const val ARGENTUM_ID = "argentum"
        private const val PAGES_CLASS = "dev.rdh.argentum.impl.gui.ArgentumOptionPages"
        private const val SCREEN_CLASS = "dev.rdh.argentum.impl.gui.VideoOptionsScreen"
        private const val IMPACT_KEY = "sodium.options.performance_impact_string"
        private const val RENDERER_RELOAD = "REQUIRES_RENDERER_RELOAD"
        private const val RENDERER_UPDATE = "REQUIRES_RENDERER_UPDATE"
        private const val ASSET_RELOAD = "REQUIRES_ASSET_RELOAD"
        private const val GAME_RESTART = "REQUIRES_GAME_RESTART"
        private val FORMATTING = Regex("§.")
        private val logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/argentum-settings")

        val modName: String? by lazy {
            runCatching { FabricLoader.getInstance().getModContainer(ARGENTUM_ID).map { it.metadata.name }.orElse(null) }.getOrNull()
        }

        var restartPending = false
            private set

        private var failed = false

        private val instance: ArgentumBridge? by lazy {
            if (!FabricLoader.getInstance().isModLoaded(ARGENTUM_ID)) return@lazy null
            runCatching { ArgentumBridge() }.onFailure { fail(it) }.getOrNull()
        }

        fun get(): ArgentumBridge? = if (failed) null else instance

        fun <T> guard(fallback: T, block: (ArgentumBridge) -> T): T {
            val bridge = get() ?: return fallback
            return try {
                block(bridge)
            } catch (t: Throwable) {
                fail(t)
                fallback
            }
        }

        fun openArgentum(parent: Screen?) {
            val screen = runCatching { Class.forName(SCREEN_CLASS).getConstructor(Screen::class.java).newInstance(parent) as? Screen }
                .onFailure { logger.warn("Could not open Argentum's video settings", it) }
                .getOrNull() ?: return
            SettingsRoutes.pass(screen)
        }

        private fun fail(t: Throwable) {
            if (failed) return
            failed = true
            logger.warn("Could not read Argentum's options, video settings will open Argentum's own screen", t)
        }

        private fun declared(owner: Class<*>, name: String): Field = owner.getDeclaredField(name).apply { isAccessible = true }

        private fun celeritas(name: String): Class<*> = Class.forName("org.taumc.celeritas.$name")

        private fun embeddium(name: String): Class<*> = Class.forName("org.embeddedt.embeddium.impl.$name")
    }
}
*///?}
