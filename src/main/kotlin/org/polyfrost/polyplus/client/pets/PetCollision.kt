package org.polyfrost.polyplus.client.pets

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

//? if > 1.8.9 {
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.level.block.CactusBlock
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.MagmaBlock
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.phys.shapes.Shapes
//?} else {
/*import net.minecraft.block.material.Material
import net.minecraft.util.math.Box
*///?}

internal object PetCollision {
    const val SAFE = 0
    const val WATER = 1
    const val DANGER = 2

    private const val EPSILON = 1.0e-7
    private const val FLOOR_REACH = 1
    private const val MAX_GATHER_BLOCKS = 1024L

    private var boxes = DoubleArray(6 * 64)
    private var count = 0

    //? if > 1.8.9 {
    private val cursor = BlockPos.MutableBlockPos()
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetZ = 0.0
    private val collector = Shapes.DoubleLineConsumer { x1, y1, z1, x2, y2, z2 ->
        add(offsetX + x1, offsetY + y1, offsetZ + z1, offsetX + x2, offsetY + y2, offsetZ + z2)
    }
    //?}

    class Move {
        var dx = 0.0
        var dy = 0.0
        var dz = 0.0
        var onGround = false
        var blocked = false
    }

    fun move(
        level: ClientLevel,
        x: Double,
        y: Double,
        z: Double,
        halfWidth: Double,
        height: Double,
        dx: Double,
        dy: Double,
        dz: Double,
        step: Double,
        out: Move,
    ) {
        val gathered = gather(
            level,
            min(x - halfWidth, x - halfWidth + dx) - EPSILON,
            min(y, y + dy) - EPSILON,
            min(z - halfWidth, z - halfWidth + dz) - EPSILON,
            max(x + halfWidth, x + halfWidth + dx) + EPSILON,
            max(y + height, y + height + dy) + step + EPSILON,
            max(z + halfWidth, z + halfWidth + dz) + EPSILON,
        )
        if (!gathered) {
            out.dx = 0.0
            out.dy = 0.0
            out.dz = 0.0
            out.onGround = false
            out.blocked = dx != 0.0 || dz != 0.0
            return
        }
        val minX = x - halfWidth
        val minZ = z - halfWidth
        val maxX = x + halfWidth
        val maxZ = z + halfWidth

        var my = clipY(minX, y, minZ, maxX, y + height, maxZ, dy)
        var mx = clipX(minX, y + my, minZ, maxX, y + height + my, maxZ, dx)
        var mz = clipZ(minX + mx, y + my, minZ, maxX + mx, y + height + my, maxZ, dz)
        val landed = dy < 0.0 && my > dy

        if (step > 0.0 && landed && (mx != dx || mz != dz)) {
            val up = clipY(minX, y, minZ, maxX, y + height, maxZ, step)
            val sx = clipX(minX, y + up, minZ, maxX, y + height + up, maxZ, dx)
            val sz = clipZ(minX + sx, y + up, minZ, maxX + sx, y + height + up, maxZ, dz)
            val down = clipY(minX + sx, y + up, minZ + sz, maxX + sx, y + height + up, maxZ + sz, -up + min(dy, 0.0))
            if (sx * sx + sz * sz > mx * mx + mz * mz + EPSILON) {
                mx = sx
                mz = sz
                my = up + down
            }
        }

        out.dx = mx
        out.dy = my
        out.dz = mz
        out.onGround = landed
        out.blocked = abs(mx - dx) > EPSILON || abs(mz - dz) > EPSILON
    }

    fun overlaps(level: ClientLevel, x: Double, y: Double, z: Double, halfWidth: Double, height: Double): Boolean {
        val minX = x - halfWidth
        val minZ = z - halfWidth
        val maxX = x + halfWidth
        val maxZ = z + halfWidth
        val maxY = y + height
        if (!gather(level, minX, y, minZ, maxX, maxY, maxZ)) return true
        return !free(minX, y, minZ, maxX, maxY, maxZ)
    }

    fun surface(
        level: ClientLevel,
        x: Double,
        z: Double,
        halfWidth: Double,
        height: Double,
        top: Double,
        bottom: Double,
    ): Double {
        val minX = x - halfWidth
        val minZ = z - halfWidth
        val maxX = x + halfWidth
        val maxZ = z + halfWidth
        if (!gather(level, minX, bottom, minZ, maxX, top + height, maxZ)) return Double.NaN
        var best = Double.NaN
        for (i in 0 until count) {
            val at = i * 6
            if (boxes[at + 3] <= minX + EPSILON || boxes[at] >= maxX - EPSILON) continue
            if (boxes[at + 5] <= minZ + EPSILON || boxes[at + 2] >= maxZ - EPSILON) continue
            val surface = boxes[at + 4]
            if (surface < bottom - EPSILON || surface > top + EPSILON) continue
            if (!best.isNaN() && surface <= best) continue
            if (free(minX, surface, minZ, maxX, surface + height, maxZ)) best = surface
        }
        return best
    }

