package org.polyfrost.polyplus.client.pets

import net.minecraft.client.multiplayer.ClientLevel
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Long as JavaLong
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class PetInteraction(val key: String, val value: Float, val seconds: Double) {
    Greet("greet", 1f, 3.0),
    Follow("follow", 2f, 6.0),
    Nap("nap", 3f, 30.0),
    Play("play", 4f, 8.0),
    Chase("chase", 5f, 12.0);

    companion object {
        fun fromKey(key: String): PetInteraction? = entries.firstOrNull { it.key == key }
    }
}

internal class PetRoles(val asA: Boolean, val asB: Boolean)

internal fun interface PetSolid {
    fun overlaps(x: Double, y: Double, z: Double, halfWidth: Double, height: Double): Boolean
}

internal fun interface PetGround {
    fun surface(x: Double, z: Double, halfWidth: Double, height: Double, top: Double, bottom: Double): Double
}

internal interface PetPairRules<T> {
    fun x(pet: T): Double

    fun gapSq(p: T, q: T): Double

    fun compatible(p: T, q: T): Boolean

    fun cooling(p: T, q: T): Boolean

    fun startable(p: T, q: T): Boolean

    fun before(q: T, best: T): Boolean

    fun refused(p: T, q: T): Boolean = false

    fun refuse(p: T, q: T) = Unit
}

internal class PetMeet {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var firstX = 0.0
    var firstZ = 0.0
    var secondX = 0.0
    var secondZ = 0.0

    fun set(x: Double, y: Double, z: Double, firstX: Double, firstZ: Double, secondX: Double, secondZ: Double): PetMeet {
        this.x = x
        this.y = y
        this.z = z
        this.firstX = firstX
        this.firstZ = firstZ
        this.secondX = secondX
        this.secondZ = secondZ
        return this
    }
}

internal class PetSharedRole(
    val index: Int,
    val interaction: PetInteraction,
    val role: Int,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val yaw: Float,
    val seconds: Double,
    val looping: Boolean,
    val startDelay: Double = 0.0,
)

object PetInteractions {
    const val ROLE_A = 0
    const val ROLE_B = 1

    private const val NOTICE_BLOCKS = 8.0
    private const val APART_BLOCKS = 12.0
    private const val NOTICE_SECONDS = 1.5
    private const val COOLDOWN_SECONDS = 60.0
    private const val APPROACH_SECONDS = 6.0
    private const val MAX_ACTIVE = 2
    private const val BUDGET_BLOCKS = 24.0
    private const val FLY_MARK_HEIGHT = 1.0
    private const val NAP_HOVER = 0.3
    private const val GAP = 0.2
    private const val CIRCLE_GAP = 0.6
    private const val GROUND_REACH = 3.0
    private const val MOOD_PLAY = 0.2f
    private const val PAIR_TICKS = 5L
    private const val MAX_CANDIDATES = 8
    private const val MAX_PROBES = 16
    private const val REFUSE_SECONDS = 1.0
    private const val RETRY_SECONDS = 5.0
    private const val MARK_ROOM = 0.5
    private const val TICK_EPSILON = 1.0e-6

    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/pets")

    @JvmStatic
    val ownPetPlay: PetPlay
        get() = PolyPlusCosmeticsConfig.petPlay

    private val permissions = PetPlayPermissions()
    private val allInteractions = PetInteraction.entries.toTypedArray()
    private val probe = PairKey(UUID(0L, 0L), UUID(0L, 0L))
    private val localFriend: (UUID) -> Boolean = { FriendsRepository.isFriend(it) }

    private class Session(
        val key: PairKey,
        val interaction: PetInteraction,
        val a: PetManager.Pet,
        val b: PetManager.Pet,
        val begunAt: Double,
        val midX: Double,
        val midY: Double,
        val midZ: Double,
        val ownerGap: Double,
        val baseX: Double,
        val baseY: Double,
        val baseZ: Double,
        val aRides: Boolean,
        val bRides: Boolean,
        val aOwnerX: Double,
        val aOwnerY: Double,
        val aOwnerZ: Double,
        val bOwnerX: Double,
        val bOwnerY: Double,
        val bOwnerZ: Double,
        val seconds: Double,
        val roleTimed: Boolean,
    ) {
        var startedAt = Double.NaN
        var startedClock = Double.NaN
        val shoulder: Boolean get() = aRides || bRides
    }

    private class PairKey(var first: UUID, var second: UUID) {
        override fun equals(other: Any?): Boolean = other is PairKey && other.first == first && other.second == second

        override fun hashCode(): Int = first.hashCode() * 31 + second.hashCode()
    }

    private val sessions = ArrayList<Session>()
    private val cooldowns = HashMap<PairKey, Double>()
    private val refusals = HashMap<PairKey, Double>()
    private val order = ArrayList<PetManager.Pet>()
    private val spot = DoubleArray(3)
    private val shift = DoubleArray(3)
    private val arranged = DoubleArray(3)
    private val reach = DoubleArray(3)
    private val cleared = DoubleArray(2)
    private val axis = DoubleArray(2)
    private var solidLevel: ClientLevel? = null
    private val levelSolid = PetSolid { x, y, z, halfWidth, height ->
        val level = solidLevel
        level == null || PetCollision.overlaps(level, x, y, z, halfWidth, height)
    }
    private val levelGround = PetGround { x, z, halfWidth, height, top, bottom ->
        val level = solidLevel
        if (level == null) Double.NaN else PetCollision.surface(level, x, z, halfWidth, height, top, bottom)
    }
    private val sharedMeet = PetMeet()
    private val localMeet = PetMeet()
    private val discs = DoubleArray(PetMarkClearance.STRIDE * 2)
    private var lastTick = Long.MIN_VALUE
    private var lastPairTick = Long.MIN_VALUE
    private var generation = 0

    @JvmStatic
    fun applyPetPlaySnapshot(players: Collection<UUID>, off: Collection<UUID>, friends: Map<UUID, Collection<UUID>>) {
        permissions.applySnapshot(players, off, friends)
    }

    @JvmStatic
    fun setPetPlay(owner: UUID, play: PetPlay, friends: Collection<UUID>) {
        permissions.applyChange(owner, play, friends)
    }

    @JvmStatic
    fun forgetPetPlay(owners: Collection<UUID>) {
        permissions.forget(owners)
    }

    @JvmStatic
    fun clearPetPlay() {
        permissions.clear()
    }

