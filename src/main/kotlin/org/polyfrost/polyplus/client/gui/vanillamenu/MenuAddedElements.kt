package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.screens.packs.PackFiles
import org.polyfrost.polyplus.client.lobby.LobbyMusic
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The pieces a player can add to the vanilla menu from the editor's Elements panel. */
internal object MenuAddedElements {
    class Kind(
        val type: String,
        val label: String,
        val w: Int,
        val h: Int,
        val props: Map<String, String> = emptyMap(),
        val available: (VanillaTitleMenu.State) -> Boolean = { true },
    )

    val kinds: List<Kind> by lazy {
        buildList {
            add(Kind("image", "Custom image", 64, 64, mapOf("file" to "")))
            add(Kind("text", "Text label", 80, 12, mapOf("text" to "Text", "color" to "FFFFFF", "shadow" to "true", "align" to "left")))
            add(Kind("music-volume", "Music volume", 150, 20))
            add(Kind("music-mute", "Mute when unfocused", 150, 20))
            add(Kind("music-track", "Music track", 150, 20))
            add(Kind("button", "Custom button", 100, 20, mapOf("label" to "Button", "action" to "url", "value" to "https://")))
            add(Kind("panel", "Panel / background", 120, 80, mapOf("color" to "000000", "radius" to "4")))
            add(Kind("player", "Player preview", 90, 128) { it.preview == null })
            add(Kind("quickplay", "Quickplay panel", 140, 20) { state -> state.pieces.values.none { piece -> piece.extras.any { it is QuickplayPanel.Row } } && QuickplayPanel.entries().isNotEmpty() })
            add(Kind("clock", "Clock", 30, 10, mapOf("color" to "FFFFFF", "shadow" to "true", "align" to "left")))
            add(Kind("fps", "FPS", 40, 10, mapOf("color" to "FFFFFF", "shadow" to "true", "align" to "left")))
            for (feature in EssentialMenu.Feature.entries) {
                add(Kind("essential-${feature.name.lowercase()}", "Essential: ${feature.label}", 100, 20) { EssentialMenu.present })
            }
        }
    }

    fun kind(type: String): Kind? = kinds.firstOrNull { it.type == type }

    /** Adds a new piece of [kind] in the middle of the screen, on top of everything; returns its id. */
    fun add(state: VanillaTitleMenu.State, kind: Kind): String {
        val scope = MenuLayoutStore.VANILLA
        MenuLayoutStore.checkpoint(scope)
        val id = MenuLayoutStore.newId(scope, kind.type)
        val element = MenuLayoutStore.edit(scope, id)
        element.type = kind.type
        element.anchor = "center"
        element.x = 0f
        element.y = 0f
        element.w = kind.w.toFloat()
        element.h = kind.h.toFloat()
        element.z = (state.pieces.values.maxOfOrNull { it.z } ?: 0) + 10
        element.props.putAll(kind.props)
        MenuLayoutStore.save()
        return id
    }

    fun duplicate(state: VanillaTitleMenu.State, piece: MenuPiece): String? {
        if (piece.builtin) return null
        val scope = MenuLayoutStore.VANILLA
        val source = piece.data ?: return null
        val id = MenuLayoutStore.newId(scope, source.type)
        val copy = MenuLayoutStore.edit(scope, id)
        copy.type = source.type
        copy.anchor = source.anchor
        copy.x = source.x + 8
        copy.y = source.y + 8
        copy.w = source.w
        copy.h = source.h
        copy.scale = source.scale
        copy.opacity = source.opacity
        copy.z = (state.pieces.values.maxOfOrNull { it.z } ?: 0) + 10
        copy.props.putAll(source.props)
        return id
    }