    private var probeLevel: ClientLevel? = null
    private val levelHazard = PetFootprint.Probe { bx, by, bz -> probeLevel?.let { hazardAt(it, bx, by, bz) } ?: DANGER }

    fun hazard(level: ClientLevel, x: Double, y: Double, z: Double, halfWidth: Double, height: Double): Int {
        probeLevel = level
        val hazard = PetFootprint.hazard(x, y, z, halfWidth, height, levelHazard)
        probeLevel = null
        return hazard
    }

    fun sweep(
        level: ClientLevel,
        x: Double,
        y: Double,
        z: Double,
        dx: Double,
        dy: Double,
        dz: Double,
        halfWidth: Double,
        height: Double,
    ): Int {
        probeLevel = level
        val hazard = PetFootprint.sweep(x, y, z, dx, dy, dz, halfWidth, height, levelHazard)
        probeLevel = null
        return hazard
    }

    private fun free(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean {
        for (i in 0 until count) {
            val at = i * 6
            if (boxes[at + 3] > minX + EPSILON && boxes[at] < maxX - EPSILON &&
                boxes[at + 4] > minY + EPSILON && boxes[at + 1] < maxY - EPSILON &&
                boxes[at + 5] > minZ + EPSILON && boxes[at + 2] < maxZ - EPSILON
            ) return false
        }
        return true
    }

    class Ray {
        var fraction = 1.0
        var normalX = 0
        var normalY = 0
        var normalZ = 0
    }

    fun ray(level: ClientLevel, x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double, out: Ray): Boolean {
        if (!gather(level, min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1))) return false
        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0
        var best = Double.POSITIVE_INFINITY
        var axis = -1
        for (i in 0 until count) {
            val at = i * 6
            var near = 0.0
            var far = 1.0
            var entry = -1
            var hit = true
            for (a in 0..2) {
                val origin = when (a) { 0 -> x0; 1 -> y0; else -> z0 }
                val delta = when (a) { 0 -> dx; 1 -> dy; else -> dz }
                val low = boxes[at + a]
                val high = boxes[at + 3 + a]
                if (abs(delta) < EPSILON) {
                    if (origin <= low || origin >= high) {
                        hit = false
                        break
                    }
                    continue
                }
                val t0 = (low - origin) / delta
                val t1 = (high - origin) / delta
                val enter = min(t0, t1)
                if (enter > near) {
                    near = enter
                    entry = a
                }
                far = min(far, max(t0, t1))
                if (near > far) {
                    hit = false
                    break
                }
            }
            if (!hit || entry < 0 || near >= best) continue
            best = near
            axis = entry
        }
        if (axis < 0) return false
        out.fraction = best
        out.normalX = if (axis == 0) (if (dx > 0.0) -1 else 1) else 0
        out.normalY = if (axis == 1) (if (dy > 0.0) -1 else 1) else 0
        out.normalZ = if (axis == 2) (if (dz > 0.0) -1 else 1) else 0
        return true
    }

