package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
//? if >= 1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem
//?}
//? if < 1.21.4 {
/*import net.minecraft.util.FastColor
*///?}

object NameplateStrips {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    const val WIDTH: Int = PixelImage.MAX_STRIP_LENGTH

    val ATLAS: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "nameplate/strips")

    private const val ROWS = 256
    private const val HEIGHT = ROWS + 1
    private const val WHITE = -1
    private const val ROW_GRACE_NANOS = 1_000_000_000L
    private const val IDLE_NANOS = 60_000_000_000L

    const val WHITE_U: Float = 0.5f / WIDTH
    const val WHITE_V: Float = 0.5f / HEIGHT

    private class Key(var period: Int, val colors: IntArray) {
        fun copy(): Key = Key(period, colors.copyOf(period))

        override fun equals(other: Any?): Boolean {
            if (other !is Key || other.period != period) {
                return false
            }
            for (k in 0 until period) {
                if (other.colors[k] != colors[k]) {
                    return false
                }
            }
            return true
        }

        override fun hashCode(): Int {
            var hash = period
            for (k in 0 until period) {
                hash = hash * HASH_PRIME + colors[k]
            }
            return hash
        }
    }

    private const val HASH_PRIME = 31

    private val probe = Key(0, IntArray(PixelImage.MAX_STRIP_PERIOD))
    private val rows = HashMap<Key, Int>()
    private val keys = arrayOfNulls<Key>(HEIGHT)
    private val lastUsed = LongArray(HEIGHT)
    private var texture: DynamicTexture? = null
    //? if >= 1.21.5 {
    private var line: NativeImage? = null
    //?} else {
    /*private var pixels: NativeImage? = null
    *///?}
    private var lastActive = 0L
    private var failed = false

    fun renderType(): NameplateLayer = NameplateLayers.text(ATLAS)

    fun v(row: Int): Float = (row + 0.5f) / HEIGHT

    fun row(colors: IntArray, period: Int, now: Long): Int {
        val atlas = atlas() ?: return -1
        lastActive = now
        probe.period = period
        colors.copyInto(probe.colors, 0, 0, period)
        rows[probe]?.let { found ->
            lastUsed[found] = now
            return found
        }
        val free = freeRow(now)
        if (free < 0 || !write(atlas, free, colors, period)) {
            return -1
        }
        keys[free]?.let(rows::remove)
        val key = probe.copy()
        keys[free] = key
        rows[key] = free
        lastUsed[free] = now
        return free
    }

    fun sweep(now: Long) {
        if (texture == null || now - lastActive < IDLE_NANOS) {
            return
        }
        Minecraft.getInstance().textureManager.release(ATLAS)
        texture = null
        //? if >= 1.21.5 {
        line?.close()
        line = null
        //?} else {
        /*pixels = null
        *///?}
        rows.clear()
        keys.fill(null)
        LOGGER.info("Nameplate strip atlas released: no nameplate drew a strip for 60 s")
    }

    private fun freeRow(now: Long): Int {
        var oldest = -1
        for (r in 1 until HEIGHT) {
            if (keys[r] == null) {
                return r
            }
            if (now - lastUsed[r] >= ROW_GRACE_NANOS && (oldest < 0 || lastUsed[r] < lastUsed[oldest])) {
                oldest = r
            }
        }
        return oldest
    }

    private fun write(atlas: DynamicTexture, row: Int, colors: IntArray, period: Int): Boolean {
        //? if >= 1.21.5 {
        val image = line ?: NativeImage(WIDTH, 1, false).also { line = it }
        val y = 0
        //?} else {
        /*val image = pixels ?: return false
        val y = row
        *///?}
        for (x in 0 until WIDTH) {
            setPixel(image, x, y, colors[x % period])
        }
        return try {
            //? if >= 26.2 {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(atlas.getTexture(), image, 0, 0, 0, row)
            //?} elif >= 1.21.6 {
            /*RenderSystem.getDevice().createCommandEncoder().writeToTexture(atlas.getTexture(), image, 0, 0, 0, row, WIDTH, 1, 0, 0)
            *///?} elif >= 1.21.5 {
            /*RenderSystem.getDevice().createCommandEncoder().writeToTexture(atlas.getTexture(), image, 0, 0, row, WIDTH, 1, 0, 0)
            *///?} else {
            /*atlas.upload()
            *///?}
            true
        } catch (e: RuntimeException) {
            LOGGER.error("Nameplate strip row {} could not be uploaded; its strips draw as single quads", row, e)
            false
        }
    }

    private fun atlas(): DynamicTexture? {
        texture?.let { return it }
        if (failed) {
            return null
        }
        return try {
            val image = NativeImage(WIDTH, HEIGHT, true)
            for (x in 0 until WIDTH) {
                setPixel(image, x, 0, WHITE)
            }
            //? if >= 1.21.5 {
            val dynamic = DynamicTexture({ ATLAS.toString() }, image)
            //?} else {
            /*val dynamic = DynamicTexture(image)
            pixels = image
            *///?}
            Minecraft.getInstance().textureManager.register(ATLAS, dynamic)
            texture = dynamic
            dynamic
        } catch (e: RuntimeException) {
            failed = true
            LOGGER.error("Nameplate strip atlas could not be created; strips draw as single quads", e)
            null
        }
    }

    private fun setPixel(image: NativeImage, x: Int, y: Int, argb: Int) {
        //? if >= 1.21.4 {
        image.setPixel(x, y, argb)
        //?} else {
        /*image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(argb))
        *///?}
    }
}
//?} else {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.texture.DynamicTexture
import net.minecraft.client.render.texture.TextureUtil
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage

