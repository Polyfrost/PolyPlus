package org.polyfrost.polyplus.client.cosmetics.render

import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.joml.Matrix4f
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.CosmeticClock
import java.util.UUID

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
//?} else {
/*import net.minecraft.client.Minecraft
import net.minecraft.entity.living.player.PlayerEntity
*///?}

object CosmeticWearers {
    const val MINIMAL_DETAIL_BLOCKS = 64.0
    private const val IDLE_SECONDS = 30.0
    private const val STALLED_FRAME_SECONDS = 0.25
    private const val PREVIEW_BASE_ENTITY_ID = Int.MIN_VALUE + 1L
    private const val PREVIEW_SLOT_STRIDE = 16L

    class Wearer internal constructor(val id: Long) {
        internal var entityId = 0
        internal var preview = false
        internal var local = false
        internal var firstPerson = false
        internal var hand = false
        internal var follows = 0L
        internal var distanceSq = 0.0
        internal var frame = -1
        internal var lastSeen = 0.0
        internal var detail = Renderer.DETAIL_FULL
        internal var detailFrame = -1
        internal var drawn = ArrayList<CosmeticScene>(4)
        internal var previous = ArrayList<CosmeticScene>(4)
        internal val looks = HashMap<CosmeticScene, PaletteTextures.Look>(4)
        internal val previewView = FloatArray(Renderer.VIEW_FLOATS)
        internal val previewModel = Matrix4f()
        internal var previewPlaced = false
    }

    var frame = 0
        private set

    var clock = 0.0
        private set

    val localWearer: Long get() = localId

    private var token = 0
    private var rolledToken = -1
    private var endedFrame = -1
    private var localId = 0L

    @Volatile
    private var forgetAll = false

    private val all = ArrayList<Wearer>()
    private val index = WearerIndex()
    private val ranked = ArrayList<Wearer>()
    private var rankedFrame = -1
    private var rankedCount = 0
    private var fullLimit = 0
    private var fullDistanceSq = 0.0
    private var pressure = 0
    private var frameCost = 0L
    private val budget = DetailBudget()
    private val followers = ArrayList<Wearer>()

    val detailPressure: Int get() = pressure

    init {
        eventHandler<FramebufferRenderEvent.End> { token += 1 }
        eventHandler<WorldEvent.Unload> {
            forgetAll = true
            CosmeticOrigin.reset()
        }
    }

    @JvmStatic
    fun wearerId(uuid: UUID): Long = uuid.mostSignificantBits xor uuid.leastSignificantBits

    @JvmStatic
    fun previewWearerId(entityId: Int): Long = previewWearer() - (entityId - PREVIEW_BASE_ENTITY_ID)

    @JvmStatic
    fun previewWearer(): Long = Renderer.PREVIEW_WEARER - previewSlot * PREVIEW_SLOT_STRIDE

    @Volatile
    internal var previewSlot = 0

    fun begin() {
        val now = CosmeticClock.seconds()
        if (rolledToken == token && now - clock < STALLED_FRAME_SECONDS) return
        rolledToken = token
        roll(now)
    }

    fun track(id: Long, entityId: Int, distanceSq: Double, preview: Boolean): Wearer {
        val wearer = index[id] ?: Wearer(id).also {
            index.put(it)
            all += it
        }
        wearer.frame = frame
        wearer.entityId = entityId
        wearer.preview = preview
        wearer.local = !preview && id == localId
        wearer.firstPerson = false
        wearer.hand = false
        wearer.follows = 0L
        wearer.previewPlaced = false
        wearer.distanceSq = distanceSq
        wearer.lastSeen = clock
        return wearer
    }

    internal fun breakRings(id: Long) {
        val wearer = index[id] ?: return
        val drawn = wearer.drawn
        val previous = wearer.previous
        var i = 0
        while (i < drawn.size) drawn[i++].breakRings(id)
        i = 0
        while (i < previous.size) {
            val scene = previous[i++]
            if (scene !in drawn) scene.breakRings(id)
        }
    }

    internal fun trackedThisFrame(id: Long): Boolean = index[id]?.let { it.frame == frame && !it.preview } == true