    fun inWater(level: ClientLevel, x: Double, y: Double, z: Double): Boolean {
        //? if > 1.8.9 {
        cursor.set(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
        return level.hasChunkAt(cursor) && level.getFluidState(cursor).`is`(FluidTags.WATER)
        //?} else {
        /*val pos = BlockPos(x, y, z)
        return level.isChunkLoaded(pos) && level.getBlockState(pos).block.material == Material.WATER
        *///?}
    }

    fun loaded(level: ClientLevel, x: Double, y: Double, z: Double): Boolean {
        //? if > 1.8.9 {
        cursor.set(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
        return level.hasChunkAt(cursor)
        //?} else {
        /*return level.isChunkLoaded(BlockPos(x, y, z))
        *///?}
    }

    fun time(level: ClientLevel): Long {
        //? if > 1.8.9 {
        return level.gameTime
        //?} else {
        /*return level.time
        *///?}
    }

    private fun hazardAt(level: ClientLevel, bx: Int, by: Int, bz: Int): Int {
        //? if > 1.8.9 {
        cursor.set(bx, by, bz)
        if (!level.hasChunkAt(cursor)) return DANGER
        val state = level.getBlockState(cursor)
        val fluid = state.fluidState
        if (fluid.`is`(FluidTags.LAVA)) return DANGER
        val block = state.block
        if (block is BaseFireBlock || block is CactusBlock || block is MagmaBlock || block is CampfireBlock ||
            block is SweetBerryBushBlock || block is PowderSnowBlock
        ) {
            return DANGER
        }
        return if (fluid.`is`(FluidTags.WATER)) WATER else SAFE
        //?} else {
        /*val pos = BlockPos(bx, by, bz)
        if (!level.isChunkLoaded(pos)) return DANGER
        val material = level.getBlockState(pos).block.material
        if (material == Material.LAVA || material == Material.FIRE || material == Material.CACTUS) return DANGER
        return if (material == Material.WATER) WATER else SAFE
        *///?}
    }

    private fun gather(level: ClientLevel, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean {
        count = 0
        val x0 = floor(minX).toInt()
        val x1 = floor(maxX).toInt()
        val y0 = floor(minY).toInt() - FLOOR_REACH
        val y1 = floor(maxY).toInt()
        val z0 = floor(minZ).toInt()
        val z1 = floor(maxZ).toInt()
        val volume = (x1.toLong() - x0 + 1) * (y1.toLong() - y0 + 1) * (z1.toLong() - z0 + 1)
        if (x1 < x0 || y1 < y0 || z1 < z0 || volume > MAX_GATHER_BLOCKS) return false
        //? if > 1.8.9 {
        for (bx in x0..x1) {
            for (bz in z0..z1) {
                for (by in y0..y1) {
                    cursor.set(bx, by, bz)
                    if (!level.hasChunkAt(cursor)) {
                        add(bx.toDouble(), by.toDouble(), bz.toDouble(), bx + 1.0, by + 1.0, bz + 1.0)
                        continue
                    }
                    val state = level.getBlockState(cursor)
                    if (state.isAir) continue
                    val shape = state.getCollisionShape(level, cursor)
                    if (shape.isEmpty) continue
                    offsetX = bx.toDouble()
                    offsetY = by.toDouble()
                    offsetZ = bz.toDouble()
                    shape.forAllBoxes(collector)
                }
            }
        }
        //?} else {
        /*for (box in level.getBlockCollisions(Box(minX, minY, minZ, maxX, maxY, maxZ))) {
            add(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
        }
        *///?}
        return true
    }

    private fun add(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
        if ((count + 1) * 6 > boxes.size) boxes = boxes.copyOf(boxes.size * 2)
        val at = count * 6
        boxes[at] = minX
        boxes[at + 1] = minY
        boxes[at + 2] = minZ
        boxes[at + 3] = maxX
        boxes[at + 4] = maxY
        boxes[at + 5] = maxZ
        count++
    }

    private fun clipY(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, delta: Double): Double {
        var d = delta
        if (d == 0.0) return 0.0
        for (i in 0 until count) {
            val at = i * 6
            if (boxes[at + 3] <= minX + EPSILON || boxes[at] >= maxX - EPSILON) continue
            if (boxes[at + 5] <= minZ + EPSILON || boxes[at + 2] >= maxZ - EPSILON) continue
            if (d > 0.0 && maxY <= boxes[at + 1] + EPSILON) d = min(d, boxes[at + 1] - maxY)
            else if (d < 0.0 && minY >= boxes[at + 4] - EPSILON) d = max(d, boxes[at + 4] - minY)
        }
        return d
    }

    private fun clipX(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, delta: Double): Double {
        var d = delta
        if (d == 0.0) return 0.0
        for (i in 0 until count) {
            val at = i * 6
            if (boxes[at + 4] <= minY + EPSILON || boxes[at + 1] >= maxY - EPSILON) continue
            if (boxes[at + 5] <= minZ + EPSILON || boxes[at + 2] >= maxZ - EPSILON) continue
            if (d > 0.0 && maxX <= boxes[at] + EPSILON) d = min(d, boxes[at] - maxX)
            else if (d < 0.0 && minX >= boxes[at + 3] - EPSILON) d = max(d, boxes[at + 3] - minX)
        }
        return d
    }

    private fun clipZ(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, delta: Double): Double {
        var d = delta
        if (d == 0.0) return 0.0
        for (i in 0 until count) {
            val at = i * 6
            if (boxes[at + 3] <= minX + EPSILON || boxes[at] >= maxX - EPSILON) continue
            if (boxes[at + 4] <= minY + EPSILON || boxes[at + 1] >= maxY - EPSILON) continue
            if (d > 0.0 && maxZ <= boxes[at + 2] + EPSILON) d = min(d, boxes[at + 2] - maxZ)
            else if (d < 0.0 && minZ >= boxes[at + 5] - EPSILON) d = max(d, boxes[at + 5] - minZ)
        }
        return d
    }
}
