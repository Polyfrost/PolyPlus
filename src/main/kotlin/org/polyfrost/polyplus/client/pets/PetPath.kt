package org.polyfrost.polyplus.client.pets

import net.minecraft.client.multiplayer.ClientLevel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

internal class PetStall {
    private var index = -1
    private var best = Double.POSITIVE_INFINITY
    private var ticks = 0

    fun reset() {
        index = -1
        best = Double.POSITIVE_INFINITY
        ticks = 0
    }

    fun stuck(index: Int, distance: Double): Boolean {
        if (index != this.index || distance < best - PROGRESS_BLOCKS) {
            this.index = index
            best = distance
            ticks = 0
            return false
        }
        if (++ticks < STUCK_TICKS) return false
        reset()
        return true
    }

    companion object {
        const val STUCK_TICKS = 20
        private const val PROGRESS_BLOCKS = 0.05
    }
}

internal class PetPath {
    enum class State { Idle, Searching, Found, Failed }

    var state = State.Idle
        private set
    var reached = false
        private set
    var count = 0
        private set
    var index = 0
    var goalX = 0.0
        private set
    var goalY = 0.0
        private set
    var goalZ = 0.0
        private set
    val work: Int get() = credit.work

    private var points = DoubleArray(3 * 64)
    private var level: ClientLevel? = null
    private var flying = false
    private var swimmer = false
    private var halfWidth = 0.3
    private var height = 0.6
    private var nodeCost = 1
    private val credit = PetPathCredit()
    private var originX = 0
    private var originY = 0
    private var originZ = 0
    private var startH = 0.0

    private val nodeX = IntArray(MAX_NODES)
    private val nodeZ = IntArray(MAX_NODES)
    private val nodeY = DoubleArray(MAX_NODES)
    private val nodeG = DoubleArray(MAX_NODES)
    private val nodeF = DoubleArray(MAX_NODES)
    private val nodeParent = IntArray(MAX_NODES)
    private val nodeClosed = BooleanArray(MAX_NODES)
    private var nodes = 0
    private val heap = IntArray(MAX_NODES * HEAP_FACTOR)
    private var heapSize = 0
    private val keys = IntArray(TABLE)
    private val slots = IntArray(TABLE)
    private var best = -1
    private var bestH = Double.POSITIVE_INFINITY

    val active: Boolean get() = state == State.Found && index < count

    fun x(i: Int): Double = points[i * 3]

    fun y(i: Int): Double = points[i * 3 + 1]

    fun z(i: Int): Double = points[i * 3 + 2]

    fun clear() {
        state = State.Idle
        count = 0
        index = 0
        reached = false
        level = null
        credit.reset()
    }

    fun start(
        level: ClientLevel,
        fromX: Double,
        fromY: Double,
        fromZ: Double,
        toX: Double,
        toY: Double,
        toZ: Double,
        flying: Boolean,
        swimmer: Boolean,
        halfWidth: Double,
        height: Double,
    ) {
        this.level = level
        this.flying = flying
        this.swimmer = swimmer
        this.halfWidth = halfWidth
        this.height = height
        nodeCost = probeCost(halfWidth, height)
        goalX = toX
        goalY = toY
        goalZ = toZ
        originX = floor(fromX).toInt()
        originY = floor(fromY).toInt()
        originZ = floor(fromZ).toInt()
        nodes = 0
        heapSize = 0
        keys.fill(0)
        best = -1
        bestH = Double.POSITIVE_INFINITY
        reached = false
        count = 0
        index = 0
        credit.reset()
        state = State.Searching
        val startY = if (flying) floor(fromY) else fromY
        startH = heuristic(originX + 0.5, startY, originZ + 0.5)
        open(originX, startY, originZ, 0.0, -1)
    }

    fun step(budget: Int, headroom: Int = Int.MAX_VALUE): Int {
        credit.begin(nodeCost, budget, headroom)
        if (state != State.Searching) return credit.end(false)
        val level = level ?: run {
            state = State.Failed
            return credit.end(false)
        }
        while (credit.affordable()) {
            if (heapSize == 0 || nodes >= MAX_NODES) {
                finish(false)
                break
            }
            val current = pop()
            if (nodeClosed[current]) continue
            nodeClosed[current] = true
            credit.charge()
            val cx = nodeX[current]
            val cy = nodeY[current]
            val cz = nodeZ[current]
            val h = heuristic(cx + 0.5, cy, cz + 0.5)
            if (h < bestH) {
                bestH = h
                best = current
            }
            if (arrived(cx + 0.5, cy, cz + 0.5)) {
                best = current
                finish(true)
                break
            }
            if (flying) expandFlying(level, current, cx, cy, cz) else expandWalking(level, current, cx, cy, cz)
        }
        return credit.end(state == State.Searching)
    }

