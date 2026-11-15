package org.polyfrost.polyplus.client.ping

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.events.WindowFocusEvent
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.gui.WheelOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.experimental.or
import kotlin.math.roundToInt

//? if > 1.8.9 {
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.phys.HitResult
//?} else {
/*import net.minecraft.resource.Identifier
import net.minecraft.world.HitResult
import org.polyfrost.oneconfig.internal.legacy.InputConstants as LegacyInputConstants
import org.polyfrost.oneconfig.internal.legacy.KeyCodes
*///?}

object PingInput {
    private const val DOUBLE_TAP_MS = 350L
    private var lastTap: LastTap? = null

    private const val HOLD_MS = 350L
    private const val DRAG_DELAY_MS = 150L
    private const val DRAG_OPEN_PX = 7.0
    private const val SEND_PITCH = 1.6f
    private const val RECEIVE_PITCH = 1.25f
    private const val REPLY_PITCH = 0.9f
    private const val BLOCKED_PITCH = 0.7f
    private const val VOLUME = 0.6f
    private const val HINT_MS = 1600L
    private const val HINT_FADE_MS = 300L
    private const val HINT_OFFSET = 16
    private const val HINT_PAD_X = 4
    private const val HINT_PAD_Y = 2
    private const val HINT_PLATE = 0xB0101014.toInt()
    private const val HINT_TEXT = 0xFFFFFFFF.toInt()
    private const val MIN_ALPHA = 0.05f
    private const val MS_PER_SECOND = 1000L

    @Volatile
    private var held = false
    private var pending = false
    private var pressedAt = 0L
    private var dragX = 0.0
    private var dragY = 0.0
    private var started = false
    private var seen: Set<String> = emptySet()

    private var pickPress = false
    private var swallowPick = false

    private val downKeys = HashSet<Int>()
    private val downMouse = HashSet<Int>()
    private var rawActive = false
    private var rawSeen = false

    private var hint: String? = null
    private var hintUntil = 0L

    fun ensureStarted() {
        if (started) return
        started = true
        eventHandler<TickEvent.End> { tick() }
        eventHandler { (button, state): MouseInputEvent ->
            if (state == MouseInputEvent.PRESSED) downMouse.add(button) else downMouse.remove(button)
            syncRaw()
        }
        eventHandler { (key, _, state): KeyInputEvent ->
            if (key == 0 || state == KeyInputEvent.REPEAT) return@eventHandler
            if (state == KeyInputEvent.PRESSED) downKeys.add(key) else downKeys.remove(key)
            syncRaw()
        }
        eventHandler { _: WindowFocusEvent.Lost -> resetRaw() }
    }

    fun isHeld(): Boolean = held

    @JvmStatic
    fun onKeybindState(pressed: Boolean) {
        ensureStarted()
        if (rawSeen) return
        if (pressed) press() else release()
    }

    private fun syncRaw() {
        val bind = PolyPlusConfig.pingKeybind
        var mods = KeyModifiers.NONE
        for (key in downKeys) mods = mods or KeyModifiers.of(key)
        val active = bind.test(downKeys, downMouse, mods)
        if (active == rawActive) return
        rawActive = active
        rawSeen = true
        if (active) press() else release()
    }

    private fun resetRaw() {
        downKeys.clear()
        downMouse.clear()
        if (!rawActive) return
        rawActive = false
        held = false
        pending = false
        pickPress = false
    }

    private fun press() {
        held = true
        pending = false
        pickPress = false
        swallowPick = false
        if (!canPing() || WheelOverlay.isOpen || TextInputFocus.isTyping) return
        pending = true
        pressedAt = System.currentTimeMillis()
        dragX = 0.0
        dragY = 0.0
        if (!sharesPickInput(PolyPlusConfig.pingKeybind)) return
        if (hoveredTarget() == null && crosshairInReach() && !usingItem()) pickPress = true else swallowPick = true
    }

    private fun release() {
        held = false
        if (!pending) return
        pending = false
        val picked = pickPress
        pickPress = false
        if (!picked && canPing()) tap()
    }

