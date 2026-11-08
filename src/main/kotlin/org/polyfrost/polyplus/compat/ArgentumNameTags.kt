package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader

/**
 * Argentum (1.8.9) batches nametags into its own NameTagBatch, which draws glyphs itself, so the PolyPlus badge
 * (drawn in vanilla TextRenderer) never shows on them. Badged nametags opt out of the batch while they render.
 * See MixinEntityRendererArgentum and MixinNameTagBatch.
 */
object ArgentumNameTags {
    @JvmField
    val ENABLED: Boolean = FabricLoader.getInstance().isModLoaded("argentum")

    private var unbatchedThread: Thread? = null
    private var unbatchedDepth = 0

    @JvmStatic
    fun beginUnbatched() {
        unbatchedThread = Thread.currentThread()
        unbatchedDepth++
    }

    @JvmStatic
    fun endUnbatched() {
        if (--unbatchedDepth <= 0) {
            unbatchedDepth = 0
            unbatchedThread = null
        }
    }

    @JvmStatic
    fun isUnbatched(): Boolean = unbatchedDepth > 0 && unbatchedThread === Thread.currentThread()
}