    internal fun reset() {
        for (session in sessions) {
            session.a.body.directive.clear()
            session.b.body.directive.clear()
        }
        sessions.clear()
        cooldowns.clear()
        refusals.clear()
        for (i in order.indices) order[i].notice = null
        order.clear()
        solidLevel = null
        Pairs.level = null
        lastTick = Long.MIN_VALUE
        lastPairTick = Long.MIN_VALUE
    }

    internal fun update(
        pets: List<PetManager.Pet>,
        local: UUID,
        level: ClientLevel,
        now: Double,
        clock: Double,
    ) {
        endPlayed(now, clock)
        val tick = floor(now / PetSimulation.TICK_SECONDS + TICK_EPSILON).toLong()
        if (tick == lastTick) return
        lastTick = tick
        generation++
        for (i in pets.indices) pets[i].pairSeen = generation

        var s = sessions.size - 1
        while (s >= 0) {
            val session = sessions[s]
            if (sessionEnds(session.shoulder, { ends(session, local, now) }, { follow(session, level) }, { marksTaken(session) })) {
                finish(session, now)
                sessions.removeAt(s)
            } else {
                if (session.startedClock.isNaN()) {
                    if (session.a.body.arrived && session.b.body.arrived) {
                        session.startedAt = now
                        session.startedClock = clock
                        for (directive in arrayOf(session.a.body.directive, session.b.body.directive)) {
                            directive.startedAt = now
                            directive.startedClock = clock
                        }
                        session.a.body.cheer(MOOD_PLAY)
                        session.b.body.cheer(MOOD_PLAY)
                    } else if (now - session.begunAt >= APPROACH_SECONDS) {
                        finish(session, now)
                        sessions.removeAt(s)
                    }
                }
            }
            s--
        }

        if (!PetTimers.due(tick, lastPairTick, PAIR_TICKS)) return
        lastPairTick = tick
        val expired = refusals.values.iterator()
        while (expired.hasNext()) if (expired.next() <= now) expired.remove()
        gather(pets, local, now)
        notice(now, level, local)
        for (i in order.indices) {
            val p = order[i]
            val q = p.notice ?: continue
            if (q.notice !== p || p.owner > q.owner) continue
            if (p.body.directive.active || q.body.directive.active) continue
            if (now - max(p.noticeSince, q.noticeSince) < NOTICE_SECONDS) continue
            if (now < p.noticeReadyAt || now < q.noticeReadyAt) continue
            if (begin(keyOf(p.owner, q.owner), p, q, level, now)) continue
            p.noticeReadyAt = now + RETRY_SECONDS
            q.noticeReadyAt = now + RETRY_SECONDS
        }
    }

    private fun gather(pets: List<PetManager.Pet>, local: UUID, now: Double) {
        var kept = 0
        for (i in order.indices) {
            val pet = order[i]
            if (pet.pairSeen == generation && candidate(pet, local, now)) {
                order[kept++] = pet
                pet.pairListed = generation
            } else {
                pet.notice = null
            }
        }
        while (order.size > kept) order.removeAt(order.size - 1)
        for (i in pets.indices) {
            val pet = pets[i]
            if (pet.pairListed == generation || !candidate(pet, local, now)) continue
            pet.pairListed = generation
            pet.notice = null
            order += pet
        }
        for (i in 1 until order.size) {
            val pet = order[i]
            var j = i - 1
            while (j >= 0 && sortsAfter(order[j], pet)) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = pet
        }
    }

    private object Pairs : PetPairRules<PetManager.Pet> {
        var now = 0.0
        var level: ClientLevel? = null
        var local: UUID? = null

        override fun x(pet: PetManager.Pet): Double = pet.ownerState.x

        override fun gapSq(p: PetManager.Pet, q: PetManager.Pet): Double = PetInteractions.gapSq(p, q)

        override fun compatible(p: PetManager.Pet, q: PetManager.Pet): Boolean {
            val local = local ?: return false
            return PetInteractions.compatible(p, q) && permits(p, q, local)
        }

        override fun cooling(p: PetManager.Pet, q: PetManager.Pet): Boolean = PetInteractions.cooling(p, q, now)

        override fun startable(p: PetManager.Pet, q: PetManager.Pet): Boolean {
            val level = level ?: return false
            return startableNow(p, q, level)
        }

        override fun before(q: PetManager.Pet, best: PetManager.Pet): Boolean = q.owner < best.owner

        override fun refused(p: PetManager.Pet, q: PetManager.Pet): Boolean =
            (refusals[probeOf(p.owner, q.owner)] ?: Double.NEGATIVE_INFINITY) > now

        override fun refuse(p: PetManager.Pet, q: PetManager.Pet) {
            refusals[keyOf(p.owner, q.owner)] = now + REFUSE_SECONDS
        }
    }

    private fun notice(now: Double, level: ClientLevel, local: UUID) {
        Pairs.now = now
        Pairs.level = level
        Pairs.local = local
        try {
            for (i in order.indices) {
                val p = order[i]
                val best = partner(order, i, Pairs)
                if (best === p.notice) continue
                p.notice = best
                p.noticeSince = now
                p.noticeReadyAt = if (best == null) Double.POSITIVE_INFINITY else cooldowns[probeOf(p.owner, best.owner)] ?: Double.NEGATIVE_INFINITY
            }
        } finally {
            Pairs.level = null
            Pairs.local = null
        }
    }

    internal fun <T : Any> partner(order: List<T>, i: Int, rules: PetPairRules<T>): T? {
        val p = order[i]
        val reach = NOTICE_BLOCKS * NOTICE_BLOCKS
        var best: T? = null
        var bestSq = reach
        var checked = 0
        var probes = 0
        var j = i - 1
        while (j >= 0 && checked < MAX_CANDIDATES && probes < MAX_PROBES) {
            val q = order[j--]
            if (rules.x(p) - rules.x(q) > NOTICE_BLOCKS) break
            val gap = rules.gapSq(p, q)
            if (gap > reach || !rules.compatible(p, q) || rules.cooling(p, q) || rules.refused(p, q)) continue
            if (!nearer(gap, q, bestSq, best, rules)) {
                checked++
                continue
            }
            probes++
            if (!rules.startable(p, q)) {
                rules.refuse(p, q)
                continue
            }
            checked++
            bestSq = gap
            best = q
        }
        checked = 0
        probes = 0
        j = i + 1
        while (j < order.size && checked < MAX_CANDIDATES && probes < MAX_PROBES) {
            val q = order[j++]
            if (rules.x(q) - rules.x(p) > NOTICE_BLOCKS) break
            val gap = rules.gapSq(p, q)
            if (gap > reach || !rules.compatible(p, q) || rules.cooling(p, q) || rules.refused(p, q)) continue
            if (!nearer(gap, q, bestSq, best, rules)) {
                checked++
                continue
            }
            probes++
            if (!rules.startable(p, q)) {
                rules.refuse(p, q)
                continue
            }
            checked++
            bestSq = gap
            best = q
        }
        return best
    }

