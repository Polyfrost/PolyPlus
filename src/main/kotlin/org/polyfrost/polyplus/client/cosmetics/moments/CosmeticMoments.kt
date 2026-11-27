package org.polyfrost.polyplus.client.cosmetics.moments

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticOrigin
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticScene
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.render.CustomColors
import org.polyfrost.polyplus.client.cosmetics.render.EffectColors
import org.polyfrost.polyplus.client.cosmetics.render.FirstPersonEffects
import org.polyfrost.polyplus.client.cosmetics.render.FirstPersonView
import org.polyfrost.polyplus.client.cosmetics.render.PaletteTextures
import org.polyfrost.polyplus.client.cosmetics.render.hiddenSlotsFor
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.CosmeticClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
*///?}

object CosmeticMoments : MomentEffects {
    private const val PLAYER_SCALE = 0.9375f
    private const val MAX_DETACHED = 32
    private const val INSTALL_MS = 2_000L
    private const val RETRY_MS = 30_000L
    private const val MIN_ROW_SECONDS = 0.5f
    private const val ABSENT = -1
    private const val HIDDEN = -2

    internal class Detached(
        val scene: CosmeticScene,
        val cosmeticId: Int,
        val slot: BodySlot,
        val wearer: Long,
        val owner: Long,
        val local: Boolean,
        val onSelf: Boolean,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val until: Double,
        val colorway: Int,
        val custom: CustomColors?,
    ) {
        var row = -1
        var frame = -1
        var hidden = false
        var tracked: CosmeticWearers.Wearer? = null
    }

    private class Attached(
        val scene: CosmeticScene,
        val slot: BodySlot,
        val owner: Long,
        val uuid: UUID,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val until: Double,
    )

    private val detached = ArrayList<Detached>()
    private val attached = ArrayList<Attached>()
    private val root = FloatArray(12)
    private var nonce = 0

    private val loading: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val retryAt = ConcurrentHashMap<Int, Long>()

    override fun play(play: MomentPlay): Boolean = start(play)

    internal fun clear() {
        detached.clear()
        attached.clear()
    }

    internal fun addRows() {
        addAttachedRows()
        if (detached.isEmpty()) return
        val now = CosmeticWearers.clock
        val ownEyes = FirstPersonView.ownEyes()
        val iterator = detached.iterator()
        while (iterator.hasNext()) {
            val moment = iterator.next()
            if (now > moment.until) {
                iterator.remove()
                continue
            }
            val dx = moment.x - CosmeticOrigin.cameraX
            val dy = moment.y - CosmeticOrigin.cameraY
            val dz = moment.z - CosmeticOrigin.cameraZ
            val wearer = CosmeticWearers.track(moment.wearer, 0, dx * dx + dy * dy + dz * dz, false)
            wearer.local = moment.local
            if (moment.wearer != moment.owner) wearer.follows = moment.owner
            moment.tracked = wearer
            moment.hidden = moment.onSelf && ownEyes
            wearer.firstPerson = moment.hidden
            CosmeticOrigin.fromWorld(root, moment.x, moment.y, moment.z, moment.yaw, PLAYER_SCALE)
            moment.row = moment.scene.addBare(wearer, PlayerSignals.NEUTRAL, 0, root)
            moment.frame = CosmeticWearers.frame
        }
    }

    private fun addAttachedRows() {
        if (attached.isEmpty()) return
        val now = CosmeticWearers.clock
        val iterator = attached.iterator()
        while (iterator.hasNext()) {
            val moment = iterator.next()
            if (now > moment.until || moment.scene.closed) {
                iterator.remove()
                continue
            }
            var entityId = placeWearer(moment.uuid, moment.slot, root)
            if (entityId == HIDDEN) continue
            if (moment.scene.claimRow(moment.owner)) continue
            if (entityId < 0) {
                entityId = 0
                CosmeticOrigin.fromWorld(root, moment.x, moment.y, moment.z, moment.yaw, PLAYER_SCALE)
            }
            val dx = CosmeticOrigin.x + root[3] - CosmeticOrigin.cameraX
            val dy = CosmeticOrigin.y + root[7] - CosmeticOrigin.cameraY
            val dz = CosmeticOrigin.z + root[11] - CosmeticOrigin.cameraZ
            val wearer = CosmeticWearers.track(moment.owner, entityId, dx * dx + dy * dy + dz * dz, false)
            wearer.firstPerson = wearer.local && FirstPersonView.ownEyes()
            moment.scene.addBare(wearer, PlayerSignals.NEUTRAL, 0, root)
        }
    }

