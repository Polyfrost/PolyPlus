package org.polyfrost.polyplus.client.nameplate.render

//? if > 1.8.9 {
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
//?}
import org.polyfrost.polyplus.client.nameplate.NameplateBinding

object NameplateDetail {
    //? if > 1.8.9 {
    private val seen = IntOpenHashSet()
    //?} else {
    /*private val seen = HashSet<Int>()
    *///?}
    private var distances = DoubleArray(64)
    private var count = 0
    private var crowdThreshold = Double.MAX_VALUE

    fun detailed(entityId: Int, distanceSq: Double): Boolean {
        val settings = NameplateBinding.settings
        if (!seen.add(entityId)) {
            roll(settings.crowdLimit)
            seen.add(entityId)
        }
        if (count == distances.size) {
            distances = distances.copyOf(count * 2)
        }
        distances[count++] = distanceSq

        val limit = settings.detailDistance.toDouble()
        return distanceSq <= limit * limit && distanceSq <= crowdThreshold
    }

    private fun roll(crowdLimit: Int) {
        crowdThreshold = when {
            crowdLimit <= 0 -> -1.0
            count <= crowdLimit -> Double.MAX_VALUE
            else -> distances.copyOf(count).also { it.sort() }[crowdLimit - 1]
        }
        count = 0
        seen.clear()
    }
}
