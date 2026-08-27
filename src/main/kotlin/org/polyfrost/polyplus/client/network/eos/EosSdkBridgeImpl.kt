package org.polyfrost.polyplus.client.network.eos

import gg.sona.eos.Eos
import gg.sona.eos.EosClientCredentials
import gg.sona.eos.EosInitializeOptions
import gg.sona.eos.EosPlatform
import gg.sona.eos.EosPlatformFlags
import gg.sona.eos.EosPlatformOptions
import gg.sona.eos.EosResult
import gg.sona.eos.common.EosExternalCredentialType
import gg.sona.eos.common.EosLoginStatus
import gg.sona.eos.common.ProductUserId as SdkProductUserId
import gg.sona.eos.logging.EosLogCategory
import gg.sona.eos.logging.EosLogLevel
import gg.sona.eos.logging.EosLogging
import gg.sona.eos.p2p.EosNetworkConnectionType
import gg.sona.eos.p2p.EosP2PSocketId as SdkSocketId
import gg.sona.eos.p2p.EosPacketReliability
import gg.sona.eos.p2p.EosRelayControl
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.p2p.P2PChannelRegistry

class EosSdkBridgeImpl : EosSdkBridge {
    private val logger = LogManager.getLogger()

    @Volatile private var platform: EosPlatform? = null
    @Volatile private var tickThread: Thread? = null
    @Volatile private var running = false
    private val starting = AtomicBoolean(false)

    private val pendingCalls = ConcurrentLinkedQueue<() -> Unit>()
    private val isOnTickThread: Boolean get() = Thread.currentThread() === tickThread

    private var inboundHandler: ((EosSdkBridge.Received) -> Unit)? = null
    @Volatile private var loginLostHandler: (() -> Unit)? = null

    private data class StateHandlerEntry(val handle: Long, val callback: (EosSdkBridge.ConnectionStateEvent) -> Unit)

    private val requestHandlers = ConcurrentHashMap<String, (EosProductUserId) -> Unit>()
    private val stateHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<StateHandlerEntry>>()
    private val nextHandle = AtomicLong(1)

    @Volatile override var localUser: EosProductUserId? = null
        private set

    private var lastLoggedReceiveFailure: Pair<EosProductUserId, String>? = null

    @Volatile private var lastTickMs = 0L

    override val isLoggedIn: Boolean get() = localUser != null

    override fun isStalled(): Boolean =
        EosTickHealth.isStalled(running && platform != null, lastTickMs, EosTickHealth.monotonicMs())

    companion object {
        private const val STARTUP_TIMEOUT_SECONDS = 10L
        private const val EOS_CALL_TIMEOUT_SECONDS = 30L

        private const val MIN_TICK_SLEEP_NANOS = 1_000_000L
        private const val MAX_TICK_SLEEP_NANOS = 8_000_000L
        private const val ACTIVITY_BACKOFF_AFTER_IDLE_TICKS = 4

        private const val MAX_PACKETS_PER_TICK = 4096
        private const val CONGESTION_CHECK_EVERY_TICKS = 64
    }

