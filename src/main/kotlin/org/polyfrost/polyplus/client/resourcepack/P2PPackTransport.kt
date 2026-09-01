package org.polyfrost.polyplus.client.resourcepack

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge

object P2PPackTransport {
    private val LOGGER = LogManager.getLogger("PolyPlus/PackTransport")

    val SOCKET_ID: EosP2PSocketId = EosP2PSocketId("polyplus-resourcepack-share")

    private const val CHUNK_SIZE = 1100

    private const val QUEUE_PACING_THRESHOLD_BYTES = 96L * 1024

    private const val TYPE_REQUEST: Byte = 10
    private const val TYPE_HEAD: Byte = 11
    private const val TYPE_DATA: Byte = 12
    private const val TYPE_END: Byte = 13
    private const val TYPE_MISS: Byte = 14

    private const val SHA1_BYTES = 20

    @Volatile private var bridge: EosSdkBridge? = null

    private val nextRequestId = AtomicInteger()

    private val downloads = ConcurrentHashMap<EosProductUserId, Download>()

    fun install(bridge: EosSdkBridge) {
        this.bridge = bridge
        bridge.addConnectionRequestHandler(SOCKET_ID) { remote ->
            LOGGER.info("Accepting a resource-pack transfer connection from {}", remote)
            bridge.acceptConnection(SOCKET_ID, remote)
        }
    }

    fun uninstall() {
        bridge = null
    }

    class Download internal constructor(
        val remote: EosProductUserId,
        val sha1Hex: String,
        internal val requestId: Int,
    ) {
        private val chunks = LinkedBlockingQueue<ByteArray>()
        private val header = LinkedBlockingQueue<Int>()

        @Volatile internal var failed: String? = null
        @Volatile private var finished = false

        internal fun onHeader(totalBytes: Int) {
            header.offer(totalBytes)
        }

        internal fun onChunk(data: ByteArray) {
            chunks.offer(data)
        }

        internal fun onEnd() {
            finished = true
            chunks.offer(EMPTY)
        }

        internal fun onFailed(reason: String) {
            failed = reason
            finished = true
            header.offer(-1)
            chunks.offer(EMPTY)
        }

        fun awaitTotalBytes(timeoutMs: Long): Int? {
            val value = header.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
            return if (value < 0) null else value
        }

        fun nextChunk(timeoutMs: Long): ByteArray? {
            val chunk = chunks.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
            return if (chunk.isEmpty()) null else chunk
        }

        fun isComplete(): Boolean = finished && failed == null

        fun close() {
            downloads.remove(remote, this)
        }

        private companion object {
            val EMPTY = ByteArray(0)
        }
    }

    fun request(remote: EosProductUserId, sha1Hex: String): Download? {
        val bridge = this.bridge ?: run {
            LOGGER.warn("Asked to fetch a resource pack from {} before the EOS bridge was installed", remote)
            return null
        }

        val sha1 = sha1Hex.fromHex() ?: run {
            LOGGER.warn("Refusing to request a resource pack with a malformed hash '{}'", sha1Hex)
            return null
        }

        val download = Download(remote, sha1Hex, nextRequestId.incrementAndGet())
        downloads.put(remote, download)?.onFailed("Superseded by a newer request")

        bridge.acceptConnection(SOCKET_ID, remote)
        val frame = ByteBuffer.allocate(1 + 4 + SHA1_BYTES).apply {
            put(TYPE_REQUEST)
            putInt(download.requestId)
            put(sha1)
            flip()
        }
        bridge.sendPacket(SOCKET_ID, remote, frame)
        LOGGER.info("Requested resource pack {} from {} (request {})", sha1Hex, remote, download.requestId)
        return download
    }

    private val serving = ConcurrentHashMap<EosProductUserId, Job>()

    private fun serve(bridge: EosSdkBridge, remote: EosProductUserId, requestId: Int, requestedSha1: ByteArray) {
        val prepared = HostSharedPack.packFor(requestedSha1)
        if (prepared == null) {
            LOGGER.warn("{} asked for resource pack {}, which we aren't sharing", remote, requestedSha1.toHex())
            bridge.sendPacket(SOCKET_ID, remote, frame(TYPE_MISS, requestId))
            return
        }

        val previous = serving[remote]
        val job = PolyPlusClient.SCOPE.launch {
            previous?.cancelAndJoin()
            try {
                sendPack(bridge, remote, requestId, prepared)
            } catch (e: CancellationException) {
                LOGGER.info("Restarting the resource pack transfer to {}", remote)
                throw e
            } catch (e: Throwable) {
                LOGGER.error("Resource pack transfer to {} failed", remote, e)
            }
        }
        serving[remote] = job
        job.invokeOnCompletion { serving.remove(remote, job) }
    }