    internal fun ownerOf(wearer: Wearer): UUID? {
        if (wearer.preview) return null
        //? if > 1.8.9 {
        val entity = Minecraft.getInstance().level?.getEntity(wearer.entityId) as? Player ?: return null
        //?} else {
        /*val entity = Minecraft.getInstance().world?.getEntity(wearer.entityId) as? PlayerEntity ?: return null
        *///?}
        return entity.uuid.takeIf { wearerId(it) == wearer.id }
    }

    internal fun colorOwner(wearer: Wearer): UUID? =
        if (wearer.preview) ClientPlatform.localPlayerUuid() else ownerOf(wearer)

    internal fun look(wearer: Wearer, scene: CosmeticScene): PaletteTextures.Look =
        wearer.looks.getOrPut(scene) { PaletteTextures.Look() }

    internal fun drawn(wearer: Wearer, scene: CosmeticScene) {
        if (scene !in wearer.drawn) wearer.drawn += scene
    }

    internal fun detail(wearer: Wearer): Int {
        if (wearer.detailFrame == frame) return wearer.detail
        if (rankedFrame != frame) rank()
        if (wearer.detailFrame != frame) assign(wearer)
        return wearer.detail
    }

    fun charge(nanos: Long) {
        frameCost += nanos
    }

    fun emoteDetail(id: Long, entityId: Int, distanceSq: Double): Int {
        begin()
        val known = index[id]
        val wearer = if (known != null && known.frame == frame) known else track(id, entityId, distanceSq, false)
        if (wearer.detailFrame == frame) return wearer.detail
        if (rankedFrame == frame) {
            assign(wearer)
            return wearer.detail
        }
        if (wearer.detailFrame == endedFrame) return wearer.detail
        return DetailBudget.level(0, distanceSq, detailDistanceSq(), fullDetailWearers(), pressure)
    }

    fun detailLevel(order: Int, distanceSq: Double, fullDistanceSq: Double, fullLimit: Int): Int =
        DetailBudget.level(order, distanceSq, fullDistanceSq, fullLimit, pressure)

    fun detailDistanceSq(): Double {
        val blocks = PolyPlusCosmeticsConfig.cosmeticDetailDistance.toDouble().coerceIn(0.0, MINIMAL_DETAIL_BLOCKS)
        return blocks * blocks
    }

    fun fullDetailWearers(): Int = PolyPlusCosmeticsConfig.cosmeticFullDetailPlayers.toInt().coerceAtLeast(0)

    private fun rank() {
        rankedFrame = frame
        rankedCount = 0
        fullLimit = fullDetailWearers()
        fullDistanceSq = detailDistanceSq()
        ranked.clear()
        followers.clear()
        for (index in 0 until all.size) {
            val wearer = all[index]
            if (wearer.frame != frame && wearer.frame != endedFrame) continue
            if (wearer.local || wearer.preview || wearer.hand) {
                assign(wearer)
                continue
            }
            if (wearer.follows != 0L) {
                followers += wearer
                continue
            }
            var at = ranked.size
            ranked += wearer
            val distance = distanceOf(wearer)
            while (at > 0 && distanceOf(ranked[at - 1]) > distance) {
                ranked[at] = ranked[at - 1]
                at--
            }
            ranked[at] = wearer
        }
        for (index in 0 until ranked.size) assign(ranked[index])
        for (index in 0 until followers.size) assign(followers[index])
        ranked.clear()
        followers.clear()
    }

    private fun assign(wearer: Wearer) {
        wearer.detailFrame = frame
        wearer.detail = when {
            wearer.hand -> Renderer.DETAIL_FULL
            wearer.firstPerson -> Renderer.DETAIL_REDUCED
            wearer.local || wearer.preview -> Renderer.DETAIL_FULL
            wearer.follows != 0L -> followed(wearer)
            else -> DetailBudget.level(rankedCount++, wearer.distanceSq, fullDistanceSq, fullLimit, pressure)
        }
    }