    private fun sortsAfter(p: PetManager.Pet, q: PetManager.Pet): Boolean {
        val px = p.ownerState.x
        val qx = q.ownerState.x
        return px > qx || px == qx && p.owner > q.owner
    }

    private fun <T : Any> nearer(gap: Double, q: T, bestSq: Double, best: T?, rules: PetPairRules<T>): Boolean =
        gap < bestSq || gap == bestSq && best != null && rules.before(q, best)

    private fun candidate(pet: PetManager.Pet, local: UUID, now: Double): Boolean {
        return eligible(pet, local, now) && !pet.body.directive.active && PetBrain.ownerFree(pet)
    }

    private fun cooling(p: PetManager.Pet, q: PetManager.Pet, now: Double): Boolean =
        (cooldowns[probeOf(p.owner, q.owner)] ?: Double.NEGATIVE_INFINITY) > now

    private fun gapSq(p: PetManager.Pet, q: PetManager.Pet): Double {
        if (p.owner == q.owner) return Double.POSITIVE_INFINITY
        val dx = p.ownerState.x - q.ownerState.x
        val dy = p.ownerState.y - q.ownerState.y
        val dz = p.ownerState.z - q.ownerState.z
        return dx * dx + dy * dy + dz * dz
    }

    internal fun celebrating(pet: PetManager.Pet): Boolean {
        val directive = pet.body.directive
        if (!directive.started) return false
        return when (directive.interaction) {
            PetInteraction.Greet -> true
            PetInteraction.Play -> !directive.authored
            else -> false
        }
    }

    internal fun interactionTime(pet: PetManager.Pet, clock: Double): Float {
        val directive = pet.body.directive
        if (!directive.active) return 0f
        return if (directive.started) roleAge(directive, clock) else -1f
    }

    internal fun roleAge(directive: PetDirective, clock: Double): Float =
        if (directive.startedClock.isNaN()) 0f else (clock - directive.startedClock).coerceAtLeast(0.0).toFloat()

    internal fun played(startedClock: Double, seconds: Double, clock: Double): Boolean =
        !startedClock.isNaN() && clock - startedClock >= seconds

    internal fun sessionPlayed(
        roleTimed: Boolean,
        startedAt: Double,
        startedClock: Double,
        seconds: Double,
        now: Double,
        clock: Double,
    ): Boolean = if (roleTimed) played(startedClock, seconds, clock) else played(startedAt, seconds, now)

    internal fun roleTimed(vararg roles: PetSharedRole?): Boolean = roles.any { it != null && it.seconds > 0.0 }

    private fun endPlayed(now: Double, clock: Double) {
        var s = sessions.size - 1
        while (s >= 0) {
            val session = sessions[s]
            if (sessionPlayed(session.roleTimed, session.startedAt, session.startedClock, session.seconds, now, clock)) {
                finish(session, now)
                sessions.removeAt(s)
            }
            s--
        }
    }

    private fun eligible(pet: PetManager.Pet, local: UUID, now: Double): Boolean {
        if (!pet.present || pet.asset == null) return false
        if (!permissions.plays(pet.owner, local, ownPetPlay)) return false
        return !pet.ownerState.inCombat(now)
    }

    private fun permits(p: PetManager.Pet, q: PetManager.Pet, local: UUID): Boolean =
        permissions.allows(p.owner, q.owner, local, ownPetPlay, localFriend)

    private fun ends(session: Session, local: UUID, now: Double): Boolean {
        val a = session.a
        val b = session.b
        if (a.pairSeen != generation || b.pairSeen != generation) return true
        if (!eligible(a, local, now) || !eligible(b, local, now) || !permits(a, b, local)) return true
        if (!PetBrain.ownerFree(a) || !PetBrain.ownerFree(b)) return true
        if (gapSq(a, b) > APART_BLOCKS * APART_BLOCKS) return true
        if (session.shoulder && ownerGap(a, b) > session.ownerGap + OWNERS_APART_BLOCKS) return true
        return session.interaction == PetInteraction.Nap && !session.startedClock.isNaN() &&
            (a.ownerState.flag(PlayerSignals.IS_MOVING) || b.ownerState.flag(PlayerSignals.IS_MOVING))
    }

    internal inline fun sessionEnds(
        shoulder: Boolean,
        ends: () -> Boolean,
        follows: () -> Boolean,
        marksTaken: () -> Boolean,
    ): Boolean = if (shoulder) {
        ends() || !follows() || marksTaken()
    } else {
        ends() || marksTaken() || !follows()
    }

    private fun marksTaken(session: Session): Boolean =
        walkedOff(session.a) || walkedOff(session.b) || occupied(session)

    private fun follow(session: Session, level: ClientLevel): Boolean {
        if (!session.shoulder) return true
        shift(session)
        val dx = shift[0]
        val dy = shift[1]
        val dz = shift[2]
        var fits = true
        solidLevel = level
        try {
            for (seat in 0..1) {
                val pet = if (seat == 0) session.a else session.b
                val asset = pet.asset ?: return false
                val directive = pet.body.directive
                val lift = roleLift(directive, asset, session.interaction, directive.role)
                val moved = shiftMark(
                    directive,
                    asset.archetype,
                    dx,
                    dy,
                    dz,
                    lift,
                    PetSimulation.halfWidth(asset),
                    PetSimulation.height(asset),
                    levelGround,
                    levelSolid,
                )
                fits = fits && moved
            }
        } finally {
            solidLevel = null
        }
        for (seat in 0..1) {
            val pet = if (seat == 0) session.a else session.b
            val petDirective = pet.body.directive
            petDirective.anchorX = session.midX + dx
            petDirective.anchorZ = session.midZ + dz
            val asset = pet.asset
            petDirective.anchorY = if (asset == null) session.midY + dy else anchorY(petDirective, asset, session.midY + dy)
        }
        return fits
    }