    fun create(screen: TitleScreen, access: ScreenAccessor, state: VanillaTitleMenu.State, element: MenuElement): MenuPiece? {
        val w = element.w?.roundToInt() ?: kind(element.type)?.w ?: 100
        val h = element.h?.roundToInt() ?: kind(element.type)?.h ?: 20
        val label = kind(element.type)?.label ?: element.type
        if (element.type == "player") return VanillaTitleMenu.addPreviewPiece(access, state, element)
        if (element.type == "quickplay") return VanillaTitleMenu.addQuickplay(screen, access, state, 0, 0, w, element.type, element.id)
        val widget: AbstractWidget = when (element.type) {
            "image" -> ImagePiece(element)
            "text" -> TextPiece(element) { element.prop("text") }
            "clock" -> TextPiece(element) { LocalTime.now().format(CLOCK) }
            "fps" -> TextPiece(element) { "${Minecraft.getInstance().fps} FPS" }
            "panel" -> PanelPiece(element)
            "music-volume" -> MusicVolume(w, h)
            "music-mute" -> toggleButton({ "Mute when unfocused: " + if (PolyPlusLobbyConfig.muteLobbyMusicUnfocused) "ON" else "OFF" }) {
                PolyPlusLobbyConfig.muteLobbyMusicUnfocused = !PolyPlusLobbyConfig.muteLobbyMusicUnfocused
                PolyPlusLobbyConfig.save()
            }
            "music-track" -> toggleButton({ "Music: ${PolyPlusLobbyConfig.lobbyMusic}" }) {
                val all = LobbyMusic.entries
                PolyPlusLobbyConfig.lobbyMusic = all[(PolyPlusLobbyConfig.lobbyMusic.ordinal + 1) % all.size]
                PolyPlusLobbyConfig.save()
            }
            "button" -> Button.builder(Component.literal(element.prop("label", "Button"))) { CustomAction.run(element, screen) }.build()
            else -> {
                val feature = EssentialMenu.Feature.entries.firstOrNull { element.type == "essential-${it.name.lowercase()}" } ?: return null
                VanillaTitleMenu.textButton(feature.label, feature.tooltip) { EssentialMenu.open(feature, screen) }
            }
        }
        widget.setRectangle(w, h, 0, 0)
        access.polyplusAddRenderableWidget(widget)
        val name = when (element.type) {
            "text" -> "Text: " + element.prop("text").take(16)
            "button" -> "Button: " + element.prop("label").take(16)
            "image" -> "Image: " + element.prop("file").ifEmpty { "none" }
            else -> label
        }
        return VanillaTitleMenu.piece(state, widget, element.id, name, type = element.type, legacyKey = null)
    }

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    private fun toggleButton(label: () -> String, onPress: () -> Unit): Button {
        lateinit var button: Button
        button = Button.builder(Component.literal(label())) {
            onPress()
            button.message = Component.literal(label())
        }.build()
        return button
    }

    internal fun color(hex: String, fallback: Int = 0xFFFFFF): Int = hex.trim().removePrefix("#").toIntOrNull(16)?.and(0xFFFFFF) ?: fallback

    private fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    /** Text, the clock or FPS: color, shadow and alignment from the element, sized by its scale. */
    private class TextPiece(private val element: MenuElement, private val text: () -> String) :
        AbstractWidget(0, 0, 10, 10, Component.literal("Text")) {
        init {
            active = false
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            val font = Minecraft.getInstance().font
            val value = text()
            val textWidth = font.width(value)
            val tx = when (element.prop("align", "left")) {
                "center" -> x + (width - textWidth) / 2
                "right" -> x + width - textWidth
                else -> x
            }
            val ty = y + (height - 8) / 2
            graphics.text(font, value, tx, ty, withAlpha(color(element.prop("color", "FFFFFF")), alpha), element.prop("shadow", "true") == "true")
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = Unit
    }

    /** A rounded rectangle behind other pieces. */
    private class PanelPiece(private val element: MenuElement) : AbstractWidget(0, 0, 10, 10, Component.literal("Panel")) {
        init {
            active = false
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            val color = withAlpha(color(element.prop("color", "000000"), 0), alpha * 0.6f)
            val radius = (element.prop("radius", "4").toIntOrNull() ?: 4).coerceIn(0, minOf(width, height) / 2)
            fillRounded(graphics, x, y, width, height, radius, color)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = Unit
    }

    internal fun fillRounded(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) {
        if (radius <= 0) {
            graphics.fill(x, y, x + w, y + h, color)
            return
        }
        graphics.fill(x, y + radius, x + w, y + h - radius, color)
        for (row in 0 until radius) {
            val dy = radius - row - 0.5f
            val inset = (radius - sqrt(radius * radius - dy * dy)).roundToInt()
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, color)
            graphics.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color)
        }
    }

    /** A picture from config/polyplus/menu-images, stretched to the piece. */
    private class ImagePiece(private val element: MenuElement) : AbstractWidget(0, 0, 10, 10, Component.literal("Image")) {
        init {
            active = false
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
            val image = MenuImages.get(element.prop("file"))
            if (image == null) {
                graphics.fill(x, y, x + width, y + height, 0x60000000)
                graphics.outline(x, y, width, height, 0x80FFFFFF.toInt())
                val font = Minecraft.getInstance().font
                graphics.centeredText(font, "No image", x + width / 2, y + (height - 8) / 2, 0xFFAAAAAA.toInt())
                return
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.id, x, y, 0f, 0f, width, height, image.width, image.height, image.width, image.height, withAlpha(0xFFFFFF, alpha))
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = Unit
    }

    /** The lobby music volume, the same setting as the lobby's Music > Volume. */
    private class MusicVolume(width: Int, height: Int) :
        AbstractSliderButton(0, 0, width, height, Component.empty(), PolyPlusLobbyConfig.lobbyMusicVolume / 100.0) {
        init {
            updateMessage()
        }

        override fun updateMessage() {
            message = Component.literal("Music volume: ${(value * 100).roundToInt()}%")
        }

        override fun applyValue() {
            PolyPlusLobbyConfig.lobbyMusicVolume = ((value * 100 / 5).roundToInt() * 5).toFloat()
        }

        override fun onRelease(event: MouseButtonEvent) {
            super.onRelease(event)
            PolyPlusLobbyConfig.save()
        }
    }
}

/** What a custom button does: join a server, open a website or open a screen. */
internal object CustomAction {
    val actions = listOf("server" to "Join server", "url" to "Open website", "screen" to "Open screen")

