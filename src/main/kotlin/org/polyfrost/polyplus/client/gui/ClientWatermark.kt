@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.compose.minecraft.hud.HudLayer
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeOptions
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.LauncherEnvironment
import kotlin.math.abs

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.options.OptionsSubScreen
import org.polyfrost.polyplus.client.ping.PingGraphics
//?} else {
/*import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen
import net.minecraft.client.gui.screen.menu.options.ChatOptionsScreen
import net.minecraft.client.gui.screen.menu.options.ControlsOptionsScreen
import net.minecraft.client.gui.screen.menu.options.LanguageOptionsScreen
import net.minecraft.client.gui.screen.menu.options.OptionsScreen
import net.minecraft.client.gui.screen.menu.options.SkinCustomizationScreen
import net.minecraft.client.gui.screen.menu.options.SoundsScreen
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen
import org.lwjgl.input.Mouse
*///?}

object ClientWatermark {
    const val POSITION_TOP_LEFT = 0
    const val POSITION_TOP_RIGHT = 1
    const val POSITION_BOTTOM_LEFT = 2
    const val POSITION_BOTTOM_RIGHT = 3
    const val POSITION_TOP_CENTER = 4
    const val POSITION_BOTTOM_CENTER = 5
    const val POSITION_LEFT_CENTER = 6
    const val POSITION_RIGHT_CENTER = 7

    private const val LOGO = "assets/polyplus/mainmenu/logo.svg"
    private const val WORDMARK = "assets/polyplus/brand/oneclient.svg"

    private const val LOGO_SIZE = 17f
    private const val GAP = 5f
    private const val WORDMARK_HEIGHT = 11f
    private const val WORDMARK_WIDTH = WORDMARK_HEIGHT * (159f / 19f)

    private const val SHADOW_OFFSET = 0.5f
    private const val SHADOW_ALPHA = 0.2f

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 4f
    private const val MAX_OFFSET = 200f
    private const val ZONES = 3f

    private data class Style(
        val unit: Float,
        val position: Int,
        val offsetX: Float,
        val offsetY: Float,
        val accent: Boolean,
        val color: Int,
    )

    private var style by mutableStateOf<Style?>(null)

    private val layer = HudLayer("client_watermark", OneConfigComposeOptions) { Content() }.apply { acceptsInput = false }

    @JvmStatic
    fun visibleOn(screen: Screen): Boolean =
        PolyPlusConfig.watermarkEnabled && LauncherEnvironment.isOneClient && shownOn(screen)

    //? if > 1.8.9 {
    private fun shownOn(screen: Screen): Boolean =
        screen is AbstractContainerScreen<*> || screen is OptionsScreen || screen is OptionsSubScreen

    private fun draggableOn(screen: Screen): Boolean = screen is AbstractContainerScreen<*>

    private fun mouse(screen: Screen): Pair<Float, Float> {
        val mc = Minecraft.getInstance()
        val window = mc.window
        return Pair(
            (mc.mouseHandler.xpos() * screen.width / window.screenWidth.coerceAtLeast(1)).toFloat(),
            (mc.mouseHandler.ypos() * screen.height / window.screenHeight.coerceAtLeast(1)).toFloat(),
        )
    }

    @JvmStatic
    fun render(graphics: PingGraphics, screen: Screen) {
        if (!visibleOn(screen)) return
        drag(screen)
        update()
        layer.draw(graphics)
    }
    //?} else {
    /*private fun shownOn(screen: Screen): Boolean =
        screen is InventoryMenuScreen || screen is OptionsScreen || screen is VideoOptionsScreen ||
            screen is ControlsOptionsScreen || screen is LanguageOptionsScreen || screen is SoundsScreen ||
            screen is ChatOptionsScreen || screen is SkinCustomizationScreen

    private fun draggableOn(screen: Screen): Boolean = screen is InventoryMenuScreen

    private fun mouse(screen: Screen): Pair<Float, Float> {
        val window = Minecraft.getInstance().window
        return Pair(
            Mouse.getX().toFloat() * screen.width / window.width.coerceAtLeast(1),
            screen.height - Mouse.getY().toFloat() * screen.height / window.height.coerceAtLeast(1),
        )
    }

    @JvmStatic
    fun render() {
        val screen = ClientPlatform.currentScreen() ?: return
        if (!visibleOn(screen)) return
        drag(screen)
        update()
        layer.draw()
    }
    *///?}

