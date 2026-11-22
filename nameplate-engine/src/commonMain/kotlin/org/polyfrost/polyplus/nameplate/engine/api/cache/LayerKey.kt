package org.polyfrost.polyplus.nameplate.engine.api.cache

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialPlacement

class LayerKey(
    bundle: NameplateBundle,
    layerId: String,
    width: Int,
    height: Int,
    flagId: String?,
    state: Int,
    placement: MaterialPlacement? = null,
    variant: String? = null,
    params: DoubleArray? = null,
    source: PixelImage? = null,
    clip: IntArray? = null,
    clipSize: Int = clip?.size ?: 0,
) {
    var bundle: NameplateBundle = bundle
        private set
    var layerId: String = layerId
        private set
    var width: Int = width
        private set
    var height: Int = height
        private set
    internal var flagId: String? = flagId
        private set
    var state: Int = state
        private set
    var placement: MaterialPlacement? = placement
        private set
    var variant: String? = variant
        private set
    private var params: DoubleArray? = params
    var source: PixelImage? = source
        private set
    private var clip: IntArray? = clip
    private var clipSize: Int = clipSize

    internal fun set(
        bundle: NameplateBundle,
        layerId: String,
        width: Int,
        height: Int,
        flagId: String?,
        state: Int,
        placement: MaterialPlacement? = null,
        variant: String? = null,
        params: DoubleArray? = null,
        source: PixelImage? = null,
        clip: IntArray? = null,
        clipSize: Int = 0,
    ): LayerKey {
        this.bundle = bundle
        this.layerId = layerId
        this.width = width
        this.height = height
        this.flagId = flagId
        this.state = state
        this.placement = placement
        this.variant = variant
        this.params = params
        this.source = source
        this.clip = clip
        this.clipSize = clipSize
        return this
    }

    internal fun stored(): LayerKey =
        LayerKey(
            bundle,
            layerId,
            width,
            height,
            flagId,
            state,
            placement,
            variant,
            params?.copyOf(),
            source,
            clip?.copyOf(clipSize),
            clipSize,
        )

    private fun sameClip(other: LayerKey): Boolean {
        if (other.clipSize != clipSize) return false
        val mine = clip
        val theirs = other.clip
        if (mine == null || theirs == null) return mine == null && theirs == null
        for (i in 0 until clipSize) {
            if (mine[i] != theirs[i]) return false
        }
        return true
    }

    override fun equals(other: Any?): Boolean = other === this || other is LayerKey &&
        other.bundle == bundle &&
        other.width == width &&
        other.height == height &&
        other.state == state &&
        other.layerId == layerId &&
        other.flagId == flagId &&
        other.placement == placement &&
        other.variant == variant &&
        other.params.contentEquals(params) &&
        other.source === source &&
        sameClip(other)

    override fun hashCode(): Int {
        var result = bundle.hashCode()
        result = HASH * result + layerId.hashCode()
        result = HASH * result + width
        result = HASH * result + height
        result = HASH * result + (flagId?.hashCode() ?: 0)
        result = HASH * result + state
        result = HASH * result + (placement?.hashCode() ?: 0)
        result = HASH * result + (variant?.hashCode() ?: 0)
        result = HASH * result + params.contentHashCode()
        result = HASH * result + (source?.hashCode() ?: 0)
        val clip = clip
        if (clip != null) {
            for (i in 0 until clipSize) {
                result = HASH * result + clip[i]
            }
        }
        return result
    }

    override fun toString(): String =
        "LayerKey(bundle=$bundle, layerId=$layerId, width=$width, height=$height, flagId=$flagId, state=$state, " +
            "placement=$placement, variant=$variant, params=${params.contentToString()}, clipped=${clip != null})"

    private companion object {
        internal const val HASH = 31
    }
}
