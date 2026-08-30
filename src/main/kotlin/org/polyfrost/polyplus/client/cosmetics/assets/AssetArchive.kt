package org.polyfrost.polyplus.client.cosmetics.assets

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

internal class OutOfDiskSpaceException(cause: IOException) :
    IOException("Out of disk space while writing cosmetic assets", cause)

internal object AssetArchive {
    private val OUT_OF_SPACE_MARKERS =
        listOf("no space left", "not enough space", "disk full", "insufficient disk space")

    fun materialize(bytes: ByteArray, targetDir: Path): Path = mappingDiskFull {
        targetDir.createDirectories()
        if (isZip(bytes)) {
            extractZip(bytes, targetDir)
        } else {
            val single = targetDir.resolve("asset.bin")
            Files.write(single, bytes)
        }
        targetDir
    }

    private inline fun <T> mappingDiskFull(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw if (isOutOfSpace(e)) OutOfDiskSpaceException(e) else e
    }

    private fun isOutOfSpace(error: IOException): Boolean {
        val messages = generateSequence<Throwable>(error) { current ->
            current.cause?.takeIf { it !== current }
        }.mapNotNull { it.message }.joinToString(" ")
        return OUT_OF_SPACE_MARKERS.any { messages.contains(it, ignoreCase = true) }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun extractZip(bytes: ByteArray, targetDir: Path) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = targetDir.resolve(entry.name)
                    out.parent?.createDirectories()
                    out.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    fun readBytes(path: Path): ByteArray = Files.readAllBytes(path)
}
