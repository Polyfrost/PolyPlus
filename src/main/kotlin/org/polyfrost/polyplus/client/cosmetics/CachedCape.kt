package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.render.NativeImage
import org.polyfrost.polyplus.client.utils.copyFrameInto
import java.awt.image.BufferedImage

internal const val DEFAULT_MILLIS_PER_FRAME = 100L

internal const val MIN_MILLIS_PER_FRAME = 50L
internal const val MAX_MILLIS_PER_FRAME = 5_000L

private val LOGGER = LogManager.getLogger()

class CachedCape(
    private val id: Int,
    private val sheet: BufferedImage,
    private val frames: Int,
    private val millisPerFrame: Long = DEFAULT_MILLIS_PER_FRAME,
) {
    private val frameHeight = sheet.height / frames
    private var pixels: NativeImage? = null
    private var frameBuffer: IntArray? = null
    private var texture: DynamicTexture? = null
    @Volatile
    private var location: Identifier? = null
    private var shownFrame = -1
    private var registerFailed = false

    val isAnimated: Boolean get() = frames > 1

    private fun register() {
        if (registerFailed) return
        var image: NativeImage? = null
        runCatching {
            val frame = NativeImage(sheet.width, frameHeight, true).also { image = it }
            val dynamic = DynamicTexture(
                //?if >= 1.21.5 {
                 { "polyplus:cape/$id" },
                //?}
                frame,
            )
            val buffer = IntArray(sheet.width * frameHeight)
            Minecraft.getInstance().textureManager.register(texturePath(), dynamic)
            pixels = frame
            frameBuffer = buffer
            texture = dynamic
            location = Identifier.fromNamespaceAndPath(
                PolyPlusConstants.ID,
                "cape/$id",
            )
        }.onFailure {
            registerFailed = true
            runCatching { image?.close() }
            LOGGER.error("Failed to register cape texture for cosmetic {}", id, it)
            return
        }
        drawFrame(0)
    }

    fun release() {
        if (texture == null) return
        Minecraft.getInstance().textureManager.release(texturePath())
        texture = null
        pixels = null
        frameBuffer = null
        location = null
        shownFrame = -1
    }

    fun asResource(): Identifier? {
        if (location == null) {
            if (!Minecraft.getInstance().isSameThread) return null
            register()
        }
        if (frames > 1) {
            drawFrame(capeFrameIndex(System.nanoTime() / 1_000_000L, frames, millisPerFrame))
        }
        return location
    }

    private fun drawFrame(frame: Int) {
        if (!Minecraft.getInstance().isSameThread) return
        if (frame == shownFrame) return
        val target = pixels ?: return
        val dynamic = texture ?: return
        sheet.copyFrameInto(target, capeFrameOffset(frameHeight, frame), frameBuffer)
        dynamic.upload()
        shownFrame = frame
    }

    private fun texturePath(): Identifier {
        //? if >= 1.21.10 {
        return Identifier.fromNamespaceAndPath(
            PolyPlusConstants.ID,
            "textures/cape/$id.png",
        )
        //?} else {
        /*return Identifier.fromNamespaceAndPath(
            PolyPlusConstants.ID,
            "cape/$id",
        )
        *///?}
    }
}

internal fun capeFrameIndex(millis: Long, frames: Int, millisPerFrame: Long = DEFAULT_MILLIS_PER_FRAME): Int =
    if (frames <= 1) {
        0
    } else {
        Math.floorMod(Math.floorDiv(millis, millisPerFrame.coerceAtLeast(1L)), frames.toLong()).toInt()
    }

internal fun capeMillisPerFrameFromName(fileName: String): Long {
    val asked = CAPE_PACE_IN_NAME.find(fileName)?.groupValues?.get(1)?.toLongOrNull()
        ?: return DEFAULT_MILLIS_PER_FRAME
    return if (asked in MIN_MILLIS_PER_FRAME..MAX_MILLIS_PER_FRAME) asked else DEFAULT_MILLIS_PER_FRAME
}

private val CAPE_PACE_IN_NAME = Regex("""\.(\d{1,6})ms\.sheet$""", RegexOption.IGNORE_CASE)

internal fun capeFrameOffset(frameHeight: Int, frame: Int): Int = frameHeight * frame
