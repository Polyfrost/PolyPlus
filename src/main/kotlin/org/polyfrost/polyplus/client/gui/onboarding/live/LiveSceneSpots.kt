package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

internal class LiveSceneSpot(val place: LiveSpotPlace, val min: BlockPos, val max: BlockPos)

internal class LiveSceneSpots(private val level: ClientLevel) {
    private val heights = HashMap<Long, Int>()
    private val tops = HashMap<Long, Int>()
    private var centerY = 0

    fun find(spot: LiveSpot, center: BlockPos): LiveSceneSpot? {
        centerY = center.y
        return when (spot) {
            LiveSpot.STANDING -> standing(center)
            LiveSpot.RIDING_PATH -> ridingPath(center)
        }
    }

    private fun standing(center: BlockPos): LiveSceneSpot? {
        var best: LiveSceneSpot? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (dx in -SEARCH..SEARCH step STEP) for (dz in -SEARCH..SEARCH step STEP) {
            val x = center.x + dx
            val z = center.z + dz
            val h = ground(x, z) ?: continue
            if (!flat(x, z, h, FLAT_RADIUS)) continue
            if (!loaded(x - REGION, z - REGION, x + REGION, z + REGION)) continue
            var sumX = 0.0
            var sumZ = 0.0
            var scenery = 0
            for (rx in -SCENERY_FAR..SCENERY_FAR step 2) for (rz in -SCENERY_FAR..SCENERY_FAR step 2) {
                val d = sqrt((rx * rx + rz * rz).toDouble())
                if (d < SCENERY_NEAR || d > SCENERY_FAR) continue
                if (tall(x + rx, z + rz, h)) {
                    scenery++
                    sumX += rx
                    sumZ += rz
                }
            }
            val score = scenery - sqrt((dx * dx + dz * dz).toDouble()) * CENTER_WEIGHT
            if (score <= bestScore) continue
            bestScore = score
            val yaw = if (scenery == 0) 0f else Math.toDegrees(atan2(-sumX, sumZ)).toFloat()
            val feet = Vec3(x + HALF, standY(x, h, z), z + HALF)
            best = LiveSceneSpot(
                LiveSpotPlace(feet, yaw, 0.0),
                BlockPos(x - REGION, h - BELOW, z - REGION),
                BlockPos(x + REGION, h + ABOVE, z + REGION),
            )
        }
        return best
    }

    private fun ridingPath(center: BlockPos): LiveSceneSpot? {
        var best: LiveSceneSpot? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (dx in -SEARCH..SEARCH step STEP) for (dz in -SEARCH..SEARCH step STEP) {
            val sx = center.x + dx
            val sz = center.z + dz
            for ((stepX, stepZ) in DIRECTIONS) {
                val score = pathScore(sx, sz, stepX, stepZ) ?: continue
                if (score <= bestScore) continue
                val ex = sx + stepX * PATH_LENGTH
                val ez = sz + stepZ * PATH_LENGTH
                if (!loaded(minOf(sx, ex) - REGION, minOf(sz, ez) - REGION, maxOf(sx, ex) + REGION, maxOf(sz, ez) + REGION)) continue
                val h = ground(sx, sz) ?: continue
                bestScore = score
                val yaw = Math.toDegrees(atan2(-stepX.toDouble(), stepZ.toDouble())).toFloat()
                best = LiveSceneSpot(
                    LiveSpotPlace(Vec3(sx + HALF, standY(sx, h, sz), sz + HALF), yaw, PATH_LENGTH.toDouble()),
                    BlockPos(minOf(sx, ex) - REGION, h - BELOW - PATH_DROP, minOf(sz, ez) - REGION),
                    BlockPos(maxOf(sx, ex) + REGION, h + ABOVE + PATH_DROP, maxOf(sz, ez) + REGION),
                )
            }
        }
        return best
    }