    private fun shift(session: Session) {
        val a = session.a.ownerState
        val b = session.b.ownerState
        markShift(
            shift,
            session.aRides,
            a.x - session.aOwnerX,
            a.y - session.aOwnerY,
            a.z - session.aOwnerZ,
            session.bRides,
            b.x - session.bOwnerX,
            b.y - session.bOwnerY,
            b.z - session.bOwnerZ,
        )
    }

    internal fun markShift(
        out: DoubleArray,
        aRides: Boolean,
        aDx: Double,
        aDy: Double,
        aDz: Double,
        bRides: Boolean,
        bDx: Double,
        bDy: Double,
        bDz: Double,
    ) {
        val aShare = when {
            !aRides -> 0.0
            bRides -> 0.5
            else -> 1.0
        }
        val bShare = if (bRides) 1.0 - aShare else 0.0
        out[0] = aDx * aShare + bDx * bShare
        out[1] = aDy * aShare + bDy * bShare
        out[2] = aDz * aShare + bDz * bShare
    }

    private fun ownerGap(p: PetManager.Pet, q: PetManager.Pet): Double {
        val dx = p.ownerState.x - q.ownerState.x
        val dz = p.ownerState.z - q.ownerState.z
        return sqrt(dx * dx + dz * dz)
    }

    private fun walkedOff(pet: PetManager.Pet): Boolean {
        val asset = pet.asset ?: return true
        val directive = pet.body.directive
        return spotRoom(pet, asset, directive.interaction, directive.role) < 0.0
    }

    private fun spotRoom(pet: PetManager.Pet, asset: PetAsset, interaction: PetInteraction?, role: Int): Double {
        playSpot(reach, pet.body.directive, interaction, role, pet.ownerState.x, pet.ownerState.z)
        return PetBrain.playRoom(pet, asset, reach[0], reach[1], reach[2])
    }

    internal fun playSpot(
        out: DoubleArray,
        directive: PetDirective,
        interaction: PetInteraction?,
        role: Int,
        ownerX: Double,
        ownerZ: Double,
    ) {
        out[1] = directive.markY
        val circling = !directive.authored &&
            (interaction == PetInteraction.Chase || interaction == PetInteraction.Follow && role == ROLE_A)
        if (!circling) {
            out[0] = directive.markX
            out[2] = directive.markZ
            return
        }
        val dx = directive.circleX - ownerX
        val dz = directive.circleZ - ownerZ
        val length = sqrt(dx * dx + dz * dz)
        if (length < 1.0e-4) {
            out[0] = directive.circleX + directive.circleRadius
            out[2] = directive.circleZ
        } else {
            out[0] = directive.circleX + dx / length * directive.circleRadius
            out[2] = directive.circleZ + dz / length * directive.circleRadius
        }
    }

    private fun finish(session: Session, now: Double) {
        session.a.body.directive.clear()
        session.b.body.directive.clear()
        session.a.body.arrived = false
        session.b.body.arrived = false
        session.a.notice = null
        session.b.notice = null
        val iterator = cooldowns.values.iterator()
        while (iterator.hasNext()) if (iterator.next() <= now) iterator.remove()
        cooldowns[session.key] = now + COOLDOWN_SECONDS
    }

    private fun begin(
        key: PairKey,
        p: PetManager.Pet,
        q: PetManager.Pet,
        level: ClientLevel,
        now: Double,
    ): Boolean {
        val pAsset = p.asset ?: return false
        val qAsset = q.asset ?: return false
        meetBase(p, pAsset, q, qAsset, level)
        val baseX = spot[0]
        val baseY = spot[1]
        val baseZ = spot[2]
        var active = 0
        for (i in sessions.indices) {
            val session = sessions[i]
            if (near(session.baseX, session.baseY, session.baseZ, baseX, baseY, baseZ)) active++
        }
        if (active >= MAX_ACTIVE) return false
        val baseRoom = min(PetBrain.playRoom(p, pAsset, baseX, baseY, baseZ), PetBrain.playRoom(q, qAsset, baseX, baseY, baseZ))
        if (baseRoom < MARK_ROOM) return false

        val pWearer = CosmeticWearers.wearerId(p.owner)
        val qWearer = CosmeticWearers.wearerId(q.owner)
        val pFirst = JavaLong.compareUnsigned(pWearer, qWearer) <= 0
        val first = if (pFirst) p else q
        val second = if (pFirst) q else p
        val firstAsset = first.asset ?: return false
        val secondAsset = second.asset ?: return false

        val firstLeads = { play: PetInteraction ->
            supports(firstAsset, play, ROLE_A) && supports(secondAsset, play, ROLE_B)
        }
        val secondLeads = { play: PetInteraction ->
            supports(secondAsset, play, ROLE_A) && supports(firstAsset, play, ROLE_B)
        }
        val candidates = PetInteraction.entries.filter { firstLeads(it) || secondLeads(it) }
        if (candidates.isEmpty()) return false

        val seed = try {
            Renderer.pairSeed(
                if (pFirst) pWearer else qWearer,
                if (pFirst) qWearer else pWearer,
                (first.cosmeticId.toLong() shl 32) or (second.cosmeticId.toLong() and PET_ID_MASK),
            )
        } catch (ex: LinkageError) {
            LOGGER.debug("plusrenderer is not available to pair pets", ex)
            return false
        }
        val chaseRadius = min(CHASE_RADIUS, baseRoom - MARK_ROOM)
        val tieSide = (seed ushr 34) and 1L == 0L
        val firstIsA = { play: PetInteraction ->
            if (firstLeads(play) && secondLeads(play)) (seed ushr 32) and 1L == 0L else firstLeads(play)
        }
        sharedMeet.set(baseX, baseY, baseZ, first.ownerState.x, first.ownerState.z, second.ownerState.x, second.ownerState.z)
        val local = if (pAsset.archetype == PetArchetype.Shoulder || qAsset.archetype == PetArchetype.Shoulder) {
            null
        } else {
            meet((p.body.x + q.body.x) / 2.0, (p.body.y + q.body.y) / 2.0, (p.body.z + q.body.z) / 2.0, level)
            localMeet.set(spot[0], spot[1], spot[2], first.body.x, first.body.z, second.body.x, second.body.z)
        }
        var toward = 0f
        val interaction = choose(candidates, seed, sharedMeet, local) { play, meet ->
            if (!startable(play, first, second, chaseRadius)) return@choose false
            val leads = firstIsA(play)
            meetAxis(axis, meet, leads)
            val layout = layout(level, play, if (leads) first else second, if (leads) second else first, seed) ?: return@choose false
            if (arrange(layout, meet.x, meet.y, meet.z, tieSide)) {
                toward = layout.toward
                true
            } else {
                layout.a.body.directive.clear()
                layout.b.body.directive.clear()
                false
            }
        } ?: return false
        val leads = firstIsA(interaction)
        val a = if (leads) first else second
        val b = if (leads) second else first
        val aAsset = a.asset ?: return false
        val bAsset = b.asset ?: return false
        val aDirective = a.body.directive
        val bDirective = b.body.directive
        val midX = arranged[0]
        val midY = arranged[1]
        val midZ = arranged[2]
        for (directive in arrayOf(aDirective, bDirective)) {
            directive.anchorX = midX
            directive.anchorZ = midZ
            directive.anchorYaw = toward
        }
        aDirective.anchorY = anchorY(aDirective, aAsset, midY)
        bDirective.anchorY = anchorY(bDirective, bAsset, midY)
        aDirective.interaction = interaction
        aDirective.role = ROLE_A
        aDirective.partner = b
        bDirective.interaction = interaction
        bDirective.role = ROLE_B
        bDirective.partner = a
        a.body.arrived = false
        b.body.arrived = false
        for (directive in arrayOf(aDirective, bDirective)) {
            directive.baseMarkX = directive.markX
            directive.baseMarkY = directive.markY
            directive.baseMarkZ = directive.markZ
        }
        val aShared = aAsset.sharedRole(interaction, ROLE_A)?.takeIf { aDirective.authored }
        val bShared = bAsset.sharedRole(interaction, ROLE_B)?.takeIf { bDirective.authored }
        sessions += Session(
            key,
            interaction,
            a,
            b,
            now,
            midX,
            midY,
            midZ,
            ownerGap(a, b),
            baseX,
            baseY,
            baseZ,
            aAsset.archetype == PetArchetype.Shoulder,
            bAsset.archetype == PetArchetype.Shoulder,
            a.ownerState.x,
            a.ownerState.y,
            a.ownerState.z,
            b.ownerState.x,
            b.ownerState.y,
            b.ownerState.z,
            sessionSeconds(interaction, aShared, bShared),
            roleTimed(aShared, bShared),
        )
        return true
    }