    private class Bounds(val left: Float, val top: Float, val width: Float, val height: Float)

    private var dragging = false
    private var dragScreen: Screen? = null
    private var grabX = 0f
    private var grabY = 0f

    private fun scaleFactor(): Float = (PolyPlusConfig.watermarkScale / 100f).coerceIn(MIN_SCALE, MAX_SCALE)

    private fun onRight(position: Int): Boolean =
        position == POSITION_TOP_RIGHT || position == POSITION_BOTTOM_RIGHT || position == POSITION_RIGHT_CENTER

    private fun onBottom(position: Int): Boolean =
        position == POSITION_BOTTOM_LEFT || position == POSITION_BOTTOM_RIGHT || position == POSITION_BOTTOM_CENTER

    private fun bounds(screen: Screen): Bounds {
        val scale = scaleFactor()
        val width = (LOGO_SIZE + GAP + WORDMARK_WIDTH) * scale
        val height = LOGO_SIZE * scale
        val position = PolyPlusConfig.watermarkPosition
        val offsetX = PolyPlusConfig.watermarkOffsetX * scale
        val offsetY = PolyPlusConfig.watermarkOffsetY * scale
        val left = when {
            centeredX(position) -> (screen.width - width) / 2
            onRight(position) -> screen.width - offsetX - width
            else -> offsetX
        }
        val top = when {
            centeredY(position) -> (screen.height - height) / 2
            onBottom(position) -> screen.height - offsetY - height
            else -> offsetY
        }
        return Bounds(left, top, width, height)
    }

    private fun zone(value: Float, size: Float): Int = when {
        value < size / ZONES -> -1
        value > size * 2 / ZONES -> 1
        else -> 0
    }

    @JvmStatic
    fun onPress(): Boolean {
        val screen = ClientPlatform.currentScreen() ?: return false
        if (!visibleOn(screen) || !draggableOn(screen)) return false
        val (x, y) = mouse(screen)
        val area = bounds(screen)
        if (x < area.left || x > area.left + area.width || y < area.top || y > area.top + area.height) return false
        grabX = x - area.left
        grabY = y - area.top
        dragging = true
        dragScreen = screen
        return true
    }

    @JvmStatic
    fun onRelease(): Boolean {
        if (!dragging) return false
        dragging = false
        dragScreen = null
        PolyPlusConfig.save()
        return true
    }

