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
    private const val MAX_NAME_BYTES = 512
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
        var receivedBytes: Int = 0,
    )

    private val incoming = ConcurrentHashMap<EosProductUserId, IncomingTransfer>()

    private val sendingTo = ConcurrentHashMap.newKeySet<EosProductUserId>()
    private val lastSentHash = ConcurrentHashMap<EosProductUserId, String>()

    @Volatile private var bridge: EosSdkBridge? = null

    fun install(bridge: EosSdkBridge) {
        this.bridge = bridge
        bridge.addConnectionRequestHandler(SOCKET_ID) { remote -> bridge.acceptConnection(SOCKET_ID, remote) }
    }

    /** Players we currently have an active P2P game connection with - the natural "share with" targets. */
    fun connectedPeers(): List<EosProductUserId> = P2PChannelRegistry.connectedPeers()

    fun shareEquippedPackWith(remote: EosProductUserId) {
        val bridge = this.bridge ?: run {
            SocialErrors.emit("Multiplayer services aren't ready yet")
            return
        }

        if (!sendingTo.add(remote)) {
            LOGGER.info("Already sending a resource pack to {}, skipping duplicate request", remote)
            return
        }

        PolyPlusClient.SCOPE.launch {
            try {
                val pack = runCatching { readEquippedPack() }
                    .onFailure { LOGGER.error("Failed to read the equipped resource pack", it) }
                    .getOrNull()
                if (pack == null) {
                    SocialErrors.emit("Couldn't find your equipped resource pack's file - try re-selecting it in Options > Resource Packs")
                    return@launch
                }
                if (pack.second.isEmpty()) {
                    SocialErrors.emit("Your equipped resource pack is empty, nothing to share")
                    return@launch
                }
                if (pack.second.size > MAX_PACK_BYTES) {
                    SocialErrors.emit("That resource pack is too large to share (max 50 MB)")
                    return@launch
                }

                val hash = MessageDigest.getInstance("SHA-256").digest(pack.second).toHex()
                if (lastSentHash[remote] == hash) {
                    LOGGER.info("{} already has this resource pack, skipping resend", remote)
                    return@launch
                }

                bridge.acceptConnection(SOCKET_ID, remote)
                runCatching { sendPack(bridge, remote, pack.first, pack.second, hash) }
                    .onFailure {
                        LOGGER.error("Failed to send resource pack '{}' to {}", pack.first, remote, it)
                        SocialErrors.emit("Failed to send your resource pack", it)
                    }
            } finally {
                sendingTo.remove(remote)
            }
        }
    }

    private fun readEquippedPack(): Pair<String, ByteArray>? {
        val minecraft = Minecraft.getInstance()
        val selected = minecraft.resourcePackRepository?.selectedIds ?: return null
        val id = selected.lastOrNull() ?: return null

        val fileName = id.removePrefix("file/")
        if (fileName.isBlank() || fileName == id && id in BUILTIN_PACK_IDS) return null

        val packsDir = File(minecraft.gameDirectory, "resourcepacks")
        val entry = File(packsDir, fileName)
        if (!entry.exists()) {
            LOGGER.warn("Equipped resource pack id '{}' (resolved to '{}') has no matching file under resourcepacks/", id, fileName)
            return null
        }

        return if (entry.isFile) {
            fileName to entry.readBytes()
        } else {
            fileName to zipDirectory(entry)
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

    private suspend fun sendPack(bridge: EosSdkBridge, remote: EosProductUserId, fileName: String, data: ByteArray, hashHex: String) {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data)
        val totalChunks = (data.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        val nameBytes = fileName.toByteArray(StandardCharsets.UTF_8).let {
            if (it.size > MAX_NAME_BYTES) it.copyOf(MAX_NAME_BYTES) else it
        }

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
            var waited = 0
            while (bridge.outboundQueueBytes(SOCKET_ID) >= QUEUE_PACING_THRESHOLD_BYTES) {
                delay(50)
                if (++waited > 1200) { // 60s stuck with a full queue, the connection is dead, so just give up
                    error("Outbound queue stayed full for too long, aborting transfer to $remote")
                }
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

        lastSentHash[remote] = hashHex
        LOGGER.info("Sent resource pack '{}' ({} bytes, {} chunks) to {}", fileName, data.size, totalChunks, remote)
        SocialErrors.emit("Sent your resource pack to ${displayName(remote)}")
    }

    fun handlePacket(received: EosSdkBridge.Received): Boolean {
        if (received.socket != SOCKET_ID) return false

        runCatching {
            val buf = received.data
            if (!buf.hasRemaining()) return@runCatching
            when (val type = buf.get()) {
                TYPE_OFFER -> handleOffer(received.remote, buf)
                TYPE_CHUNK -> handleChunk(received.remote, buf)
                TYPE_COMPLETE -> handleComplete(received.remote)
                TYPE_REJECT -> {
                    incoming.remove(received.remote)
                    LOGGER.info("{} rejected the resource pack transfer", received.remote)
                }
                else -> LOGGER.warn("Unknown resource-pack-share frame type {} from {}", type, received.remote)
            }
        }.onFailure {
            LOGGER.warn("Discarding malformed resource-pack-share frame from {}", received.remote, it)
            incoming.remove(received.remote)
        }
        return true
    }

    private fun handleOffer(remote: EosProductUserId, buf: ByteBuffer) {
        val nameLen = buf.int
        if (nameLen < 0 || nameLen > MAX_NAME_BYTES || nameLen > buf.remaining()) {
            LOGGER.warn("Rejecting resource pack offer from {} with invalid name length {}", remote, nameLen)
            return
        }
        val nameBytes = ByteArray(nameLen)
        buf.get(nameBytes)
        val fileName = String(nameBytes, StandardCharsets.UTF_8)
        if (buf.remaining() < 4 + 4 + 32) {
            LOGGER.warn("Rejecting truncated resource pack offer from {}", remote)
            return
        }
        val totalBytes = buf.int
        val totalChunks = buf.int
        val sha256 = ByteArray(32)
        buf.get(sha256)

        if (totalBytes <= 0 || totalBytes > MAX_PACK_BYTES || totalChunks < 0) {
            LOGGER.warn("Rejecting invalid resource pack offer from {} ({} bytes, {} chunks)", remote, totalBytes, totalChunks)
            return
        }

        if (incoming.containsKey(remote)) {
            LOGGER.warn("New resource pack offer from {} while a previous transfer was still in progress; abandoning the old one", remote)
        }

        incoming[remote] = IncomingTransfer(fileName, totalBytes, sha256, ByteArray(totalBytes))
        LOGGER.info("Receiving resource pack '{}' ({} bytes, {} chunks) from {}", fileName, totalBytes, totalChunks, remote)
    }

    private fun handleChunk(remote: EosProductUserId, buf: ByteBuffer) {
        val transfer = incoming[remote] ?: return
        if (buf.remaining() < 4) {
            LOGGER.warn("Discarding truncated resource pack chunk header from {}", remote)
            return
        }
        val index = buf.int
        val remaining = buf.remaining()
        if (remaining <= 0) return
        val start = index.toLong() * CHUNK_SIZE
        if (index < 0 || start < 0 || start + remaining > transfer.data.size) {
            LOGGER.warn("Discarding out-of-range resource pack chunk {} from {}", index, remote)
            return
        }
        buf.get(transfer.data, start.toInt(), remaining)
        transfer.receivedBytes += remaining
    }

    private fun handleComplete(remote: EosProductUserId) {
        val transfer = incoming.remove(remote) ?: return

        if (transfer.receivedBytes != transfer.totalBytes) {
            LOGGER.error(
                "Resource pack transfer from {} finished incomplete ({}/{} bytes received), discarding",
                remote,
                transfer.receivedBytes,
                transfer.totalBytes,
            )
            SocialErrors.emit("Resource pack transfer from ${displayName(remote)} was incomplete")
            return
        }

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
                    SocialErrors.emit("Received a resource pack from ${displayName(remote)} - check Options > Resource Packs")
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

        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
            .filter { it !in FORBIDDEN_FILENAME_CHARS }
            .ifBlank { "shared-pack" }
        var target = File(packsDir, safeName)
        var suffix = 1
        while (target.exists()) {
            val dot = safeName.lastIndexOf('.')
            val stem = if (dot >= 0) safeName.substring(0, dot) else safeName
            val ext = if (dot >= 0) safeName.substring(dot) else ""
            target = File(packsDir, "$stem-received-$suffix$ext")
            suffix++
        }
        check(target.canonicalFile.parentFile == packsDir.canonicalFile) { "Resulting pack path escaped resourcepacks/" }

        val tmp = File(packsDir, "${target.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Couldn't finalize the downloaded resource pack file")
        }

        val repository = minecraft.resourcePackRepository
        val rescanned = repository != null && listOf("reload", "scanPacks", "reloadPacks").any { name ->
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

    private fun displayName(remote: EosProductUserId): String =
        PlayerNamesRepository.names.value[remote.raw] ?: remote.raw.take(8)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val FORBIDDEN_FILENAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', ' ')
    private val BUILTIN_PACK_IDS = setOf("vanilla", "programer_art", "fabric", "minecraft")
}
