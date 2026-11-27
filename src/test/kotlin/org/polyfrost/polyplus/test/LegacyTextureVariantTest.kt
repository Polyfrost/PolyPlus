package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.cosmetics.assets.DiskAssetReader
import java.nio.file.Files
import java.nio.file.Path

class LegacyTextureVariantTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `the requested variant picks its own texture`() {
        textures("hat_blue.png", "hat_red.png")
        assertEquals("hat_red.png", DiskAssetReader.findTexture(root, "red")?.fileName.toString())
    }

    @Test
    fun `the variant match ignores case`() {
        textures("hat_blue.png", "Hat_Red.png")
        assertEquals("Hat_Red.png", DiskAssetReader.findTexture(root, "RED")?.fileName.toString())
    }

    @Test
    fun `no variant keeps the first texture`() {
        textures("hat_blue.png", "hat_red.png")
        assertEquals("hat_blue.png", DiskAssetReader.findTexture(root, null)?.fileName.toString())
    }

    @Test
    fun `an unknown variant falls back to the first texture`() {
        textures("hat_blue.png", "hat_red.png")
        assertEquals("hat_blue.png", DiskAssetReader.findTexture(root, "green")?.fileName.toString())
    }

    private fun textures(vararg names: String) {
        for (name in names) Files.write(root.resolve(name), ByteArray(0))
    }
}
