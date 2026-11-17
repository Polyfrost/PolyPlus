package org.polyfrost.polyplus.client.gui.screens.packs

//? if > 1.8.9 {
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.packs.PackSelectionModel
import net.minecraft.client.gui.screens.packs.PackSelectionScreen
//? if >= 1.21.10 {
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
//?}
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.repository.PackRepository
import org.polyfrost.polyplus.mixin.client.access.PackModelAccessor
import org.polyfrost.polyplus.mixin.client.access.PackSelectionScreenAccessor
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Path
import java.util.function.Consumer
//?}

internal class PackOnlineAction(val icon: String, val label: String, val run: () -> Unit)

//? if > 1.8.9 {
internal object PackBridges {
    private val loader get() = FabricLoader.getInstance()

    private const val PACKED_PACKS = "io.github.fishstiz.packed_packs.gui"

    private class PackedPacks(val args: Constructor<*>, val screen: Constructor<*>, val withClient: Boolean)

    private val packedPacksPresent: Boolean by lazy { loader.isModLoaded("packed_packs") }

    private val packedPacks: PackedPacks? by lazy {
        if (!packedPacksPresent) return@lazy null
        val argsType = listOf("$PACKED_PACKS.screens.PackSelectionScreenArgs", "$PACKED_PACKS.metadata.PackSelectionScreenArgs")
            .firstNotNullOfOrNull { name -> runCatching { Class.forName(name) }.getOrNull() } ?: return@lazy null
        runCatching {
            val args = argsType.getConstructor(PackRepository::class.java, Consumer::class.java, Path::class.java, Component::class.java)
            val screenType = Class.forName("$PACKED_PACKS.screens.PackedPacksScreen")
            val direct = runCatching { screenType.getConstructor(Screen::class.java, argsType) }.getOrNull()
            if (direct != null) {
                PackedPacks(args, direct, false)
            } else {
                PackedPacks(args, screenType.getConstructor(Minecraft::class.java, Screen::class.java, argsType), true)
            }
        }.getOrNull()
    }

    private val packedPacksOptions: Constructor<*>? by lazy {
        if (!packedPacksPresent) return@lazy null
        runCatching { Class.forName("$PACKED_PACKS.screens.OptionsScreen").getConstructor(Screen::class.java) }.getOrNull()
    }

    val packedPacksLoaded: Boolean get() = packedPacks != null || packedPacksOptions != null

    val packedPacksLinks: Boolean by lazy {
        packedPacksPresent && runCatching {
            Class.forName("io.github.fishstiz.packed_packs.pack.PackNodeRepository", false, PackBridges::class.java.classLoader)
        }.isSuccess
    }

