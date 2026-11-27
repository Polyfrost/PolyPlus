package org.polyfrost.polyplus.client.cosmetics.assets

import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import net.minecraft.client.renderer.texture.DynamicTexture
import org.polyfrost.polyplus.client.render.NativeImage
//?} else {
/*import net.minecraft.client.render.texture.DynamicTexture
import net.minecraft.client.render.texture.TextureManager
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
*///?}
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

internal object RemoteTextures {
    private val logger = LoggerFactory.getLogger("polyplus/textures")
    private val registered = LinkedHashMap<Identifier, DynamicTexture>()
    private val persistent = HashSet<Identifier>()
    private val translucent = HashSet<Identifier>()

    fun register(textureId: Identifier, pngFile: Path, keepOnReset: Boolean = false): Identifier {
        //? if > 1.8.9 {
        val nativeImage = Files.newInputStream(pngFile).use(NativeImage::read)
        var adopted = false
        try {
            val partial = partialAlpha(nativeImage)
            return ClientPlatform.runOnMainSync {
                release(textureId)
                if (partial) translucent.add(textureId)
                val dynamicTexture = DynamicTexture(
                    //? if >= 1.21.5 {
                    { textureId.toString() },
                    //?}
                    nativeImage,
                )
                adopted = true
                Minecraft.getInstance().textureManager.register(textureId, dynamicTexture)
                registered[textureId] = dynamicTexture
                if (keepOnReset) persistent.add(textureId)
                logger.debug("Registered remote texture {}", textureId)
                textureId
            }
        } finally {
            // the texture closes the image once it has one, until then nothing else will
            if (!adopted) nativeImage.close()
        }
        //?} else {
        /*val image = Files.newInputStream(pngFile).use(ImageIO::read) ?: error("Could not decode texture $pngFile")
        val partial = partialAlpha(image)
        return ClientPlatform.runOnMainSync {
            release(textureId)
            if (partial) translucent.add(textureId)
            val dynamicTexture = DynamicTexture(image)
            Minecraft.getInstance().textureManager.register(textureId, dynamicTexture)
            registered[textureId] = dynamicTexture
            if (keepOnReset) persistent.add(textureId)
            logger.debug("Registered remote texture {}", textureId)
            textureId
        }
        *///?}
    }

    fun registerPixels(textureId: Identifier, width: Int, height: Int, rgba: ByteArray): Identifier {
        val staged = stagePixels(textureId, width, height, rgba)
        try {
            return ClientPlatform.runOnMainSync(staged::publish)
        } finally {
            staged.close()
        }
    }

    fun stagePixels(textureId: Identifier, width: Int, height: Int, rgba: ByteArray): Staged {
        val partial = partialAlpha(rgba)
        //? if > 1.8.9 {
        val nativeImage = NativeImage(width, height, false)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val argb = argb(rgba, (y * width + x) * 4)
                    //? if >= 1.21.4
                    nativeImage.setPixel(x, y, argb)
                    //? if < 1.21.4
                    //nativeImage.setPixelRGBA(x, y, toAbgr(argb))
                }
            }
        } catch (error: Throwable) {
            nativeImage.close()
            throw error
        }
        return Staged(textureId, partial, nativeImage)
        //?} else {
        /*val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) image.setRGB(x, y, argb(rgba, (y * width + x) * 4))
        }
        return Staged(textureId, partial, image)
        *///?}
    }

    fun stagePng(textureId: Identifier, pngFile: Path): Staged {
        //? if > 1.8.9 {
        val nativeImage = Files.newInputStream(pngFile).use(NativeImage::read)
        val partial = try {
            partialAlpha(nativeImage)
        } catch (error: Throwable) {
            nativeImage.close()
            throw error
        }
        return Staged(textureId, partial, nativeImage)
        //?} else {
        /*val image = Files.newInputStream(pngFile).use(ImageIO::read) ?: error("Could not decode texture $pngFile")
        return Staged(textureId, partialAlpha(image), image)
        *///?}
    }

    class Staged internal constructor(
        val id: Identifier,
        private val partial: Boolean,
        //? if > 1.8.9 {
        private var image: NativeImage?,
        //?} else {
        /*private var image: BufferedImage?,
        *///?}
    ) : AutoCloseable {
        fun publish(): Identifier {
            val pending = checkNotNull(image) { "Texture $id was already published or discarded" }
            release(id)
            if (partial) translucent.add(id)
            //? if > 1.8.9 {
            val dynamicTexture = DynamicTexture(
                //? if >= 1.21.5 {
                { id.toString() },
                //?}
                pending,
            )
            //?} else {
            /*val dynamicTexture = DynamicTexture(pending)
            *///?}
            image = null
            Minecraft.getInstance().textureManager.register(id, dynamicTexture)
            registered[id] = dynamicTexture
            logger.debug("Registered remote texture {}", id)
            return id
        }

        override fun close() {
            //? if > 1.8.9 {
            image?.close()
            //?}
            image = null
        }
    }

    fun isTranslucent(textureId: Identifier): Boolean = translucent.isNotEmpty() && textureId in translucent

    private fun partialAlpha(rgba: ByteArray): Boolean {
        var at = 3
        while (at < rgba.size) {
            if ((rgba[at].toInt() and 0xFF) in 1..254) return true
            at += 4
        }
        return false
    }

    //? if > 1.8.9 {
    private fun partialAlpha(image: NativeImage): Boolean {
        if (!image.format().hasAlpha()) return false
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                //? if >= 1.21.4
                val alpha = image.getPixel(x, y) ushr 24
                //? if < 1.21.4
                //val alpha = image.getPixelRGBA(x, y) ushr 24
                if (alpha in 1..254) return true
            }
        }
        return false
    }
    //?} else {
    /*private fun partialAlpha(image: BufferedImage): Boolean {
        if (!image.colorModel.hasAlpha()) return false
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) in 1..254) return true
            }
        }
        return false
    }
    *///?}

    private fun argb(rgba: ByteArray, at: Int): Int {
        val r = rgba[at].toInt() and 0xFF
        val g = rgba[at + 1].toInt() and 0xFF
        val b = rgba[at + 2].toInt() and 0xFF
        val a = rgba[at + 3].toInt() and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    //? if < 1.21.4 && > 1.8.9 {
    /*private fun toAbgr(argb: Int): Int =
        (argb and 0xFF00FF00.toInt()) or ((argb and 0x00FF0000) ushr 16) or ((argb and 0x000000FF) shl 16)
    *///?}

    fun findTexture(root: Path, baseName: String): Path? {
        val candidates = listOf(
            root.resolve("textures/$baseName.png"),
            root.resolve("$baseName.png"),
            root.resolve("textures/emotes/$baseName.png"),
            root.resolve("textures/cosmetics/$baseName.png"),
        )
        return candidates.firstOrNull { Files.isRegularFile(it) }
    }

    fun isRegistered(textureId: Identifier): Boolean = textureId in registered

    fun releaseAll() {
        ClientPlatform.runOnMainSync {
            val client = Minecraft.getInstance()
            for (id in registered.keys.toList()) {
                if (id in persistent) continue
                client.textureManager.release(id)
                registered.remove(id)
                translucent.remove(id)
            }
        }
    }

    fun release(textureId: Identifier) {
        persistent.remove(textureId)
        translucent.remove(textureId)
        if (!registered.containsKey(textureId)) {
            return
        }
        Minecraft.getInstance().textureManager.release(textureId)
        registered.remove(textureId)
    }
}

//? if = 1.8.9 {
/*private fun TextureManager.release(id: Identifier) = close(id)
*///?}