    private suspend fun sendPack(
        bridge: EosSdkBridge,
        remote: EosProductUserId,
        requestId: Int,
        prepared: SharedResourcePack.Prepared,
    ) {
        val data = prepared.bytes
        val totalChunks = (data.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        LOGGER.info(
            "Streaming '{}' ({}) to {} in {} chunk(s)",
            prepared.name,
            SharedResourcePack.humanSize(data.size.toLong()),
            remote,
            totalChunks,
        )

        val head = ByteBuffer.allocate(1 + 4 + 4).apply {
            put(TYPE_HEAD)
            putInt(requestId)
            putInt(data.size)
            flip()
        }
        bridge.sendPacket(SOCKET_ID, remote, head)

        for (index in 0 until totalChunks) {
            awaitQueueDrain(bridge, remote, index, totalChunks)

            val start = index * CHUNK_SIZE
            val end = minOf(start + CHUNK_SIZE, data.size)
            val chunk = ByteBuffer.allocate(1 + 4 + 4 + (end - start)).apply {
                put(TYPE_DATA)
                putInt(requestId)
                putInt(index)
                put(data, start, end - start)
                flip()
            }
            bridge.sendPacket(SOCKET_ID, remote, chunk)
        }

        bridge.sendPacket(SOCKET_ID, remote, frame(TYPE_END, requestId))
        LOGGER.info("Finished streaming '{}' to {}", prepared.name, remote)
    }

    private suspend fun awaitQueueDrain(bridge: EosSdkBridge, remote: EosProductUserId, index: Int, totalChunks: Int) {
        currentCoroutineContext().ensureActive()
        var waited = 0
        while (bridge.outboundQueueBytes(SOCKET_ID) >= QUEUE_PACING_THRESHOLD_BYTES) {
            delay(20)
            if (++waited > 3000) {
                error("Outbound queue stayed full while sending chunk ${index + 1}/$totalChunks to $remote")
            }
        }
    }

    fun handlePacket(received: EosSdkBridge.Received): Boolean {
        if (received.socket != SOCKET_ID) return false
        val bridge = this.bridge ?: return true

        runCatching {
            val buf = received.data
            if (!buf.hasRemaining()) return@runCatching

            when (val type = buf.get()) {
                TYPE_REQUEST -> {
                    if (buf.remaining() < 4 + SHA1_BYTES) return@runCatching
                    val requestId = buf.int
                    val sha1 = ByteArray(SHA1_BYTES).also(buf::get)
                    serve(bridge, received.remote, requestId, sha1)
                }
                TYPE_HEAD -> {
                    if (buf.remaining() < 4 + 4) return@runCatching
                    val download = matching(received.remote, buf.int) ?: return@runCatching
                    download.onHeader(buf.int)
                }
                TYPE_DATA -> {
                    if (buf.remaining() < 4 + 4) return@runCatching
                    val download = matching(received.remote, buf.int) ?: return@runCatching
                    buf.int
                    download.onChunk(ByteArray(buf.remaining()).also(buf::get))
                }
                TYPE_END -> {
                    if (buf.remaining() < 4) return@runCatching
                    matching(received.remote, buf.int)?.onEnd()
                }
                TYPE_MISS -> {
                    if (buf.remaining() < 4) return@runCatching
                    matching(received.remote, buf.int)?.onFailed("Host is no longer sharing that resource pack")
                }
                else -> LOGGER.warn("Unknown pack-transfer frame type {} from {}", type, received.remote)
            }
        }.onFailure {
            LOGGER.warn("Malformed pack-transfer packet from {}", received.remote, it)
            downloads[received.remote]?.onFailed("Malformed transfer packet")
        }
        return true
    }

    private fun matching(remote: EosProductUserId, requestId: Int): Download? =
        downloads[remote]?.takeIf { it.requestId == requestId }

    private fun frame(type: Byte, requestId: Int): ByteBuffer =
        ByteBuffer.allocate(1 + 4).apply {
            put(type)
            putInt(requestId)
            flip()
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length != SHA1_BYTES * 2) return null
        return runCatching {
            ByteArray(SHA1_BYTES) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }
}