    //? if > 1.8.9 {
    private fun placeWearer(uuid: UUID, slot: BodySlot, out: FloatArray): Int {
        val mc = Minecraft.getInstance()
        val player = mc.level?.getPlayerByUUID(uuid) ?: return if (PolyPlusCosmeticsConfig.isVisible(slot, uuid)) ABSENT else HIDDEN
        if (player.isInvisible || player.isSpectator) return HIDDEN
        val hidden = (player as? AbstractClientPlayer)?.let(::hiddenSlotsFor)
        if (if (hidden != null) slot in hidden else !PolyPlusCosmeticsConfig.isVisible(slot, uuid)) return HIDDEN
        FirstPersonEffects.place(player, FirstPersonEffects.partialTick(mc), out)
        return player.id
    }
    //?} else {
    /*private fun placeWearer(uuid: UUID, slot: BodySlot, out: FloatArray): Int {
        val mc = Minecraft.getInstance()
        val player = mc.world?.players?.firstOrNull { it.uuid == uuid }
            ?: return if (PolyPlusCosmeticsConfig.isVisible(slot, uuid)) ABSENT else HIDDEN
        if (player.isInvisible || player.isSpectator) return HIDDEN
        val hidden = (player as? ClientPlayerEntity)?.let(::hiddenSlotsFor)
        if (if (hidden != null) slot in hidden else !PolyPlusCosmeticsConfig.isVisible(slot, uuid)) return HIDDEN
        FirstPersonEffects.place(player, FirstPersonEffects.partialTick(mc), out)
        return player.networkId
    }
    *///?}

    internal fun forEachModel(draw: (Detached, Identifier) -> Unit) {
        if (detached.isEmpty()) return
        for (moment in detached) {
            if (moment.row < 0 || moment.frame != CosmeticWearers.frame || moment.hidden || moment.scene.isEmpty) continue
            val tracked = moment.tracked ?: continue
            if (!moment.scene.showsModel(tracked, moment.slot)) continue
            val custom = moment.custom
            val texture = PaletteTextures.texture(moment.wearer, moment.cosmeticId, moment.colorway, custom?.slots, custom?.rgb)
                ?: continue
            if (RemoteTextures.isRegistered(texture)) draw(moment, texture)
        }
    }

    private fun start(play: MomentPlay): Boolean {
        val cosmetic = CosmeticAssetCache.getAttachedCosmetic(play.cosmeticId)
        if (cosmetic == null) {
            load(play.cosmeticId)
            return false
        }
        val scene = cosmetic.scene
        if (!scene.hasEffects) return true
        val owner = CosmeticWearers.wearerId(play.wearer)
        val wearer = if (play.detached) detachedKey(scene, owner, play) else owner
        val colorway = EffectColors.colorway(play.cosmeticId, play.colorway)
        scene.paint(wearer, colorway, play.customColors)
        if (!scene.fireEffect(wearer, play.moment.effectName)) {
            if (play.detached) scene.forget(wearer)
            return true
        }
        val now = CosmeticClock.seconds()
        scene.startClip(wearer, play.moment.effectName, now)
        val seconds = scene.momentSeconds(play.moment.effectName)?.let { max(it, MIN_ROW_SECONDS) }
            ?: (max(Renderer.EVENT_EFFECT_SECONDS, scene.clipSeconds) + Renderer.EFFECT_PARTICLE_LIFE_SECONDS)
        if (!play.detached) {
            attached.removeIf { it.scene === scene && it.owner == owner }
            if (attached.size >= MAX_DETACHED) attached.removeAt(0)
            attached += Attached(scene, play.slot, owner, play.wearer, play.x, play.y, play.z, play.yaw, now + seconds)
            return true
        }
        if (detached.size >= MAX_DETACHED) detached.removeAt(0)
        val local = CosmeticCatalog.accountId(play.wearer) == ClientPlatform.localPlayerUuid()
        detached += Detached(
            scene = scene,
            cosmeticId = play.cosmeticId,
            slot = play.slot,
            wearer = wearer,
            owner = owner,
            local = local,
            onSelf = local && play.moment != Moment.Kill,
            x = play.x,
            y = play.y,
            z = play.z,
            yaw = play.yaw,
            until = now + seconds,
            colorway = scene.colorwayIndex(colorway),
            custom = scene.custom(play.customColors),
        )
        return true
    }

    private fun detachedKey(scene: CosmeticScene, owner: Long, play: MomentPlay): Long {
        nonce = if (nonce == Int.MAX_VALUE) 1 else nonce + 1
        val seed = Renderer.eventWearer(owner, play.moment.ordinal, play.sequence)
        val key = Renderer.eventWearer(owner, play.moment.ordinal, play.sequence, nonce)
        scene.setSeedWearer(key, seed)
        return key
    }

    private fun load(cosmeticId: Int) {
        val retry = retryAt[cosmeticId]
        if (retry != null && System.currentTimeMillis() < retry) return
        if (!loading.add(cosmeticId)) return
        PolyPlusClient.SCOPE.launch {
            try {
                val loaded = CosmeticAssetCache.ensureCosmeticLoaded(cosmeticId)
                retryAt[cosmeticId] = System.currentTimeMillis() + if (loaded) INSTALL_MS else RETRY_MS
            } finally {
                loading.remove(cosmeticId)
            }
        }
    }
}
