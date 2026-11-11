package org.polyfrost.polyplus.nameplate.engine.api.source

fun interface BundleBytes {
    fun bytes(path: String): ByteArray?

    companion object {
        val NONE: BundleBytes = BundleBytes { null }

        fun of(files: Map<String, ByteArray>): BundleBytes = BundleBytes { files[it] }
    }
}
