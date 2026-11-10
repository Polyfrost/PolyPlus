package org.polyfrost.polyplus.nameplate.engine.api.source

fun interface BundleSource {
    fun text(path: String): String?

    companion object {
        fun of(files: Map<String, String>): BundleSource = BundleSource { files[it] }
    }
}