    private fun arrived(x: Double, y: Double, z: Double): Boolean {
        val dx = x - goalX
        val dz = z - goalZ
        val dy = y - goalY
        return dx * dx + dz * dz <= ARRIVE_BLOCKS * ARRIVE_BLOCKS && abs(dy) <= if (flying) ARRIVE_BLOCKS else ARRIVE_HEIGHT
    }

    private fun expandWalking(level: ClientLevel, current: Int, cx: Int, cy: Double, cz: Int) {
        val inWater = PetCollision.hazard(level, cx + 0.5, cy, cz + 0.5, halfWidth, height) == PetCollision.WATER
        for (d in 0 until 8) {
            val dx = DX[d]
            val dz = DZ[d]
            val nx = cx + dx
            val nz = cz + dz
            if (outside(nx, nz)) continue
            val ny = stand(level, nx, nz, cy)
            if (ny.isNaN()) continue
            if (d >= 4) {
                val ax = stand(level, cx + dx, cz, cy)
                if (ax.isNaN()) continue
                val az = stand(level, cx, cz + dz, cy)
                if (az.isNaN()) continue
                if (abs(ax - cy) > STEP || abs(az - cy) > STEP || abs(ax - ny) > STEP || abs(az - ny) > STEP) continue
            }
            val rise = ny - cy
            if (rise > STEP && !PetCollision.surface(level, cx + 0.5, cz + 0.5, halfWidth, height + rise, cy + EPS, cy - EPS).isFinite()) continue
            val hazard = PetCollision.hazard(level, nx + 0.5, ny, nz + 0.5, halfWidth, height)
            if (hazard == PetCollision.DANGER) continue
            if (hazard == PetCollision.WATER && !swimmer && !inWater) continue
            var cost = if (d >= 4) DIAGONAL else 1.0
            if (rise > STEP) cost += JUMP_COST
            if (rise < -STEP) cost += -rise * DROP_COST
            if (hazard == PetCollision.WATER && !swimmer) cost += WATER_COST
            open(nx, ny, nz, nodeG[current] + cost, current)
        }
    }

    private fun expandFlying(level: ClientLevel, current: Int, cx: Int, cy: Double, cz: Int) {
        for (d in 0 until 10) {
            val dx = if (d < 8) DX[d] else 0
            val dz = if (d < 8) DZ[d] else 0
            val dy = when (d) {
                8 -> 1
                9 -> -1
                else -> 0
            }
            val nx = cx + dx
            val nz = cz + dz
            val ny = cy + dy
            if (outside(nx, nz) || abs(ny - originY) > MAX_RISE) continue
            if (!air(level, nx, ny, nz)) continue
            if (d in 4..7 && (!air(level, cx + dx, cy, cz) || !air(level, cx, cy, cz + dz))) continue
            if (PetCollision.hazard(level, nx + 0.5, ny + EPS, nz + 0.5, halfWidth, height + AIR_LIFT) == PetCollision.DANGER) continue
            if (d in 4..7 && (danger(level, cx + dx, cy, cz) || danger(level, cx, cy, cz + dz))) continue
            open(nx, ny, nz, nodeG[current] + if (d in 4..7) DIAGONAL else 1.0, current)
        }
    }

    private fun danger(level: ClientLevel, x: Int, y: Double, z: Int): Boolean =
        PetCollision.hazard(level, x + 0.5, y + EPS, z + 0.5, halfWidth, height + AIR_LIFT) == PetCollision.DANGER

    private fun air(level: ClientLevel, x: Int, y: Double, z: Int): Boolean =
        PetCollision.loaded(level, x + 0.5, y, z + 0.5) &&
            !PetCollision.overlaps(level, x + 0.5, y + AIR_LIFT, z + 0.5, halfWidth, height)

    private fun stand(level: ClientLevel, x: Int, z: Int, fromY: Double): Double {
        if (!PetCollision.loaded(level, x + 0.5, fromY, z + 0.5)) return Double.NaN
        return PetCollision.surface(level, x + 0.5, z + 0.5, halfWidth, height, fromY + JUMP, fromY - MAX_DROP)
    }

    private fun outside(x: Int, z: Int): Boolean = abs(x - originX) > MAX_REACH || abs(z - originZ) > MAX_REACH

    private fun heuristic(x: Double, y: Double, z: Double): Double {
        val dx = x - goalX
        val dy = y - goalY
        val dz = z - goalZ
        return sqrt(dx * dx + dy * dy + dz * dz) * GREED
    }

