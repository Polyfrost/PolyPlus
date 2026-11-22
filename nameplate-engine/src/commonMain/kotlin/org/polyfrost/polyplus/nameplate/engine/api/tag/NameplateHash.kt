package org.polyfrost.polyplus.nameplate.engine.api.tag

object NameplateHash {
    private const val FNV_OFFSET = 0x811C9DC5.toInt()
    private const val FNV_PRIME = 0x01000193
    private const val C1 = 0xCC9E2D51.toInt()
    private const val C2 = 0x1B873593
    private const val ROUND_ADD = 0xE6546B64.toInt()
    private const val FMIX_1 = 0x85EBCA6B.toInt()
    private const val FMIX_2 = 0xC2B2AE35.toInt()
    private const val HASH_BYTES = 12
    private const val DERIVE_BYTES = 4

    fun text(value: String): Int {
        var hash = FNV_OFFSET
        for (c in value) {
            hash = (hash xor c.code) * FNV_PRIME
        }
        return hash
    }

    fun mix(hash: Int, value: Int): Int {
        val k = (value * C1).rotateLeft(15) * C2
        return (hash xor k).rotateLeft(13) * 5 + ROUND_ADD
    }

    fun finish(hash: Int, length: Int): Int {
        var h = hash xor length
        h = h xor (h ushr 16)
        h *= FMIX_1
        h = h xor (h ushr 13)
        h *= FMIX_2
        return h xor (h ushr 16)
    }

    fun hash(seed: Int, id: Int, index: Int): Int = finish(mix(mix(mix(0, seed), id), index), HASH_BYTES)

    internal fun derive(hash: Int, salt: Int): Int = finish(mix(hash, salt), DERIVE_BYTES)
}
