package org.polyfrost.polyplus.client.network.p2p

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SocialErrors

object ResourcePackShare {
    private val LOGGER = LogManager.getLogger()

    val SOCKET_ID: EosP2PSocketId = EosP2PSocketId("polyplus-resourcepack-share")

    private const val CHUNK_SIZE = 1024
    private const val MAX_PACK_BYTES = 50L * 1024 * 1024
    private const val QUEUE_PACING_THRESHOLD_BYTES = 2L * 1024 * 1024

    private const val TYPE_OFFER: Byte = 0
    private const val TYPE_CHUNK: Byte = 1
    private const val TYPE_COMPLETE: Byte = 2
    private const val TYPE_REJECT: Byte = 3

    private data class IncomingTransfer(
        val fileName: String,
        val totalBytes: Int,
        val sha256: ByteArray,
        val data: ByteArray,
        var receivedChunks: Int = 0,
        var receivedBytes: Int = 0,
    )

    private val incoming = ConcurrentHashMap<EosProductUserId, IncomingTransfer>()

    @Volatile private var bridge: EosSdkBridge? = null

    fun install(bridge: EosSdkBridge) {
        this.bridge = bridge
        bridge.addConnectionRequestHandler(SOCKET_ID) { remote -> bridge.acceptConnection(SOCKET_ID, remote) }
    }

    fun connectedPeers(): List<EosProductUserId> = P2PChannelRegistry.connectedPeers()

    fun shareEquippedPackWith(remote: EosProductUserId) {
        val bridge = this.bridge ?: run {
            SocialErrors.emit("Multiplayer services aren't ready yet")
            return
        }

        PolyPlusClient.SCOPE.launch {
            val pack = runCatching { readEquippedPack() }.getOrNull()
            if (pack == null) {
                LOGGER.warn("No equipped resource pack found to share, or it couldn't be read")
                SocialErrors.emit("You don't have a resource pack equipped to share")
                return@launch
            }
            if (pack.second.size > MAX_PACK_BYTES) {
                SocialErrors.emit("That resource pack is too large to share (max 50 MB)")
                return@launch
            }

            bridge.acceptConnection(SOCKET_ID, remote)
            sendPack(bridge, remote, pack.first, pack.second)
        }
    }

    private fun readEquippedPack(): Pair<String, ByteArray>? {
        val minecraft = Minecraft.getInstance()
        val selected = minecraft.resourcePackRepository.selectedIds
        val id = selected.lastOrNull() ?: return null

        val packsDir = File(minecraft.gameDirectory, "resourcepacks")
        val entry = File(packsDir, id)
        if (!entry.exists()) return null

        return if (entry.isFile) {
            id to entry.readBytes()
        } else {
            id to zipDirectory(entry)
        }
    }

