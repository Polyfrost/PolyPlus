package org.polyfrost.polyplus.client.party

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.network.http.PartyApi
import org.polyfrost.polyplus.client.network.p2p.EosStatus
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object SceneTransfer {
    private val LOGGER = LogManager.getLogger()

    val SOCKET = EosP2PSocketId("polyplus-party-scene")

    private const val VERSION: Byte = 1
    private const val HELLO: Byte = 0x01
    private const val GET: Byte = 0x02
    private const val INFO: Byte = 0x03
    private const val CHUNK: Byte = 0x04
    private const val MISSING: Byte = 0x05
    private const val CHUNK_BYTES = 1000
    const val MAX_BYTES = 1024 * 1024
    private const val HASH_BYTES = 32
    private const val CACHE_BYTES = 32L * 1024 * 1024
    private const val FETCH_TIMEOUT_MS = 30_000L
    private const val RETRY_MS = 60_000L
    private const val PASS_MARGIN_MS = 60_000L
    private const val VERIFY_WAITS = 30
    private const val VERIFY_WAIT_MS = 100L

    private class Download(val leader: EosProductUserId, val hash: String) {
        var size = -1
        var data: ByteArray? = null
        var received = 0
        val chunks = HashSet<Int>()
    }

    private val verified = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()

    @Volatile private var bridge: EosSdkBridge? = null
    @Volatile private var download: Download? = null
    @Volatile private var verifiedParty: String? = null
    @Volatile private var pass: String? = null
    @Volatile private var passParty: String? = null
    @Volatile private var passExpires = 0L
    private val serving = AtomicInteger()
    @Volatile private var relayHeldUntil = 0L

    private val cacheDir: Path by lazy { FabricLoader.getInstance().gameDir.resolve("polyplus").resolve("lobby").resolve("party-scenes") }

    private fun validHash(hash: String): Boolean = hash.length == HASH_BYTES * 2 && hash.all { it in '0'..'9' || it in 'a'..'f' }

    fun cached(hash: String): Path? {
        if (!validHash(hash)) return null
        val path = cacheDir.resolve("$hash.nbt.gz")
        return path.takeIf { Files.isRegularFile(it) }
    }

    fun tick() {
        val current = P2PSessionManager.activeBridge
        if (current !== bridge) {
            bridge = current
            verified.clear()
            current?.addConnectionRequestHandler(SOCKET, ::onConnectionRequest)
        }
        val partyId = PartyRepository.party.value?.id
        if (partyId != verifiedParty) {
            verifiedParty = partyId
            verified.clear()
            pass = null
            passExpires = 0L
        }
        if (download == null && serving.get() == 0 && System.currentTimeMillis() > relayHeldUntil) P2PSessionManager.setSceneRelay(false)
    }

    private fun memberPuids(): Set<String> =
        PartyRepository.others().mapNotNullTo(HashSet()) { it.eosProductUserId }

    private fun onConnectionRequest(remote: EosProductUserId) {
        val bridge = bridge ?: return
        if (remote.raw !in memberPuids() && download?.leader != remote) {
            LOGGER.info("Ignoring a party scene connection from {}", remote)
            return
        }
        relayHeldUntil = System.currentTimeMillis() + FETCH_TIMEOUT_MS
        P2PSessionManager.setSceneRelay(PolyPlusConfig.voiceHideIp)
        bridge.acceptConnection(SOCKET, remote)
    }

    fun fetch(leaderPuid: String, hash: String) {
        if (download != null || !validHash(hash) || cached(hash) != null) return
        if (P2PSessionManager.status.value != EosStatus.Ready) return
        val last = failedAt[hash]
        if (last != null && System.currentTimeMillis() - last < RETRY_MS) return
        val bridge = bridge ?: return
        val party = PartyRepository.party.value ?: return
        val leader = EosProductUserId(leaderPuid)
        val job = Download(leader, hash)
        download = job
        PolyPlusClient.SCOPE.launch {
            val token = ownPass(party.id) ?: return@launch fail(job, "no party pass")
            if (PartyRepository.party.value?.id != party.id) return@launch fail(job, "left the party")
            P2PSessionManager.setSceneRelay(PolyPlusConfig.voiceHideIp)
            bridge.acceptConnection(SOCKET, leader)
            bridge.sendPacket(SOCKET, leader, frame(HELLO, token.toByteArray(Charsets.UTF_8)), EosSdkBridge.PacketReliability.ReliableOrdered)
            bridge.sendPacket(SOCKET, leader, frame(GET, hex(hash)), EosSdkBridge.PacketReliability.ReliableOrdered)
            delay(FETCH_TIMEOUT_MS)
            if (download === job) fail(job, "timed out")
        }
    }

    private suspend fun ownPass(partyId: String): String? {
        val current = pass
        if (current != null && passParty == partyId && passExpires - System.currentTimeMillis() > PASS_MARGIN_MS) return current
        val fresh = PartyApi.voicePass().getOrNull() ?: return null
        if (PartyRepository.party.value?.id != partyId) return null
        pass = fresh.pass
        passParty = partyId
        passExpires = VoicePassVerifier.expiresAt(fresh.pass) ?: (System.currentTimeMillis() + PASS_MARGIN_MS * 2)
        return fresh.pass
    }

    private fun fail(job: Download, reason: String) {
        if (download !== job) return
        download = null
        failedAt[job.hash] = System.currentTimeMillis()
        LOGGER.warn("Could not get the party scene {}: {}", job.hash.take(12), reason)
        bridge?.closeConnection(SOCKET, job.leader)
    }

    fun handlePacket(received: EosSdkBridge.Received): Boolean {
        if (received.socket != SOCKET) return false
        val data = received.data
        if (data.remaining() < 2) return true
        val type = data.get()
        if (data.get() != VERSION) return true
        when (type) {
            HELLO -> onHello(received.remote, ByteArray(data.remaining()).also(data::get))
            GET -> if (data.remaining() >= HASH_BYTES) onGet(received.remote, ByteArray(HASH_BYTES).also(data::get))
            INFO -> if (data.remaining() >= 6) onInfo(received.remote, data.int, data.short.toInt() and 0xFFFF)
            CHUNK -> if (data.remaining() >= 2) onChunk(received.remote, data.short.toInt() and 0xFFFF, data)
            MISSING -> download?.takeIf { it.leader == received.remote }?.let { fail(it, "the leader no longer shares it") }
        }
        return true
    }

    private fun onHello(remote: EosProductUserId, body: ByteArray) {
        val party = PartyRepository.party.value ?: return
        if (!PartyRepository.isLeader(party) || remote.raw !in memberPuids()) return
        val token = String(body, Charsets.UTF_8)
        PolyPlusClient.SCOPE.launch {
            if (VoicePassVerifier.verify(token, party.id, remote.raw)) verified.add(remote.raw) else LOGGER.warn("Rejected a party scene pass from {}", remote)
        }
    }

    private fun onGet(remote: EosProductUserId, hash: ByteArray) {
        val bridge = bridge ?: return
        val wanted = HexFormat.of().formatHex(hash)
        serving.incrementAndGet()
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            var waits = 0
            while (remote.raw !in verified && waits++ < VERIFY_WAITS) delay(VERIFY_WAIT_MS)
            val shared = PartyScene.sharedCapture()
            val party = PartyRepository.party.value
            val allowed = remote.raw in verified && remote.raw in memberPuids() && party != null && PartyRepository.isLeader(party)
            val bytes = if (allowed && shared != null && shared.first == wanted) {
                runCatching { Files.readAllBytes(shared.second) }.getOrNull()?.takeIf { it.size <= MAX_BYTES }
            } else {
                null
            }
            if (bytes == null) {
                bridge.sendPacket(SOCKET, remote, frame(MISSING, ByteArray(0)), EosSdkBridge.PacketReliability.ReliableOrdered)
                return@launch
            }
            val count = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
            val info = ByteBuffer.allocate(6).putInt(bytes.size).putShort(count.toShort()).array()
            bridge.sendPacket(SOCKET, remote, frame(INFO, info), EosSdkBridge.PacketReliability.ReliableOrdered)
            for (index in 0 until count) {
                val start = index * CHUNK_BYTES
                val end = minOf(bytes.size, start + CHUNK_BYTES)
                val body = ByteBuffer.allocate(2 + end - start).putShort(index.toShort()).put(bytes, start, end - start).array()
                bridge.sendPacket(SOCKET, remote, frame(CHUNK, body), EosSdkBridge.PacketReliability.ReliableOrdered)
            }
            LOGGER.info("Sent the party scene to {} ({} bytes)", remote, bytes.size)
        }.invokeOnCompletion { serving.decrementAndGet() }
    }

    private fun onInfo(remote: EosProductUserId, size: Int, count: Int) {
        val job = download?.takeIf { it.leader == remote } ?: return
        if (size <= 0 || size > MAX_BYTES || count != (size + CHUNK_BYTES - 1) / CHUNK_BYTES) return fail(job, "bad size $size")
        job.size = size
        job.data = ByteArray(size)
    }

    private fun onChunk(remote: EosProductUserId, index: Int, body: ByteBuffer) {
        val job = download?.takeIf { it.leader == remote } ?: return
        val buffer = job.data ?: return
        val start = index * CHUNK_BYTES
        val length = body.remaining()
        if (start + length > buffer.size || length > CHUNK_BYTES || !job.chunks.add(index)) return
        body.get(buffer, start, length)
        job.received += length
        if (job.received < buffer.size) return
        download = null
        bridge?.closeConnection(SOCKET, remote)
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) { store(job, buffer) }
    }

    private fun store(job: Download, bytes: ByteArray) {
        if (PartyScene.sha256(bytes) != job.hash) {
            failedAt[job.hash] = System.currentTimeMillis()
            LOGGER.warn("The party scene {} did not match its hash", job.hash.take(12))
            return
        }
        runCatching {
            Files.createDirectories(cacheDir)
            val target = cacheDir.resolve("${job.hash}.nbt.gz")
            val temporary = cacheDir.resolve("${job.hash}.tmp")
            Files.write(temporary, bytes)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()))
            trim()
            LOGGER.info("Cached the party scene {} ({} bytes)", job.hash.take(12), bytes.size)
        }.onFailure {
            failedAt[job.hash] = System.currentTimeMillis()
            LOGGER.warn("Could not cache the party scene", it)
        }
    }

    fun touch(path: Path) {
        runCatching { Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis())) }
    }

    private fun trim() {
        val files = Files.list(cacheDir).use { stream -> stream.filter { it.toString().endsWith(".nbt.gz") }.toList() }
            .sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
        var total = 0L
        for (file in files) {
            total += Files.size(file)
            if (total > CACHE_BYTES) Files.deleteIfExists(file)
        }
    }

    private fun hex(value: String): ByteArray = HexFormat.of().parseHex(value)

    private fun frame(type: Byte, body: ByteArray): ByteBuffer {
        val buffer = ByteBuffer.allocate(2 + body.size)
        buffer.put(type).put(VERSION).put(body)
        buffer.flip()
        return buffer
    }
}
