package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.cosmetics.assets.ArchiveLimits
import org.polyfrost.polyplus.client.cosmetics.assets.AssetArchive
import org.polyfrost.polyplus.client.cosmetics.assets.RejectedArchiveException
import org.polyfrost.polyplus.client.nameplate.NameplateLoader
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NameplateBundleLimitsTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `nameplate archives are capped at 2 MiB zipped, 8 MiB unpacked and 128 entries`() {
        assertEquals(ArchiveLimits(maxDownloadBytes = 2 * MIB, maxExtractedBytes = 8 * MIB, maxEntries = 128), NameplateLoader.ARCHIVE_LIMITS)
    }

    @Test
    fun `an archive with 128 entries unpacks`() {
        val zip = zip((0 until 128).associate { "sprites/s$it.json" to "{}".toByteArray() })
        val target = AssetArchive.materialize(zip, dir.resolve("ok"), NameplateLoader.ARCHIVE_LIMITS)
        assertTrue(Files.isRegularFile(target.resolve("sprites/s127.json")))
    }

    @Test
    fun `an archive with 129 entries is refused and nothing is left behind`() {
        val zip = zip((0 until 129).associate { "sprites/s$it.json" to "{}".toByteArray() })
        val target = dir.resolve("many")
        assertThrows<RejectedArchiveException> { AssetArchive.materialize(zip, target, NameplateLoader.ARCHIVE_LIMITS) }
        assertFalse(Files.exists(target))
    }

    @Test
    fun `an archive that unpacks past 8 MiB is refused`() {
        val half = ByteArray((4 * MIB + 1).toInt())
        val zip = zip(mapOf("a.json" to half, "b.json" to half))
        assertTrue(zip.size < NameplateLoader.ARCHIVE_LIMITS.maxDownloadBytes)
        val target = dir.resolve("big")
        assertThrows<RejectedArchiveException> { AssetArchive.materialize(zip, target, NameplateLoader.ARCHIVE_LIMITS) }
        assertFalse(Files.exists(target))
    }

    @Test
    fun `an archive that unpacks to exactly 8 MiB is kept`() {
        val half = ByteArray((4 * MIB).toInt())
        val zip = zip(mapOf("a.json" to half, "b.json" to half))
        assertNotNull(AssetArchive.materialize(zip, dir.resolve("edge"), NameplateLoader.ARCHIVE_LIMITS))
    }

    @Test
    fun `a bundle file over 1 MiB is not read`() {
        val small = bundle(dir.resolve("small"), padding = 0)
        assertNotNull(NameplateLoader.loadDownloaded("limits-small", "a", -1, small))

        val padded = bundle(dir.resolve("padded"), padding = MIB.toInt())
        assertTrue(Files.size(padded.resolve("layers/body.json")) > MIB)
        assertNull(NameplateLoader.loadDownloaded("limits-padded", "a", -1, padded))
    }

    private fun bundle(root: Path, padding: Int): Path {
        write(root, "manifest.json", """{ "format": 1, "id": "limits", "fps": 8, "loop": 16, "layers": [{ "id": "body" }] }""")
        write(root, "palettes.json", """{ "slots": [{ "id": "fill", "ramp": ["#FFFFFF"] }] }""")
        write(root, "layers/body.json", """{ "kind": "body", "fill": { "color": "fill" } }""" + " ".repeat(padding))
        return root
    }

    private fun write(root: Path, path: String, text: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val MIB = 1024L * 1024
    }
}