    val screens = listOf(
        "options" to "Options",
        "mods" to "Mods (OneConfig)",
        "oneclient" to "OneClient settings",
        "singleplayer" to "Singleplayer",
        "multiplayer" to "Multiplayer",
        "accounts" to "Accounts",
        "social" to "Social",
        "wardrobe" to "Wardrobe",
        "host" to "Host World",
        "notifications" to "Notifications",
        "lobby" to "OneClient Lobby",
        "polyplus" to "PolyPlus Menu",
        "quit" to "Quit game",
    )

    fun run(element: MenuElement, screen: Screen) {
        val value = element.prop("value").trim()
        when (element.prop("action", "url")) {
            "server" -> if (value.isNotEmpty()) MultiplayerTabs.connect(screen, element.prop("label", value), value)
            "url" -> if (value.startsWith("https://") || value.startsWith("http://")) ClientPlatform.openUri(value)
            "screen" -> open(value, screen)
        }
    }

    private fun open(target: String, parent: Screen) {
        val mc = Minecraft.getInstance()
        when (target) {
            //? if >= 26.3 || < 26.1 {
            "options" -> ClientPlatform.setScreen(OptionsScreen(parent, mc.options))
            //?} else {
            /*"options" -> ClientPlatform.setScreen(OptionsScreen(parent, mc.options, false))
            *///?}
            "mods" -> PolyPlusOneConfigIntegration.openMods()
            "oneclient" -> PolyPlusOneConfigIntegration.openSettings()
            "singleplayer" -> ClientPlatform.setScreen(SelectWorldScreen(parent))
            "multiplayer" -> ClientPlatform.setScreen(JoinMultiplayerScreen(parent))
            "accounts" -> MenuPanelScreen.open(MenuPanelScreen.Panel.Accounts)
            "social" -> SocialOverlay.open(parent)
            "wardrobe" -> PolyPlusOneConfigIntegration.openCosmetics()
            "host" -> MenuPanelScreen.open(MenuPanelScreen.Panel.HostWorld)
            "notifications" -> MenuPanelScreen.open(MenuPanelScreen.Panel.Notifications)
            "lobby" -> MainMenuReplacement.switchToLobby()
            "polyplus" -> MainMenuReplacement.switchToPolyPlusMenu()
            "quit" -> mc.stop()
        }
    }
}

/** Pictures from config/polyplus/menu-images as textures, loaded once each. PNG, JPG and GIF (its first frame). */
internal object MenuImages {
    private val LOGGER = LogManager.getLogger("PolyPlus/MenuImages")
    private val EXTENSIONS = setOf("png", "jpg", "jpeg", "gif")

    class Image(val id: Identifier, val width: Int, val height: Int)

    private val loaded = HashMap<String, Image?>()

    fun files(): List<String> = runCatching {
        Files.createDirectories(MenuLayoutStore.imageDir)
        Files.list(MenuLayoutStore.imageDir).use { stream ->
            stream.filter { it.isRegularFile() && it.extension.lowercase() in EXTENSIONS }.map { it.name }.sorted().toList()
        }
    }.getOrDefault(emptyList())

    fun openFolder() {
        runCatching { Files.createDirectories(MenuLayoutStore.imageDir) }
        // the file manager, not the browser
        runCatching { PackFiles.openPath(MenuLayoutStore.imageDir.toAbsolutePath()) }
    }

    fun get(file: String): Image? {
        if (file.isEmpty()) return null
        return loaded.getOrPut(file) { load(MenuLayoutStore.imageDir.resolve(file), file) }
    }

    /** Forgets the loaded pictures, so changed files are read again. */
    fun reload() {
        val textures = Minecraft.getInstance().textureManager
        loaded.values.filterNotNull().forEach { textures.release(it.id) }
        loaded.clear()
    }

    private fun load(path: Path, file: String): Image? = runCatching {
        // only files right in the folder
        if (path.parent != MenuLayoutStore.imageDir || !path.isRegularFile()) return null
        val bytes = Files.readAllBytes(path)
        val image = if (path.extension.lowercase() == "png") {
            NativeImage.read(bytes)
        } else {
            val decoded = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
            NativeImage(decoded.width, decoded.height, false).also { native ->
                for (y in 0 until decoded.height) for (x in 0 until decoded.width) native.setPixel(x, y, decoded.getRGB(x, y))
            }
        }
        val safe = file.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        val id = Identifier.fromNamespaceAndPath("polyplus", "menu_image/$safe")
        val width = image.width
        val height = image.height
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "PolyPlus menu image $file" }, image))
        Image(id, width, height)
    }.onFailure { LOGGER.warn("Couldn't load menu image {}", file, it) }.getOrNull()
}
//?}
