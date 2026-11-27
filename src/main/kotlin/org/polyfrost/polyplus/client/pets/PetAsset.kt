package org.polyfrost.polyplus.client.pets

import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision
import org.polyfrost.polyplus.client.cosmetics.render.CustomColors
import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class PetArchetype {
    Walking,
    Flying,
    Shoulder;

    companion object {
        fun fromName(name: String?): PetArchetype? = when (name?.lowercase()) {
            "walking" -> Walking
            "flying" -> Flying
            "shoulder" -> Shoulder
            else -> null
        }
    }
}

internal class PetPaint(val texture: Identifier, private val retryAt: Long = Long.MAX_VALUE) : PetPaintEntry {
    override var drawnFrame = Long.MIN_VALUE
    var released = false

    fun expired(now: Long): Boolean = released || now >= retryAt
}

class PetAsset internal constructor(
    val id: Int,
    internal val revision: CosmeticRevision,
    internal val cosmetic: CosmeticAsset,
    val archetype: PetArchetype,
    val leashRadius: Float,
    val scale: Float,
    val anchor: String?,
    internal val frames: Int,
    val isAnimated: Boolean,
    private val width: Int,
    private val height: Int,
    private var pixels: ByteArray?,
    internal val interactions: Map<PetInteraction, PetRoles>,
    internal val sharedRoles: List<PetSharedRole>,
    internal val meta: JsonObject,
    private val paletteSlots: List<String>,
    internal val colorways: Int,
    internal val colorway: Int,
    private val paintable: Boolean,
    private var glowPixels: ByteArray? = null,
) {
    var texture: Identifier? = null
        private set

    internal var glowTexture: Identifier? = null
        private set

    internal var paintGeneration = 0
        private set

    private val texturePrefix = PetAssets.texturePrefix(id, revision)
    private var basePaint: PetPaint? = null
    private val painted = PetPaintCache<PetPaint>(width.toLong() * height * 4)
    private var scene: PetScene? = null
    private var sceneFailed = false
    private var paintFailed = false
    private var closed = false

    internal fun customColors(colors: Map<String, Int>?): CustomColors? = CustomColors.of(paletteSlots, colors)

    internal fun sharedRole(interaction: PetInteraction, role: Int): PetSharedRole? =
        sharedRoles.firstOrNull { it.interaction == interaction && it.role == role }

    internal fun upload() {
        val rgba = pixels ?: return
        pixels = null
        texture = PetTextures.register(texturePrefix, width, height, rgba).also { basePaint = PetPaint(it) }
        glowPixels?.let { glow ->
            glowPixels = null
            glowTexture = PetTextures.register(texturePrefix, width, height, glow)
        }
    }

    internal fun scene(): PetScene? {
        if (closed || sceneFailed || texture == null) return null
        scene?.let { return it }
        return try {
            PetScene(this).also { scene = it }
        } catch (ex: RuntimeException) {
            sceneFailed = true
            LOGGER.error("Could not set up pet cosmetic {} for drawing", id, ex)
            null
        }
    }

    internal fun paint(colorway: Int, colors: Map<String, Int>?, now: Long): PetPaint? {
        val base = basePaint ?: return null
        if (closed || paintFailed || !paintable) return base
        val chosen = if (colorway in 0 until colorways) colorway else CosmeticAsset.NO_COLORWAY
        var count = 0
        colors?.keys?.forEach { if (paletteSlots.indexOf(it) >= 0) count++ }
        if (chosen == CosmeticAsset.NO_COLORWAY && count == 0) return base
        val slots = IntArray(count)
        val rgb = IntArray(count)
        if (colors != null && count > 0) {
            var i = 0
            for ((slot, color) in colors) {
                val index = paletteSlots.indexOf(slot)
                if (index < 0) continue
                slots[i] = index
                rgb[i] = color
                i++
            }
        }
        return try {
            val key = cosmetic.textureKey(0, chosen, slots, rgb)
            painted[key]?.let { return it }
            val frame = PetFrames.frame
            if (!painted.admit(frame, ::release)) return PetPaint(base.texture, now + RETRY_NANOS)
            val rgba = ByteArray(width * height * 4)
            cosmetic.resolveTexture(0, chosen, slots, rgb, IntArray(2), rgba)
            PetPaint(PetTextures.register(texturePrefix, width, height, rgba)).also { painted.put(key, it, frame) }
        } catch (ex: RuntimeException) {
            paintFailed = true
            LOGGER.error("Could not colour pet cosmetic {}", id, ex)
            base
        }
    }

    private fun release(paint: PetPaint) {
        paint.released = true
        PetTextures.release(paint.texture)
    }

    internal fun forget(wearer: Long) {
        scene?.forget(wearer)
    }

    internal fun breakRings(wearer: Long) {
        scene?.breakRings(wearer)
    }

    internal fun close() {
        if (closed) return
        closed = true
        scene?.close()
        scene = null
        texture?.let(PetTextures::release)
        texture = null
        glowTexture?.let(PetTextures::release)
        glowTexture = null
        glowPixels = null
        basePaint?.released = true
        basePaint = null
        painted.clear(::release)
        paintGeneration++
        pixels = null
        cosmetic.close()
    }

    private companion object {
        const val RETRY_NANOS = 2_000_000_000L
        val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/pets")
    }
}