    private fun meetBase(
        p: PetManager.Pet,
        pAsset: PetAsset,
        q: PetManager.Pet,
        qAsset: PetAsset,
        level: ClientLevel,
    ) {
        val pShoulder = pAsset.archetype == PetArchetype.Shoulder
        val qShoulder = qAsset.archetype == PetArchetype.Shoulder
        val perched = when {
            pShoulder == qShoulder -> null
            pShoulder -> p
            else -> q
        }
        val walker = if (perched === p) q else p
        if (perched != null) {
            meet(perched, walker.ownerState.x, walker.ownerState.z, level)
        } else {
            meet((p.ownerState.x + q.ownerState.x) / 2.0, (p.ownerState.y + q.ownerState.y) / 2.0, (p.ownerState.z + q.ownerState.z) / 2.0, level)
        }
    }

    private fun startableNow(p: PetManager.Pet, q: PetManager.Pet, level: ClientLevel): Boolean {
        val pAsset = p.asset ?: return false
        val qAsset = q.asset ?: return false
        meetBase(p, pAsset, q, qAsset, level)
        val room = min(PetBrain.playRoom(p, pAsset, spot[0], spot[1], spot[2]), PetBrain.playRoom(q, qAsset, spot[0], spot[1], spot[2]))
        if (room < MARK_ROOM) return false
        val chaseRadius = min(CHASE_RADIUS, room - MARK_ROOM)
        for (index in allInteractions.indices) {
            val interaction = allInteractions[index]
            if (shares(pAsset, qAsset, interaction) && startable(interaction, p, q, chaseRadius)) return true
        }
        return false
    }

    private fun shares(pAsset: PetAsset, qAsset: PetAsset, interaction: PetInteraction): Boolean =
        supports(pAsset, interaction, ROLE_A) && supports(qAsset, interaction, ROLE_B) ||
            supports(qAsset, interaction, ROLE_A) && supports(pAsset, interaction, ROLE_B)

    internal fun sessionSeconds(interaction: PetInteraction, vararg roles: PetSharedRole?): Double {
        var longest = 0.0
        var looping = false
        for (shared in roles) {
            if (shared == null || !(shared.seconds > 0.0)) continue
            val delay = if (shared.startDelay > 0.0) shared.startDelay else 0.0
            longest = max(longest, delay + shared.seconds)
            if (shared.looping) looping = true
        }
        return when {
            longest <= 0.0 -> interaction.seconds
            looping -> max(longest, interaction.seconds)
            else -> longest
        }
    }

    internal fun <T> pick(candidates: List<T>, seed: Long, accept: (T) -> Boolean): T? {
        if (candidates.isEmpty()) return null
        val start = JavaLong.remainderUnsigned(seed, candidates.size.toLong()).toInt()
        for (i in candidates.indices) {
            val candidate = candidates[(start + i) % candidates.size]
            if (accept(candidate)) return candidate
        }
        return null
    }

    internal fun <T> choose(candidates: List<T>, seed: Long, shared: PetMeet, local: PetMeet?, fits: (T, PetMeet) -> Boolean): T? {
        val chosen = pick(candidates, seed) { fits(it, shared) } ?: return null
        if (local != null && fits(chosen, local)) return chosen
        return if (fits(chosen, shared)) chosen else null
    }

    internal fun meetAxis(out: DoubleArray, meet: PetMeet, firstIsA: Boolean) {
        val ax = if (firstIsA) meet.firstX else meet.secondX
        val az = if (firstIsA) meet.firstZ else meet.secondZ
        val bx = if (firstIsA) meet.secondX else meet.firstX
        val bz = if (firstIsA) meet.secondZ else meet.firstZ
        val dx = bx - ax
        val dz = bz - az
        val length = sqrt(dx * dx + dz * dz)
        if (length < 1.0e-4) {
            out[0] = 0.0
            out[1] = 1.0
        } else {
            out[0] = dx / length
            out[1] = dz / length
        }
    }

