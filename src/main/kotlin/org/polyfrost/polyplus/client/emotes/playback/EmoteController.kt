package org.polyfrost.polyplus.client.emotes.playback

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.util.Mth
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.mixin.client.signals.SignalsTimerAccessor
*///?}
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticOrigin
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticView
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.render.FirstPersonView
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.emotes.EmoteRole
import org.polyfrost.polyplus.client.emotes.EmoteServerClock
import org.polyfrost.polyplus.client.emotes.EndedEmote
import org.polyfrost.polyplus.client.emotes.conditions.EmoteConditions
import org.polyfrost.polyplus.client.emotes.duo.DuoEmoteState
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.emotes.duo.DuoPlacement
import org.polyfrost.polyplus.client.emotes.duo.DuoPlayers
import org.polyfrost.polyplus.client.emotes.props.EmotePropDraw
import org.polyfrost.polyplus.client.lobby.LobbyAvatar
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import java.util.concurrent.atomic.AtomicLong

//? if >= 1.21.11 {
import net.minecraft.util.Util
//?}

//? if < 1.21.11 && > 1.8.9 {
/*import net.minecraft.Util
*///?}

//? if = 1.8.9 {
/*private object Util {
    fun getMillis(): Long = Minecraft.getTime()
}
*///?}

private object EmoteClock {
    private val origin = System.nanoTime()

    fun seconds(): Double = (System.nanoTime() - origin) / NANOS_PER_SECOND

    private const val NANOS_PER_SECOND = 1_000_000_000.0
}

class EmoteController {
    var clock: (() -> Long)? = null

    private fun now(): Long = clock?.invoke() ?: Util.getMillis()

    private var emote: Emote? = null

    private var role: EmoteRole? = null

    private var placed: EmoteRole? = null

    private var startMs = 0L

    private var clockOrigin = 0.0

    private var holding = false

    private var heldAge = 0f


    private var lastAge = 0f

    private var stopping = false

    private var stoppedAt = 0.0

    private var playingEmoteId: Int? = null

    private var playingStartTime: Long? = null

    private var playingDev = false

    private var playingRequests = 0L

    private var pendingEnd: EndedEmote? = null

    private var wearer = NEXT_WEARER.incrementAndGet()

    private var lobby = false

    private var owner: AbstractClientPlayer? = null

    private val root = FloatArray(Renderer.ROOT_FLOATS)

    private val sharedCheck: () -> Boolean = ::inSharedSession

    private val partnerCheck: () -> Boolean = ::partnerFinished

    private val markCheck: () -> Boolean = ::onMark

    private var propDraw: EmotePropDraw? = null

    val isActive: Boolean
        get() = emote != null

    val isBlendingOut: Boolean
        get() = emote != null && stopping

    val networkedEmoteId: Int?
        get() = playingEmoteId.takeIf { emote != null && !stopping }

    val playing: Emote?
        get() = emote

    val playsSolo: Boolean
        get() = emote != null && !stopping && !inSharedSession()

    fun play(
        emote: Emote,
        elapsedMs: Long = 0L,
        emoteId: Int? = null,
        role: EmoteRole? = null,
        startTime: Long? = null,
        dev: Boolean = false,
    ): Boolean {
        this.emote?.scene?.forget(wearer)
        if (emoteId == null) noteNetworkedEnd() else pendingEnd = null
        this.emote = emote
        this.role = role
        placed = null
        startMs = now() - elapsedMs.coerceAtLeast(0L)
        clockOrigin = (startTime ?: (EmoteServerClock.now() - elapsedMs.coerceAtLeast(0L))) / MS_PER_SECOND
        holding = false
        stopping = false
        playingEmoteId = emoteId
        playingStartTime = startTime
        playingDev = dev
        playingRequests = EmoteApi.playRequestCount()
        return true
    }

    fun retime(startTime: Long, elapsedMs: Long): Boolean {
        if (emote == null || stopping || playingStartTime != startTime) return false
        startMs = now() - elapsedMs.coerceAtLeast(0L)
        return true
    }