    private fun followed(wearer: Wearer): Int {
        val owner = index[wearer.follows]?.takeIf { (it.frame == frame || it.frame == endedFrame) && it.follows == 0L }
            ?: return DetailBudget.level(rankedCount, wearer.distanceSq, fullDistanceSq, fullLimit, pressure)
        val level = if (owner.detailFrame == frame) owner.detail else detail(owner)
        val far = if (distanceOf(wearer) > MINIMAL_DETAIL_BLOCKS * MINIMAL_DETAIL_BLOCKS) Renderer.DETAIL_MINIMAL else Renderer.DETAIL_FULL
        return maxOf(level, far)
    }

    private fun distanceOf(wearer: Wearer): Double =
        if (wearer.distanceSq.isNaN()) Double.POSITIVE_INFINITY else wearer.distanceSq

    private fun roll(now: Double) {
        if (rankedFrame != frame) rank()
        endedFrame = frame
        frame++
        clock = now
        pressure = budget.sample(frameCost)
        frameCost = 0L
        localId = wearerId(Minecraft.getInstance().player?.uuid ?: ClientPlatform.localPlayerUuid())
        CosmeticOrigin.update()
        CosmeticEffects.roll(frame)
        val dropAll = forgetAll
        forgetAll = false
        var removed = false
        var i = 0
        while (i < all.size) {
            val wearer = all[i]
            if (!dropAll && wearer.frame == endedFrame) {
                val shown = wearer.previous
                for (at in 0 until shown.size) {
                    val scene = shown[at]
                    if (scene in wearer.drawn) continue
                    scene.forget(wearer.id)
                    wearer.looks.remove(scene)
                }
                val previous = wearer.previous
                wearer.previous = wearer.drawn
                wearer.drawn = previous
                previous.clear()
                i++
                continue
            }
            if (dropAll || now - wearer.lastSeen >= IDLE_SECONDS || gone(wearer)) {
                for (at in 0 until wearer.previous.size) wearer.previous[at].forget(wearer.id)
                for (at in 0 until wearer.drawn.size) wearer.drawn[at].forget(wearer.id)
                PaletteTextures.forget(wearer.id)
                all[i] = all[all.size - 1]
                all.removeAt(all.size - 1)
                removed = true
                continue
            }
            i++
        }
        if (removed) index.rebuild(all)
    }

    private fun gone(wearer: Wearer): Boolean {
        if (wearer.preview) return false
        //? if > 1.8.9 {
        val level = Minecraft.getInstance().level ?: return true
        val entity = level.getEntity(wearer.entityId) as? Player ?: return true
        //?} else {
        /*val world = Minecraft.getInstance().world ?: return true
        val entity = world.getEntity(wearer.entityId) as? PlayerEntity ?: return true
        *///?}
        val owner = wearerId(entity.uuid)
        return if (wearer.hand) !FirstPersonHands.owns(owner, wearer.id) else owner != wearer.id
    }

    private class WearerIndex {
        private var keys = LongArray(64)
        private var values = arrayOfNulls<Wearer>(64)
        private var size = 0

        operator fun get(id: Long): Wearer? {
            val mask = keys.size - 1
            var slot = mix(id) and mask
            while (true) {
                val value = values[slot] ?: return null
                if (keys[slot] == id) return value
                slot = (slot + 1) and mask
            }
        }

        fun put(wearer: Wearer) {
            if ((size + 1) * 2 > keys.size) grow()
            insert(wearer)
            size++
        }

        fun rebuild(wearers: List<Wearer>) {
            keys.fill(0L)
            values.fill(null)
            size = 0
            for (wearer in wearers) put(wearer)
        }

        private fun insert(wearer: Wearer) {
            val mask = keys.size - 1
            var slot = mix(wearer.id) and mask
            while (values[slot] != null) slot = (slot + 1) and mask
            keys[slot] = wearer.id
            values[slot] = wearer
        }

        private fun grow() {
            val old = values
            keys = LongArray(keys.size * 2)
            values = arrayOfNulls(old.size * 2)
            for (wearer in old) if (wearer != null) insert(wearer)
        }

        private fun mix(id: Long): Int {
            val h = id * -0x61c8864680b583ebL
            return (h xor (h ushr 32)).toInt()
        }
    }
}
