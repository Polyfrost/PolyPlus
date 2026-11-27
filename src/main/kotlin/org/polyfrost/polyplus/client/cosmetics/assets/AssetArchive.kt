package org.polyfrost.polyplus.client.cosmetics.assets

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

internal class OutOfDiskSpaceException(cause: IOException) :
    IOException("Out of disk space while writing cosmetic assets", cause)

internal class RejectedArchiveException(message: String) : IOException(message)

internal data class ArchiveLimits(
    val maxDownloadBytes: Long,
    val maxExtractedBytes: Long,
    val maxEntries: Int,
    val maxEntryBytes: Long = Long.MAX_VALUE,
)

internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val bytes = readNBytes(limit + 1)
    if (bytes.size > limit) throw RejectedArchiveException("Archive entry is over the $limit byte limit")
    return bytes
}

internal object AssetArchive {
    private val OUT_OF_SPACE_MARKERS =
        listOf("no space left", "not enough space", "disk full", "insufficient disk space")

    fun materialize(bytes: ByteArray, targetDir: Path, limits: ArchiveLimits): Path = mappingDiskFull {
        targetDir.createDirectories()
        try {
            if (isZip(bytes)) {
                extractZip(bytes, targetDir, limits)
            } else {
                val single = targetDir.resolve("asset.bin")
                Files.write(single, bytes)
            }
        } catch (e: RejectedArchiveException) {
            targetDir.toFile().deleteRecursively()
            throw e
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

    fun verify(bytes: ByteArray, limits: ArchiveLimits) {
        if (isZip(bytes)) forEachFile(bytes, limits) { OutputStream.nullOutputStream() }
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun extractZip(bytes: ByteArray, targetDir: Path, limits: ArchiveLimits) {
        val root = targetDir.toAbsolutePath().normalize()
        forEachFile(bytes, limits) { name ->
            val out = entryPath(root, name)
            out.parent?.createDirectories()
            out.outputStream()
        }
    }

    private inline fun forEachFile(bytes: ByteArray, limits: ArchiveLimits, open: (String) -> OutputStream) {
        var entries = 0
        var extracted = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (++entries > limits.maxEntries) {
                    throw RejectedArchiveException("Archive has more than ${limits.maxEntries} entries")
                }
                if (!entry.isDirectory) {
                    val budget = minOf(limits.maxEntryBytes, limits.maxExtractedBytes - extracted)
                    extracted += open(entry.name).use { copyAtMost(zip, it, budget) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun copyAtMost(input: InputStream, output: OutputStream, budget: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return copied
            copied += read
            if (copied > budget) throw RejectedArchiveException("Archive expands past its size limit")
            output.write(buffer, 0, read)
        }
    }

    private fun entryPath(root: Path, name: String): Path {
        val out = runCatching { root.resolve(name).normalize() }.getOrNull()
        if (out == null || out == root || !out.startsWith(root)) {
            throw RejectedArchiveException("Zip entry $name escapes the cosmetic folder")
        }
        return out
    }
}

internal fun detectVerticalTextureFrameCount(
    declaredWidth: Int,
    declaredHeight: Int,
    actualWidth: Int,
    actualHeight: Int,
): Int {
    if (declaredWidth <= 0 || declaredHeight <= 0) return 1
    if (actualWidth != declaredWidth || actualHeight <= declaredHeight) return 1
    if (actualHeight % declaredHeight != 0) return 1
    return actualHeight / declaredHeight
}
