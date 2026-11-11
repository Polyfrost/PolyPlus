package org.polyfrost.polyplus.client.ping

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.gui.WheelOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.ping.PingPickInvoker
import kotlin.experimental.or
import kotlin.math.abs

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

    private const val HOLD_MS = 200L
    private const val DRAG_OPEN_PX = 7.0
    private const val CLEAR_RADIUS = 14f
    private const val SEND_PITCH = 1.6f
    private const val RECEIVE_PITCH = 1.25f
    private const val REPLY_PITCH = 0.9f
    private const val VOLUME = 0.6f

    @Volatile
    private var held = false
    private var pending = false
    private var pressedAt = 0L
    private var dragX = 0.0
    private var dragY = 0.0
    private var started = false
    private var seen: Set<String> = emptySet()

    @Volatile
    private var pickDeferred = false
    private var vanillaPick = false

    fun ensureStarted() {
        if (started) return
        started = true
        eventHandler<TickEvent.End> { tick() }
    }

    fun isHeld(): Boolean = held

    @JvmStatic
    fun onKeybindState(pressed: Boolean) {
        ensureStarted()
        if (pressed) {
            held = true
            if (!canPing() || WheelOverlay.isOpen) return
            pending = true
            pressedAt = System.currentTimeMillis()
            dragX = 0.0
            dragY = 0.0
            return
        }
        held = false
        if (!pending) {
            pickDeferred = false
            return
        }
        pending = false
        val deferred = pickDeferred
        pickDeferred = false
        if (!canPing()) return
        if (deferred && crosshairInReach()) pickBlock() else tap()
    }

    fun consumePendingLook(dx: Double, dy: Double): Boolean {
        if (!pending || !held) return false
        dragX += dx
        dragY += dy
        if (dragX * dragX + dragY * dragY < DRAG_OPEN_PX * DRAG_OPEN_PX) return true
        val seedX = dragX
        val seedY = dragY
        pending = false
        pickDeferred = false
        if (canPing() && !WheelOverlay.isOpen && openWheel()) WheelOverlay.consumeLook(seedX, seedY)
        return true
    }

    @JvmStatic
    fun takesPickBlock(): Boolean {
        if (vanillaPick || !canPing()) return false
        val bind = PolyPlusConfig.pingKeybind
        if (!bind.isBound || !sharesPickInput(bind)) return false
        if (!pending && !isBindDown(bind)) return false
        pickDeferred = true
        return true
    }

    private fun sharesPickInput(bind: OneConfigKeybind): Boolean {
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

    private fun isBindDown(bind: OneConfigKeybind): Boolean {
        val buttons = bind.mouseBtns
        if (buttons != null && buttons.isNotEmpty()) return buttons.any { KeybindManager.isTriggeredByMouse(bind, it) }
        val keys = KeybindManager.heldKeys
        var mods = KeyModifiers.NONE
        for (key in keys) mods = mods or KeyModifiers.of(key)
        return bind.test(keys, emptySet(), mods)
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
        PartyPings.reply(ping, next)
    }

    internal fun sendFromWheel(kind: PingKind, hit: PingHit?) {
        send(kind, hit ?: PingTargeting.raycast(1f) ?: return)
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

    private fun pickBlock() {
        vanillaPick = true
        try {
            (Minecraft.getInstance() as PingPickInvoker).`polyplus$pick`()
        } finally {
            vanillaPick = false
        }
    }

    private fun tick() {
        if (pending && held && System.currentTimeMillis() - pressedAt >= HOLD_MS) {
            pending = false
            pickDeferred = false
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
        if (previous != null && now - previous.at <= DOUBLE_TAP_MS) {
            val ping = PartyPings.pings.value.firstOrNull { it.own && it.id == previous.id }
            if (ping != null && (ping.kind == PingKind.GO || ping.kind == PingKind.LOOK) && (ping.target == null || ping.target.type == PingTargetType.BLOCK)) {
                PartyPings.clear(ping.id)
                send(PingKind.DANGER, previous.hit)
                return
            }
        }
        pingUnderCrosshair(PartyPings.pings.value.filter { it.own })?.let {
            PartyPings.clear(it.id)
            return
        }
        replyTarget()?.let {
            val next = if (PartyPings.myReply(it.id) == PingReply.COMING) PingReply.NONE else PingReply.COMING
            PartyPings.reply(it, next)
            return
        }
        val hit = PingTargeting.raycast(1f) ?: return
        val id = send(hit.kind, hit) ?: return
        lastTap = LastTap(id, hit, now)
    }

    private fun replyTarget(): Ping? {
        if (pingUnderCrosshair(PartyPings.pings.value.filter { it.own }) != null) return null
        return pingUnderCrosshair(PingMarkers.visiblePings().filter { !it.own && PartyPings.canReply(it) })
    }

    private fun pingUnderCrosshair(candidates: List<Ping>): Ping? {
        if (candidates.isEmpty()) return null
        val cx = PingProjection.guiWidth() / 2f
        val cy = PingProjection.guiHeight() / 2f
        val radius = CLEAR_RADIUS * PolyPlusPartyConfig.pingMarkerScale / 100f
        return candidates.firstOrNull { ping ->
            val point = PingMarkers.screenPosition(ping, 1f) ?: return@firstOrNull false
            point.onScreen && abs(point.x - cx) <= radius && abs(point.y - cy) <= radius
        }
    }

    private fun send(kind: PingKind, hit: PingHit): String? {
        val dimension = PartyPings.currentDimension() ?: return null
        val label = hit.label?.takeIf { hit.target != null }
        if (!PartyPings.send(kind, dimension, hit.x, hit.y, hit.z, hit.target, label)) return null
        seen = seen + PartyPings.pings.value.map { it.id }
        playSound(SEND_PITCH)
        return PartyPings.pings.value.filter { it.own }.maxByOrNull { it.sentAt }?.id
    }

    private class LastTap(val id: String, val hit: PingHit, val at: Long)

    private fun playSound(pitch: Float) {
        if (!PolyPlusPartyConfig.pingsSound) return
        //? if > 1.8.9 {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, VOLUME))
        //?} else {
        /*Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(Identifier("note.pling"), pitch, VOLUME))
        *///?}
    }
}
