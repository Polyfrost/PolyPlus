package org.polyfrost.polyplus.client.render

internal object DepthOrder {
    fun key(depth: Float, index: Int): Long {
        val bits = depth.toRawBits()
        val sortable = bits xor ((bits shr 31) and 0x7FFFFFFF)
        return (sortable.toLong() shl 32) or (index.toLong() and 0xFFFFFFFFL)
    }

    fun index(key: Long): Int = key.toInt()
}