    private fun pathScore(sx: Int, sz: Int, stepX: Int, stepZ: Int): Double? {
        var previous = ground(sx, sz) ?: return null
        val start = previous
        var bumps = 0
        var paved = 0
        for (k in 1..PATH_LENGTH) {
            val x = sx + stepX * k
            val z = sz + stepZ * k
            val h = ground(x, z) ?: return null
            if (abs(h - previous) > 1 || abs(h - start) > PATH_DROP) return null
            for (side in -1..1 step 2) {
                val sh = ground(x + stepZ * side, z + stepX * side) ?: return null
                if (abs(sh - h) > 1) return null
            }
            if (h != previous) bumps++
            if (level.getBlockState(BlockPos(x, h, z)).`is`(Blocks.DIRT_PATH)) paved++
            previous = h
        }
        val ex = sx + stepX * PATH_LENGTH
        val ez = sz + stepZ * PATH_LENGTH
        var scenery = 0
        for (rx in -SCENERY_NEAR * 2..SCENERY_NEAR * 2 step 2) for (rz in -SCENERY_NEAR * 2..SCENERY_NEAR * 2 step 2) {
            if (tall(ex + rx, ez + rz, previous)) scenery++
        }
        return scenery + paved * PAVED_WEIGHT - bumps * BUMP_WEIGHT
    }

    private fun flat(x: Int, z: Int, h: Int, radius: Int): Boolean {
        for (rx in -radius..radius) for (rz in -radius..radius) {
            if (ground(x + rx, z + rz) != h) return false
        }
        return true
    }

    private fun tall(x: Int, z: Int, base: Int): Boolean {
        ground(x, z)
        val top = tops[BlockPos.asLong(x, 0, z)] ?: return false
        return top - base in TALL_MIN..TALL_MAX
    }

    private fun standY(x: Int, h: Int, z: Int): Double {
        val above = level.getBlockState(BlockPos(x, h + 1, z))
        val layers = if (above.`is`(Blocks.SNOW)) above.getValue(SnowLayerBlock.LAYERS) - 1 else 0
        return h + 1.0 + layers * SNOW_LAYER
    }

    private fun loaded(minX: Int, minZ: Int, maxX: Int, maxZ: Int): Boolean {
        for (cx in SectionPos.blockToSectionCoord(minX)..SectionPos.blockToSectionCoord(maxX)) {
            for (cz in SectionPos.blockToSectionCoord(minZ)..SectionPos.blockToSectionCoord(maxZ)) {
                if (!level.hasChunk(cx, cz)) return false
            }
        }
        return true
    }

    private fun ground(x: Int, z: Int): Int? {
        val key = BlockPos.asLong(x, 0, z)
        heights[key]?.let { return if (it == NO_GROUND) null else it }
        val found = scanGround(x, z)
        heights[key] = found ?: NO_GROUND
        return found
    }

    private fun scanGround(x: Int, z: Int): Int? {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) return null
        val pos = BlockPos.MutableBlockPos()
        for (y in centerY + SCAN_UP downTo centerY - SCAN_DOWN) {
            pos.set(x, y, z)
            val state = level.getBlockState(pos)
            if (state.isAir) continue
            tops.putIfAbsent(BlockPos.asLong(x, 0, z), y)
            if (!state.fluidState.isEmpty) return null
            if (state.`is`(Blocks.SNOW) || state.getCollisionShape(level, pos).isEmpty) continue
            return if (natural(state)) y else null
        }
        return null
    }

    private fun natural(state: BlockState): Boolean =
        state.`is`(BlockTags.DIRT) || state.`is`(BlockTags.SAND) || state.`is`(BlockTags.BASE_STONE_OVERWORLD) ||
            state.`is`(Blocks.DIRT_PATH) || state.`is`(Blocks.GRAVEL) || state.`is`(Blocks.SNOW_BLOCK) ||
            state.`is`(Blocks.PACKED_ICE) || state.`is`(Blocks.GRASS_BLOCK) || state.`is`(Blocks.COBBLESTONE)

    private companion object {
        val DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        const val SEARCH = 40
        const val STEP = 2
        const val FLAT_RADIUS = 2
        const val REGION = 48
        const val BELOW = 14
        const val ABOVE = 26
        const val SCENERY_NEAR = 4
        const val SCENERY_FAR = 14
        const val TALL_MIN = 2
        const val TALL_MAX = 10
        const val CENTER_WEIGHT = 0.3
        const val PATH_LENGTH = 30
        const val PATH_DROP = 3
        const val PAVED_WEIGHT = 2.0
        const val BUMP_WEIGHT = 3.0
        const val SCAN_UP = 40
        const val SCAN_DOWN = 30
        const val NO_GROUND = Int.MIN_VALUE
        const val HALF = 0.5
        const val SNOW_LAYER = 0.125
    }
}
//?}