    fun consumePendingLook(dx: Double, dy: Double): Boolean {
        if (!pending || !held) return false
        if (System.currentTimeMillis() - pressedAt < DRAG_DELAY_MS) return false
        dragX += dx
        dragY += dy
        if (dragX * dragX + dragY * dragY < DRAG_OPEN_PX * DRAG_OPEN_PX) return true
        val seedX = dragX
        val seedY = dragY
        pending = false
        pickPress = false
        if (canPing() && !WheelOverlay.isOpen && openWheel()) WheelOverlay.consumeLook(seedX, seedY)
        return true
    }

    @JvmStatic
    fun takesPickBlock(): Boolean {
        if (!swallowPick) return false
        swallowPick = false
        return true
    }

    private fun sharesPickInput(bind: OneConfigKeybind): Boolean {
        if (!bind.isBound) return false
        //? if > 1.8.9 {
        val key = InputConstants.getKey(Minecraft.getInstance().options.keyPickItem.saveString())
        if (key.type == InputConstants.Type.MOUSE) return bind.mouseBtns?.contains(key.value) == true
        return bind.keyCodes?.contains(key.value) == true
        //?} else {
        /*val code = Minecraft.getInstance().options.pickItemKey.keyCode
        if (code == 0) return false
        val key = KeyCodes.fromLegacy(code)
        if (key.type == LegacyInputConstants.Type.MOUSE) return bind.mouseBtns?.contains(key.value) == true
        return bind.keyCodes?.contains(key.value) == true
        *///?}
    }

    fun canPing(): Boolean {
        if (!PolyPlusPartyConfig.pingsEnabled) return false
        val mc = Minecraft.getInstance()
        if (mc.player == null || ClientPlatform.currentScreen() != null) return false
        if (LobbyScene.terrainActive()) return false
        return PartyPings.currentDimension() != null
    }

    fun replyCue() {
        if (PolyPlusPartyConfig.pingsEnabled) playSound(REPLY_PITCH)
    }

    private fun openWheel(): Boolean {
        val ping = replyTarget()
        return if (ping != null) PingReplyWheel.open(ping) else PingWheel.open()
    }

    internal fun replyFromWheel(ping: Ping, reply: PingReply) {
        val next = if (PartyPings.myReply(ping.id) == reply) PingReply.NONE else reply
        sendReply(ping, next)
    }

    internal fun sendFromWheel(kind: PingKind, hit: PingHit?) {
        val target = hit ?: PingTargeting.raycast(1f)
        if (target == null) blocked("Nothing to ping there") else send(kind, target)
    }

    private fun usingItem(): Boolean {
        //? if > 1.8.9 {
        return Minecraft.getInstance().player?.isUsingItem == true
        //?} else {
        /*return false
        *///?}
    }

    private fun crosshairInReach(): Boolean {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        val type = mc.hitResult?.type ?: return false
        return type != HitResult.Type.MISS
        //?} else {
        /*val type = mc.crosshairTarget?.type ?: return false
        return type != HitResult.Type.MISS
        *///?}
    }

    private fun tick() {
        if (!held) swallowPick = false
        if (pending && held && System.currentTimeMillis() - pressedAt >= HOLD_MS) {
            pending = false
            pickPress = false
            if (canPing() && !WheelOverlay.isOpen) openWheel()
        }
        val pings = PartyPings.pings.value
        val ids = pings.mapTo(HashSet()) { it.id }
        if (PolyPlusPartyConfig.pingsEnabled && PolyPlusPartyConfig.pingsShowOthers) {
            if (pings.any { !it.own && it.id !in seen }) playSound(RECEIVE_PITCH)
        }
        seen = ids
    }

    private fun tap() {
        val now = System.currentTimeMillis()
        val previous = lastTap
        lastTap = null
        if (previous != null && now - previous.at <= DOUBLE_TAP_MS && escalate(previous)) return
        val hovered = hoveredTarget()
        if (hovered != null) {
            if (hovered.own) {
                PartyPings.clear(hovered.id)
            } else {
                sendReply(hovered, if (PartyPings.myReply(hovered.id) == PingReply.COMING) PingReply.NONE else PingReply.COMING)
            }
            return
        }
        val hit = PingTargeting.raycast(1f)
        if (hit == null) {
            blocked("Nothing to ping there")
            return
        }
        val id = send(hit.kind, hit) ?: return
        lastTap = LastTap(id, hit, now)
    }

