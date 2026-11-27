@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import org.lwjgl.system.Platform
import org.lwjgl.system.windows.User32
//?}
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenu
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuInput
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuState
import org.polyfrost.compose.minecraft.hud.HudLayer
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeOptions
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.ping.PingGraphics
import org.polyfrost.polyplus.client.ping.PingInput
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.abs

class WheelSession(
    val held: () -> Boolean,
    val onPick: (Int) -> Unit,
    val onScroll: ((Int) -> Unit)? = null,
    val content: @Composable (WheelSession) -> Unit,
) {
    val state = RadialMenuState()

    var input: RadialMenuInput = RadialMenuInput.Steered
        internal set
}

private const val WHEEL_SURFACE_ALPHA = 0.78f
private const val WHEEL_HUB_ALPHA = 0.88f

@Composable
fun HudRadialMenu(
    items: List<RadialMenuItem>,
    session: WheelSession,
    title: String,
    subtitle: String?,
    below: (@Composable () -> Unit)? = null,
) {
    val pointer = session.input == RadialMenuInput.Pointer
    val onSelect: ((Int) -> Unit)? = if (pointer) {
        { WheelOverlay.settle(session, true) }
    } else {
        null
    }
    val onDismiss: (() -> Unit)? = if (pointer) {
        { WheelOverlay.settle(session, false) }
    } else {
        null
    }
    RadialMenu(
        items = items,
        state = session.state,
        onSelect = onSelect,
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle,
        input = session.input,
        glass = liquidGlassTheme && hudBackdropGlass,
        surfaceAlpha = WHEEL_SURFACE_ALPHA,
        hubAlpha = WHEEL_HUB_ALPHA,
        sounds = RadialMenuDefaults.silent,
        pixelRatio = hudPixelRatio,
        onScroll = if (pointer) session.onScroll else null,
        below = below,
    )
}

object WheelOverlay {
    private const val SCROLL_NOTCH = 1.0
    private const val PULL_GAIN = 1.9

    private var session: WheelSession? = null
    private var shown by mutableStateOf<WheelSession?>(null)
    private var lobbyHost: PolyPlusLobbyScreen? = null
    private var lobbyShown by mutableStateOf<WheelSession?>(null)
    private var fadeEndsAt = 0L
    private var scrollAccum = 0.0
    private var started = false

    private val layer = HudLayer("radial_wheel", OneConfigComposeOptions) { Content() }.apply { acceptsInput = false }

    val isOpen: Boolean get() = session != null

    val isOpenOverLobby: Boolean get() = session != null && lobbyHost != null

    fun open(next: WheelSession): Boolean {
        if (session != null || ClientPlatform.currentScreen() != null) return false
        if (Minecraft.getInstance().player == null) return false
        ensureStarted()
        scrollAccum = 0.0
        next.input = RadialMenuInput.Steered
        lobbyHost = null
        session = next
        shown = next
        return true
    }

    fun openOverLobby(next: WheelSession): Boolean {
        if (session != null) return false
        val host = ClientPlatform.currentScreen() as? PolyPlusLobbyScreen ?: return false
        if (!host.emoteWheelReady()) return false
        ensureStarted()
        scrollAccum = 0.0
        next.input = RadialMenuInput.Pointer
        lobbyHost = host
        session = next
        lobbyShown = next
        return true
    }

    fun cancel() = finish(false)

    internal fun settle(target: WheelSession, pick: Boolean) {
        if (session === target) finish(pick)
    }

    @JvmStatic
    fun consumeLook(dx: Double, dy: Double): Boolean {
        val current = session ?: return PingInput.consumePendingLook(dx, dy)
        if (lobbyHost != null) return false
        val scale = PULL_GAIN * sensitivityGain() / layer.density.coerceAtLeast(0.01f)
        current.state.steer((dx * scale).toFloat(), (dy * scale).toFloat())
        return true
    }

    private fun sensitivityGain(): Double = runCatching {
        val options = Minecraft.getInstance().options
        //? if > 1.8.9 {
        val sensitivity = options.sensitivity().get()
        //?} else {
        /*val sensitivity = options.mouseSensitivity.toDouble()
        *///?}
        val f = sensitivity * 0.6 + 0.2
        f * f * f * 8.0
    }.getOrDefault(1.0)

    @JvmStatic
    fun consumeButton(button: Int, pressed: Boolean): Boolean {
        val current = session ?: return false
        if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) return false
        if (!pressed) return false
        finish(isPhysicalLeft(button))
        return true
    }

    private fun isPhysicalLeft(button: Int): Boolean {
        val left = button == InputConstants.MOUSE_BUTTON_LEFT
        //? if > 1.8.9 {
        val swapped = runCatching {
            Platform.get() == Platform.WINDOWS && User32.GetSystemMetrics(User32.SM_SWAPBUTTON) != 0
        }.getOrDefault(false)
        return left != swapped
        //?} else {
        /*return left
        *///?}
    }

    @JvmStatic
    fun consumeScroll(amount: Double): Boolean {
        val current = session ?: return false
        val scroll = current.onScroll ?: return true
        scrollAccum += amount
        while (abs(scrollAccum) >= SCROLL_NOTCH) {
            val up = scrollAccum > 0
            scroll(if (up) -1 else 1)
            scrollAccum -= if (up) SCROLL_NOTCH else -SCROLL_NOTCH
        }
        return true
    }

    @JvmStatic
    fun consumeEscape(): Boolean {
        if (session == null) return false
        cancel()
        return true
    }

    @JvmStatic
    fun render(graphics: PingGraphics?) {
        update()
        if (session == null && shown != null && System.currentTimeMillis() >= fadeEndsAt) shown = null
        if (shown == null) return
        //? if > 1.8.9 {
        if (graphics != null) layer.draw(graphics)
        //?} else {
        /*layer.draw()
        *///?}
    }

    private fun update() {
        val current = session ?: return
        val host = lobbyHost
        val gone = if (host != null) {
            ClientPlatform.currentScreen() !== host || !LobbyScene.showing()
        } else {
            Minecraft.getInstance().player == null || ClientPlatform.currentScreen() != null
        }
        if (gone) {
            cancel()
            return
        }
        if (!current.held()) finish(true)
    }

    private fun expireLobby() {
        if (session == null && lobbyShown != null && System.currentTimeMillis() >= fadeEndsAt) lobbyShown = null
    }

    private fun finish(pick: Boolean) {
        val current = session ?: return
        session = null
        lobbyHost = null
        fadeEndsAt = System.currentTimeMillis() + if (Motion.reduced) 0L else RadialMenuDefaults.fadeMillis.toLong()
        if (!pick) {
            current.state.dismiss()
            return
        }
        val chosen = current.state.select()
        if (chosen >= 0) current.onPick(chosen)
    }

    private fun ensureStarted() {
        if (started) return
        started = true
        eventHandler<TickEvent.End> {
            update()
            expireLobby()
        }
    }

    @Composable
    private fun Content() {
        val current = shown ?: return
        key(current) { current.content(current) }
    }

    @Composable
    fun LobbyLayer() {
        val current = lobbyShown ?: return
        key(current) { current.content(current) }
    }
}
