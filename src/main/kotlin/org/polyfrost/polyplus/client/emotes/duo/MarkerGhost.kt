package org.polyfrost.polyplus.client.emotes.duo

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.world.entity.player.PlayerModelPart
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
import net.minecraft.client.render.model.PlayerModelPart
import net.minecraft.resource.Identifier
*///?}
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.player.PlayerSkin
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?}
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.cosmetics.render.FirstPersonView*/
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.EmoteRole
import org.polyfrost.polyplus.client.emotes.context.ContextEmotes
import org.polyfrost.polyplus.client.emotes.playback.EmoteController
import org.polyfrost.polyplus.client.lobby.LobbyHologram
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.apache.logging.log4j.LogManager
//? if > 1.8.9
import org.polyfrost.polyplus.client.lobby.LobbyCompat
import java.util.UUID

internal class GhostPlayer(level: ClientLevel) : RemotePlayer(level, GameProfile(GHOST_ID, GHOST_NAME)) {
    var source: AbstractClientPlayer? = null

    init {
        //? if > 1.8.9 {
        id = GHOST_ENTITY_ID
        //?} else {
        /*setNetworkId(GHOST_ENTITY_ID)
        *///?}
    }

    //? if > 1.8.9 {
    override fun getSkin(): PlayerSkin = source?.skin ?: super.getSkin()

    override fun isModelPartShown(part: PlayerModelPart): Boolean = source?.isModelPartShown(part) ?: true

    fun place(x: Double, y: Double, z: Double, facing: Float) {
        setPos(x, y, z)
        xo = x
        yo = y
        zo = z
        xOld = x
        yOld = y
        zOld = z
        setYRot(facing)
        yRotO = facing
        setXRot(0f)
        xRotO = 0f
        yBodyRot = facing
        yBodyRotO = facing
        yHeadRot = facing
        yHeadRotO = facing
    }
    //?} else {
    /*override fun getSkinTextureLocation(): Identifier = source?.skinTextureLocation ?: super.getSkinTextureLocation()

    override fun getModelType(): String = source?.modelType ?: super.getModelType()

    override fun getCapeTextureLocation(): Identifier? = source?.capeTextureLocation

    override fun hasSkinTexture(): Boolean = true

    override fun hasInfo(): Boolean = true

    override fun isModelPartVisible(part: PlayerModelPart): Boolean = source?.isModelPartVisible(part) ?: true

    fun place(x: Double, y: Double, z: Double, facing: Float) {
        setPosition(x, y, z)
        prevX = x
        prevY = y
        prevZ = z
        yaw = facing
        lastYaw = facing
        pitch = 0f
        lastPitch = 0f
        bodyYaw = facing
        lastBodyYaw = facing
        headYaw = facing
        lastHeadYaw = facing
    }
    *///?}

    private companion object {
        val GHOST_ID: UUID = UUID.nameUUIDFromBytes("polyplus:marker_ghost".toByteArray())
        const val GHOST_NAME = "PolyPlusGhost"
        const val GHOST_ENTITY_ID = 0x47485354
    }
}

object MarkerGhost {
    private val LOGGER = LogManager.getLogger("polyplus/emotes")

    private val frozen: () -> Long = { 0L }

    private var failed = false

    private var ghost: GhostPlayer? = null
    private var key: Any? = null
    private var spot: Spot? = null
    private var ready = false
    private var source: AbstractClientPlayer? = null
    private var emote: Emote? = null
    private var role: EmoteRole? = null
    private var poseMs = 0L
    private var posedKey: Any? = null
    private var markedAt = Long.MIN_VALUE
    private var shownAt = 0L

    //? if >= 1.21.10 {
    private var state: EntityRenderState? = null
    private var extracted: EntityRenderState? = null
    private var drawX = 0.0
    private var drawY = 0.0
    private var drawZ = 0.0
    //?}

    //? if = 1.8.9
    /*private val clockOrigin = System.nanoTime()*/

    @JvmStatic
    fun isGhost(entity: Any?): Boolean = entity != null && entity === ghost

    internal fun markDuo(approach: DuoEmotes.Approach) {
        val spot = approach.spot ?: return clear()
        val anchor = approach.anchor ?: return clear()
        mark(approach.request.requestId, spot, anchor, approach.emote, approach.request.emoteId, approach.emote.roleIndex(Emote.PARTNER), approach.ready, approach.follower)
    }

    internal fun markContext(target: ContextEmotes.Target) {
        mark(target, target.spot, target.anchor, null, target.emoteId, CONTEXT_ROLE, false, null)
    }

    internal fun clear() {
        spot = null
        source = null
        markedAt = Long.MIN_VALUE
        //? if >= 1.21.10
        extracted = null
    }

    private fun mark(key: Any, spot: Spot, anchor: EmoteAnchor, emote: Emote?, emoteId: Int, roleIndex: Int, ready: Boolean, source: AbstractClientPlayer?) {
        val now = System.currentTimeMillis()
        if (key != this.key) {
            val played = emote ?: CosmeticAssetCache.getEmote(emoteId)
            this.key = key
            this.emote = played
            role = DuoPlacement.role(roleIndex, anchor, spot.x, spot.y, spot.z, spot.yaw)
            poseMs = MarkerGhostRules.poseMillis(played?.roleDelay(roleIndex) ?: 0f)
            shownAt = now
        } else if (!MarkerGhostRules.fresh(markedAt, now)) {
            shownAt = now
        }
        this.spot = spot
        this.ready = ready
        this.source = source
        markedAt = now
    }

