@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import org.polyfrost.compose.backend.drawPoly
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.PolyPlusScreensConfig
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle

internal enum class RenderScreenKind(val label: String) {
    PAUSE("Pause"),
    OPTIONS("Settings"),
    INVENTORY("Inventory with watermark"),
    WORLDS("New world"),
    CONNECTING("Connecting"),
    REALMS("Realms"),
    LOADING("Loading screen"),
    BACKDROP("Backdrop"),
}

private const val CORNER_RADIUS = 12
private const val NOTE_PADDING = 10

@Composable
internal fun RenderScreenPreview(
    kind: RenderScreenKind,
    modifier: Modifier,
    note: Boolean = true,
    overlay: (@Composable BoxScope.(guiUnit: Dp) -> Unit)? = null,
) {
    val capture = remember { RenderScreenCapture() }
    DisposableEffect(capture) {
        RenderScreenCaptures.add(capture)
        onDispose { capture.close() }
    }
    SideEffect { capture.kind = kind }
    val frameNanos by produceState(0L) { while (true) withFrameNanos { value = it } }
    Box(modifier.clip(RoundedCornerShape(CORNER_RADIUS.dp))) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                if (frameNanos == 0L) return@drawBehind
                capture.lastDrawNanos = System.nanoTime()
                val cw = capture.contentWidth.toFloat()
                val ch = capture.contentHeight.toFloat()
                if (cw <= 0f || ch <= 0f) return@drawBehind
                val fit = minOf(size.width / cw, size.height / ch)
                val w = cw * fit
                val h = ch * fit
                val x = (size.width - w) / 2f
                val y = (size.height - h) / 2f
                capture.wantedWidth = w
                drawPoly { capture.draw(it, x, y, w, h) }
            },
        )
        if (overlay != null) GuiOverlay(overlay)
        if (note && oursIsCompose(kind)) {
            Text(
                "Showing vanilla. Ours can't be captured yet.",
                style = OneConfigTheme.current.typography.caption,
                modifier = Modifier.align(Alignment.BottomCenter).padding(NOTE_PADDING.dp),
            )
        }
    }
}

@Composable
private fun GuiOverlay(overlay: @Composable BoxScope.(guiUnit: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val window = Minecraft.getInstance().window
        val windowWidth = window.width.toFloat()
        val windowHeight = window.height.toFloat()
        val guiWidth = window.guiScaledWidth
        if (windowWidth <= 0f || windowHeight <= 0f || guiWidth <= 0) return@BoxWithConstraints
        val fit = minOf(maxWidth.value / windowWidth, maxHeight.value / windowHeight)
        val width = windowWidth * fit
        val height = windowHeight * fit
        Box(Modifier.offset(((maxWidth.value - width) / 2f).dp, ((maxHeight.value - height) / 2f).dp).size(width.dp, height.dp)) {
            overlay((width / guiWidth).dp)
        }
    }
}

private fun oursIsCompose(kind: RenderScreenKind): Boolean = when (kind) {
    RenderScreenKind.PAUSE -> PolyPlusPauseConfig.pauseScreenStyle != PauseScreenStyle.VANILLA
    RenderScreenKind.OPTIONS -> PolyPlusScreensConfig.customSettings
    RenderScreenKind.WORLDS -> PolyPlusScreensConfig.customWorlds
    RenderScreenKind.CONNECTING -> PolyPlusScreensConfig.customConnecting
    RenderScreenKind.REALMS -> PolyPlusScreensConfig.customRealms
    RenderScreenKind.LOADING -> PolyPlusConfig.brandedStartup
    RenderScreenKind.INVENTORY, RenderScreenKind.BACKDROP -> false
}