    private fun layout(
        level: ClientLevel,
        interaction: PetInteraction,
        a: PetManager.Pet,
        b: PetManager.Pet,
        seed: Long,
    ): Layout? {
        val aAsset = a.asset ?: return null
        val bAsset = b.asset ?: return null
        val needed = MARK_ROOM + if (interaction == PetInteraction.Chase) MIN_CHASE_RADIUS else 0.0
        return Layout(level, interaction, a, aAsset, b, bAsset, axis[0], axis[1], seed, needed)
    }

    private fun startable(
        interaction: PetInteraction,
        first: PetManager.Pet,
        second: PetManager.Pet,
        chaseRadius: Double,
    ): Boolean = when (interaction) {
        PetInteraction.Nap -> resting(first.ownerState) && resting(second.ownerState)
        PetInteraction.Chase -> chaseRadius >= MIN_CHASE_RADIUS
        else -> true
    }

    private fun resting(owner: PetOwner): Boolean =
        !owner.flag(PlayerSignals.IS_MOVING) &&
            owner.flag(PlayerSignals.IS_ON_GROUND) &&
            !owner.flag(PlayerSignals.IS_SNEAKING) &&
            !owner.flag(PlayerSignals.IS_RIDING)

    private fun meet(perched: PetManager.Pet, towardX: Double, towardZ: Double, level: ClientLevel) {
        val owner = perched.ownerState
        val dx = towardX - owner.x
        val dz = towardZ - owner.z
        val length = sqrt(dx * dx + dz * dz).coerceAtLeast(1.0e-4)
        meet(owner.x + dx / length * SHOULDER_GREET, owner.y, owner.z + dz / length * SHOULDER_GREET, level)
    }

    private fun meet(x: Double, y: Double, z: Double, level: ClientLevel) {
        spot[0] = x
        spot[1] = y
        spot[2] = z
        val ground = PetCollision.surface(level, x, z, GAP, 0.6, y + 1.0, y - GROUND_REACH)
        if (!ground.isNaN()) spot[1] = ground
    }

    private class Layout(
        val level: ClientLevel,
        val interaction: PetInteraction,
        val a: PetManager.Pet,
        val aAsset: PetAsset,
        val b: PetManager.Pet,
        val bAsset: PetAsset,
        val axisX: Double,
        val axisZ: Double,
        val seed: Long,
        val needed: Double,
    ) {
        val aReach = PetSimulation.halfWidth(aAsset) + GAP / 2.0
        val bReach = PetSimulation.halfWidth(bAsset) + GAP / 2.0
        val toward = PetSimulation.headingOf(axisX, axisZ) ?: 0f
        val away = PetSimulation.headingOf(-axisX, -axisZ) ?: 0f
        val aside = PetSimulation.headingOf(-axisZ, axisX) ?: 0f
    }

    private fun arrange(layout: Layout, x: Double, y: Double, z: Double, tieSide: Boolean): Boolean {
        val aOwner = layout.a.ownerState
        val bOwner = layout.b.ownerState
        return PetMarkClearance.search(cleared, x, z, aOwner.x, aOwner.z, bOwner.x, bOwner.z, tieSide) { cx, cz ->
            fits(layout, cx, y, cz)
        }
    }

    private fun fits(layout: Layout, x: Double, y: Double, z: Double): Boolean {
        meet(x, y, z, layout.level)
        val midY = spot[1]
        val room = min(
            PetBrain.playRoom(layout.a, layout.aAsset, x, midY, z),
            PetBrain.playRoom(layout.b, layout.bAsset, x, midY, z),
        )
        if (room < layout.needed) return false
        val chaseRadius = min(CHASE_RADIUS, room - MARK_ROOM)
        placeMarks(layout, x, midY, z, chaseRadius)
        val aDirective = layout.a.body.directive
        val bDirective = layout.b.body.directive
        val interaction = layout.interaction
        val aLift = roleLift(aDirective, layout.aAsset, interaction, ROLE_A)
        val bLift = roleLift(bDirective, layout.bAsset, interaction, ROLE_B)
        if (!settle(aDirective, layout.aAsset, layout.level, aDirective.markY - aLift, aLift)) return false
        if (!settle(bDirective, layout.bAsset, layout.level, bDirective.markY - bLift, bLift)) return false
        var count = footprint(0, layout.aAsset, aDirective, interaction, ROLE_A, x, z)
        count = footprint(count, layout.bAsset, bDirective, interaction, ROLE_B, x, z)
        if (!ownerClear(layout.a.ownerState, count, x, z) || !ownerClear(layout.b.ownerState, count, x, z)) return false
        if (spotRoom(layout.a, layout.aAsset, interaction, ROLE_A) < 0.0) return false
        if (spotRoom(layout.b, layout.bAsset, interaction, ROLE_B) < 0.0) return false
        arranged[0] = x
        arranged[1] = midY
        arranged[2] = z
        return true
    }

    private fun placeMarks(layout: Layout, midX: Double, midY: Double, midZ: Double, chaseRadius: Double) {
        val interaction = layout.interaction
        val aDirective = layout.a.body.directive
        val bDirective = layout.b.body.directive
        val aAsset = layout.aAsset
        val bAsset = layout.bAsset
        val axisX = layout.axisX
        val axisZ = layout.axisZ
        val aReach = layout.aReach
        val bReach = layout.bReach
        val toward = layout.toward
        when (interaction) {
            PetInteraction.Greet, PetInteraction.Play -> {
                if (!roleMark(aDirective, aAsset, interaction, ROLE_A, midX, midY, midZ, toward)) {
                    mark(aDirective, aAsset, midX - axisX * aReach, midY, midZ - axisZ * aReach, toward, false)
                }
                if (!roleMark(bDirective, bAsset, interaction, ROLE_B, midX, midY, midZ, toward)) {
                    mark(bDirective, bAsset, midX + axisX * bReach, midY, midZ + axisZ * bReach, layout.away, false)
                }
            }
            PetInteraction.Nap -> {
                if (!roleMark(aDirective, aAsset, interaction, ROLE_A, midX, midY, midZ, toward)) {
                    mark(aDirective, aAsset, midX - axisX * aReach, midY, midZ - axisZ * aReach, layout.aside, true)
                }
                if (!roleMark(bDirective, bAsset, interaction, ROLE_B, midX, midY, midZ, toward)) {
                    mark(bDirective, bAsset, midX + axisX * bReach, midY, midZ + axisZ * bReach, layout.aside, true)
                }
            }
            PetInteraction.Follow -> {
                val least = PetSimulation.halfWidth(aAsset) + PetSimulation.halfWidth(bAsset) + CIRCLE_GAP
                if (!authored(aDirective, aAsset, bDirective, bAsset, interaction, midX, midY, midZ, toward)) {
                    mark(aDirective, aAsset, midX - axisX * least, midY, midZ - axisZ * least, toward, false)
                    mark(bDirective, bAsset, midX, midY, midZ, layout.away, false)
                    aDirective.circleX = midX
                    aDirective.circleZ = midZ
                    aDirective.circleRadius = least
                    aDirective.circlePhase = atan2(-axisZ, -axisX)
                }
            }
            PetInteraction.Chase -> {
                if (!authored(aDirective, aAsset, bDirective, bAsset, interaction, midX, midY, midZ, toward)) {
                    mark(aDirective, aAsset, midX - axisX * aReach, midY, midZ - axisZ * aReach, toward, false)
                    mark(bDirective, bAsset, midX + axisX * bReach, midY, midZ + axisZ * bReach, layout.away, false)
                    val spin = if ((layout.seed ushr 33) and 1L == 0L) 1.0 else -1.0
                    for ((directive, sign) in arrayOf(aDirective to -1.0, bDirective to 1.0)) {
                        directive.circleX = midX
                        directive.circleZ = midZ
                        directive.circleRadius = chaseRadius
                        directive.circlePhase = atan2(axisZ * sign, axisX * sign)
                        directive.spin = spin
                    }
                }
            }
        }
    }