    fun initialize() {
        if (platform != null || !starting.compareAndSet(false, true)) return

        val ready = CountDownLatch(1)
        val worker = Thread({
            runCatching { start() }.onFailure { logger.error("Could not start EOS! P2P hosting/joining will be unavailable", it) }
            ready.countDown()
            if (platform != null) tickLoop()
        }, EosTickHealth.THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
        }
        tickThread = worker
        running = true
        worker.start()

        if (!ready.await(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            logger.warn("EOS is still starting after {}s, continuing without it for now", STARTUP_TIMEOUT_SECONDS)
        }
    }

    private fun start() {
        val init = Eos.initialize(EosInitializeOptions.create(EosConstants.PRODUCT_NAME, EosConstants.PRODUCT_VERSION))
        if (init != EosResult.Success && init != EosResult.AlreadyConfigured) {
            logger.error("EOS::Initialize failed: {}", init)
            running = false
            return
        }

        EosLogging.setCallback { msg -> onLog(msg.category, msg.level, msg.message) }
        EosLogging.setLogLevel(EosLogCategory.AllCategories, EosLogLevel.Info)

        val options = EosPlatformOptions.create(
            productId = EosConstants.PRODUCT_ID,
            sandboxId = EosConstants.SANDBOX_ID,
            deploymentId = EosConstants.DEPLOYMENT_ID,
            clientCredentials = EosClientCredentials.of(EosConstants.CLIENT_ID, EosConstants.CLIENT_SECRET),
        ).apply {
            flags = EosPlatformFlags.DisableOverlay or EosPlatformFlags.DisableSocialOverlay
        }

        val created = runCatching { Eos.createPlatform(options) }
            .onFailure { logger.error("Could not create the EOS platform", it) }
            .getOrNull()
        if (created == null) {
            running = false
            return
        }
        platform = created
    }

    @Volatile private var hookedUser: EosProductUserId? = null
    private val loginWatchInstalled = AtomicBoolean(false)

    private fun installP2PHooksOnce(local: SdkProductUserId) {
        val user = EosProductUserId(local.toStringValue())
        if (hookedUser == user) return
        hookedUser = user
        val p2p = requireNotNull(platform).p2p

        p2p.setPacketQueueSize(DEFAULT_QUEUE_BYTES, DEFAULT_QUEUE_BYTES)

        p2p.addNotifyPeerConnectionRequest(local, null) { info ->
            requestHandlers[info.socketId.name]?.invoke(EosProductUserId(info.remoteUserId.toStringValue()))
        }
        p2p.addNotifyPeerConnectionEstablished(local, null) { info ->
            dispatchState(info.socketId.name, info.remoteUserId) {
                EosSdkBridge.ConnectionStateEvent.Established(it, info.networkType == EosNetworkConnectionType.DirectConnection)
            }
        }
        p2p.addNotifyPeerConnectionInterrupted(local, null) { info ->
            dispatchState(info.socketId.name, info.remoteUserId) { EosSdkBridge.ConnectionStateEvent.Interrupted(it) }
        }
        p2p.addNotifyPeerConnectionClosed(local, null) { info ->
            dispatchState(info.socketId.name, info.remoteUserId) {
                EosSdkBridge.ConnectionStateEvent.Closed(it, info.reason.toString())
            }
        }
    }

    private fun installLoginWatchOnce() {
        if (!loginWatchInstalled.compareAndSet(false, true)) return
        requireNotNull(platform).connect.addNotifyLoginStatusChanged { info ->
            if (info.currentStatus == EosLoginStatus.LoggedIn) return@addNotifyLoginStatusChanged
            logger.warn("EOS Connect login lost ({}), re-authenticating", info.currentStatus)
            localUser = null
            lastLoggedReceiveFailure = null
            loginLostHandler?.let { handler -> runCatching(handler).onFailure { logger.error("EOS re-authentication hook failed", it) } }
        }
    }

    private fun dispatchState(
        socket: String,
        sdkRemote: SdkProductUserId,
        make: (EosProductUserId) -> EosSdkBridge.ConnectionStateEvent,
    ) {
        val handlers = stateHandlers[socket] ?: return
        val event = make(EosProductUserId(sdkRemote.toStringValue()))
        for (entry in handlers) entry.callback(event)
    }

    private fun tickLoop() {
        var idleTicks = 0
        var tick = 0L

        while (running) {
            lastTickMs = EosTickHealth.monotonicMs()
            runCatching { pump() }.onFailure { logger.error("An EOS tick failed", it) }

            tick++
            if (tick % CONGESTION_CHECK_EVERY_TICKS == 0L) {
                runCatching { checkCongestion() }.onFailure { logger.warn("EOS congestion check failed", it) }
            }

            val hadWork = runCatching { drainInboundPackets() }
                .onFailure { logger.error("Draining inbound EOS packets failed", it) }
                .getOrDefault(false)
            idleTicks = if (hadWork) 0 else idleTicks + 1

            park(idleTicks)
        }
        teardown()
    }

    private fun pump() {
        var call = pendingCalls.poll()
        while (call != null) {
            runCatching(call).onFailure { logger.warn("A queued EOS call failed", it) }
            call = pendingCalls.poll()
        }
        platform?.tick()
    }

    private fun drainInboundPackets(): Boolean {
        val platform = this.platform ?: return false
        val local = localUser ?: return false
        val handler = inboundHandler ?: return false
        val sdkLocal = SdkProductUserId.fromString(local.raw)

        var budget = MAX_PACKETS_PER_TICK
        var delivered = false
        while (budget-- > 0) {
            val packet = runCatching { platform.p2p.receivePacket(sdkLocal) }
                .onFailure { error ->
                    val failure = local to error.message.orEmpty()
                    if (lastLoggedReceiveFailure != failure) {
                        lastLoggedReceiveFailure = failure
                        logger.error("EOS_P2P_ReceivePacket failed; suppressing repeats of this same failure", error)
                    }
                }
                .getOrNull() ?: break

            lastLoggedReceiveFailure = null
            delivered = true
            runCatching {
                handler(
                    EosSdkBridge.Received(
                        socket = EosP2PSocketId(packet.socketId.name),
                        remote = EosProductUserId(packet.remoteUserId.toStringValue()),
                        data = ByteBuffer.wrap(packet.data),
                    ),
                )
            }.onFailure { logger.error("Inbound EOS packet handler failed on socket '{}'", packet.socketId.name, it) }
        }
        return delivered
    }

    private fun checkCongestion() {
        P2PChannelRegistry.allChannels().forEach { channel ->
            val socket = channel.currentSocket() ?: return@forEach
            channel.updateCongestion(outboundQueueBytes(socket) >= CONGESTION_THRESHOLD_BYTES)
        }
    }

    private fun park(idleTicks: Int) {
        if (idleTicks < ACTIVITY_BACKOFF_AFTER_IDLE_TICKS) {
            Thread.onSpinWait()
            return
        }
        val steps = (idleTicks - ACTIVITY_BACKOFF_AFTER_IDLE_TICKS).coerceAtMost(10)
        val nanos = (MIN_TICK_SLEEP_NANOS shl steps).coerceAtMost(MAX_TICK_SLEEP_NANOS)
        java.util.concurrent.locks.LockSupport.parkNanos(nanos)
    }

    private fun teardown() {
        val platform = this.platform ?: return
        this.platform = null
        localUser = null
        hookedUser = null
        loginWatchInstalled.set(false)
        lastLoggedReceiveFailure = null
        runCatching { platform.close() }.onFailure { logger.warn("Closing the EOS platform failed", it) }
        runCatching { Eos.shutdown() }.onFailure { logger.warn("Shutting the EOS SDK down failed", it) }
    }

    private fun <T> call(block: () -> T): java.util.concurrent.CompletableFuture<T> {
        val future = java.util.concurrent.CompletableFuture<T>()
        if (isOnTickThread) {
            try {
                future.complete(block())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
            return future
        }
        pendingCalls.add {
            try {
                future.complete(block())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun post(block: () -> Unit) {
        if (isOnTickThread) {
            runCatching(block).onFailure { logger.warn("A posted EOS call failed", it) }
        } else {
            pendingCalls.add(block)
        }
    }

    private fun onLog(category: String, level: EosLogLevel, message: String) {
        val logName = category.removePrefix("LogEOS").ifBlank { "polyplus/eos" }.let { "polyplus/eos/$it" }
        val log = LogManager.getLogger(logName)
        when (level) {
            EosLogLevel.Fatal, EosLogLevel.Error -> log.error(message)
            EosLogLevel.Warning -> log.warn(message)
            else -> log.info(message)
        }
    }

    override suspend fun connectLogin(openIdAccessToken: String): Result<EosProductUserId> = runCatching {
        check(platform != null) { "EOS SDK has not started yet" }

        val loginResult = suspendCoroutine<gg.sona.eos.connect.LoginResult> { cont ->
            call {
                requireNotNull(platform).connect.login(EosExternalCredentialType.OpenIdAccessToken, openIdAccessToken)
                    .orTimeout(EOS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete { info, error ->
                        if (error != null) cont.resumeWithException(error) else cont.resume(info)
                    }
            }
        }

        val userId = if (loginResult.result == EosResult.Success) {
            loginResult.localUserId
        } else {
            check(loginResult.result == EosResult.InvalidUser) { "EOS::Connect::Login failed: ${loginResult.result}" }
            logger.info("No EOS user mapped to this Poly+ account yet, creating one")

            val createResult = suspendCoroutine<gg.sona.eos.connect.LoginResult> { cont ->
                call {
                    requireNotNull(platform).connect.createUser(loginResult.continuanceToken)
                        .orTimeout(EOS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .whenComplete { info, error ->
                            if (error != null) cont.resumeWithException(error) else cont.resume(info)
                        }
                }
            }
            check(createResult.result == EosResult.Success) { "EOS::Connect::CreateUser failed: ${createResult.result}" }
            createResult.localUserId
        }

        val resolved = EosProductUserId(userId.toStringValue())
        localUser = resolved
        post {
            installP2PHooksOnce(userId)
            installLoginWatchOnce()
        }
        resolved
    }

    override fun setRelayControl(forceRelays: Boolean) = post {
        platform?.p2p?.setRelayControl(if (forceRelays) EosRelayControl.ForceRelays else EosRelayControl.AllowRelays)
    }

    override suspend fun queryNatType(): Result<EosSdkBridge.NatType> = runCatching {
        val result = suspendCoroutine<String> { cont ->
            call {
                requireNotNull(platform).p2p.queryNATType()
                    .orTimeout(EOS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete { info, error ->
                        if (error != null) cont.resumeWithException(error) else cont.resume(info.natType.toString())
                    }
            }
        }
        runCatching { EosSdkBridge.NatType.valueOf(result) }.getOrDefault(EosSdkBridge.NatType.Unknown)
    }

    override fun setPacketQueueSize(inboundBytes: Long, outboundBytes: Long) = post {
        platform?.p2p?.setPacketQueueSize(inboundBytes, outboundBytes)
    }

    override fun outboundQueueBytes(socket: EosP2PSocketId): Long {
        return runCatching {
            platform?.p2p?.getPacketQueueInfo()?.outgoingCurrentSizeBytes ?: 0L
        }.getOrDefault(0L)
    }

    override fun sendPacket(
        socket: EosP2PSocketId,
        remote: EosProductUserId,
        data: ByteBuffer,
        reliability: EosSdkBridge.PacketReliability,
    ) {
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        val sdkReliability = when (reliability) {
            EosSdkBridge.PacketReliability.UnreliableUnordered -> EosPacketReliability.UnreliableUnordered
            EosSdkBridge.PacketReliability.ReliableUnordered -> EosPacketReliability.ReliableUnordered
            EosSdkBridge.PacketReliability.ReliableOrdered -> EosPacketReliability.ReliableOrdered
        }

        post {
            val local = localUser
            if (local == null) {
                logger.warn(
                    "Dropping outbound packet to {} on '{}': not logged into EOS Connect yet " +
                        "(this packet is lost, nothing will retry it)",
                    remote,
                    socket,
                )
                return@post
            }
            val result = requireNotNull(platform).p2p.sendPacket(
                localUserId = SdkProductUserId.fromString(local.raw),
                remoteUserId = SdkProductUserId.fromString(remote.raw),
                socketId = SdkSocketId(socket.name),
                channel = 0,
                data = bytes,
                reliability = sdkReliability,
            )
            if (result != EosResult.Success) {
                logger.warn("sendPacket to {} on '{}' -> {}", remote, socket, result)
            }
        }
    }

    override fun setInboundPacketHandler(handler: (EosSdkBridge.Received) -> Unit) {
        inboundHandler = handler
    }

    override fun setLoginLostHandler(handler: () -> Unit) {
        loginLostHandler = handler
    }

    override fun addConnectionRequestHandler(
        socket: EosP2PSocketId,
        handler: (remote: EosProductUserId) -> Unit,
    ): EosNotificationHandle {
        requestHandlers[socket.name] = handler
        return EosNotificationHandle(nextHandle.getAndIncrement())
    }

    override fun acceptConnection(socket: EosP2PSocketId, remote: EosProductUserId) = post {
        val local = localUser
        if (local == null) {
            logger.warn("Cannot accept connection from {} on '{}': not logged into EOS Connect yet", remote, socket)
            return@post
        }
        platform?.p2p?.acceptConnection(
            SdkProductUserId.fromString(local.raw),
            SdkProductUserId.fromString(remote.raw),
            SdkSocketId(socket.name),
        )
    }

    override fun closeConnection(socket: EosP2PSocketId, remote: EosProductUserId?) = post {
        val local = localUser
        if (local == null) {
            logger.warn("Cannot close connection on '{}': not logged into EOS Connect yet", socket)
            return@post
        }
        val sdkSocket = SdkSocketId(socket.name)
        val sdkLocal = SdkProductUserId.fromString(local.raw)
        if (remote != null) {
            platform?.p2p?.closeConnection(sdkLocal, SdkProductUserId.fromString(remote.raw), sdkSocket)
        } else {
            platform?.p2p?.closeConnections(sdkLocal, sdkSocket)
            requestHandlers.remove(socket.name)
            stateHandlers.remove(socket.name)
        }
    }

    override fun addConnectionStateHandler(
        socket: EosP2PSocketId,
        handler: (event: EosSdkBridge.ConnectionStateEvent) -> Unit,
    ): EosNotificationHandle {
        val handle = nextHandle.getAndIncrement()
        stateHandlers.getOrPut(socket.name) { CopyOnWriteArrayList() }.add(StateHandlerEntry(handle, handler))
        return EosNotificationHandle(handle)
    }

    override fun removeNotificationHandler(handle: EosNotificationHandle) {
        for (list in stateHandlers.values) {
            list.removeIf { it.handle == handle.raw }
        }
    }
}

private const val DEFAULT_QUEUE_BYTES = 16L * 1024 * 1024
private const val CONGESTION_THRESHOLD_BYTES = 4L * 1024 * 1024