    private val filterHidden: Method? by lazy {
        if (!packedPacksPresent) return@lazy null
        runCatching {
            Class.forName("io.github.fishstiz.packed_packs.transform.interfaces.FilterableModel")
                .getMethod("packed_packs\$filterHidden", Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }

    private var showingHidden = 0

    fun <T> withHidden(model: PackSelectionModel, block: () -> T): T {
        val method = filterHidden?.takeIf { it.declaringClass.isInstance(model) } ?: return block()
        if (showingHidden == 0 && runCatching { method.invoke(model, false) }.isFailure) return block()
        showingHidden++
        try {
            return block()
        } finally {
            if (--showingHidden == 0) runCatching { method.invoke(model, true) }
        }
    }

    private val respackoptsLoaded: Boolean by lazy { loader.isModLoaded("respackopts") }

    private val resourcifyLoaded: Boolean by lazy { loader.isModLoaded("resourcify") }

    private class Widgets(val field: Field, val visible: Method, val click: Method)

    private val widgets: Widgets? by lazy {
        if (!respackoptsLoaded) return@lazy null
        runCatching {
            val type = Class.forName("dev.jfronny.libjf.entrywidgets.api.v0.ResourcePackEntryWidget")
            Widgets(
                type.getField("WIDGETS"),
                type.getMethod("isVisible", PackSelectionModel.Entry::class.java, Boolean::class.javaPrimitiveType),
                type.getMethod("onClick", PackSelectionModel.Entry::class.java),
            )
        }.getOrNull()
    }

    private val forceReload: Field? by lazy {
        if (!respackoptsLoaded) return@lazy null
        runCatching { Class.forName("dev.jfronny.respackopts.RespackoptsClient").getField("forcePackReload") }.getOrNull()
    }

    private class Resourcify(val instance: Any, val getType: Method, val getButtons: Method)

    private val resourcify: Resourcify? by lazy {
        if (!resourcifyLoaded) return@lazy null
        runCatching {
            val type = Class.forName("dev.dediamondpro.resourcify.gui.injections.PackScreensAddition")
            val projectType = Class.forName("dev.dediamondpro.resourcify.services.ProjectType")
            Resourcify(
                type.getField("INSTANCE").get(null),
                type.getMethod("getType", String::class.java),
                type.getMethod("getButtons", Screen::class.java, projectType),
            )
        }.getOrNull()
    }

    fun configurable(entry: PackSelectionModel.Entry): Boolean = widget(entry) != null

    fun configure(entry: PackSelectionModel.Entry) {
        val bridge = widgets ?: return
        val widget = widget(entry) ?: return
        runCatching { bridge.click.invoke(widget, entry) }
    }

    fun consumeForcedReload(): Boolean {
        val field = forceReload ?: return false
        return runCatching {
            val forced = field.getBoolean(null)
            if (forced) field.setBoolean(null, false)
            forced
        }.getOrDefault(false)
    }

    fun online(screen: PackSelectionScreen): List<PackOnlineAction> {
        val bridge = resourcify ?: return emptyList()
        val buttons = runCatching {
            val type = bridge.getType.invoke(bridge.instance, "resourcePack.title") ?: return emptyList()
            bridge.getButtons.invoke(bridge.instance, screen, type) as? List<*>
        }.getOrNull() ?: return emptyList()
        return buttons.filterIsInstance<Button>().mapIndexed { index, button ->
            if (index == 0) {
                PackOnlineAction("cloud", "Browse packs online") { press(button) }
            } else {
                PackOnlineAction("refresh", "Update packs") { press(button) }
            }
        }
    }

    fun packedPacksScreen(screen: PackSelectionScreen, previous: Screen?): Screen? =
        packedPacksMain(screen, previous) ?: packedPacksOptions?.let { options -> runCatching { options.newInstance(previous) as Screen }.getOrNull() }

    private fun packedPacksMain(screen: PackSelectionScreen, previous: Screen?): Screen? {
        val bridge = packedPacks ?: return null
        val access = screen as Any as PackSelectionScreenAccessor
        val model = access.`polyplus$model`() as Any as? PackModelAccessor ?: return null
        return runCatching {
            val args = bridge.args.newInstance(model.`polyplus$repository`(), model.`polyplus$output`(), access.`polyplus$packDir`(), screen.title)
            val created = if (bridge.withClient) bridge.screen.newInstance(Minecraft.getInstance(), previous, args) else bridge.screen.newInstance(previous, args)
            created as Screen
        }.getOrNull()
    }

    private fun widget(entry: PackSelectionModel.Entry): Any? {
        val bridge = widgets ?: return null
        val all = runCatching { bridge.field.get(null) as? Iterable<*> }.getOrNull() ?: return null
        val selectable = !entry.isFixedPosition || !entry.isRequired
        return all.firstOrNull { widget ->
            widget != null && runCatching { bridge.visible.invoke(widget, entry, selectable) as Boolean }.getOrDefault(false)
        }
    }

    private fun press(button: Button) {
        //? if >= 1.21.10 {
        runCatching { button.onPress(KeyEvent(InputConstants.KEY_RETURN, 0, 0)) }
        //?} else {
        /*runCatching { button.onPress() }
        *///?}
    }
}
//?} else {
/*internal object PackBridges {
    val packedPacksLoaded: Boolean get() = false

    val packedPacksLinks: Boolean get() = false

    fun consumeForcedReload(): Boolean = false
}
*///?}
