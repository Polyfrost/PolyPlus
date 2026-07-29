//? if >= 1.21.11 {
package org.polyfrost.polyplus.client.gui.panorama

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.CubeMapTexture
import net.minecraft.client.renderer.texture.MipmapStrategy
import net.minecraft.client.renderer.texture.TextureContents
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class DiskCubeMapTexture(id: Identifier, private val dir: Path) : CubeMapTexture(id) {
    override fun loadContents(resourceManager: ResourceManager): TextureContents {
        var stacked: NativeImage? = null
        var width = 0
        var height = 0

        try {
            FACE_ORDER.forEachIndexed { slot, face ->
                readFace(face).use { image ->
                    if (slot == 0) {
                        width = image.width
                        height = image.height
                        stacked = NativeImage(width, height * FACE_ORDER.size, false)
                    } else if (image.width != width || image.height != height) {
                        throw IOException(
                            "Panorama face $face is ${image.width}x${image.height}, expected ${width}x$height",
                        )
                    }
                    image.copyRect(stacked!!, 0, 0, 0, slot * height, width, height, false, true)
                }
            }
        } catch (t: Throwable) {
            stacked?.close()
            throw t
        }

        return TextureContents(stacked!!, TextureMetadataSection(true, false, MipmapStrategy.MEAN, 0.0f))
    }

    private fun readFace(index: Int): NativeImage =
        Files.newInputStream(dir.resolve("panorama_$index.png")).use(NativeImage::read)

    private companion object {
        val FACE_ORDER = intArrayOf(1, 3, 5, 4, 0, 2)
    }
}
//?}
