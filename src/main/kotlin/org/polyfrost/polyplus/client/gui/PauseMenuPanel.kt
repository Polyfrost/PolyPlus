package org.polyfrost.polyplus.client.gui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.backend.PolyCanvas
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOffscreen
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.WeakHashMap
import kotlin.math.roundToInt

//? if > 1.8.9 {
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket
import net.minecraft.stats.Stats
//?} else {
/*import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.resource.language.I18n
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket
import net.minecraft.stat.Stats
import org.lwjgl.input.Mouse
*///?}

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if < 26.3
//import org.polyfrost.polyplus.client.PolyPlusConfig

/**
 * The title screen's player preview with Social and Wardrobe buttons under it, shown beside the pause menu's
 * buttons in both the vanilla and the Compose pause menu. Layout is in vanilla GUI pixels.
 */
object PauseMenuPanel {
    private val LOGGER = LogManager.getLogger()

    // a 20px vanilla button comes out at the title screen's 45dp pill height
    const val DP_PER_GUI_PIXEL = 45f / 20f

    // the title screen's 300dp right column and its 50dp edge padding
    const val WIDTH = 133
    private const val BUTTON_WIDTH = 112
    private const val EDGE_MARGIN = 22

    const val BUTTON_HEIGHT = 20
    private const val PREVIEW_GAP = 6
    private const val MIN_EDGE_MARGIN = 6
    private const val MENU_GAP = 8
    private const val MIN_WIDTH = 80
    private const val TOP_MARGIN = 4

    // vanilla's grid puts rows 24px apart
    private const val VANILLA_ROW_STEP = 24

    // PlayerPreview's drag sensitivity and pitch limit
    private const val DRAG_DEGREES_PER_PIXEL = 0.5f
    private const val MAX_DRAG_PITCH = 45f

    /** The buttons span [x]..[x] + [width]; the preview is centered above them and shrinks by [previewScale]. */
    class Layout(
        val x: Int,
        val width: Int,
        val previewX: Int,
        val previewY: Int,
        val previewWidth: Int,
        val previewHeight: Int,
        val socialY: Int?,
        val wardrobeY: Int,
        val hostWorldY: Int?,
        val leftX: Int?,
        val playtimeY: Int,
        val onLeft: Boolean,
    ) {
        val playtimeX: Int? get() = if (onLeft) x else leftX
        val previewScale: Float get() = previewWidth / WIDTH.toFloat()
        val showsPreview: Boolean get() = previewWidth > 0 && previewHeight > 0
    }

