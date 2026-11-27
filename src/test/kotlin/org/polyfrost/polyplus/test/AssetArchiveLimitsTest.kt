package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.assets.ArchiveLimits
import org.polyfrost.polyplus.client.cosmetics.assets.AssetArchive
import org.polyfrost.polyplus.client.cosmetics.assets.RejectedArchiveException
import org.polyfrost.polyplus.client.cosmetics.assets.readAtMost
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AssetArchiveLimitsTest {
    private val limits = ArchiveLimits(maxDownloadBytes = 1024, maxExtractedBytes = 4096, maxEntries = 4, maxEntryBytes = 2048)

    @Test
    fun `a stream that fits the limit is read whole`() {
        val bytes = ByteArray(16) { it.toByte() }
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readAtMost(16))
    }

    @Test
    fun `a stream past the limit is rejected`() {
        assertThrows(RejectedArchiveException::class.java) { ByteArrayInputStream(ByteArray(17)).readAtMost(16) }
    }

    @Test
    fun `an archive inside every limit passes`() {
        assertDoesNotThrow { AssetArchive.verify(zip("manifest.json" to 2048, "palettes.json" to 2048), limits) }
    }

    @Test
    fun `one entry that expands past its own limit is rejected`() {
        val bomb = zip("palettes.json" to 1024 * 1024)
        assertThrows(RejectedArchiveException::class.java) { AssetArchive.verify(bomb, limits) }
    }

    @Test
    fun `entries that together expand past the total are rejected`() {
        val archive = zip("a.json" to 2048, "b.json" to 2048, "c.json" to 1)
        assertThrows(RejectedArchiveException::class.java) { AssetArchive.verify(archive, limits) }
    }

    @Test
    fun `too many entries are rejected`() {
        val archive = zip(*Array(5) { "file$it.json" to 1 })
        assertThrows(RejectedArchiveException::class.java) { AssetArchive.verify(archive, limits) }
    }

    @Test
    fun `bytes that are not an archive are left alone`() {
        assertDoesNotThrow { AssetArchive.verify(ByteArray(1 shl 20), limits) }
    }

    private fun zip(vararg entries: Pair<String, Int>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, size) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(ByteArray(size))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