    fun stop(notifyServer: Boolean = true) {
        if (notifyServer) noteNetworkedEnd() else pendingEnd = null
        playingEmoteId = null
        playingStartTime = null
        playingDev = false
        beginStop()
    }

    internal fun takeEnd(): EndedEmote? = pendingEnd.also { pendingEnd = null }

    fun tick(player: AbstractClientPlayer) {
        lobby = player is LobbyAvatar
        owner = player
        own(player)
        val current = emote
        placed = when {
            lobby || current == null -> null
            stopping -> placed
            else -> DuoEmotes.roleFor(player, playingEmoteId, current)
        }
        when {
            current == null -> Unit
            stopping -> settle(current)
            !lobby && EmoteConditions.blocking(player) -> end()
            else -> {
                current.scene.standing()
                val signals = if (lobby) PlayerSignals.NEUTRAL else PlayerSignals.sample(player, 1f)
                val events = if (!lobby && EmoteConditions.swinging(player)) PlayerSignals.EVENT_ATTACK_SWING else 0
                react(frame(current, signals, events, tickRoot(current, player)))
            }
        }
        pendingEnd?.let {
            pendingEnd = null
            EmoteApi.onEmoteEnded(player, it)
        }
    }

    fun applyToModel(model: PlayerModel, renderContext: PlayerRenderContext) {
        val current = emote ?: return
        if (stopping && settle(current)) return
        EmotePoser.feed(model, current.scene)
        val status = frame(current, if (lobby) PlayerSignals.NEUTRAL else renderContext.signals, 0, renderRoot(current))
        if (status >= 0) EmotePoser.apply(model, current.scene, current.allowsVanillaPose)
        react(status)
    }

    internal fun props(): EmotePropDraw? {
        val current = emote ?: return null
        val skin = current.scene.props ?: return null
        val texture = current.propsTexture ?: return null
        if (!RemoteTextures.isRegistered(texture)) return null
        var draw = propDraw
        if (draw == null || draw.skin !== skin || draw.texture != texture) {
            draw = EmotePropDraw(skin, FloatArray(skin.frameFloats), texture)
            propDraw = draw
        }
        current.scene.copyProps(draw.frame)
        return draw
    }

    private fun frame(current: Emote, signals: PlayerSignalRecord, events: Int, effects: Int): Int {
        val elapsed = (now() - startMs) / MS_PER_SECOND
        lastAge = elapsed.toFloat()
        val age = if (holding) heldAge else lastAge
        val input = placed ?: role
        val clip = if (stopping) NO_CLIP else current.clip(input != null)
        val local = !lobby && wearer == CosmeticWearers.localWearer
        val held = holding && !stopping
        val clock = current.scene.clock(wearer, clockOrigin + elapsed, clockOrigin - startMs / MS_PER_SECOND, EmoteClock.seconds())
        return current.scene.frame(wearer, clock, signals, events, clip, age, input, held, root, effects, local, detail(local))
    }

    private fun detail(local: Boolean): Int {
        val player = owner
        return when {
            local -> if (FirstPersonView.ownEyes()) Renderer.DETAIL_REDUCED else Renderer.DETAIL_FULL
            lobby || player == null || previewing(player) -> Renderer.DETAIL_FULL
            else -> {
                val dx = player.x - CosmeticOrigin.cameraX
                val dy = player.y - CosmeticOrigin.cameraY
                val dz = player.z - CosmeticOrigin.cameraZ
                CosmeticWearers.emoteDetail(wearer, entityId(player), dx * dx + dy * dy + dz * dz)
            }
        }
    }

    private fun tickRoot(current: Emote, player: AbstractClientPlayer): Int {
        if (!current.scene.hasEffects) return EmoteScene.EFFECTS_OFF
        CosmeticWearers.begin()
        place(player, 1f)
        return if (inWorld(player)) EmoteScene.EFFECTS_EVENTS else EmoteScene.EFFECTS_OFF
    }

