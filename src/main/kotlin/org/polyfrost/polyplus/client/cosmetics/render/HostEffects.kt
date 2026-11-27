package org.polyfrost.polyplus.client.cosmetics.render

import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.assets.CosmeticBundles
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import java.lang.ref.Cleaner
import kotlin.math.max

internal interface EffectSource {
    val effectTextures: List<Identifier>
    val quads: FloatArray
    val quadCount: Int

    fun computeFor(frame: Int): Boolean
}

internal object EffectMedia {
    fun sounds(asset: CosmeticAsset?, meta: JsonObject?): Array<CosmeticSoundRef?> {
        val list = meta?.let { CosmeticBundles.array(it, "effects", "sounds") } ?: return emptyArray()
        val identity = meta.get("identity")?.asString.orEmpty()
        return Array(list.size()) { index ->
            val entry = list[index].takeIf { it.isJsonObject }?.asJsonObject ?: return@Array null
            val vanilla = entry.get("vanilla")?.takeIf { it.isJsonPrimitive }?.asString
            val bytes = if (vanilla == null) asset?.sound(index)?.takeIf { it.isNotEmpty() } else null
            when {
                vanilla != null -> CosmeticSoundRef(vanilla, null)
                bytes != null -> {
                    val key = "${identity}_$index"
                    CosmeticSoundRef(null, CosmeticSoundLibrary.register(key, bytes), key)
                }
                else -> null
            }
        }
    }

    val CLEANER: Cleaner = Cleaner.create()

    fun releaser(sounds: Array<CosmeticSoundRef?>): Runnable = Runnable {
        for (sound in sounds) sound?.key?.let(CosmeticSoundLibrary::release)
    }

    fun vanilla(meta: JsonObject?): Array<String?> {
        val list = meta?.let { CosmeticBundles.array(it, "effects", "emitter_vanilla") } ?: return emptyArray()
        return Array(list.size()) { index -> list[index].takeIf { it.isJsonPrimitive }?.asString }
    }

    fun upload(asset: CosmeticAsset, name: String): List<Identifier> = List(asset.effectTextureCount()) { index ->
        val info = IntArray(CosmeticAsset.TEXTURE_INFO_INTS)
        val pixels = ByteArray(asset.effectTextureInfo(index, info))
        asset.effectTexture(index, pixels)
        val width = info[CosmeticAsset.TEXTURE_WIDTH].coerceAtLeast(1)
        val height = info[CosmeticAsset.TEXTURE_HEIGHT].coerceAtLeast(1)
        val filled = if (pixels.size == width * height * 4) pixels else ByteArray(width * height * 4)
        RemoteTextures.registerPixels(
            Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$name/effect_$index"),
            width,
            height,
            filled,
        )
    }
}

class HostEffects(private val renderer: Renderer, asset: CosmeticAsset, meta: JsonObject?, name: String) {
    val hasEffects: Boolean = renderer.hasEffects()
    private val sounds = if (hasEffects) EffectMedia.sounds(asset, meta) else emptyArray()
    private val soundCleanable = if (sounds.isEmpty()) null else EffectMedia.CLEANER.register(this, EffectMedia.releaser(sounds))
    private val vanilla = if (hasEffects) EffectMedia.vanilla(meta) else emptyArray()

    private var textures: List<Identifier> = if (hasEffects) EffectMedia.upload(asset, name) else emptyList()
    val effectTextures: List<Identifier> get() = textures
    private var quads = FloatArray(0)
    private var quadCount = 0
    private val source = Source()

    private var frame = -1
    private var eventInts = IntArray(Renderer.EFFECT_EVENT_INTS * 8)
    private var eventFloats = FloatArray(Renderer.EFFECT_EVENT_FLOATS * 8)
    private val view = FloatArray(Renderer.VIEW_FLOATS)
    private var geometryOut = FloatArray(0)
    private var viewEpoch = -1
    private var viewX = 0.0
    private var viewY = 0.0
    private var viewZ = 0.0

    internal val effectSource: EffectSource get() = source

    fun afterFrame(root: FloatArray, local: Boolean, draw: Boolean = true) {
        afterRows(root, 0, 0, if (local) 0 else -1, draw)
    }