    private fun zipDirectory(dir: File): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bytes).use { zip ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                zip.putNextEntry(java.util.zip.ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private suspend fun sendPack(bridge: EosSdkBridge, remote: EosProductUserId, fileName: String, data: ByteArray) {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data)
        val totalChunks = (data.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        val nameBytes = fileName.toByteArray(StandardCharsets.UTF_8)

        val offer = ByteBuffer.allocate(1 + 4 + nameBytes.size + 4 + 4 + sha256.size).apply {
            put(TYPE_OFFER)
            putInt(nameBytes.size)
            put(nameBytes)
            putInt(data.size)
            putInt(totalChunks)
            put(sha256)
            flip()
        }
        bridge.sendPacket(SOCKET_ID, remote, offer)

        for (index in 0 until totalChunks) {
            while (bridge.outboundQueueBytes(SOCKET_ID) >= QUEUE_PACING_THRESHOLD_BYTES) {
                delay(50)
            }

            val start = index * CHUNK_SIZE
            val end = minOf(start + CHUNK_SIZE, data.size)
            val chunk = ByteBuffer.allocate(1 + 4 + (end - start)).apply {
                put(TYPE_CHUNK)
                putInt(index)
                put(data, start, end - start)
                flip()
            }
            bridge.sendPacket(SOCKET_ID, remote, chunk)
        }

        val complete = ByteBuffer.allocate(1).apply { put(TYPE_COMPLETE); flip() }
        bridge.sendPacket(SOCKET_ID, remote, complete)

        LOGGER.info("Sent resource pack '{}' ({} bytes, {} chunks) to {}", fileName, data.size, totalChunks, remote)
        SocialErrors.emit("Sent your resource pack to ${PlayerNamesRepository.names.value[remote.raw] ?: remote.raw.take(8)}")
    }

    fun handlePacket(received: EosSdkBridge.Received): Boolean {
        if (received.socket != SOCKET_ID) return false

        val buf = received.data
        if (!buf.hasRemaining()) return true
        val type = buf.get()

        when (type) {
            TYPE_OFFER -> handleOffer(received.remote, buf)
            TYPE_CHUNK -> handleChunk(received.remote, buf)
            TYPE_COMPLETE -> handleComplete(received.remote)
            TYPE_REJECT -> {
                incoming.remove(received.remote)
                LOGGER.info("{} rejected the resource pack transfer", received.remote)
            }
            else -> LOGGER.warn("Unknown resource-pack-share frame type {} from {}", type, received.remote)
        }
        return true
    }

    private fun handleOffer(remote: EosProductUserId, buf: ByteBuffer) {
        val nameLen = buf.int
        val nameBytes = ByteArray(nameLen)
        buf.get(nameBytes)
        val fileName = String(nameBytes, StandardCharsets.UTF_8)
        val totalBytes = buf.int
        val totalChunks = buf.int
        val sha256 = ByteArray(32)
        buf.get(sha256)

        if (totalBytes < 0 || totalBytes > MAX_PACK_BYTES) {
            LOGGER.warn("Rejecting oversized resource pack offer from {} ({} bytes)", remote, totalBytes)
            return
        }

        incoming[remote] = IncomingTransfer(fileName, totalBytes, sha256, ByteArray(totalBytes))
        LOGGER.info("Receiving resource pack '{}' ({} bytes, {} chunks) from {}", fileName, totalBytes, totalChunks, remote)
    }

    private fun handleChunk(remote: EosProductUserId, buf: ByteBuffer) {
        val transfer = incoming[remote] ?: return
        val index = buf.int
        val remaining = buf.remaining()
        val start = index * CHUNK_SIZE
        if (start < 0 || start + remaining > transfer.data.size) {
            LOGGER.warn("Discarding out-of-range resource pack chunk {} from {}", index, remote)
            return
        }
        buf.get(transfer.data, start, remaining)
        transfer.receivedChunks++
        transfer.receivedBytes += remaining
    }

    private fun handleComplete(remote: EosProductUserId) {
        val transfer = incoming.remove(remote) ?: return

        val digest = MessageDigest.getInstance("SHA-256").digest(transfer.data)
        if (!digest.contentEquals(transfer.sha256)) {
            LOGGER.error("Resource pack from {} failed integrity check, discarding", remote)
            SocialErrors.emit("Received resource pack failed an integrity check")
            return
        }

        PolyPlusClient.SCOPE.launch {
            runCatching { installPack(transfer.fileName, transfer.data) }
                .onSuccess {
                    LOGGER.info("Installed resource pack '{}' from {}", transfer.fileName, remote)
                    SocialErrors.emit("Received a resource pack from ${PlayerNamesRepository.names.value[remote.raw] ?: remote.raw.take(8)} - check Options > Resource Packs")
                }
                .onFailure {
                    LOGGER.error("Failed to install resource pack '{}' from {}", transfer.fileName, remote, it)
                    SocialErrors.emit("Couldn't install the received resource pack", it)
                }
        }
    }

    private fun installPack(fileName: String, data: ByteArray) {
        val minecraft = Minecraft.getInstance()
        val packsDir = File(minecraft.gameDirectory, "resourcepacks")
        packsDir.mkdirs()

        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "shared-pack" }
        var target = File(packsDir, safeName)
        var suffix = 1
        while (target.exists()) {
            val dot = safeName.lastIndexOf('.')
            val stem = if (dot >= 0) safeName.substring(0, dot) else safeName
            val ext = if (dot >= 0) safeName.substring(dot) else ""
            target = File(packsDir, "$stem-received-$suffix$ext")
            suffix++
        }

        target.writeBytes(data)

        val repository = minecraft.resourcePackRepository
        val rescanned = listOf("reload", "scanPacks", "reloadPacks").any { name ->
            runCatching {
                repository.javaClass.getMethod(name).invoke(repository)
                true
            }.getOrDefault(false)
        }

        if (!rescanned) {
            LOGGER.warn(
                "Couldn't find a resource pack rescan method on this Minecraft version; saved '{}' to " +
                    "resourcepacks/ but you may need to reopen Options > Resource Packs for it to show up",
                target.name,
            )
        }
    }
}
