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
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.platform.ComposeHudLayer
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.privacy.LauncherEnvironment

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
import org.polyfrost.polyplus.client.utils.ClientPlatform
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

    private data class Style(
        val unit: Float,
        val position: Int,
        val offsetX: Float,
        val offsetY: Float,
        val accent: Boolean,
        val color: Int,
    )

    private var style by mutableStateOf<Style?>(null)

    private val layer = ComposeHudLayer("client_watermark") { Content() }.apply { acceptsInput = false }

    @JvmStatic
    fun visibleOn(screen: Screen): Boolean =
        PolyPlusConfig.watermarkEnabled && LauncherEnvironment.isOneClient && shownOn(screen)

    //? if > 1.8.9 {
    private fun shownOn(screen: Screen): Boolean =
        screen is AbstractContainerScreen<*> || screen is OptionsScreen || screen is OptionsSubScreen

    @JvmStatic
    fun render(graphics: PingGraphics, screen: Screen) {
        if (!visibleOn(screen)) return
        update()
        layer.draw(graphics)
    }
    //?} else {
    /*private fun shownOn(screen: Screen): Boolean =
        screen is InventoryMenuScreen || screen is OptionsScreen || screen is VideoOptionsScreen ||
            screen is ControlsOptionsScreen || screen is LanguageOptionsScreen || screen is SoundsScreen ||
            screen is ChatOptionsScreen || screen is SkinCustomizationScreen

    @JvmStatic
    fun render() {
        val screen = ClientPlatform.currentScreen() ?: return
        if (!visibleOn(screen)) return
        update()
        layer.draw()
    }
    *///?}

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
        Placed(with(LocalDensity.current) { current.unit.toDp() }, current)
    }

    @Composable
    internal fun Sample(guiUnit: Dp) {
        val scale = (PolyPlusConfig.watermarkScale / 100f).coerceIn(MIN_SCALE, MAX_SCALE)
        Placed(
            guiUnit * scale,
            Style(
                0f,
                PolyPlusConfig.watermarkPosition,
                PolyPlusConfig.watermarkOffsetX,
                PolyPlusConfig.watermarkOffsetY,
                PolyPlusConfig.watermarkAccentColor,
                PolyPlusConfig.watermarkColor.argb,
            ),
        )
    }

    @Composable
    private fun Placed(unit: Dp, current: Style) {
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