    private fun hidden(mc: Minecraft): Boolean {
        //? if >= 26.2 {
        if (mc.gui.hud.isHidden) return true
        //?} else {
        /*if (mc.options.hideGui) return true
        *///?}
        return LobbyScene.terrainActive()
    }

    private fun visible(mc: Minecraft, now: Long, cameraX: Double, cameraY: Double, cameraZ: Double): Spot? {
        val spot = spot ?: return null
        if (!MarkerGhostRules.fresh(markedAt, now) || hidden(mc)) return null
        if (MarkerGhostRules.surrounds(cameraX, cameraY, cameraZ, spot.x, spot.y, spot.z)) return null
        return spot
    }

    private fun progress(now: Long): Double = MarkerGhostRules.appear(now - shownAt)

    private fun controller(player: AbstractClientPlayer): EmoteController = (player as PlayerEmotesAccess).`polyplus$emoteController`()

    private fun ghostFor(mc: Minecraft, spot: Spot): GhostPlayer? {
        //? if > 1.8.9 {
        val level = mc.level ?: return null
        val current = ghost?.takeIf { it.level() === level } ?: create(level) ?: return null
        //?} else {
        /*val level = mc.world ?: return null
        val current = ghost?.takeIf { it.world === level } ?: create(level) ?: return null
        *///?}
        current.source = source ?: mc.player
        current.place(spot.x, spot.y, spot.z, spot.yaw)
        pose(current)
        return current
    }

    private fun create(level: ClientLevel): GhostPlayer? {
        ghost?.let { controller(it).stop(false) }
        ghost = null
        val made = runCatching { GhostPlayer(level) }.getOrNull() ?: return null
        controller(made).clock = frozen
        ghost = made
        posedKey = null
        //? if >= 1.21.10
        state = null
        return made
    }

    private fun pose(ghost: GhostPlayer) {
        val controller = controller(ghost)
        val wanted = emote
        if (wanted == null) {
            if (controller.isActive && !controller.isBlendingOut) controller.stop(false)
            posedKey = key
            return
        }
        if (posedKey == key && controller.playing === wanted && !controller.isBlendingOut) return
        controller.play(wanted, poseMs, role = role)
        posedKey = key
    }

    private inline fun guarded(block: () -> Unit) {
        if (failed) return
        try {
            block()
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    private fun fail(error: RuntimeException) {
        failed = true
        LobbyHologram.drawing = false
        LobbyHologram.extracting = false
        //? if >= 1.21.10
        extracted = null
        LOGGER.warn("Turned off the emote marker ghost for this session after it failed to render", error)
    }

    //? if >= 1.21.10 {
    @JvmStatic
    fun extract(renderer: GameRenderer) = guarded { extractGhost(renderer) }

    private fun extractGhost(renderer: GameRenderer) {
        extracted = null
        val mc = Minecraft.getInstance()
        val camera = LobbyCompat.cameraPosition(renderer)
        val spot = visible(mc, System.currentTimeMillis(), camera.x, camera.y, camera.z) ?: return
        val ghost = ghostFor(mc, spot) ?: return
        @Suppress("UNCHECKED_CAST")
        val entityRenderer = mc.entityRenderDispatcher.getRenderer(ghost) as? EntityRenderer<AbstractClientPlayer, EntityRenderState> ?: return
        val target = state ?: entityRenderer.createRenderState().also { state = it }
        target.shadowPieces.clear()
        LobbyHologram.extracting = true
        try {
            entityRenderer.extractRenderState(ghost, target, 1f)
        } finally {
            LobbyHologram.extracting = false
        }
        target.nameTag = null
        target.shadowPieces.clear()
        drawX = spot.x
        drawY = spot.y
        drawZ = spot.z
        extracted = target
    }

    @JvmStatic
    fun draw(renderer: GameRenderer) = guarded {
        //? if < 26.1
        /*extractGhost(renderer)*/
        val target = extracted ?: return@guarded
        extracted = null
        LobbyHologram.drawGhost(renderer, target, drawX, drawY, drawZ, progress(System.currentTimeMillis()), MarkerGhostRules.tint(ready))
    }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun draw(renderer: GameRenderer) = guarded { drawGhost(renderer) }

    private fun drawGhost(renderer: GameRenderer) {
        val mc = Minecraft.getInstance()
        val now = System.currentTimeMillis()
        val camera = LobbyCompat.cameraPosition(renderer)
        val spot = visible(mc, now, camera.x, camera.y, camera.z) ?: return
        val ghost = ghostFor(mc, spot) ?: return
        LobbyHologram.drawGhost(renderer, ghost, progress(now), MarkerGhostRules.tint(ready), 1f)
    }
    *///?} else {
    /*@JvmStatic
    fun draw(tickDelta: Float) = guarded { drawGhost(tickDelta) }

    private fun drawGhost(tickDelta: Float) {
        val mc = Minecraft.getInstance()
        val now = System.currentTimeMillis()
        val dispatcher = mc.entityRenderDispatcher
        val inside = if (FirstPersonView.ownEyes()) dispatcher.cameraY else Double.NaN
        val spot = visible(mc, now, dispatcher.cameraX, inside, dispatcher.cameraZ) ?: return
        val ghost = ghostFor(mc, spot) ?: return
        LobbyHologram.drawGhost(ghost, progress(now), MarkerGhostRules.tint(ready), tickDelta, seconds())
    }

    private fun seconds(): Float = (((System.nanoTime() - clockOrigin) / NANOS_PER_SECOND) % SECONDS_WRAP).toFloat()

    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val SECONDS_WRAP = 1200.0
    *///?}

    private const val CONTEXT_ROLE = 0
}
