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

    private const val PROGRESS_LOG_EVERY_CHUNKS = 200

    private const val TYPE_OFFER: Byte = 0
    private const val TYPE_CHUNK: Byte = 1
    private const val TYPE_COMPLETE: Byte = 2
    private const val TYPE_REJECT: Byte = 3

    private data class IncomingTransfer(
        val fileName: String,
        val totalBytes: Int,
        val totalChunks: Int,
        val sha256: ByteArray,
        val data: ByteArray,
        var receivedBytes: Int = 0,
        var receivedChunks: Int = 0,
    )

    private val incoming = ConcurrentHashMap<EosProductUserId, IncomingTransfer>()

    private val sendingTo = ConcurrentHashMap.newKeySet<EosProductUserId>()
    private val lastSentHash = ConcurrentHashMap<EosProductUserId, String>()

    @Volatile private var bridge: EosSdkBridge? = null

    fun install(bridge: EosSdkBridge) {
        this.bridge = bridge
        bridge.addConnectionRequestHandler(SOCKET_ID) { remote ->
            LOGGER.info("{} wants to open a resource-pack-sharing connection with us", remote)
            bridge.acceptConnection(SOCKET_ID, remote)
        }
    }

    /** Players we currently have an active P2P game connection with - the natural "share with" targets. */
    fun connectedPeers(): List<EosProductUserId> = P2PChannelRegistry.connectedPeers()

    fun shareEquippedPackWith(remote: EosProductUserId, force: Boolean = false) {
        val bridge = this.bridge ?: run {
            LOGGER.warn("Asked to share a resource pack with {}, but the EOS P2P bridge isn't installed yet - too early in startup?", remote)
            SocialErrors.emit("Multiplayer services aren't ready yet")
            return
        }

        LOGGER.info("Starting a resource pack share with {} (force={})...", remote, force)

        if (!sendingTo.add(remote)) {
            LOGGER.info("Already in the middle of sending a resource pack to {} - ignoring this extra request so we don't cross the streams.", remote)
            return
        }

        PolyPlusClient.SCOPE.launch {
            try {
                LOGGER.info("Looking up which resource pack(s) you currently have equipped...")
                val packs = runCatching { readEquippedPacks() }
                    .onFailure { LOGGER.error("Something went wrong while trying to read your equipped resource packs from disk.", it) }
                    .getOrDefault(emptyList())
                if (packs.isEmpty()) {
                    LOGGER.warn("Couldn't find any shareable resource pack files for {} - see the warning(s) above for why. Bailing out.", remote)
                    SocialErrors.emit("Couldn't find your equipped resource pack's file - try re-selecting it in Options > Resource Packs")
                    return@launch
                }
                LOGGER.info(
                    "Found {} equipped, file-backed resource pack(s) to share with {}: {} (lowest to highest priority).",
                    packs.size,
                    remote,
                    packs.joinToString(", ") { it.first },
                )

                val validated = ArrayList<Triple<String, ByteArray, String>>()
                for ((fileName, data) in packs) {
                    if (data.isEmpty()) {
                        LOGGER.warn("Skipping '{}' - the file is completely empty, nothing to send.", fileName)
                        continue
                    }
                    if (data.size > MAX_PACK_BYTES) {
                        LOGGER.warn(
                            "Skipping '{}' - it's {} which is over our {} per-pack sharing limit.",
                            fileName,
                            humanSize(data.size.toLong()),
                            humanSize(MAX_PACK_BYTES),
                        )
                        SocialErrors.emit("'$fileName' is too large to share (max 50 MB) - skipping it")
                        continue
                    }
                    val hash = MessageDigest.getInstance("SHA-256").digest(data).toHex()
                    LOGGER.info("Hash of '{}' is {}.", fileName, hash)
                    validated += Triple(fileName, data, hash)
                }

                if (validated.isEmpty()) {
                    LOGGER.warn("None of your equipped resource packs were shareable after validation - nothing to send to {}.", remote)
                    return@launch
                }

                val combinedHash = validated.joinToString(";") { it.third }
                if (!force && lastSentHash[remote] == combinedHash) {
                    LOGGER.info("{} was already sent this exact set of packs (same combined hash) earlier this session - skipping the automatic resend to save bandwidth.", remote)
                    SocialErrors.emit("${displayName(remote)} already has these resource packs")
                    return@launch
                }
                if (force && lastSentHash[remote] == combinedHash) {
                    LOGGER.info("{} was already sent this exact set of packs, but this is a deliberate share request - sending them again anyway.", remote)
                }

                LOGGER.info("Making sure we have a resource-pack-sharing connection open with {}...", remote)
                bridge.acceptConnection(SOCKET_ID, remote)

                runCatching {
                    validated.forEachIndexed { index, (fileName, data, hash) ->
                        LOGGER.info("Sending pack {}/{}: '{}'...", index + 1, validated.size, fileName)
                        sendPack(bridge, remote, fileName, data, hash)
                    }
                }.onSuccess {
                    lastSentHash[remote] = combinedHash
                    LOGGER.info("Finished sending all {} resource pack(s) to {}. Hopefully they enjoy them!", validated.size, remote)
                    val packWord = if (validated.size == 1) "resource pack" else "${validated.size} resource packs"
                    SocialErrors.emit("Sent your $packWord to ${displayName(remote)}")
                }.onFailure {
                    LOGGER.error("The resource pack transfer to {} didn't fully make it - here's what went wrong:", remote, it)
                    SocialErrors.emit("Failed to send your resource pack(s)", it)
                }
            } finally {
                sendingTo.remove(remote)
                LOGGER.info("Done handling the resource-pack-share request for {} (either it finished, failed, or was skipped above).", remote)
            }
        }
    }

    private fun readEquippedPacks(): List<Pair<String, ByteArray>> {
        val minecraft = Minecraft.getInstance()
        val selected = minecraft.resourcePackRepository?.selectedIds
        if (selected == null) {
            LOGGER.warn("Minecraft's resource pack repository isn't available right now, so we can't tell what's equipped.")
            return emptyList()
        }
        LOGGER.info("Currently selected resource pack ids, in priority order: {}", selected)

        val fileBackedIds = selected.filter { it.startsWith("file/") }
        if (fileBackedIds.isEmpty()) {
            LOGGER.warn(
                "None of your currently-selected resource packs are backed by an actual file under resourcepacks/ " +
                    "(selected: {}) - packs bundled inside other mods can't be shared this way. " +
                    "Add and select a real resource pack file to share one.",
                selected,
            )
            return emptyList()
        }

        val packsDir = File(minecraft.gameDirectory, "resourcepacks")
        return fileBackedIds.mapNotNull { id ->
            val fileName = id.removePrefix("file/")
            val entry = File(packsDir, fileName)
            if (!entry.exists()) {
                LOGGER.warn(
                    "Expected to find '{}' at '{}', but nothing exists there. Did it get renamed or deleted after being selected? Skipping it.",
                    fileName,
                    entry.absolutePath,
                )
                return@mapNotNull null
            }

            if (entry.isFile) {
                LOGGER.info("'{}' is a single pack file ({}). Reading it into memory now...", fileName, humanSize(entry.length()))
                fileName to entry.readBytes()
            } else {
                LOGGER.info("'{}' is a folder-style resource pack. Zipping it up in memory before sending...", fileName)
                fileName to zipDirectory(entry)
            }
        }
    }

    private fun zipDirectory(dir: File): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        var fileCount = 0
        java.util.zip.ZipOutputStream(bytes).use { zip ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                zip.putNextEntry(java.util.zip.ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                fileCount++
            }
        }
        LOGGER.info("Zipped {} file(s) from '{}' into a {} archive.", fileCount, dir.name, humanSize(bytes.size().toLong()))
        return bytes.toByteArray()
    }

    private suspend fun sendPack(bridge: EosSdkBridge, remote: EosProductUserId, fileName: String, data: ByteArray, hashHex: String) {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data)
        val totalChunks = (data.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        val nameBytes = fileName.toByteArray(StandardCharsets.UTF_8).let {
            if (it.size > MAX_NAME_BYTES) it.copyOf(MAX_NAME_BYTES) else it
        }

        LOGGER.info(
            "Sending the 'here's what's coming' offer for '{}' to {}: {} in {} chunk(s) of up to {} bytes each (hash {}).",
            fileName,
            remote,
            humanSize(data.size.toLong()),
            totalChunks,
            CHUNK_SIZE,
            hashHex,
        )
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

        LOGGER.info("Streaming '{}' to {} now, {} chunk(s) total...", fileName, remote, totalChunks)
        for (index in 0 until totalChunks) {
            var waited = 0
            while (bridge.outboundQueueBytes(SOCKET_ID) >= QUEUE_PACING_THRESHOLD_BYTES) {
                if (waited == 0) {
                    LOGGER.info(
                        "Pausing briefly before chunk {}/{} to {}, the outbound queue is a bit full, letting it drain first.",
                        index + 1,
                        totalChunks,
                        remote,
                    )
                }
                delay(50)
                if (++waited > 1200) { // ~60s stuck with a full queue - the connection is dead, give up
                    LOGGER.error(
                        "Gave up waiting for the outbound queue to drain while sending '{}' to {}, it's been stuck full for about a minute, so the connection is probably dead.",
                        fileName,
                        remote,
                    )
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

            val chunksSent = index + 1
            if (chunksSent % PROGRESS_LOG_EVERY_CHUNKS == 0 || chunksSent == totalChunks) {
                val percent = (chunksSent * 100L / totalChunks)
                LOGGER.info("Sent {}/{} chunk(s) of '{}' to {} ({}% done)...", chunksSent, totalChunks, fileName, remote, percent)
            }
        }

        LOGGER.info("All chunks of '{}' sent to {} - wrapping up with a 'that's everything' frame.", fileName, remote)
        val complete = ByteBuffer.allocate(1).apply { put(TYPE_COMPLETE); flip() }
        bridge.sendPacket(SOCKET_ID, remote, complete)

        LOGGER.info("Finished sending resource pack '{}' ({}, {} chunks) to {}.", fileName, humanSize(data.size.toLong()), totalChunks, remote)
    }

    /** Returns true if this packet belonged to the resource-pack-share protocol (and was handled). */
    fun handlePacket(received: EosSdkBridge.Received): Boolean {
        if (received.socket != SOCKET_ID) return false

        runCatching {
            val buf = received.data
            if (!buf.hasRemaining()) {
                LOGGER.warn("Got a completely empty resource-pack-share packet from {} - ignoring it.", received.remote)
                return@runCatching
            }
            when (val type = buf.get()) {
                TYPE_OFFER -> handleOffer(received.remote, buf)
                TYPE_CHUNK -> handleChunk(received.remote, buf)
                TYPE_COMPLETE -> handleComplete(received.remote)
                TYPE_REJECT -> {
                    incoming.remove(received.remote)
                    LOGGER.info("{} declined the resource pack we offered them.", received.remote)
                }
                else -> LOGGER.warn(
                    "Got a resource-pack-share packet from {} with an unrecognized frame type ({}) - ignoring it. " +
                        "This usually means a version mismatch between clients.",
                    received.remote,
                    type,
                )
            }
        }.onFailure {
            LOGGER.warn(
                "Couldn't make sense of a resource-pack-share packet from {} - it was probably malformed or truncated. " +
                    "Dropping this frame and any transfer in progress from them so we don't end up with a corrupted file.",
                received.remote,
                it,
            )
            incoming.remove(received.remote)
        }
        return true
    }

    private fun handleOffer(remote: EosProductUserId, buf: ByteBuffer) {
        LOGGER.info("{} is offering to send us a resource pack. Reading the offer details...", remote)

        val nameLen = buf.int
        if (nameLen < 0 || nameLen > MAX_NAME_BYTES || nameLen > buf.remaining()) {
            LOGGER.warn(
                "Rejecting a resource pack offer from {} - the file name length ({}) doesn't make sense (must be 0-{} and fit in the packet).",
                remote,
                nameLen,
                MAX_NAME_BYTES,
            )
            return
        }
        val nameBytes = ByteArray(nameLen)
        buf.get(nameBytes)
        val fileName = String(nameBytes, StandardCharsets.UTF_8)

        if (buf.remaining() < 4 + 4 + 32) {
            LOGGER.warn("Rejecting a resource pack offer from {} for '{}' - the packet was cut off before we could read the size/hash.", remote, fileName)
            return
        }
        val totalBytes = buf.int
        val totalChunks = buf.int
        val sha256 = ByteArray(32)
        buf.get(sha256)

        if (totalBytes <= 0 || totalBytes > MAX_PACK_BYTES || totalChunks < 0) {
            LOGGER.warn(
                "Rejecting the resource pack offer '{}' from {} - the claimed size ({} bytes, {} chunks) is invalid or over our {} limit.",
                fileName,
                remote,
                totalBytes,
                totalChunks,
                humanSize(MAX_PACK_BYTES),
            )
            return
        }

        if (incoming.containsKey(remote)) {
            LOGGER.warn(
                "{} sent us a new resource pack offer ('{}') while we were still receiving a previous one from them - " +
                    "abandoning the old, incomplete transfer in favor of this new one.",
                remote,
                fileName,
            )
        }

        incoming[remote] = IncomingTransfer(fileName, totalBytes, totalChunks, sha256, ByteArray(totalBytes))
        LOGGER.info(
            "Accepted the offer - about to receive '{}' from {}: {} across {} chunk(s). Waiting for chunks to arrive...",
            fileName,
            remote,
            humanSize(totalBytes.toLong()),
            totalChunks,
        )
    }

    private fun handleChunk(remote: EosProductUserId, buf: ByteBuffer) {
        val transfer = incoming[remote]
        if (transfer == null) {
            LOGGER.warn("Got a resource pack chunk from {} but we don't have a transfer in progress for them (no offer received, or it already finished/failed) - ignoring it.", remote)
            return
        }
        if (buf.remaining() < 4) {
            LOGGER.warn("Discarding a resource pack chunk from {} for '{}' - it's too short to even contain a chunk index.", remote, transfer.fileName)
            return
        }
        val index = buf.int
        val remaining = buf.remaining()
        if (remaining <= 0) {
            LOGGER.warn("Got an empty (zero-byte) resource pack chunk #{} from {} for '{}' - skipping it.", index, remote, transfer.fileName)
            return
        }
        val start = index.toLong() * CHUNK_SIZE
        if (index < 0 || start < 0 || start + remaining > transfer.data.size) {
            LOGGER.warn(
                "Discarding an out-of-range resource pack chunk #{} from {} for '{}' - it would write outside the expected {} byte file. " +
                    "Either the sender's chunk math is wrong, or this is a stray/corrupted packet.",
                index,
                remote,
                transfer.fileName,
                transfer.totalBytes,
            )
            return
        }
        buf.get(transfer.data, start.toInt(), remaining)
        transfer.receivedBytes += remaining
        transfer.receivedChunks++

        if (transfer.receivedChunks % PROGRESS_LOG_EVERY_CHUNKS == 0) {
            val percent = if (transfer.totalChunks > 0) transfer.receivedChunks * 100L / transfer.totalChunks else 0
            LOGGER.info(
                "Received {}/{} chunk(s) of '{}' from {} so far ({}% done)...",
                transfer.receivedChunks,
                transfer.totalChunks,
                transfer.fileName,
                remote,
                percent,
            )
        }
    }

    private fun handleComplete(remote: EosProductUserId) {
        val transfer = incoming.remove(remote)
        if (transfer == null) {
            LOGGER.warn("{} told us a resource pack transfer finished, but we weren't tracking one from them - ignoring.", remote)
            return
        }

        LOGGER.info(
            "{} says the transfer of '{}' is complete. We received {}/{} bytes across {}/{} chunk(s) - double-checking before installing it.",
            remote,
            transfer.fileName,
            transfer.receivedBytes,
            transfer.totalBytes,
            transfer.receivedChunks,
            transfer.totalChunks,
        )

        if (transfer.receivedBytes != transfer.totalBytes) {
            LOGGER.error(
                "The resource pack transfer of '{}' from {} finished incomplete - we only got {} of the expected {} bytes. " +
                    "Discarding it rather than installing a truncated pack.",
                transfer.fileName,
                remote,
                humanSize(transfer.receivedBytes.toLong()),
                humanSize(transfer.totalBytes.toLong()),
            )
            SocialErrors.emit("Resource pack transfer from ${displayName(remote)} was incomplete")
            return
        }

        LOGGER.info("Byte count checks out for '{}' from {} - now verifying the SHA-256 hash matches what they promised...", transfer.fileName, remote)
        val digest = MessageDigest.getInstance("SHA-256").digest(transfer.data)
        if (!digest.contentEquals(transfer.sha256)) {
            LOGGER.error(
                "The resource pack '{}' from {} failed its integrity check - the hash we computed doesn't match the one they sent. " +
                    "Discarding it; it may have been corrupted in transit or something went wrong during chunk reassembly.",
                transfer.fileName,
                remote,
            )
            SocialErrors.emit("Received resource pack failed an integrity check")
            return
        }
        LOGGER.info("Hash matches - '{}' from {} arrived intact. Installing it now...", transfer.fileName, remote)

        PolyPlusClient.SCOPE.launch {
            runCatching { installPack(transfer.fileName, transfer.data) }
                .onSuccess {
                    LOGGER.info("Successfully installed resource pack '{}' from {}.", transfer.fileName, remote)
                    SocialErrors.emit("Received a resource pack from ${displayName(remote)} - check Options > Resource Packs")
                }
                .onFailure {
                    LOGGER.error("Received '{}' from {} intact, but installing it to disk failed.", transfer.fileName, remote, it)
                    SocialErrors.emit("Couldn't install the received resource pack", it)
                }
        }
    }

    private fun installPack(fileName: String, data: ByteArray) {
        val minecraft = Minecraft.getInstance()
        val packsDir = File(minecraft.gameDirectory, "resourcepacks")
        LOGGER.info("Making sure the resourcepacks folder exists at '{}'...", packsDir.absolutePath)
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
        if (target.name != safeName) {
            LOGGER.info("'{}' already exists in resourcepacks/, so we'll save this one as '{}' instead to avoid overwriting anything.", safeName, target.name)
        }
        check(target.canonicalFile.parentFile == packsDir.canonicalFile) { "Resulting pack path escaped resourcepacks/" }

        LOGGER.info("Writing '{}' ({}) to a temporary file first, then swapping it into place atomically...", target.name, humanSize(data.size.toLong()))
        val tmp = File(packsDir, "${target.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            LOGGER.error("Couldn't rename the temporary download to '{}' - giving up on installing this pack.", target.name)
            error("Couldn't finalize the downloaded resource pack file")
        }
        LOGGER.info("'{}' is now saved in resourcepacks/. Asking Minecraft to rescan the folder so it notices the new file...", target.name)

        val repository = minecraft.resourcePackRepository
        val rescanned = repository != null && listOf("reload", "scanPacks", "reloadPacks").any { name ->
            runCatching {
                repository.javaClass.getMethod(name).invoke(repository)
                LOGGER.info("Rescanned the resource pack repository via '{}()' - '{}' should now be selectable.", name, target.name)
                true
            }.getOrDefault(false)
        }

        if (!rescanned) {
            LOGGER.warn(
                "Couldn't find a resource pack rescan method on this Minecraft version, so '{}' was saved to resourcepacks/ " +
                    "but Minecraft doesn't know about it yet - you may need to reopen Options > Resource Packs for it to show up.",
                target.name,
            )
            return
        }

        val id = "file/${target.name}"

        val currentlySelected = runCatching { repository.selectedIds.toList() }.getOrNull()
        if (currentlySelected == null) {
            LOGGER.warn("Couldn't read the current resource pack selection, so '{}' was installed but not automatically equipped.", target.name)
            return
        }
        if (id in currentlySelected) {
            LOGGER.info("'{}' is already selected - nothing more to equip.", target.name)
            return
        }

        val updated = currentlySelected + id
        val selected = runCatching { repository.setSelected(updated) }
            .onFailure { LOGGER.warn("Failed to automatically select '{}' - it was installed but you'll need to enable it yourself.", target.name, it) }
            .isSuccess
        if (!selected) return
        LOGGER.info("Selected '{}' in the resource pack repository. Persisting this to your options and reloading resource packs...", target.name)

        val options = minecraft.options
        if (options != null) {
            runCatching {
                if (id !in options.resourcePacks) {
                    options.resourcePacks.add(id)
                    options.save()
                    LOGGER.info("Saved '{}' into your persisted resource pack options so it stays equipped next time too.", target.name)
                }
            }.onFailure { LOGGER.warn("Couldn't persist '{}' into your saved options - it's equipped for now, but may not stick after a restart.", target.name, it) }
        }

        runCatching { minecraft.reloadResourcePacks() }
            .onSuccess { LOGGER.info("Reloaded resource packs - '{}' is now active.", target.name) }
            .onFailure { LOGGER.warn("Equipped '{}' but reloading resource packs to actually apply it failed.", target.name, it) }
    }

    private fun displayName(remote: EosProductUserId): String =
        PlayerNamesRepository.names.value[remote.raw] ?: remote.raw.take(8)

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val FORBIDDEN_FILENAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', ' ')
}