    private fun renderRoot(current: Emote): Int {
        if (!current.scene.hasEffects) return EmoteScene.EFFECTS_OFF
        CosmeticWearers.begin()
        val player = owner
        if (player == null) {
            CosmeticOrigin.still(root)
            return EmoteScene.EFFECTS_OFF
        }
        //? if > 1.8.9 {
        val pose = EmoteLayerPose.held()
        if (pose == null || lobby) {
            place(player, 1f)
            return if (inWorld(player)) EmoteScene.EFFECTS_EVENTS else EmoteScene.EFFECTS_OFF
        }
        CosmeticOrigin.fromLayer(root, CosmeticView.unshadow(pose))
        //?} else {
        /*place(player, partialTick())
        *///?}
        return if (inWorld(player)) EmoteScene.EFFECTS_DRAWN else EmoteScene.EFFECTS_OFF
    }

    private fun inWorld(player: AbstractClientPlayer): Boolean = !lobby && !previewing(player)

    //? if > 1.8.9 {
    private fun previewing(player: AbstractClientPlayer): Boolean =
        PlayerPreviewRenderer.isRenderingPreview() || PlayerPreviewRenderer.previewEquipment(player.id) != null

    private fun entityId(player: AbstractClientPlayer): Int = player.id

    private fun place(player: AbstractClientPlayer, partialTick: Float) {
        val position = player.getPosition(partialTick)
        val yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
        CosmeticOrigin.fromWorld(root, position.x, position.y, position.z, yaw, PLAYER_SCALE)
    }
    //?} else {
    /*private fun previewing(player: AbstractClientPlayer): Boolean = false

    private fun entityId(player: AbstractClientPlayer): Int = player.networkId

    private fun partialTick(): Float = (Minecraft.getInstance() as SignalsTimerAccessor).`polyplus$signalTimer`().partialTick

    private fun place(player: AbstractClientPlayer, partialTick: Float) {
        val x = player.prevX + (player.x - player.prevX) * partialTick
        val y = player.prevY + (player.y - player.prevY) * partialTick
        val z = player.prevZ + (player.z - player.prevZ) * partialTick
        var turn = (player.bodyYaw - player.lastBodyYaw) % 360f
        if (turn >= 180f) turn -= 360f
        if (turn < -180f) turn += 360f
        CosmeticOrigin.fromWorld(root, x, y, z, player.lastBodyYaw + turn * partialTick, PLAYER_SCALE)
    }
    *///?}

    private fun react(status: Int) {
        if (stopping) return
        when (holdStep(status, holding, sharedCheck, partnerCheck, markCheck)) {
            HoldStep.PLAY -> Unit
            HoldStep.HOLD -> if (!holding) {
                holding = true
                heldAge = lastAge
            }
            HoldStep.END -> end()
        }
    }

    private fun inSharedSession(): Boolean {
        val player = owner ?: return false
        if (lobby) return false
        val session = DuoEmoteState.sharedFor(player.uuid) ?: return false
        return sameSession(session.emoteId, session.startTime)
    }

    private fun sameSession(emoteId: Int, startTime: Long): Boolean {
        val id = playingEmoteId
        val start = playingStartTime
        return id != null && start != null && id == emoteId && start == startTime
    }

    private fun onMark(): Boolean {
        val player = owner ?: return false
        if (!inSharedSession()) return false
        val session = DuoEmoteState.sharedFor(player.uuid) ?: return false
        val seat = DuoEmoteState.roleOf(player.uuid) ?: return false
        val mark = emote?.mark(seat) ?: return false
        val spot = DuoPlacement.spot(session.anchor, mark)
        return spot.distanceTo(DuoPlayers.x(player), DuoPlayers.y(player), DuoPlayers.z(player)) <= DuoPlacement.LEAVE_DISTANCE
    }

