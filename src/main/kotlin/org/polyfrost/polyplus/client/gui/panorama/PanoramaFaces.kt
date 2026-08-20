package org.polyfrost.polyplus.client.gui.panorama

import java.io.DataInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal object PanoramaFaces {

    private val PNG_SIGNATURE =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private val IHDR = "IHDR".toByteArray(Charsets.US_ASCII)

    fun readPngSize(input: InputStream): Pair<Int, Int>? = runCatching {
        val data = DataInputStream(input)

        val signature = ByteArray(PNG_SIGNATURE.size)
        data.readFully(signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) return null

        data.skipNBytes(4)
        val type = ByteArray(IHDR.size)
        data.readFully(type)
        if (!type.contentEquals(IHDR)) return null

        val width = data.readInt()
        val height = data.readInt()
        if (width <= 0 || height <= 0) null else width to height
    }.getOrNull()

    fun readPngSize(path: Path): Pair<Int, Int>? =
        runCatching { Files.newInputStream(path).use { readPngSize(it) } }.getOrNull()

    fun facesUsable(sizes: List<Pair<Int, Int>?>): Boolean {
        val first = sizes.firstOrNull() ?: return false
        if (first.first != first.second) return false
        return sizes.all { it == first }
    }
}
