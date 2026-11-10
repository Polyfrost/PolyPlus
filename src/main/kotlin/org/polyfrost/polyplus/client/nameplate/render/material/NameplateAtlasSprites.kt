package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 1.21.1 {
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection
//? if >= 1.21.10 {
import net.minecraft.data.AtlasIds
//?}
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.mixin.client.nameplate.SpriteContentsAccessor
import org.polyfrost.polyplus.nameplate.engine.api.shader.TexturePixels
import java.io.IOException
//? if < 1.21.4 {
/*import net.minecraft.util.FastColor
*///?}

object NameplateAtlasSprites {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private class Read(val sprite: TextureAtlasSprite, val pixels: TexturePixels?)

    private class Animation(val sequence: IntArray, val ticks: IntArray)

    private val spriteIds = HashMap<String, Identifier?>()
    private val read = HashMap<String, Read>()
    private val logged = HashSet<String>()
    private val still = Animation(IntArray(0), IntArray(0))

    fun blockAtlas(): TextureAtlas? = try {
        //? if >= 1.21.10 {
        Minecraft.getInstance().atlasManager.getAtlasOrThrow(AtlasIds.BLOCKS)
        //?} else {
        /*Minecraft.getInstance().modelManager.getAtlas(TextureAtlas.LOCATION_BLOCKS)
        *///?}
    } catch (e: IllegalArgumentException) {
        logOnce("atlas", "The block atlas is not loaded; nameplate atlas sprites use the CPU fallback ({})", e.message)
        null
    } catch (e: IllegalStateException) {
        logOnce("atlas", "The block atlas is not loaded; nameplate atlas sprites use the CPU fallback ({})", e.message)
        null
    }

    fun sprite(atlas: TextureAtlas, name: String): TextureAtlasSprite? {
        val id = spriteIds.getOrPut(name) { Identifier.tryParse(name) } ?: return null
        val sprite = try {
            atlas.getSprite(id)
        } catch (e: IllegalStateException) {
            logOnce("atlas", "The block atlas is not loaded; nameplate atlas sprites use the CPU fallback ({})", e.message)
            return null
        }
        if (sprite.contents().name() != id) {
            logOnce("sprite:$name", "Nameplate atlas sprite {} is not in the block atlas; it shows the missing texture", name)
        }
        return sprite
    }

    fun pixels(name: String): TexturePixels? {
        val atlas = blockAtlas() ?: return null
        val sprite = sprite(atlas, name) ?: return null
        read[name]?.takeIf { it.sprite === sprite }?.let { return it.pixels }
        val pixels = try {
            frames(sprite)
        } catch (e: IllegalStateException) {
            unreadable(name, e)
        } catch (e: IllegalArgumentException) {
            unreadable(name, e)
        }
        read[name] = Read(sprite, pixels)
        return pixels
    }

    fun clear() {
        read.clear()
    }

    private fun unreadable(name: String, error: RuntimeException): TexturePixels? {
        logOnce("read:$name", "Nameplate atlas sprite {} could not be read for the CPU fallback; it draws transparent there ({})", name, error.message)
        return null
    }

    private fun frames(sprite: TextureAtlasSprite): TexturePixels {
        val contents = sprite.contents()
        val image = (contents as SpriteContentsAccessor).`polyplus$originalImage`()
        val width = contents.width()
        val height = contents.height()
        val perRow = maxOf(1, image.width / width)
        val total = perRow * maxOf(1, image.height / height)
        val animation = animation(contents.name(), total)
        val used = animation.sequence.distinct().sorted().ifEmpty { listOf(0) }
        val size = width * height
        val argb = IntArray(size * used.size)
        for ((slot, frame) in used.withIndex()) {
            val x0 = frame % perRow * width
            val y0 = frame / perRow * height
            for (y in 0 until height) {
                for (x in 0 until width) {
                    argb[slot * size + y * width + x] = argb(image, x0 + x, y0 + y)
                }
            }
        }
        val sequence = IntArray(animation.sequence.size) { used.indexOf(animation.sequence[it]) }
        return TexturePixels(width, height, argb, sequence, animation.ticks)
    }

    private fun animation(name: Identifier, total: Int): Animation {
        val path = Identifier.fromNamespaceAndPath(name.namespace, "textures/${name.path}.png")
        val resource = Minecraft.getInstance().resourceManager.getResource(path).orElse(null) ?: return still
        val frames = ArrayList<IntArray>()
        try {
            //? if >= 1.21.4 {
            val section = resource.metadata().getSection(AnimationMetadataSection.TYPE).orElse(null) ?: return still
            val listed = section.frames().orElse(null)
            listed?.forEach { frames += intArrayOf(it.index(), it.timeOr(section.defaultFrameTime())) }
            val time = section.defaultFrameTime()
            //?} else {
            /*val section = resource.metadata().getSection(AnimationMetadataSection.SERIALIZER).orElse(null) ?: return still
            val listed: List<Any>? = null
            section.forEachFrame { index, time -> frames += intArrayOf(index, time) }
            val time = section.defaultFrameTime
            *///?}
            if (frames.isEmpty() && listed == null) {
                for (index in 0 until total) {
                    frames += intArrayOf(index, time)
                }
            }
        } catch (e: IOException) {
            logOnce("meta:$name", "Nameplate atlas sprite {} has unreadable animation data; it shows its first frame on the CPU fallback ({})", name, e.message)
            return still
        }
        val valid = frames.filter { (index, time) -> time > 0 && index in 0 until total }
        if (valid.size <= 1) {
            return still
        }
        return Animation(IntArray(valid.size) { valid[it][0] }, IntArray(valid.size) { valid[it][1] })
    }

    private fun argb(image: NativeImage, x: Int, y: Int): Int =
        //? if >= 1.21.4 {
        image.getPixel(x, y)
        //?} else {
        /*FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y))
        *///?}

    private fun logOnce(key: String, message: String, vararg args: Any?) {
        if (logged.add(key)) {
            LOGGER.warn(message, *args)
        }
    }
}
//?}
