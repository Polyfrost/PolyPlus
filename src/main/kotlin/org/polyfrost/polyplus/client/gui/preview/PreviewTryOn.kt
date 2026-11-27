package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.runtime.mutableStateListOf
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.lobby.LobbyAvatar
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class PreviewTryOn {
    private val record = PlayerSignalRecord()
    private val holding = BooleanArray(PlayerSignals.SIGNAL_COUNT)
    private val pulseEnds = LongArray(PlayerSignals.SIGNAL_COUNT)
    private var jumpStart = 0L
    private val fired = ArrayList<String>()
    private val firing = ArrayList<String>()
    private var serial = 0
    @Volatile
    private var equipRequested = false
    private var equipping = false
    private var lobbyEquipFrame = NO_FRAME
    @Volatile
    private var equipTargets: Set<Int> = emptySet()

    @Volatile
    var bonuses: List<BonusRef> = emptyList()

    val held = mutableStateListOf<Int>()

    fun isHeld(signal: Int): Boolean = signal in held

    fun toggle(signal: Int) {
        if (signal !in 0 until PlayerSignals.SIGNAL_COUNT) return
        holding[signal] = !holding[signal]
        if (holding[signal]) held += signal else held -= signal
    }

    fun pulse(signal: Int) {
        if (signal !in 0 until PlayerSignals.SIGNAL_COUNT) return
        pulseEnds[signal] = System.nanoTime() + PULSE_NANOS
        if (signal == PlayerSignals.IS_HURT) record.raise(PlayerSignals.EVENT_HURT)
        if (signal == PlayerSignals.IS_SLEEPING) record.raise(PlayerSignals.EVENT_SLEEP)
    }

    fun jump() {
        jumpStart = System.nanoTime()
        record.raise(PlayerSignals.EVENT_JUMP)
    }

    fun fire(effect: String) {
        if (fired.size < MAX_FIRED) fired += effect
    }

    fun wearAgain(targets: Set<Int>) {
        if (targets.isEmpty()) return
        equipTargets = targets
        equipRequested = true
    }

    fun reset() {
        holding.fill(false)
        pulseEnds.fill(0L)
        jumpStart = 0L
        fired.clear()
        firing.clear()
        held.clear()
        equipRequested = false
        equipping = false
        lobbyEquipFrame = NO_FRAME
        equipTargets = emptySet()
    }

    internal fun lobbyEquip(frame: Int, cosmeticId: Int): Boolean {
        if (equipRequested) {
            equipRequested = false
            lobbyEquipFrame = frame
        }
        return lobbyEquipFrame == frame && cosmeticId in equipTargets
    }

    private fun equips(cosmeticId: Int): Boolean = equipping && cosmeticId in equipTargets

    internal fun crouching(): Boolean = active(PlayerSignals.IS_SNEAKING, System.nanoTime())

    internal fun walkSpeed(): Float {
        val now = System.nanoTime()
        return when {
            active(PlayerSignals.IS_SPRINTING, now) -> SPRINT_LEGS
            active(PlayerSignals.IS_MOVING, now) -> WALK_LEGS
            else -> 0f
        }
    }

    internal fun signals(): PlayerSignalRecord {
        val now = System.nanoTime()
        PlayerSignals.NEUTRAL.values.copyInto(record.values)
        for (signal in 0 until PlayerSignals.SIGNAL_COUNT) {
            if (active(signal, now)) apply(signal)
        }
        val air = airSeconds(now)
        if (air >= 0f) {
            record.set(PlayerSignals.IS_ON_GROUND, false)
            record.set(PlayerSignals.VERTICAL_SPEED, JUMP_SPEED - GRAVITY * air)
        }
        return record
    }

    internal fun lift(): Float {
        val air = airSeconds(System.nanoTime())
        if (air < 0f) return 0f
        return max(0f, JUMP_SPEED * air - GRAVITY * air * air / 2f)
    }

    internal fun beginFrame() {
        equipping = equipRequested
        equipRequested = false
        firing.clear()
        if (fired.isEmpty()) return
        firing += fired
        fired.clear()
        serial = ++serials
    }

    private fun overrides(): Int {
        val now = System.nanoTime()
        var mask = 0
        for ((bit, signal) in OVERRIDDEN.withIndex()) {
            if (active(signal, now)) mask = mask or (1 shl bit)
        }
        return mask
    }

    private fun airSeconds(now: Long): Float {
        if (jumpStart == 0L) return -1f
        val seconds = (now - jumpStart) / NANOS_PER_SECOND
        if (seconds > JUMP_SECONDS) {
            jumpStart = 0L
            return -1f
        }
        return seconds
    }

    private fun active(signal: Int, now: Long): Boolean = holding[signal] || now < pulseEnds[signal]

    private fun apply(signal: Int) {
        record.set(signal, true)
        when (signal) {
            PlayerSignals.IS_SPRINTING -> moving(SPRINT_SPEED)
            PlayerSignals.IS_MOVING -> moving(WALK_SPEED)
            PlayerSignals.IS_SWIMMING -> {
                record.set(PlayerSignals.IS_IN_WATER, true)
                record.set(PlayerSignals.IS_ON_GROUND, false)
                moving(SWIM_SPEED)
            }
            PlayerSignals.IS_IN_OCEAN -> record.set(PlayerSignals.IS_IN_WATER, true)
            PlayerSignals.IS_GLIDING -> {
                record.set(PlayerSignals.IS_ON_GROUND, false)
                record.set(PlayerSignals.VERTICAL_SPEED, GLIDE_SINK)
                moving(GLIDE_SPEED)
            }
            PlayerSignals.IS_FLYING -> record.set(PlayerSignals.IS_ON_GROUND, false)
            PlayerSignals.IS_FALLING -> {
                record.set(PlayerSignals.IS_ON_GROUND, false)
                record.set(PlayerSignals.VERTICAL_SPEED, FALL_SPEED)
            }
            PlayerSignals.IS_ON_GROUND -> record.set(PlayerSignals.IS_ON_GROUND, false)
        }
    }

    private fun moving(speed: Float) {
        record.set(PlayerSignals.IS_MOVING, true)
        record.set(PlayerSignals.GROUND_SPEED, max(speed, record[PlayerSignals.GROUND_SPEED]))
    }

    companion object {
        private const val PULSE_NANOS = 600_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val JUMP_SPEED = 8.4f
        private const val GRAVITY = 32f
        private const val JUMP_SECONDS = 0.55f
        private const val WALK_SPEED = 4.3f
        private const val SPRINT_SPEED = 5.6f
        private const val SWIM_SPEED = 2f
        private const val GLIDE_SPEED = 20f
        private const val GLIDE_SINK = -3f
        private const val FALL_SPEED = -15f
        private const val WALK_LEGS = 0.6f
        private const val SPRINT_LEGS = 1f
        private const val MAX_FIRED = 8
        private const val PIXELS_PER_BLOCK = 16f
        private const val NO_FRAME = Int.MIN_VALUE

        @Volatile
        internal var current: PreviewTryOn? = null

        @Volatile
        internal var yaw = 0f

        @Volatile
        internal var lobby: PreviewTryOn? = null

        @PublishedApi
        @Volatile
        internal var scoped: PreviewTryOn? = null

        @PublishedApi
        @Volatile
        internal var scopeThread: Thread? = null

        @JvmStatic
        fun lobbyTryOn(entity: Any?): PreviewTryOn? {
            val tryOn = lobby ?: return null
            return tryOn.takeIf { entity is LobbyAvatar && entity.isLocal }
        }

        @JvmStatic
        fun lobbySignals(entity: Any?): PlayerSignalRecord? = lobbyTryOn(entity)?.signals()

        inline fun <T> withLobby(tryOn: PreviewTryOn?, block: () -> T): T {
            if (tryOn == null) return block()
            val previous = scoped
            val previousThread = scopeThread
            scopeThread = Thread.currentThread()
            scoped = tryOn
            try {
                return block()
            } finally {
                scoped = previous
                scopeThread = previousThread
            }
        }

        private fun inScope(): PreviewTryOn? = scoped?.takeIf { scopeThread === Thread.currentThread() }

        @JvmStatic
        fun inLobbyScope(): Boolean = inScope() != null

        @JvmStatic
        fun lobbyBonuses(): List<BonusRef>? = inScope()?.bonuses

        @JvmStatic
        fun lobbyOverrideMask(): Int = inScope()?.overrides() ?: 0

        @JvmStatic
        fun lobbyEquipEvents(frame: Int, cosmeticId: Int): Int =
            if (inScope()?.lobbyEquip(frame, cosmeticId) == true) Renderer.EVENT_EQUIP else 0

        @JvmStatic
        fun previewSignals(): PlayerSignalRecord = current?.signals() ?: PlayerSignals.NEUTRAL

        private var serials = 0

        @JvmField
        internal val OVERRIDDEN = intArrayOf(PlayerSignals.IS_IN_COMBAT)

        @JvmStatic
        fun firing(): List<String> = current?.firing.orEmpty()

        @JvmStatic
        fun firingSerial(): Int = current?.takeIf { it.firing.isNotEmpty() }?.serial ?: 0

        @JvmStatic
        fun equipEvents(cosmeticId: Int): Int = if (current?.equips(cosmeticId) == true) Renderer.EVENT_EQUIP else 0

        @JvmStatic
        fun overrideMask(): Int = current?.overrides() ?: 0

        @JvmStatic
        fun previewBonuses(): List<BonusRef> = current?.bonuses.orEmpty()

        @JvmStatic
        fun previewRoot(out: FloatArray) {
            val k = 1f / PIXELS_PER_BLOCK
            val radians = Math.toRadians(yaw.toDouble())
            val c = cos(radians).toFloat()
            val s = sin(radians).toFloat()
            out.fill(0f)
            out[0] = c * k
            out[2] = -s * k
            out[5] = k
            out[7] = current?.lift() ?: 0f
            out[8] = s * k
            out[10] = c * k
        }
    }
}