    internal fun afterRows(
        roots: FloatArray,
        stride: Int,
        rootAt: Int,
        localRow: Int,
        draw: Boolean = true,
        queue: Boolean = true,
    ) {
        if (!hasEffects) return
        CosmeticWearers.begin()
        if (frame != CosmeticWearers.frame) {
            frame = CosmeticWearers.frame
            quadCount = 0
        }
        events(roots, stride, rootAt, localRow)
        if (!draw) return
        geometry()
        if (queue) CosmeticEffects.pending(source)
    }

    fun close() {
        soundCleanable?.clean()
        textures.forEach(RemoteTextures::release)
        textures = emptyList()
        quadCount = 0
    }

    private inner class Source : EffectSource {
        override val effectTextures: List<Identifier> get() = textures
        override val quads: FloatArray get() = this@HostEffects.quads
        override val quadCount: Int get() = this@HostEffects.quadCount

        override fun computeFor(frame: Int): Boolean = frame == this@HostEffects.frame
    }

    private fun events(roots: FloatArray, stride: Int, rootAt: Int, localRow: Int) {
        var events = renderer.effectEvents(eventInts)
        if (events * Renderer.EFFECT_EVENT_INTS > eventInts.size) {
            eventInts = IntArray(events * Renderer.EFFECT_EVENT_INTS)
            events = renderer.effectEvents(eventInts)
        }
        if (events * Renderer.EFFECT_EVENT_FLOATS > eventFloats.size) {
            eventFloats = FloatArray(events * Renderer.EFFECT_EVENT_FLOATS)
        }
        renderer.effectEventFloats(eventFloats)
        for (i in 0 until events) {
            val at = i * Renderer.EFFECT_EVENT_INTS
            val f = i * Renderer.EFFECT_EVENT_FLOATS
            val row = eventInts[at]
            val root = row * stride + rootAt
            if (root < 0 || root + 11 >= roots.size) continue
            val local = row == localRow
            val x = CosmeticOrigin.x + roots[root + 3] + eventFloats[f]
            val y = CosmeticOrigin.y + roots[root + 7] + eventFloats[f + 1]
            val z = CosmeticOrigin.z + roots[root + 11] + eventFloats[f + 2]
            val effect = eventInts[at + 5]
            when (eventInts[at + 4]) {
                Renderer.EFFECT_SOUND -> sounds.getOrNull(effect)?.let {
                    CosmeticSoundPlayer.play(it, x, y, z, eventFloats[f + 3], eventFloats[f + 4], local)
                }
                Renderer.EFFECT_VANILLA_PARTICLE -> vanilla.getOrNull(effect)?.let {
                    CosmeticSoundPlayer.particle(it, x, y, z)
                }
            }
        }
    }

    private fun quadsInto(buffer: FloatArray): Int =
        try {
            renderer.effectGeometry(view, buffer)
        } catch (short: IllegalArgumentException) {
            -1
        }

    private fun geometry() {
        var shiftX = 0f
        var shiftY = 0f
        var shiftZ = 0f
        if (viewEpoch != CosmeticOrigin.epoch) {
            if (viewEpoch >= 0) {
                shiftX = (CosmeticOrigin.x - viewX).toFloat()
                shiftY = (CosmeticOrigin.y - viewY).toFloat()
                shiftZ = (CosmeticOrigin.z - viewZ).toFloat()
            }
            viewEpoch = CosmeticOrigin.epoch
            viewX = CosmeticOrigin.x
            viewY = CosmeticOrigin.y
            viewZ = CosmeticOrigin.z
        }
        CosmeticOrigin.view(view, shiftX, shiftY, shiftZ)
        var drawn = quadsInto(geometryOut)
        if (drawn < 0) {
            val count = renderer.effectGeometry(view, null)
            if (count <= 0) return
            val floats = count * Renderer.QUAD_FLOATS
            geometryOut = FloatArray(floats + floats / 4)
            drawn = renderer.effectGeometry(view, geometryOut)
        }
        if (drawn <= 0) return
        val needed = (quadCount + drawn) * Renderer.QUAD_FLOATS
        if (quads.size < needed) quads = quads.copyOf(max(needed, quads.size * 2))
        System.arraycopy(geometryOut, 0, quads, quadCount * Renderer.QUAD_FLOATS, drawn * Renderer.QUAD_FLOATS)
        quadCount += drawn
    }
}