    private fun drag(screen: Screen) {
        if (!dragging) return
        if (screen !== dragScreen) {
            onRelease()
            return
        }
        val (x, y) = mouse(screen)
        val scale = scaleFactor()
        val area = bounds(screen)
        val screenWidth = screen.width.toFloat()
        val screenHeight = screen.height.toFloat()
        val left = x - grabX
        val top = y - grabY
        val centerX = left + area.width / 2
        val centerY = top + area.height / 2
        var column = zone(centerX, screenWidth)
        var row = zone(centerY, screenHeight)
        if (column == 0 && row == 0) {
            if (abs(centerX - screenWidth / 2) / screenWidth >= abs(centerY - screenHeight / 2) / screenHeight) {
                column = if (centerX < screenWidth / 2) -1 else 1
            } else {
                row = if (centerY < screenHeight / 2) -1 else 1
            }
        }
        PolyPlusConfig.watermarkPosition = when {
            column < 0 && row < 0 -> POSITION_TOP_LEFT
            column > 0 && row < 0 -> POSITION_TOP_RIGHT
            column < 0 && row > 0 -> POSITION_BOTTOM_LEFT
            column > 0 && row > 0 -> POSITION_BOTTOM_RIGHT
            row < 0 -> POSITION_TOP_CENTER
            row > 0 -> POSITION_BOTTOM_CENTER
            column < 0 -> POSITION_LEFT_CENTER
            else -> POSITION_RIGHT_CENTER
        }
        if (column < 0) PolyPlusConfig.watermarkOffsetX = (left / scale).coerceIn(0f, MAX_OFFSET)
        if (column > 0) PolyPlusConfig.watermarkOffsetX = ((screenWidth - left - area.width) / scale).coerceIn(0f, MAX_OFFSET)
        if (row < 0) PolyPlusConfig.watermarkOffsetY = (top / scale).coerceIn(0f, MAX_OFFSET)
        if (row > 0) PolyPlusConfig.watermarkOffsetY = ((screenHeight - top - area.height) / scale).coerceIn(0f, MAX_OFFSET)
    }

    private fun update() {
        val window = Platform.screen().windowWidth()
        if (window <= 0) return
        val ratio = (Platform.screen().viewportWidth().toFloat() / window).coerceAtLeast(0.01f)
        val scale = (PolyPlusConfig.watermarkScale / 100f).coerceIn(MIN_SCALE, MAX_SCALE)
        style = Style(
            mcGuiScale() / ratio * scale,
            PolyPlusConfig.watermarkPosition,
            PolyPlusConfig.watermarkOffsetX,
            PolyPlusConfig.watermarkOffsetY,
            PolyPlusConfig.watermarkAccentColor,
            PolyPlusConfig.watermarkColor.argb,
        )
        Snapshot.sendApplyNotifications()
    }

    @Composable
    private fun Content() {
        val current = style ?: return
        val unit = with(LocalDensity.current) { current.unit.toDp() }
        val color = if (current.accent) OneConfigTheme.accent else Color(current.color)
        val offsetX = if (centeredX(current.position)) 0f else current.offsetX
        val offsetY = if (centeredY(current.position)) 0f else current.offsetY
        Box(Modifier.fillMaxSize().padding(horizontal = unit * offsetX, vertical = unit * offsetY)) {
            Box(Modifier.align(alignment(current.position))) {
                Mark(OneConfigTheme.current.onMediaShadow.copy(alpha = SHADOW_ALPHA), unit, Modifier.offset(y = unit * SHADOW_OFFSET))
                Mark(color, unit, Modifier)
            }
        }
    }

    @Composable
    private fun Mark(color: Color, unit: Dp, modifier: Modifier) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(unit * GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(name = LOGO, contentDescription = null, tint = color, modifier = Modifier.size(unit * LOGO_SIZE))
            Icon(name = WORDMARK, contentDescription = null, tint = color, modifier = Modifier.size(unit * WORDMARK_WIDTH, unit * WORDMARK_HEIGHT))
        }
    }

    private fun centeredX(position: Int): Boolean =
        position == POSITION_TOP_CENTER || position == POSITION_BOTTOM_CENTER

    private fun centeredY(position: Int): Boolean =
        position == POSITION_LEFT_CENTER || position == POSITION_RIGHT_CENTER

    private fun alignment(position: Int): Alignment = when (position) {
        POSITION_TOP_LEFT -> Alignment.TopStart
        POSITION_TOP_RIGHT -> Alignment.TopEnd
        POSITION_BOTTOM_LEFT -> Alignment.BottomStart
        POSITION_BOTTOM_RIGHT -> Alignment.BottomEnd
        POSITION_BOTTOM_CENTER -> Alignment.BottomCenter
        POSITION_LEFT_CENTER -> Alignment.CenterStart
        POSITION_RIGHT_CENTER -> Alignment.CenterEnd
        else -> Alignment.TopCenter
    }
}
