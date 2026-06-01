//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.assets

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

internal object RemoteTextures {
    private val logger = LoggerFactory.getLogger("polyplus/textures")
    private val registered = LinkedHashMap<Identifier, DynamicTexture>()

    fun register(textureId: Identifier, pngFile: Path): Identifier {
        val nativeImage = Files.newInputStream(pngFile).use(NativeImage::read)
        return ClientPlatform.runOnMainSync {
            release(textureId)
            val dynamicTexture = DynamicTexture(
                //? if >= 1.21.5 {
                { textureId.toString() },
                //?}
                nativeImage,
            )
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
//?}
