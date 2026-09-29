package org.polyfrost.polyplus.client.resourcepack

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SharedResourcePack {
    private val LOGGER = LogManager.getLogger("PolyPlus/SharedPack")

    const val MAX_PACK_BYTES = 100L * 1024 * 1024

    const val MAX_TOTAL_BYTES = 150L * 1024 * 1024

    private const val STABLE_ENTRY_TIME = 0L

    class Prepared(
        val name: String,
        val bytes: ByteArray,
        val sha1: ByteArray,
        val sha1Hex: String,
    )

    fun buildFromEquipped(): Result<List<Prepared>> = runCatching {
        val minecraft = Minecraft.getInstance()
        val selected = minecraft.resourcePackRepository?.selectedIds
        checkNotNull(selected) { "Minecraft's resource pack repository isn't available yet" }

        val fileBacked = selected.filter { it.startsWith("file/") }
        check(fileBacked.isNotEmpty()) {
            "None of your selected resource packs live in resourcepacks/ - packs bundled inside mods can't be shared"
        }

        val packsDir = File(minecraft.gameDirectory, "resourcepacks")
        val prepared = ArrayList<Prepared>()
        var totalBytes = 0L

        for (id in fileBacked) {
            val fileName = id.removePrefix("file/")
            val entry = File(packsDir, fileName)
            if (!entry.exists()) {
                LOGGER.warn("'{}' is selected but no longer exists at '{}' - skipping it", fileName, entry.absolutePath)
                continue
            }

            // measured before reading so an oversized pack is never loaded
            val size = if (entry.isFile) entry.length() else entry.walkTopDown().filter { it.isFile }.sumOf { it.length() }

            if (size == 0L) {
                LOGGER.warn("'{}' is empty - skipping it", fileName)
                continue
            }
            if (size > MAX_PACK_BYTES) {
                LOGGER.warn(
                    "'{}' is {}, over the {} per-pack limit - skipping it",
                    fileName,
                    humanSize(size),
                    humanSize(MAX_PACK_BYTES),
                )
                continue
            }
            if (totalBytes + size > MAX_TOTAL_BYTES) {
                LOGGER.warn(
                    "Reached the {} total sharing limit - not sharing '{}' or anything above it in the stack",
                    humanSize(MAX_TOTAL_BYTES),
                    fileName,
                )
                break
            }

            val bytes = if (entry.isFile) entry.readBytes() else zipDirectory(entry)
            // zip headers can take a folder just past the limits its files fit within
            if (bytes.size > MAX_PACK_BYTES || totalBytes + bytes.size > MAX_TOTAL_BYTES) {
                LOGGER.warn("'{}' is {} once zipped, over the sharing limit - skipping it", fileName, humanSize(bytes.size.toLong()))
                continue
            }
            val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            prepared += Prepared(fileName, bytes, sha1, sha1.toHex())
            totalBytes += bytes.size
            LOGGER.info("Prepared '{}' for sharing ({}, sha1 {})", fileName, humanSize(bytes.size.toLong()), sha1.toHex())
        }

        check(prepared.isNotEmpty()) { "None of your equipped resource packs could be shared - see the log for why" }
        LOGGER.info("Sharing {} resource pack(s), {} in total", prepared.size, humanSize(totalBytes))
        prepared
    }.onFailure { LOGGER.warn("Couldn't prepare resource packs to share", it) }

    private fun zipDirectory(dir: File): ByteArray {
        val files = dir.walkTopDown().filter { it.isFile }
            .associateBy { it.relativeTo(dir).path.replace(File.separatorChar, '/') }
            .toSortedMap()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (path, file) ->
                zip.putNextEntry(ZipEntry(path).apply { time = STABLE_ENTRY_TIME })
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        LOGGER.info("Zipped {} file(s) from folder pack '{}'", files.size, dir.name)
        return out.toByteArray()
    }

    fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }

    private fun ByteArray.toHex(): String = HexFormat.of().formatHex(this)
}
