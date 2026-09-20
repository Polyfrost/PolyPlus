package org.polyfrost.polyplus.client.cosmetics.assets

import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import net.minecraft.client.renderer.texture.DynamicTexture
import org.polyfrost.polyplus.client.render.NativeImage
//?} else {
/*import net.minecraft.client.render.texture.DynamicTexture
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

    fun register(textureId: Identifier, pngFile: Path): Identifier {
        //? if > 1.8.9 {
        val nativeImage = Files.newInputStream(pngFile).use(NativeImage::read)
        //?} else {
        /*val image = Files.newInputStream(pngFile).use(ImageIO::read) ?: error("Could not decode texture $pngFile")
        *///?}
        return ClientPlatform.runOnMainSync {
            release(textureId)
            //? if > 1.8.9 {
            val dynamicTexture = DynamicTexture(
                //? if >= 1.21.5 {
                { textureId.toString() },
                //?}
                nativeImage,
            )
            //?} else {
            /*val dynamicTexture = DynamicTexture(image)
            *///?}
            Minecraft.getInstance().textureManager.register(textureId, dynamicTexture)
            registered[textureId] = dynamicTexture
            logger.debug("Registered remote texture {}", textureId)
            textureId
        }
    }

    fun findTexture(root: Path, baseName: String): Path? {
        val candidates = listOf(
            root.resolve("textures/$baseName.png"),
            root.resolve("$baseName.png"),
            root.resolve("textures/emotes/$baseName.png"),
            root.resolve("textures/cosmetics/$baseName.png"),
        )
        return candidates.firstOrNull { Files.isRegularFile(it) }
    }

    fun releaseAll() {
        ClientPlatform.runOnMainSync {
            val client = Minecraft.getInstance()
            for (id in registered.keys.toList()) {
                client.textureManager.release(id)
            }
            registered.clear()
        }
    }

    private fun release(textureId: Identifier) {
        if (!registered.containsKey(textureId)) {
            return
        }
        Minecraft.getInstance().textureManager.release(textureId)
        registered.remove(textureId)
    }
}

//? if = 1.8.9 {
/*private fun net.minecraft.client.render.texture.TextureManager.release(id: Identifier) = close(id)
*///?}