    /**
     * Null when the panel is off or there isn't room for it to the right of the menu's buttons. The column keeps to
     * the screen's right edge like the title screen's, and at high GUI scales, where the vanilla menu takes most
     * of the screen, it narrows and its edge margin shrinks to fit. The preview and buttons are centered together on
     * the screen's height, and the preview shrinks or is left out when there isn't room for it.
     * Social and Wardrobe are spaced like the menu's last two rows, given as [menuRowsY].
     * Host World sits under the preview, on the row above Social. Settings and playtime mirror the column on the
     * other side of the menu, from [menuLeft], when it fits: Settings level with Wardrobe and playtime on the row
     * above it.
     * [onLeft] puts the column on the left side instead, keeping to the screen's left edge, without Settings and with
     * playtime as its top row, right under the preview.
     */
    fun layout(screenWidth: Int, screenHeight: Int, menuLeft: Int, menuRight: Int, menuRowsY: List<Int>, onLeft: Boolean = false): Layout? {
        if (!PolyPlusMainMenuConfig.pauseMenuPlayerPanel) return null
        val lastRow = menuRowsY.maxOrNull() ?: return null
        val rightRoom = screenWidth - menuRight - MENU_GAP
        val rightWidth = minOf(WIDTH, rightRoom - MIN_EDGE_MARGIN)
        val leftRoom = menuLeft - MENU_GAP
        val leftWidth = minOf(WIDTH, leftRoom - MIN_EDGE_MARGIN)
        val leftFits = leftWidth >= MIN_WIDTH
        // integer centering can leave the menu a pixel off center, so both columns take the narrower side's width
        val columnWidth = when {
            onLeft -> leftWidth
            leftFits -> minOf(rightWidth, leftWidth)
            else -> rightWidth
        }
        val width = minOf(BUTTON_WIDTH, columnWidth)
        if (width < MIN_WIDTH) return null
        val edgeMargin = ((if (onLeft) leftRoom else rightRoom) - width).coerceAtMost(EDGE_MARGIN)
        val x = if (onLeft) edgeMargin else screenWidth - width - edgeMargin
        val leftX = if (!onLeft && leftFits) (leftRoom - width).coerceAtMost(EDGE_MARGIN) else null

        val social = PrivacyConsent.allowsOnlineServices()
        val rows = menuRowsY.distinct().sorted()
        val secondLastRow = rows.getOrNull(rows.size - 2) ?: (lastRow - VANILLA_ROW_STEP)
        val socialY = if (social) secondLastRow else null
        // the host flow only ever opens the local world
        //? if > 1.8.9 {
        val singleplayer = Minecraft.getInstance().hasSingleplayerServer()
        //?} else {
        /*val singleplayer = Minecraft.getInstance().server != null
        *///?}
        // MixinPauseScreen already turns the menu's Open to LAN button into Host World; 26.3 moved LAN into World Options
        //? if < 26.3 {
        /*val menuHostsWorld = PolyPlusConfig.replacePauseLanButton
        *///?} else {
        val menuHostsWorld = false
        //?}
        val hostWorld = social && singleplayer && !menuHostsWorld
        // one row above Social, right under the preview. not the menu's own row there: 1.8.9 leaves a blank row above Options
        val hostWorldY = if (hostWorld && socialY != null) socialY - VANILLA_ROW_STEP else null
        val buttonsTopY = hostWorldY ?: socialY ?: lastRow
        val playtimeY = if (onLeft) buttonsTopY - VANILLA_ROW_STEP else secondLastRow
        val topButtonY = if (onLeft) playtimeY else buttonsTopY

        // the title screen's preview at this column width, shrunk further if the column wouldn't fit the screen's height
        val fullHeight = menuPreviewSpec().height.value / DP_PER_GUI_PIXEL
        val buttonsHeight = lastRow + BUTTON_HEIGHT - topButtonY
        val headroom = screenHeight - 2 * TOP_MARGIN - buttonsHeight - PREVIEW_GAP
        val scale = minOf(columnWidth / WIDTH.toFloat(), headroom / fullHeight).coerceAtLeast(0f)
        val previewWidth = (WIDTH * scale).roundToInt()
        val previewHeight = (fullHeight * scale).roundToInt()
        val previewY = (screenHeight - previewHeight - PREVIEW_GAP - buttonsHeight) / 2
        // the rows above are relative to the menu's, so move them all to where the centered column puts the top one
        val rowOffset = previewY + previewHeight + PREVIEW_GAP - topButtonY
        return Layout(
            x = x,
            width = width,
            previewX = x + (width - previewWidth) / 2,
            previewY = previewY,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            socialY = socialY?.plus(rowOffset),
            wardrobeY = lastRow + rowOffset,
            hostWorldY = hostWorldY?.plus(rowOffset),
            leftX = leftX,
            playtimeY = playtimeY + rowOffset,
            onLeft = onLeft,
        )
    }

    private var statsRequestedAt: Long? = null