    private fun footprint(
        count: Int,
        asset: PetAsset,
        directive: PetDirective,
        interaction: PetInteraction,
        role: Int,
        midX: Double,
        midZ: Double,
    ): Int = markFootprint(
        discs,
        count,
        asset.archetype,
        PetSimulation.halfWidth(asset),
        PetSimulation.height(asset),
        roleLift(directive, asset, interaction, role),
        directive,
        interaction,
        role,
        midX,
        midZ,
    )

    internal fun markFootprint(
        out: DoubleArray,
        count: Int,
        archetype: PetArchetype,
        half: Double,
        height: Double,
        lift: Double,
        directive: PetDirective,
        interaction: PetInteraction,
        role: Int,
        midX: Double,
        midZ: Double,
    ): Int {
        if (archetype == PetArchetype.Shoulder && !directive.authored) return count
        val at = count * PetMarkClearance.STRIDE
        val circling = !directive.authored &&
            (interaction == PetInteraction.Chase || interaction == PetInteraction.Follow && role == ROLE_A)
        if (circling) {
            out[at] = directive.circleX - midX
            out[at + 1] = directive.circleZ - midZ
            out[at + 2] = directive.circleRadius + half
            out[at + 3] = directive.markY
            out[at + 4] = directive.markY + height + if (archetype == PetArchetype.Flying) CHASE_LIFT_ROOM else 0.0
        } else {
            val bottom = if (archetype == PetArchetype.Walking) directive.markY + lift else directive.markY
            out[at] = directive.markX - midX
            out[at + 1] = directive.markZ - midZ
            out[at + 2] = half
            out[at + 3] = bottom
            out[at + 4] = bottom + height
        }
        return count + 1
    }

    private fun placedFootprint(count: Int, pet: PetManager.Pet): Int {
        val directive = pet.body.directive
        if (!directive.authored) return count
        val asset = pet.asset ?: return count
        val interaction = directive.interaction ?: return count
        val shared = asset.sharedRole(interaction, directive.role) ?: return count
        val radians = Math.toRadians(directive.anchorYaw.toDouble())
        val s = sin(radians)
        val c = cos(radians)
        val at = count * PetMarkClearance.STRIDE
        val bottom = directive.anchorY + shared.offsetY
        discs[at] = directive.anchorX + shared.offsetX * c - shared.offsetZ * s
        discs[at + 1] = directive.anchorZ + shared.offsetX * s + shared.offsetZ * c
        discs[at + 2] = PetSimulation.halfWidth(asset)
        discs[at + 3] = bottom
        discs[at + 4] = bottom + PetSimulation.height(asset)
        return count + 1
    }

    private fun sessionFootprint(count: Int, session: Session, pet: PetManager.Pet): Int {
        val directive = pet.body.directive
        if (directive.authored) return placedFootprint(count, pet)
        if (!session.shoulder) return count
        val asset = pet.asset ?: return count
        return footprint(count, asset, directive, session.interaction, directive.role, 0.0, 0.0)
    }

    private fun occupied(session: Session): Boolean {
        val count = sessionFootprint(sessionFootprint(0, session, session.a), session, session.b)
        if (count == 0) return false
        for (owner in arrayOf(session.a.ownerState, session.b.ownerState)) {
            if (!PetMarkClearance.clears(discs, count, 0.0, 0.0, owner.x, owner.y, owner.z, 0.0)) return true
        }
        return false
    }

    private fun ownerClear(owner: PetOwner, count: Int, x: Double, z: Double): Boolean =
        PetMarkClearance.clears(discs, count, x, z, owner.x, owner.y, owner.z)

    private fun settle(directive: PetDirective, asset: PetAsset, level: ClientLevel, groundY: Double, lift: Double): Boolean {
        solidLevel = level
        try {
            return settleMark(
                directive,
                asset.archetype,
                groundY,
                lift,
                PetSimulation.halfWidth(asset),
                PetSimulation.height(asset),
                levelGround,
                levelSolid,
            )
        } finally {
            solidLevel = null
        }
    }

    internal fun shiftMark(
        directive: PetDirective,
        archetype: PetArchetype,
        dx: Double,
        dy: Double,
        dz: Double,
        lift: Double,
        halfWidth: Double,
        height: Double,
        ground: PetGround,
        solid: PetSolid,
    ): Boolean {
        directive.markX = directive.baseMarkX + dx
        directive.markY = directive.baseMarkY + dy
        directive.markZ = directive.baseMarkZ + dz
        return settleMark(directive, archetype, directive.markY, lift, halfWidth, height, ground, solid)
    }

    internal fun settleMark(
        directive: PetDirective,
        archetype: PetArchetype,
        groundY: Double,
        lift: Double,
        halfWidth: Double,
        height: Double,
        ground: PetGround,
        solid: PetSolid,
    ): Boolean {
        if (archetype != PetArchetype.Walking) {
            return airMarkFree(archetype, directive.authored, directive.markX, directive.markY, directive.markZ, halfWidth, height, solid)
        }
        val surface = ground.surface(
            directive.markX,
            directive.markZ,
            halfWidth,
            height,
            groundY + PetSimulation.CLIMB_SLACK,
            groundY - GROUND_REACH,
        )
        directive.markY = if (surface.isNaN()) groundY else surface
        if (surface.isNaN()) return false
        return roleBoxFree(directive.markX, surface, directive.markZ, lift, halfWidth, height, solid)
    }

