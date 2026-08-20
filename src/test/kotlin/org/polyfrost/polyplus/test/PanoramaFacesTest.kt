package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.gui.panorama.PanoramaFaces
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class PanoramaFacesTest {

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        data.writeInt(13)
        data.write("IHDR".toByteArray(Charsets.US_ASCII))
        data.writeInt(width)
        data.writeInt(height)
        data.write(byteArrayOf(8, 6, 0, 0, 0))
        return out.toByteArray()
    }

    private fun size(bytes: ByteArray) = PanoramaFaces.readPngSize(bytes.inputStream())

    @Test
    fun `reads dimensions from a png header`() {
        assertEquals(1024 to 1024, size(png(1024, 1024)))
        assertEquals(16 to 2, size(png(16, 2)))
    }

    @Test
    fun `rejects data that is not a png`() {
        assertNull(size("not a png at all".toByteArray()))
        assertNull(size(ByteArray(0)))
        assertNull(size(png(1024, 1024).copyOf(12)))
    }

    @Test
    fun `rejects a zero sized image`() {
        assertNull(size(png(0, 0)))
    }

    @Test
    fun `accepts six identical square faces`() {
        assertTrue(PanoramaFaces.facesUsable(List(6) { 1024 to 1024 }))
    }

    @Test
    fun `rejects the shipped crash case of a truncated face`() {
        val faces = MutableList<Pair<Int, Int>?>(6) { 1024 to 1024 }
        faces[3] = 16 to 2
        assertFalse(PanoramaFaces.facesUsable(faces))
    }

    @Test
    fun `rejects faces that are square but different sizes`() {
        val faces = MutableList<Pair<Int, Int>?>(6) { 1024 to 1024 }
        faces[5] = 512 to 512
        assertFalse(PanoramaFaces.facesUsable(faces))
    }

    @Test
    fun `rejects a missing or unreadable face`() {
        val faces = MutableList<Pair<Int, Int>?>(6) { 1024 to 1024 }
        faces[0] = null
        assertFalse(PanoramaFaces.facesUsable(faces))

        val trailing = MutableList<Pair<Int, Int>?>(6) { 1024 to 1024 }
        trailing[5] = null
        assertFalse(PanoramaFaces.facesUsable(trailing))
    }

    @Test
    fun `rejects an empty face list`() {
        assertFalse(PanoramaFaces.facesUsable(emptyList()))
    }
}
