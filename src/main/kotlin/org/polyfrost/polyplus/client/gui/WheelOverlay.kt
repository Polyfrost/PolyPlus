@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.polyplus.client.ping.PingGraphics
import org.polyfrost.polyplus.client.ping.PingInput
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class WheelSession(
    val held: () -> Boolean,
    val onPick: (Int) -> Unit,
    val onScroll: ((Int) -> Unit)? = null,
    val content: @Composable (WheelSession) -> Unit,
) {
    var pointer by mutableStateOf(Offset.Zero)
    var selected by mutableStateOf(-1)
    var closing by mutableStateOf(false)

    @Volatile
    var hovered = -1

    @Volatile
    var radius = 0f

    @Volatile
    var square = false

    @Volatile
    var deadZone = 0f

    @Volatile
    var enabled: List<Boolean> = emptyList()

    private var raw = Offset.Zero
    private var aimed = -1

    fun steer(delta: Offset) {
        if (closing) return
        val limit = radius
        var next = raw + delta
        if (limit > 0f) {
            val reach = reach(next)
            val cap = limit * RAW_CAP
            if (reach > cap) next *= cap / reach
        }
        raw = next
        pointer = if (limit > 0f) rubberBand(next, limit) else next
        hovered = aim(pointer)
        selected = hovered
    }

    private fun reach(p: Offset): Float = if (square) max(abs(p.x), abs(p.y)) else p.getDistance()

    private fun rubberBand(p: Offset, limit: Float): Offset {
        val reach = reach(p)
        val knee = limit * KNEE_SHARE
        if (reach <= knee) return p
        val band = limit - knee
        val soft = knee + band * (1f - exp(-(reach - knee) / band))
        return p * (soft / reach)
    }

    private fun aim(p: Offset): Int {
        val flags = enabled
        val count = flags.size
        if (count == 0 || reach(p) < deadZone) {
            aimed = -1
            return -1
        }
        val sector = 360f / count
        val angle = (Math.toDegrees(atan2(p.y.toDouble(), p.x.toDouble())).toFloat() + 90f + 720f) % 360f
        val previous = aimed
        val keep = previous in 0 until count && run {
            var diff = abs(angle - previous * sector) % 360f
            if (diff > 180f) diff = 360f - diff
            diff <= sector / 2f + min(HYSTERESIS_DEG, sector * HYSTERESIS_SHARE)
        }
        if (!keep) aimed = ((angle + sector / 2f) / sector).toInt() % count
        return if (flags[aimed]) aimed else -1
    }

    private companion object {
        const val RAW_CAP = 1.25f
        const val KNEE_SHARE = 0.85f
        const val HYSTERESIS_DEG = 5f
        const val HYSTERESIS_SHARE = 0.2f
    }
}

object WheelOverlay {
    const val FADE_MILLIS = 120

    private const val SCROLL_NOTCH = 1.0
    private const val PULL_GAIN = 1.9

    private var session: WheelSession? = null
    private var shown by mutableStateOf<WheelSession?>(null)
    private var fadeEndsAt = 0L
    private var scrollAccum = 0.0
    private var started = false

    private val host = HudComposeHost("radial_wheel") { Content() }

    val isOpen: Boolean get() = session != null

    fun open(next: WheelSession): Boolean {
        if (session != null || ClientPlatform.currentScreen() != null) return false
        if (Minecraft.getInstance().player == null) return false
        ensureStarted()
        scrollAccum = 0.0
        session = next
        shown = next
        return true
    }

    fun cancel() = finish(null)

    @JvmStatic
    fun consumeLook(dx: Double, dy: Double): Boolean {
        val current = session ?: return PingInput.consumePendingLook(dx, dy)
        val scale = PULL_GAIN * sensitivityGain() / host.density.coerceAtLeast(0.01f)
        current.steer(Offset((dx * scale).toFloat(), (dy * scale).toFloat()))
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
        if (button != 0 && button != 1) return false
        if (!pressed) return false
        if (physical(button) == 0) finish(current.hovered) else finish(null)
        return true
    }

    private fun physical(button: Int): Int {
        //? if > 1.8.9 {
        val swapped = runCatching {
            org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.WINDOWS &&
                org.lwjgl.system.windows.User32.GetSystemMetrics(org.lwjgl.system.windows.User32.SM_SWAPBUTTON) != 0
        }.getOrDefault(false)
        return if (swapped) 1 - button else button
        //?} else {
        /*return button
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
        host.draw(graphics)
    }

    private fun update() {
        val current = session ?: return
        if (Minecraft.getInstance().player == null || ClientPlatform.currentScreen() != null) {
            cancel()
            return
        }
        if (!current.held()) finish(current.hovered)
    }

    private fun finish(pick: Int?) {
        val current = session ?: return
        session = null
        current.closing = true
        fadeEndsAt = System.currentTimeMillis() + if (Motion.reduced) 0L else FADE_MILLIS.toLong()
        if (pick != null && pick >= 0) current.onPick(pick)
    }

    private fun ensureStarted() {
        if (started) return
        started = true
        eventHandler<TickEvent.End> { update() }
    }

    @Composable
    private fun Content() {
        val current = shown ?: return
        ProvideOneConfigTheme {
            key(current) { current.content(current) }
        }
    }
}