    internal fun roleBoxFree(
        x: Double,
        ground: Double,
        z: Double,
        lift: Double,
        halfWidth: Double,
        height: Double,
        solid: PetSolid,
    ): Boolean = !(lift > 0.0) || !solid.overlaps(x, ground + lift, z, halfWidth, height)

    internal fun airMarkFree(
        archetype: PetArchetype,
        authored: Boolean,
        x: Double,
        y: Double,
        z: Double,
        halfWidth: Double,
        height: Double,
        solid: PetSolid,
    ): Boolean {
        if (archetype == PetArchetype.Walking || archetype == PetArchetype.Shoulder && !authored) return true
        return !solid.overlaps(x, y, z, halfWidth, height)
    }

    private fun roleLift(directive: PetDirective, asset: PetAsset, interaction: PetInteraction, role: Int): Double {
        if (!directive.authored) return 0.0
        return asset.sharedRole(interaction, role)?.offsetY ?: 0.0
    }

    private fun anchorY(directive: PetDirective, asset: PetAsset, groundY: Double): Double =
        if (directive.authored && asset.archetype == PetArchetype.Walking) directive.markY else groundY

    private fun mark(
        directive: PetDirective,
        asset: PetAsset,
        x: Double,
        groundY: Double,
        z: Double,
        yaw: Float,
        resting: Boolean,
    ) {
        val hover = when {
            asset.archetype != PetArchetype.Flying -> 0.0
            resting -> NAP_HOVER
            else -> FLY_MARK_HEIGHT
        }
        place(directive, x, groundY + hover, z, yaw)
    }

    private fun place(directive: PetDirective, x: Double, y: Double, z: Double, yaw: Float) {
        directive.clear()
        directive.markX = x
        directive.markY = y
        directive.markZ = z
        directive.faceYaw = yaw
        directive.spin = 1.0
    }

    private fun roleMark(
        directive: PetDirective,
        asset: PetAsset,
        interaction: PetInteraction,
        role: Int,
        anchorX: Double,
        anchorY: Double,
        anchorZ: Double,
        anchorYaw: Float,
    ): Boolean {
        val shared = asset.sharedRole(interaction, role) ?: return false
        val radians = Math.toRadians(anchorYaw.toDouble())
        val s = sin(radians)
        val c = cos(radians)
        place(
            directive,
            anchorX + shared.offsetX * c - shared.offsetZ * s,
            anchorY + shared.offsetY,
            anchorZ + shared.offsetX * s + shared.offsetZ * c,
            anchorYaw + shared.yaw,
        )
        directive.authored = true
        return true
    }

    private fun authored(
        aDirective: PetDirective,
        aAsset: PetAsset,
        bDirective: PetDirective,
        bAsset: PetAsset,
        interaction: PetInteraction,
        anchorX: Double,
        anchorY: Double,
        anchorZ: Double,
        anchorYaw: Float,
    ): Boolean {
        if (aAsset.sharedRole(interaction, ROLE_A) == null || bAsset.sharedRole(interaction, ROLE_B) == null) return false
        roleMark(aDirective, aAsset, interaction, ROLE_A, anchorX, anchorY, anchorZ, anchorYaw)
        roleMark(bDirective, bAsset, interaction, ROLE_B, anchorX, anchorY, anchorZ, anchorYaw)
        return true
    }

    internal fun sharedRole(pet: PetManager.Pet): PetSharedRole? {
        val directive = pet.body.directive
        if (!directive.started || !directive.authored) return null
        val interaction = directive.interaction ?: return null
        return pet.asset?.sharedRole(interaction, directive.role)
    }

    private fun compatible(p: PetManager.Pet, q: PetManager.Pet): Boolean {
        val pAsset = p.asset ?: return false
        val qAsset = q.asset ?: return false
        return compatible(pAsset.archetype, pAsset.interactions, qAsset.archetype, qAsset.interactions)
    }

    internal fun compatible(
        pArchetype: PetArchetype,
        pRoles: Map<PetInteraction, PetRoles>,
        qArchetype: PetArchetype,
        qRoles: Map<PetInteraction, PetRoles>,
    ): Boolean {
        for (index in allInteractions.indices) {
            val interaction = allInteractions[index]
            if (supports(pArchetype, pRoles, interaction, ROLE_A) && supports(qArchetype, qRoles, interaction, ROLE_B) ||
                supports(qArchetype, qRoles, interaction, ROLE_A) && supports(pArchetype, pRoles, interaction, ROLE_B)
            ) {
                return true
            }
        }
        return false
    }

    private fun supports(asset: PetAsset, interaction: PetInteraction, role: Int): Boolean =
        supports(asset.archetype, asset.interactions, interaction, role)

    private fun supports(
        archetype: PetArchetype,
        interactions: Map<PetInteraction, PetRoles>,
        interaction: PetInteraction,
        role: Int,
    ): Boolean {
        if (archetype == PetArchetype.Shoulder && interaction != PetInteraction.Greet) return false
        val declared = interactions[interaction]
        return if (declared != null) {
            if (role == ROLE_A) declared.asA else declared.asB
        } else {
            interaction != PetInteraction.Play
        }
    }

    private fun near(x: Double, y: Double, z: Double, otherX: Double, otherY: Double, otherZ: Double): Boolean {
        val dx = x - otherX
        val dy = y - otherY
        val dz = z - otherZ
        return dx * dx + dy * dy + dz * dz <= BUDGET_BLOCKS * BUDGET_BLOCKS
    }

    private fun keyOf(a: UUID, b: UUID): PairKey = if (a <= b) PairKey(a, b) else PairKey(b, a)

    private fun probeOf(a: UUID, b: UUID): PairKey {
        if (a <= b) {
            probe.first = a
            probe.second = b
        } else {
            probe.first = b
            probe.second = a
        }
        return probe
    }

    private const val SHOULDER_GREET = 1.2
    private const val OWNERS_APART_BLOCKS = 2.0
    private const val PET_ID_MASK = 0xFFFFFFFFL
    private const val CHASE_RADIUS = 2.5
    private const val CHASE_LIFT_ROOM = 1.5
    private const val MIN_CHASE_RADIUS = 1.5
}