    private fun open(x: Int, y: Double, z: Int, g: Double, parent: Int) {
        val key = key(x, y, z)
        var slot = (key * HASH_MIX ushr (32 - TABLE_BITS))
        while (true) {
            val stored = keys[slot]
            if (stored == 0) break
            if (stored == key) {
                val node = slots[slot]
                if (nodeClosed[node] || g >= nodeG[node]) return
                nodeG[node] = g
                nodeF[node] = g + heuristic(x + 0.5, y, z + 0.5)
                nodeParent[node] = parent
                push(node)
                return
            }
            slot = (slot + 1) and (TABLE - 1)
        }
        if (nodes >= MAX_NODES) return
        val node = nodes++
        keys[slot] = key
        slots[slot] = node
        nodeX[node] = x
        nodeY[node] = y
        nodeZ[node] = z
        nodeG[node] = g
        nodeF[node] = g + heuristic(x + 0.5, y, z + 0.5)
        nodeParent[node] = parent
        nodeClosed[node] = false
        push(node)
    }

    private fun key(x: Int, y: Double, z: Int): Int {
        val kx = (x - originX + KEY_HALF) and KEY_MASK
        val kz = (z - originZ + KEY_HALF) and KEY_MASK
        val ky = (floor(y + EPS).toInt() - originY + KEY_HALF) and KEY_MASK
        return ((kx shl 16) or (ky shl 8) or kz) + 1
    }

    private fun push(node: Int) {
        if (heapSize >= heap.size) return
        var i = heapSize++
        heap[i] = node
        while (i > 0) {
            val parent = (i - 1) / 2
            if (nodeF[heap[parent]] <= nodeF[heap[i]]) break
            swap(i, parent)
            i = parent
        }
    }

    private fun pop(): Int {
        val top = heap[0]
        heap[0] = heap[--heapSize]
        var i = 0
        while (true) {
            val left = i * 2 + 1
            if (left >= heapSize) break
            val right = left + 1
            val smaller = if (right < heapSize && nodeF[heap[right]] < nodeF[heap[left]]) right else left
            if (nodeF[heap[i]] <= nodeF[heap[smaller]]) break
            swap(i, smaller)
            i = smaller
        }
        return top
    }

    private fun swap(a: Int, b: Int) {
        val t = heap[a]
        heap[a] = heap[b]
        heap[b] = t
    }

    private fun finish(found: Boolean) {
        level = null
        val end = best
        if (end < 0 || (!found && bestH > startH - PROGRESS_BLOCKS * GREED)) {
            state = State.Failed
            return
        }
        var length = 0
        var n = end
        while (n >= 0) {
            length++
            n = nodeParent[n]
        }
        if (points.size < length * 3) points = DoubleArray(max(length, points.size / 3 * 2) * 3)
        n = end
        var i = length - 1
        while (n >= 0) {
            points[i * 3] = nodeX[n] + 0.5
            points[i * 3 + 1] = nodeY[n]
            points[i * 3 + 2] = nodeZ[n] + 0.5
            i--
            n = nodeParent[n]
        }
        count = length
        index = if (length > 1) 1 else 0
        reached = found
        state = State.Found
    }

    companion object {
        const val MAX_NODES = 512
        const val MAX_REACH = 32
        const val MAX_DROP = 3.0
        const val STEP = 0.6
        const val JUMP = 1.1
        private const val MAX_RISE = 24
        private const val HEAP_FACTOR = 4
        private const val BASE_PROBE_BLOCKS = 12
        private const val ARRIVE_BLOCKS = 1.2
        private const val ARRIVE_HEIGHT = 1.5
        private const val DIAGONAL = 1.4142
        private const val JUMP_COST = 0.6
        private const val DROP_COST = 0.4
        private const val WATER_COST = 6.0
        private const val GREED = 1.3
        private const val PROGRESS_BLOCKS = 1.0
        private const val EPS = 1.0e-3
        private const val AIR_LIFT = 0.05
        private const val TABLE_BITS = 11
        private const val TABLE = 1 shl TABLE_BITS
        private const val HASH_MIX = -0x61c88647
        private const val KEY_HALF = 128
        private const val KEY_MASK = 0xFF
        private val DX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        private val DZ = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        internal fun probeCost(halfWidth: Double, height: Double): Int {
            val span = floor(2.0 * halfWidth).toInt() + 2
            val layers = ceil(height).toInt() + 2
            return max(1, (span * span * layers + BASE_PROBE_BLOCKS - 1) / BASE_PROBE_BLOCKS)
        }
    }
}