    private fun partnerFinished(): Boolean {
        val player = owner ?: return true
        val session = DuoEmoteState.sharedFor(player.uuid) ?: return true
        if (!sameSession(session.emoteId, session.startTime)) return true
        val partnerId = DuoEmoteState.partnerOf(player.uuid) ?: return true
        val partner = DuoPlayers.find(partnerId)
        if (partner != null && partner !== player) {
            val controller = (partner as PlayerEmotesAccess).`polyplus$emoteController`()
            if (controller.plays(session.emoteId, session.startTime)) return controller.finished
        }
        val current = emote ?: return true
        val seat = DuoEmoteState.roleOf(partnerId) ?: return true
        if (current.controlled) return false
        val index = current.roleIndex(seat)
        return Emote.roleDone(current.roleSeconds(index), current.roleLoops(index), EmoteServerClock.now() - session.startTime, current.roleDelay(index))
    }

    private fun plays(emoteId: Int, startTime: Long): Boolean =
        emote != null && sameSession(emoteId, startTime)

    private val finished: Boolean
        get() = stopping || holding

    private fun end() {
        endNotice(playingEmoteId, automatic = true)?.let { pendingEnd = it }
        beginStop()
    }

    private fun beginStop() {
        if (emote == null || stopping) return
        stopping = true
        stoppedAt = EmoteClock.seconds()
    }

    private fun settle(current: Emote): Boolean {
        if (EmoteClock.seconds() - stoppedAt < BLEND_SECONDS + SETTLE_SECONDS) return false
        current.scene.forget(wearer)
        emote = null
        role = null
        placed = null
        stopping = false
        holding = false
        return true
    }

    fun breakRings() {
        emote?.scene?.breakRings(wearer)
    }

    private fun noteNetworkedEnd() {
        endNotice(networkedEmoteId, automatic = false)?.let { pendingEnd = it }
    }

    private fun endNotice(emoteId: Int?, automatic: Boolean): EndedEmote? =
        emoteId?.let { EndedEmote(it, playingStartTime, playingRequests, automatic, playingDev) }

    private fun own(player: AbstractClientPlayer) {
        val uuid = player.uuid
        var id = uuid.mostSignificantBits xor uuid.leastSignificantBits
        if (lobby) id = id xor LOBBY_WEARER
        if (id == wearer) return
        emote?.scene?.forget(wearer)
        wearer = id
    }

    internal enum class HoldStep { PLAY, HOLD, END }

    internal companion object {
        fun holdStep(
            status: Int,
            holding: Boolean,
            shared: () -> Boolean,
            partnerDone: () -> Boolean,
            onMark: () -> Boolean = { false },
        ): HoldStep {
            if (status < 0) return HoldStep.END
            if (status and EmoteScene.CANCEL != 0 && !(movedOnly(status) && onMark())) return HoldStep.END
            if (!holding) {
                if (status and (EmoteScene.ENDED or EmoteScene.HELD) == 0) return HoldStep.PLAY
                if (!shared()) return HoldStep.END
            }
            return if (partnerDone()) HoldStep.END else HoldStep.HOLD
        }

        private fun movedOnly(status: Int): Boolean =
            status and MOVE_CANCELS != 0 && status and KEPT_CANCELS == 0

        private const val MOVE_CANCELS = Renderer.EMOTE_CANCEL_WALKING or Renderer.EMOTE_CANCEL_SPRINTING
        private const val KEPT_CANCELS = Renderer.EMOTE_CANCEL_CROUCHING or Renderer.EMOTE_CANCEL_FALLING or
            Renderer.EMOTE_CANCEL_ELYTRA_FLYING or Renderer.EMOTE_CANCEL_SWIMMING or Renderer.EMOTE_CANCEL_SLEEPING or
            Renderer.EMOTE_CANCEL_SWINGING
        private const val NO_CLIP = -1
        private const val MS_PER_SECOND = 1000.0
        private const val BLEND_SECONDS = 0.14
        private const val SETTLE_SECONDS = 0.05
        private const val PLAYER_SCALE = 0.9375f
        private const val LOBBY_WEARER = 0x4C4F4242594C4FL
        private val NEXT_WEARER = AtomicLong(0x5EED_0000_0000_0000L)
    }
}