    private fun escalate(previous: LastTap): Boolean {
        val ping = PartyPings.pings.value.firstOrNull { it.own && it.id == previous.id } ?: return false
        if (ping.kind != PingKind.GO && ping.kind != PingKind.LOOK) return false
        if (ping.target != null && ping.target.type != PingTargetType.BLOCK) return false
        PartyPings.clear(ping.id)
        send(PingKind.DANGER, previous.hit)
        return true
    }

    private fun hoveredTarget(): Ping? = PingMarkers.hoveredPing()?.takeIf { it.own || PartyPings.canReply(it) }

    private fun replyTarget(): Ping? = hoveredTarget()?.takeUnless { it.own }

    private fun sendReply(ping: Ping, reply: PingReply) {
        if (PartyPings.reply(ping, reply)) return
        val wait = PartyPings.replyCooldownMs()
        blocked(if (wait > 0L) cooldownText(wait) else "Could not send your reply")
    }

    private fun send(kind: PingKind, hit: PingHit): String? {
        val dimension = PartyPings.currentDimension() ?: return null
        val label = hit.label?.takeIf { hit.target != null }
        if (!PartyPings.send(kind, dimension, hit.x, hit.y, hit.z, hit.target, label)) {
            val wait = PartyPings.sendCooldownMs()
            blocked(if (wait > 0L) cooldownText(wait) else "Could not ping there")
            return null
        }
        hint = null
        seen = seen + PartyPings.pings.value.map { it.id }
        playSound(SEND_PITCH)
        return PartyPings.pings.value.filter { it.own }.maxByOrNull { it.sentAt }?.id
    }

    private fun cooldownText(wait: Long): String {
        val seconds = (wait + MS_PER_SECOND - 1) / MS_PER_SECOND
        return "Too many pings, try again in ${seconds}s"
    }

    private fun blocked(message: String) {
        hint = message
        hintUntil = System.currentTimeMillis() + HINT_MS
        playSound(BLOCKED_PITCH, blocked = true)
    }

    @JvmStatic
    fun renderHint(graphics: PingGraphics?) {
        val message = hint ?: return
        val left = hintUntil - System.currentTimeMillis()
        if (left <= 0L || !canPing()) {
            hint = null
            return
        }
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        if (mc.gui.hud.isHidden) return
        //?} else {
        /*if (mc.options.hideGui) return
        *///?}
        val alpha = (left.toFloat() / HINT_FADE_MS).coerceIn(0f, 1f)
        if (alpha < MIN_ALPHA) return
        val canvas = PingCanvas(graphics)
        val cx = (PingProjection.guiWidth() / 2f).roundToInt()
        val top = (PingProjection.guiHeight() / 2f).roundToInt() + HINT_OFFSET
        val half = canvas.width(message) / 2 + HINT_PAD_X
        val bottom = top + canvas.lineHeight + HINT_PAD_Y * 2
        canvas.fill(cx - half, top, cx + half, bottom, scaled(HINT_PLATE, alpha))
        canvas.text(message, cx - canvas.width(message) / 2, top + HINT_PAD_Y + 1, scaled(HINT_TEXT, alpha))
    }

    private fun scaled(argb: Int, alpha: Float): Int {
        val base = (argb ushr 24) and 0xFF
        return ((base * alpha).roundToInt() shl 24) or (argb and 0xFFFFFF)
    }

    private class LastTap(val id: String, val hit: PingHit, val at: Long)

    private fun playSound(pitch: Float, blocked: Boolean = false) {
        if (!PolyPlusPartyConfig.pingsSound) return
        //? if > 1.8.9 {
        val sound = if (blocked) SoundEvents.NOTE_BLOCK_BASS.value() else SoundEvents.NOTE_BLOCK_BELL.value()
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, VOLUME))
        //?} else {
        /*val sound = Identifier(if (blocked) "note.bass" else "note.pling")
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, VOLUME))
        *///?}
    }
}