    // the client's stat counter only updates when asked, as vanilla's Statistics screen does. asked again every so
    // often while the label is shown, since playtime keeps counting in multiplayer and unpaused LAN worlds
    private fun requestStats() {
        val now = System.nanoTime()
        statsRequestedAt?.let { if (now - it < STATS_REQUEST_NANOS) return }
        statsRequestedAt = now
        //? if > 1.8.9 {
        Minecraft.getInstance().connection?.send(ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS))
        //?} else {
        /*Minecraft.getInstance().networkHandler?.sendPacket(ClientStatusC2SPacket(ClientStatusC2SPacket.Status.REQUEST_STATS))
        *///?}
    }

    private const val STATS_REQUEST_NANOS = 10_000_000_000L

    fun playtimeLabel(): String {
        requestStats()
        return "Playtime: " + (playTimeTicks()?.let(::formatPlaytime) ?: "-")
    }

    private fun playTimeTicks(): Int? =
        //? if > 1.8.9 {
        Minecraft.getInstance().player?.stats?.getValue(Stats.CUSTOM, Stats.PLAY_TIME)
        //?} else {
        /*Minecraft.getInstance().player?.stats?.get(Stats.MINUTES_PLAYED) // counts ticks, despite the name
        *///?}

    internal fun formatPlaytime(ticks: Int): String {
        val minutes = ticks / 20 / 60
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }

    // vanilla pause menu: its own buttons, and the preview drawn into it through an inline canvas

    private fun openSocial(from: Screen) = SocialOverlay.open(from)

    private fun openWardrobe() = PolyPlusOneConfigIntegration.openCosmetics()

    private fun openHostWorld(from: Screen) = SocialOverlay.openHostCurrentWorld(from)

    private fun openSettings() = PolyPlusOneConfigIntegration.openMods()

    private val layouts = WeakHashMap<Screen, Layout>()
    private val icons = WeakHashMap<Screen, List<ButtonIcon>>()
    //? if > 1.8.9 {
    private val playtimeButtons = WeakHashMap<Screen, Button>()
    //?} else {
    /*private val playtimeButtons = WeakHashMap<Screen, ButtonWidget>()
    *///?}

    private val preview by lazy {
        PlayerPreviewOffscreen.register().apply {
            vanillaHost = true
            source = PlayerPreviewSource.LocalLive
            autoSpin = true
            verticalAnchor = 1f
            initialYaw = MENU_PREVIEW_YAW
        }
    }

    // the Compose pause menu mirrors a hidden vanilla one, which must not get these buttons too
    private fun vanillaMenuShown(): Boolean = !PolyPlusMainMenuConfig.usePolyPlusPauseMenu

    // Essential's pause menu adds proxy Buttons for the player and buttons it draws on either side of the menu
    fun isEssentialWidget(widget: Any): Boolean = widget.javaClass.name.startsWith("gg.essential.")

    // Essential's left side bar: its player and the Wardrobe button under it
    private val ESSENTIAL_LEFT_SIDE_BAR = setOf("player", "wardrobe_2")

    //? if > 1.8.9 {
    private val essentialFlagsHidden = WeakHashMap<Screen, Unit>()

    /**
     * The side bar's sale and NEW flags have no proxies, so this takes away the container holding them and the side bar,
     * once Essential has built it on the screen's first frames. The names are Essential's own, so if it renames them
     * the flags just stay.
     * shortcut: not restored if a resize then leaves the panel no room, until the menu is reopened. rare, and only cosmetic
     */
    private fun hideEssentialFlags(screen: Screen) {
        if (screen in essentialFlagsHidden) return
        // accessible because Essential's Layer implementation is package-private
        fun Any.call(method: String): Any? = javaClass.getMethod(method).apply { isAccessible = true }.invoke(this)
        fun Any.children(): List<Any> = (call("getChildren") as List<*>).filterNotNull()
        try {
            val layer = screen.call("essential\$getProxyHandler")?.call("getLayer") ?: return
            val container = layer.call("getWindow")!!.children().firstOrNull { child ->
                child.children().any { it.javaClass.name == "gg.essential.gui.menu.LeftSideBar" }
            } ?: return
            container.javaClass.getMethod("hide", Boolean::class.javaPrimitiveType).invoke(container, true)
        } catch (e: ReflectiveOperationException) {
            LOGGER.warn("Couldn't hide Essential's pause menu flags", e)
        }
        essentialFlagsHidden[screen] = Unit
    }

    // Essential takes an element off the screen once its proxy is hidden, as it does for FancyMenu
    private fun hideEssentialLeftSideBar(proxies: List<AbstractButton>) {
        for (proxy in proxies) {
            val id = runCatching { proxy.javaClass.getMethod("getEssentialId").invoke(proxy) as String }.getOrNull()
            if (id !in ESSENTIAL_LEFT_SIDE_BAR) continue
            proxy.visible = false
            proxy.active = false
        }
    }

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is PauseScreen || !screen.showsPauseMenu() || !vanillaMenuShown()) return@register
            val (essentialProxies, menuButtons) = screen.children().filterIsInstance<AbstractButton>()
                .filter { it.visible }
                .partition(::isEssentialWidget)
            val menuLeft = menuButtons.minOfOrNull { it.x } ?: return@register
            val menuRight = menuButtons.maxOf { it.x + it.width }
            // Essential's own bar is on the right, so the player goes in place of Essential's on the left
            val withEssential = essentialProxies.isNotEmpty()
            val layout = layout(screen.width, screen.height, menuLeft, menuRight, menuButtons.map { it.y }, onLeft = withEssential)
            if (layout == null) {
                layouts.remove(screen)
                icons.remove(screen)
                return@register
            }
            layouts[screen] = layout
            if (withEssential) hideEssentialLeftSideBar(essentialProxies)
            val access = screen as ScreenAccessor
            val buttonIcons = mutableListOf<ButtonIcon>()
            fun addIconButton(text: String, icon: String, x: Int, y: Int, onPress: () -> Unit) {
                val label = iconLabel(text)
                access.polyplusAddRenderableWidget(
                    Button.builder(Component.literal(label)) { onPress() }.bounds(x, y, layout.width, BUTTON_HEIGHT).build(),
                )
                buttonIcons += buttonIcon(icon, x, y, layout.width, label, text)
            }
            layout.socialY?.let { y -> addIconButton("Social", "social", layout.x, y) { openSocial(screen) } }
            addIconButton("Wardrobe", "wardrobe", layout.x, layout.wardrobeY) { openWardrobe() }
            layout.hostWorldY?.let { y ->
                addIconButton(Component.translatable("polyplus.hostWorld").string, "host-world", layout.x, y) { openHostWorld(screen) }
            }
            icons[screen] = buttonIcons
            layout.leftX?.let { addIconButton("Settings", "settings", it, layout.wardrobeY) { openSettings() } }
            val playtimeX = layout.playtimeX
            if (playtimeX == null) {
                playtimeButtons.remove(screen)
                return@register
            }
            // render-only, so it draws and highlights like the buttons around it but never gets the click or its sound
            playtimeButtons[screen] = access.polyplusAddRenderableOnly(
                Button.builder(Component.literal(playtimeLabel())) {}.bounds(playtimeX, layout.playtimeY, layout.width, BUTTON_HEIGHT).build(),
            )
        }
    }
    //?} else {
    /*private const val LEGACY_SOCIAL_ID = 0x50505301
    private const val LEGACY_WARDROBE_ID = 0x50505302
    private const val LEGACY_HOST_WORLD_ID = 0x50505303
    private const val LEGACY_SETTINGS_ID = 0x50505304

    fun register() {}

    @JvmStatic
    fun addLegacyButtons(screen: Screen) {
        if (!vanillaMenuShown()) return
        val buttons = (screen as ScreenAccessor).`polyplus$buttons`()
        val menuButtons = buttons.filter { it.visible }
        val menuLeft = menuButtons.minOfOrNull { it.x } ?: return
        val menuRight = menuButtons.maxOf { it.x + it.getWidth() }
        val layout = layout(screen.width, screen.height, menuLeft, menuRight, menuButtons.map { it.y })
        if (layout == null) {
            layouts.remove(screen)
            icons.remove(screen)
            return
        }
        layouts[screen] = layout
        val buttonIcons = mutableListOf<ButtonIcon>()
        fun addIconButton(id: Int, text: String, icon: String, x: Int, y: Int) {
            val label = iconLabel(text)
            buttons.add(ButtonWidget(id, x, y, layout.width, BUTTON_HEIGHT, label))
            buttonIcons += buttonIcon(icon, x, y, layout.width, label, text)
        }
        layout.socialY?.let { y -> addIconButton(LEGACY_SOCIAL_ID, "Social", "social", layout.x, y) }
        addIconButton(LEGACY_WARDROBE_ID, "Wardrobe", "wardrobe", layout.x, layout.wardrobeY)
        layout.hostWorldY?.let { y -> addIconButton(LEGACY_HOST_WORLD_ID, I18n.translate("polyplus.hostWorld"), "host-world", layout.x, y) }
        icons[screen] = buttonIcons
        val leftX = layout.leftX
        if (leftX == null) {
            playtimeButtons.remove(screen)
            return
        }
        addIconButton(LEGACY_SETTINGS_ID, "Settings", "settings", leftX, layout.wardrobeY)
        // kept out of the screen's buttons and drawn in render, so it looks like one but never takes a click or its sound
        playtimeButtons[screen] = ButtonWidget(0, leftX, layout.playtimeY, layout.width, BUTTON_HEIGHT, playtimeLabel())
    }

    @JvmStatic
    fun onLegacyButton(screen: Screen, id: Int): Boolean {
        when (id) {
            LEGACY_SOCIAL_ID -> openSocial(screen)
            LEGACY_WARDROBE_ID -> openWardrobe()
            LEGACY_HOST_WORLD_ID -> openHostWorld(screen)
            LEGACY_SETTINGS_ID -> openSettings()
            else -> return false
        }
        return true
    }
    *///?}

    //? if >= 26.1 {
    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screen: Screen) {
        val layout = layouts[screen] ?: return
        if (layout.onLeft) hideEssentialFlags(screen)
        // stats arrive a moment after the request, and keep counting while the menu is open
        if (playtimeDue()) playtimeButtons[screen]?.message = Component.literal(playtimeLabel())
        updateDrag(screen, layout)
        inline.draw(ctx) { drawPreview(it, screen, layout); drawIcons(it, screen) }
    }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun render(ctx: GuiGraphics, screen: Screen) {
        val layout = layouts[screen] ?: return
        if (layout.onLeft) hideEssentialFlags(screen)
        if (playtimeDue()) playtimeButtons[screen]?.message = Component.literal(playtimeLabel())
        updateDrag(screen, layout)
        inline.draw(ctx) { drawPreview(it, screen, layout); drawIcons(it, screen) }
    }
    *///?} else {
    /*@JvmStatic
    fun render(screen: Screen, mouseX: Int, mouseY: Int) {
        val layout = layouts[screen] ?: return
        playtimeButtons[screen]?.let {
            if (playtimeDue()) it.message = playtimeLabel()
            it.render(Minecraft.getInstance(), mouseX, mouseY)
        }
        updateDrag(screen, layout)
        inline.draw { drawPreview(it, screen, layout); drawIcons(it, screen) }
    }
    *///?}

    private val inline = InlineCanvas("pause_menu_panel")

    private var playtimeRefreshedAt = 0L

    // the label only changes once a minute, so it's rebuilt about once a second rather than every frame
    private fun playtimeDue(): Boolean {
        val now = System.nanoTime()
        if (now - playtimeRefreshedAt < PLAYTIME_REFRESH_NANOS) return false
        playtimeRefreshedAt = now
        return true
    }

    private const val PLAYTIME_REFRESH_NANOS = 1_000_000_000L

    // vanilla screens give no drag events for an area without a widget, so the mouse is polled every frame.
    // a press that starts on the preview rotates it until released, like dragging PlayerPreview
    private var mouseWasDown = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    private fun updateDrag(screen: Screen, layout: Layout) {
        val mouse = mouseState()
        val x = mouse.x * screen.width
        val y = mouse.y * screen.height
        if (mouse.down && !mouseWasDown) {
            preview.dragging = x >= layout.previewX && x < layout.previewX + layout.previewWidth &&
                y >= layout.previewY && y < layout.previewY + layout.previewHeight
        }
        if (!mouse.down) preview.dragging = false
        if (preview.dragging) {
            // PlayerPreview's sensitivity is per framebuffer pixel
            val fbPerGui = Minecraft.getInstance().window.width / screen.width.toFloat()
            preview.dragYaw -= (x - lastMouseX) * fbPerGui * DRAG_DEGREES_PER_PIXEL
            preview.dragPitch = (preview.dragPitch + (y - lastMouseY) * fbPerGui * DRAG_DEGREES_PER_PIXEL)
                .coerceIn(-MAX_DRAG_PITCH, MAX_DRAG_PITCH)
        }
        lastMouseX = x
        lastMouseY = y
        mouseWasDown = mouse.down
    }

    //? if > 1.8.9 {
    // MouseHandler.isLeftPressed only follows the button while no screen is open, so MixinMouseHandler reports it
    private var leftButtonDown = false

    @JvmStatic
    fun onMouseButton(button: Int, action: Int) {
        // 26.3 numbers buttons the SDL way, where left is 1 rather than 0
        if (button == InputConstants.MOUSE_BUTTON_LEFT) leftButtonDown = action == 1
    }
    //?}

    /** The mouse position as a fraction of the window, top-left origin. */
    private class MouseState(val x: Float, val y: Float, val down: Boolean)

    private fun mouseState(): MouseState {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val window = mc.window
        return MouseState(
            (mc.mouseHandler.xpos() / window.screenWidth.coerceAtLeast(1)).toFloat(),
            (mc.mouseHandler.ypos() / window.screenHeight.coerceAtLeast(1)).toFloat(),
            leftButtonDown,
        )
        //?} else {
        /*val width = mc.window.width.coerceAtLeast(1)
        val height = mc.window.height.coerceAtLeast(1)
        return MouseState(Mouse.getX().toFloat() / width, (height - Mouse.getY()).toFloat() / height, Mouse.isButtonDown(0))
        *///?}
    }

    /** A pixel icon drawn left of a vanilla button's label, the pair centered like the title screen's pills. */
    private class ButtonIcon(val x: Int, val y: Int, val icon: String)

    // leading spaces leave room for the icon in the text vanilla centers, as many as the font's space width needs
    private fun iconLabel(text: String): String {
        val space = textWidth(" ").coerceAtLeast(1)
        return " ".repeat((ICON_SIZE + ICON_GAP + space - 1) / space) + text
    }

    // the icon ends ICON_GAP before the text, at the end of the padding, centered the way vanilla centers labels
    private fun buttonIcon(icon: String, x: Int, y: Int, width: Int, label: String, text: String): ButtonIcon {
        val textStart = x + width / 2 - textWidth(label) / 2 + textWidth(label) - textWidth(text)
        return ButtonIcon(textStart - ICON_GAP - ICON_SIZE, y + (BUTTON_HEIGHT - ICON_SIZE) / 2, icon)
    }

    // 24px art drawn 12 GUI pixels tall, so its 2px pixels match the font's
    private const val ICON_SIZE = 12
    private const val ICON_SOURCE_SIZE = 24
    private const val ICON_GAP = 4
    // vanilla's text shadow: one GUI pixel down-right, at a quarter of white
    private const val ICON_SHADOW = 0xFF3F3F3F.toInt()

    private val iconImages = HashMap<String, ImageBitmap?>()

    private fun iconImage(name: String): ImageBitmap? = iconImages.getOrPut(name) {
        runCatching {
            PauseMenuPanel::class.java.getResourceAsStream("/assets/polyplus/textures/gui/pause/$name.png")
                ?.use { PolyBitmap.decode(it.readBytes())?.asComposeImageBitmap() }
        }.getOrNull()
    }

    private val iconPaint by lazy { Paint().apply { filterQuality = FilterQuality.None } }
    // vanilla's text shadow is the color scaled down, which a multiply by the shadow's grey matches
    private val iconShadowFilter by lazy {
        val s = (ICON_SHADOW and 0xFF) / 255f
        ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(s, s, s, 1f) })
    }
    private val iconSource = IntSize(ICON_SOURCE_SIZE, ICON_SOURCE_SIZE)

    private fun drawIcons(canvas: PolyCanvas, screen: Screen) {
        val buttonIcons = icons[screen] ?: return
        if (screen.width <= 0) return
        // the canvas draws in framebuffer pixels
        val scale = Minecraft.getInstance().window.width / screen.width.toFloat()
        for (icon in buttonIcons) {
            val image = iconImage(icon.icon) ?: continue
            drawIcon(canvas, image, icon.x + 1, icon.y + 1, scale, iconShadowFilter)
            drawIcon(canvas, image, icon.x, icon.y, scale, null)
        }
    }

    private fun drawIcon(canvas: PolyCanvas, image: ImageBitmap, x: Int, y: Int, scale: Float, filter: ColorFilter?) {
        iconPaint.colorFilter = filter
        val size = (ICON_SIZE * scale).roundToInt()
        canvas.drawImageRect(image, IntOffset.Zero, iconSource, IntOffset((x * scale).roundToInt(), (y * scale).roundToInt()), IntSize(size, size), iconPaint)
    }

    private fun textWidth(text: String): Int =
        //? if > 1.8.9 {
        Minecraft.getInstance().font.width(text)
        //?} else {
        /*Minecraft.getInstance().textRenderer.getWidth(text)
        *///?}

    private fun drawPreview(canvas: PolyCanvas, screen: Screen, layout: Layout) {
        if (!layout.showsPreview || screen.width <= 0 || screen.height <= 0) return
        val spec = menuPreviewSpec()
        preview.modelScale = spec.modelScale
        preview.fw = layout.previewWidth / screen.width.toFloat()
        preview.fh = layout.previewHeight / screen.height.toFloat()
        // the canvas draws in framebuffer pixels
        val scale = Minecraft.getInstance().window.width / screen.width.toFloat()
        PlayerPreviewOffscreen.drawFaded(
            preview,
            canvas,
            layout.previewX * scale,
            layout.previewY * scale,
            layout.previewWidth * scale,
            layout.previewHeight * scale,
            1f - spec.fadeStart,
        )
    }
}