object NameplateStrips {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    const val WIDTH: Int = PixelImage.MAX_STRIP_LENGTH

    val ATLAS: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "nameplate/strips")

    private const val ROWS = 256
    private const val HEIGHT = ROWS + 1
    private const val WHITE = -1
    private const val ROW_GRACE_NANOS = 1_000_000_000L
    private const val IDLE_NANOS = 60_000_000_000L
    private const val HASH_PRIME = 31

    const val WHITE_U: Float = 0.5f / WIDTH
    const val WHITE_V: Float = 0.5f / HEIGHT

    private class Key(var period: Int, val colors: IntArray) {
        fun copy(): Key = Key(period, colors.copyOf(period))

        override fun equals(other: Any?): Boolean {
            if (other !is Key || other.period != period) {
                return false
            }
            for (k in 0 until period) {
                if (other.colors[k] != colors[k]) {
                    return false
                }
            }
            return true
        }

        override fun hashCode(): Int {
            var hash = period
            for (k in 0 until period) {
                hash = hash * HASH_PRIME + colors[k]
            }
            return hash
        }
    }

    private val probe = Key(0, IntArray(PixelImage.MAX_STRIP_PERIOD))
    private val rows = HashMap<Key, Int>()
    private val keys = arrayOfNulls<Key>(HEIGHT)
    private val lastUsed = LongArray(HEIGHT)
    private val line = IntArray(WIDTH)
    private val upload = arrayOf(line)
    private var texture: DynamicTexture? = null
    private var lastActive = 0L
    private var failed = false

    fun renderType(): NameplateLayer = NameplateLayers.text(ATLAS)

    fun v(row: Int): Float = (row + 0.5f) / HEIGHT

    fun row(colors: IntArray, period: Int, now: Long): Int {
        val atlas = atlas() ?: return -1
        lastActive = now
        probe.period = period
        colors.copyInto(probe.colors, 0, 0, period)
        rows[probe]?.let { found ->
            lastUsed[found] = now
            return found
        }
        val free = freeRow(now)
        if (free < 0 || !write(atlas, free, colors, period)) {
            return -1
        }
        keys[free]?.let(rows::remove)
        val key = probe.copy()
        keys[free] = key
        rows[key] = free
        lastUsed[free] = now
        return free
    }

    fun sweep(now: Long) {
        val atlas = texture ?: return
        if (now - lastActive < IDLE_NANOS) {
            return
        }
        atlas.clearGlId()
        texture = null
        rows.clear()
        keys.fill(null)
        LOGGER.info("Nameplate strip atlas released: no nameplate drew a strip for 60 s")
    }

    private fun freeRow(now: Long): Int {
        var oldest = -1
        for (r in 1 until HEIGHT) {
            if (keys[r] == null) {
                return r
            }
            if (now - lastUsed[r] >= ROW_GRACE_NANOS && (oldest < 0 || lastUsed[r] < lastUsed[oldest])) {
                oldest = r
            }
        }
        return oldest
    }

    private fun write(atlas: DynamicTexture, row: Int, colors: IntArray, period: Int): Boolean {
        for (x in 0 until WIDTH) {
            line[x] = colors[x % period]
        }
        return try {
            GlStateManager.bindTexture(atlas.glId)
            TextureUtil.upload(upload, WIDTH, 1, 0, row, false, false)
            true
        } catch (e: RuntimeException) {
            LOGGER.error("Nameplate strip row {} could not be uploaded; its strips draw as single quads", row, e)
            false
        }
    }

    private fun atlas(): DynamicTexture? {
        texture?.let { return it }
        if (failed) {
            return null
        }
        return try {
            val dynamic = DynamicTexture(WIDTH, HEIGHT)
            dynamic.pixels.fill(WHITE, 0, WIDTH)
            dynamic.upload()
            Minecraft.getInstance().textureManager.register(ATLAS, dynamic)
            texture = dynamic
            dynamic
        } catch (e: RuntimeException) {
            failed = true
            LOGGER.error("Nameplate strip atlas could not be created; strips draw as single quads", e)
            null
        }
    }
}
*///?}
