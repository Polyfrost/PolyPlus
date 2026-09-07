package org.polyfrost.polyplus.client.network.p2p

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.network.eos.EosConnectAuth
import org.polyfrost.polyplus.client.network.eos.EosFailureDiagnosis
import org.polyfrost.polyplus.client.network.eos.EosNativeSupport
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.network.eos.EosSdkBridgeImpl
import org.polyfrost.polyplus.client.network.eos.EosTickHealth
import org.polyfrost.polyplus.client.network.http.SessionsApi
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.http.responses.SessionResponse
import org.polyfrost.polyplus.client.resourcepack.HostSharedPack
import org.polyfrost.polyplus.client.resourcepack.P2PPackTransport
import org.polyfrost.polyplus.client.resourcepack.PackHttpBridge
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import org.polyfrost.polyplus.utils.EarlyInitializable
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

object P2PSessionManager : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()

    private const val INBOUND_QUEUE_BYTES = 16L * 1024 * 1024
    private const val OUTBOUND_QUEUE_BYTES = 16L * 1024 * 1024

    // this is a placeholder IP we hand to MC
    // it leads to literally nothing
    const val P2P_PLACEHOLDER_IP = "127.6.6.6"

    @Volatile private var bridge: EosSdkBridge? = null
    @Volatile private var sessionOwner: UUID? = null

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionIdFlow = _currentSessionId.asStateFlow()
    val currentSessionId: String? get() = _currentSessionId.value

    private val _status = MutableStateFlow<EosStatus>(EosStatus.Connecting)
    val status = _status.asStateFlow()

    private val _joinFailures = MutableSharedFlow<String>(extraBufferCapacity = 8)

    @Volatile private var shutDownForConsent = false

    @Volatile private var starting = false

    private const val CONSENT_REQUIRED =
        "Poly+ multiplayer is off until you accept the Terms of Service and Privacy Policy."
    private const val RESTART_REQUIRED =
        "Restart your game to turn Poly+ multiplayer back on."

    private const val AUTH_READY_TIMEOUT_MS = 15_000L
    private const val JOIN_HANDSHAKE_TIMEOUT_MS = 15_000L
    private const val CHANNEL_CLOSE_TIMEOUT_MS = 250L
    private const val MAX_STALL_RECOVERIES = 2
    private const val STALL_RESTART_READY_TIMEOUT_MS = 20_000L
    private const val EOS_SDK_PACKAGE = "gg.sona.eos"
    private const val STALL_UNRECOVERABLE = "Poly+ multiplayer stopped responding. Restart your game, and if you see this message, please report it at https://polyfrost.org/discord."

    data class JoinTarget(val host: EosProductUserId, val socket: EosP2PSocketId)

    override fun earlyInitialize() {
        val unsupported = EosNativeSupport.unsupportedReason
        if (unsupported == null) {
            applyConsent()
        } else {
            LOGGER.warn("Not starting EOS on {}: {}", EosNativeSupport.platform, unsupported)
            _status.value = EosStatus.Failed(unsupported)
        }

        SessionsRepository.acceptedInvites
            .onEach { invite -> PolyPlusClient.SCOPE.launch { handleAcceptedInvite(invite) } }
            .launchIn(PolyPlusClient.SCOPE)

        HostSharedPack.registerConfigurationHook()

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            stopHosting()
            PackHttpBridge.setPackSource(null)
        }

        if (unsupported == null) eventHandler<TickEvent.End> { checkForStalledEos() }.register()
    }

    private val consentLock = Any()

    fun applyConsent() {
        if (EosNativeSupport.unsupportedReason != null) return

        synchronized(consentLock) {
            if (PrivacyConsent.allowsOnlineServices()) {
                if (bridge != null || starting) return
                if (shutDownForConsent) {
                    _status.value = EosStatus.Failed(RESTART_REQUIRED)
                    return
                }
                starting = true
                _status.value = EosStatus.Connecting
                PolyPlusClient.SCOPE.launch {
                    val candidate = EosSdkBridgeImpl()
                    val started = runCatching {
                        withContext(Dispatchers.IO) {
                            if (candidate.initialize()) {
                                candidate
                            } else {
                                LOGGER.error("EOS did not start; P2P hosting/joining is unavailable")
                                null
                            }
                        }
                    }.onFailure { LOGGER.error("Starting EOS failed; P2P hosting/joining is unavailable", it) }
                        .getOrNull()
                    if (started == null) {
                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { candidate.shutdown() }
                    }

                    val abandoned = synchronized(consentLock) {
                        starting = false
                        when {
                            started == null -> {
                                if (PrivacyConsent.allowsOnlineServices()) {
                                    _status.value = EosStatus.Failed("Unable to start Poly+ multiplayer services.")
                                }
                                false
                            }
                            PrivacyConsent.allowsOnlineServices() -> {
                                install(started)
                                false
                            }
                            else -> {
                                shutDownForConsent = true
                                true
                            }
                        }
                    }
                    if (abandoned && started != null) {
                        LOGGER.info("Consent was withdrawn while EOS was starting; shutting it back down.")
                        withContext(Dispatchers.IO) { started.shutdown() }
                    }
                }
            } else {
                _status.value = EosStatus.Failed(CONSENT_REQUIRED)
                val running = bridge ?: run {
                    if (starting) LOGGER.info("Consent withdrawn while EOS was starting; it will not be installed.")
                    return
                }
                LOGGER.info("Consent withdrawn; shutting EOS down.")
                bridge = null
                EosVoicechatBridge.uninstall()
                P2PPackTransport.uninstall()
                shutDownForConsent = true
                stopHosting()
                PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
                    closeLiveConnections()
                    EosP2PChannel.Holder.bridge = null
                    running.shutdown()
                }
            }
        }
    }

    private fun closeLiveConnections() {
        val channels = P2PChannelRegistry.allChannels().toList()
        if (channels.isEmpty()) return
        LOGGER.info("Closing {} live P2P connection(s) before EOS shuts down", channels.size)
        channels.forEach { channel ->
            runCatching { channel.close().await(CHANNEL_CLOSE_TIMEOUT_MS) }
                .onSuccess { closed ->
                    if (!closed) {
                        LOGGER.warn(
                            "A P2P channel did not close within {}ms; its peer will see a read timeout rather than a clean disconnect",
                            CHANNEL_CLOSE_TIMEOUT_MS,
                        )
                    }
                }
                .onFailure { LOGGER.warn("Failed to close a P2P channel", it) }
        }
    }

    private fun install(bridge: EosSdkBridge) {
        this.bridge = bridge
        EosP2PChannel.Holder.bridge = bridge

        bridge.setRelayControl(forceRelays = false)
        bridge.setPacketQueueSize(INBOUND_QUEUE_BYTES, OUTBOUND_QUEUE_BYTES)
        bridge.setInboundPacketHandler { received ->
            if (P2PPackTransport.handlePacket(received)) return@setInboundPacketHandler
            if (EosVoicechatBridge.handlePacket(received)) return@setInboundPacketHandler

            val channel = P2PChannelRegistry.get(received.socket, received.remote)
            if (channel == null) {
                LOGGER.warn("Dropping EOS P2P packet on socket {} from unregistered peer {}", received.socket, received.remote)
            } else {
                channel.deliverInbound(received.remote, received.data)
            }
        }
        bridge.setLoginLostHandler {
            PolyPlusClient.SCOPE.launch {
                if (!isCurrent(bridge)) return@launch
                _status.value = EosStatus.Connecting
                authenticate(bridge, forceRelogin = true)
            }
        }
        P2PPackTransport.install(bridge)
        EosVoicechatBridge.install(bridge)

        PolyPlusClient.SCOPE.launch { authenticate(bridge, forceRelogin = false) }
    }

    private var stallReported = false
    private var stallRecoveries = 0
    private var lastStallCheckMs = 0L
    private var suppressStallUntilMs = 0L

    private fun checkForStalledEos() {
        val bridge = this.bridge ?: return
        val now = EosTickHealth.monotonicMs()
        val lastCheck = lastStallCheckMs
        lastStallCheckMs = now

        if (!EosTickHealth.isObservationTrustworthy(lastCheck, now)) {
            suppressStallUntilMs = now + EosTickHealth.STALL_THRESHOLD_MS
        }
        if (now < suppressStallUntilMs) return

        if (!bridge.isStalled()) {
            if (stallReported) {
                stallReported = false
                LOGGER.info("The EOS tick thread is responding again")
                if (bridge.isLoggedIn) _status.value = EosStatus.Ready
            }
            return
        }
        if (stallReported) return

        stallReported = true
        val threads = Thread.getAllStackTraces()
        val stuck = threads.entries.firstOrNull { it.key.name == EosTickHealth.THREAD_NAME }
        val alsoInSdk = threads
            .filterKeys { it.name != EosTickHealth.THREAD_NAME }
            .filterValues { stack -> stack.any { it.className.startsWith(EOS_SDK_PACKAGE) } }
        val description = "${EosTickHealth.THREAD_NAME} hasn't ticked in over ${EosTickHealth.STALL_THRESHOLD_MS}ms"
        LOGGER.error(
            "{}.\n{}{}",
            description,
            stuck?.value?.let { renderStack("Stuck ${EosTickHealth.THREAD_NAME} thread", it) }
                ?: "The ${EosTickHealth.THREAD_NAME} thread is gone entirely.",
            alsoInSdk.entries.joinToString("") { (thread, stack) ->
                "\n" + renderStack("Thread ${thread.name} is also inside the EOS SDK", stack)
            },
        )
        stuck?.let { (thread, stack) -> PolyPlusSentry.captureStalledThread(thread, stack, description, alsoInSdk) }

        if (!restartStalledEos(bridge)) {
            _status.value = EosStatus.Failed(STALL_UNRECOVERABLE)
        }
    }

    private fun renderStack(label: String, stack: Array<StackTraceElement>): String =
        stack.joinToString("\n\tat ", prefix = "$label:\n\tat ")

    private fun restartStalledEos(stalled: EosSdkBridge): Boolean {
        if (stalled !is EosSdkBridgeImpl) return false
        if (EosSdkBridgeImpl.isSdkRetired) return false
        if (!PrivacyConsent.allowsOnlineServices()) return false
        if (stallRecoveries >= MAX_STALL_RECOVERIES) {
            LOGGER.error("Not restarting EOS again after {} stall(s) this session", stallRecoveries)
            return false
        }

        synchronized(consentLock) {
            if (bridge !== stalled || starting) return false
            stallRecoveries++
            starting = true
            bridge = null
            stallReported = false
            EosVoicechatBridge.uninstall()
            P2PPackTransport.uninstall()
            _status.value = EosStatus.Connecting
        }

        LOGGER.warn("Restarting EOS after a stalled tick thread ({} of {})", stallRecoveries, MAX_STALL_RECOVERIES)
        stopHosting()
        stalled.abandon()

        PolyPlusClient.SCOPE.launch {
            withContext(Dispatchers.IO) {
                closeLiveConnections()
                EosP2PChannel.Holder.bridge = null
            }

            val candidate = EosSdkBridgeImpl()
            val started = runCatching {
                withContext(Dispatchers.IO) { candidate.takeIf { it.initialize() } }
            }.onFailure { LOGGER.error("Could not restart EOS after a stall", it) }.getOrNull()

            if (started == null) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { candidate.shutdown() }
            }

            val abandoned = synchronized(consentLock) {
                starting = false
                when {
                    started == null -> {
                        _status.value = EosStatus.Failed(STALL_UNRECOVERABLE)
                        false
                    }
                    PrivacyConsent.allowsOnlineServices() -> {
                        install(started)
                        false
                    }
                    else -> {
                        shutDownForConsent = true
                        true
                    }
                }
            }
            if (started == null) return@launch
            if (abandoned) {
                LOGGER.info("Consent was withdrawn while EOS was restarting, shutting it back down.")
                withContext(Dispatchers.IO) { started.shutdown() }
            } else {
                awaitRestartedReadiness(started)
            }
        }
        return true
    }

    private suspend fun awaitRestartedReadiness(restarted: EosSdkBridge) {
        val settled = withTimeoutOrNull(STALL_RESTART_READY_TIMEOUT_MS.milliseconds) {
            status.first { it == EosStatus.Ready || it is EosStatus.Failed }
        }
        if (settled != null || !isCurrent(restarted)) return
        LOGGER.error("The restarted EOS bridge never came up within {}ms; multiplayer needs a game restart", STALL_RESTART_READY_TIMEOUT_MS)
        _status.value = EosStatus.Failed(STALL_UNRECOVERABLE)
    }

    fun reconnect() {
        val bridge = this.bridge ?: return
        stopHosting()
        _status.value = EosStatus.Connecting
        PolyPlusClient.SCOPE.launch { authenticate(bridge, forceRelogin = true) }
    }

    private fun isCurrent(bridge: EosSdkBridge): Boolean = this.bridge === bridge

    private suspend fun authenticate(bridge: EosSdkBridge, forceRelogin: Boolean) {
        val user = (if (forceRelogin) EosConnectAuth.forceLogin(bridge) else EosConnectAuth.ensureLoggedIn(bridge))
            .onFailure {
                if (!isCurrent(bridge)) return
                LOGGER.error("EOS Connect login failed; P2P hosting/joining is unavailable", it)
                EosFailureDiagnosis
                    .explain(EosFailureDiagnosis.openFileDescriptors(), PolyConnection.isConnected)
                    ?.let(LOGGER::error)
                _status.value = EosStatus.Failed("Unable to connect to Poly+ multiplayer services.")
            }
            .getOrNull()

        if (!isCurrent(bridge)) {
            LOGGER.info("Discarding an EOS login that finished after the transport was detached")
            return
        }

        if (user != null) {
            org.polyfrost.polyplus.client.network.http.AccountApi.linkPuid(user.raw)
                .onSuccess { if (isCurrent(bridge)) _status.value = EosStatus.Ready }
                .onFailure {
                    LOGGER.error("Failed to link EOS ProductUserId with the backend", it)
                    if (isCurrent(bridge)) _status.value = EosStatus.Failed("Unable to link your account for multiplayer sessions.")
                }

            bridge.queryNatType().onSuccess { LOGGER.info("EOS P2P NAT type: {}", it) }
        }
    }

    fun socketFor(sessionId: String): EosP2PSocketId = EosP2PSocketId(sessionId.replace("-", "").take(32))

    fun setPrivateRelay(enabled: Boolean) {
        bridge?.setRelayControl(forceRelays = enabled)
    }

    suspend fun beginHostingSession(privateRelay: Boolean = false, autoShareResourcePack: Boolean = false): Result<SessionResponse> {
        val bridge = this.bridge ?: return Result.failure(IllegalStateException("P2P transport is not installed"))
        val localUser = bridge.localUser
            ?: return Result.failure(IllegalStateException("Not logged into EOS Connect yet"))

        setPrivateRelay(privateRelay)
        if (autoShareResourcePack) HostSharedPack.enable() else HostSharedPack.disable()

        return SessionsApi.create().onSuccess { session ->
            _currentSessionId.value = session.id
            sessionOwner = localProfileId()
            val socket = socketFor(session.id)
            P2PListenContext.setPendingListen(socket, localUser)
            SessionsApi.updateEosSessionId(session.id, socket.name)
                .onFailure { LOGGER.error("Failed to record EOS session id for session {}", session.id, it) }
        }
    }

    fun stopHosting() {
        HostSharedPack.disable()
        val sessionId = currentSessionId ?: return
        val owner = sessionOwner
        _currentSessionId.value = null
        sessionOwner = null
        if (owner != null && owner != localProfileId()) return
        SessionsRepository.close(sessionId)
    }

    private fun localProfileId(): UUID? = runCatching { Minecraft.getInstance().user.profileId }.getOrNull()

    private fun unavailableReason(): String = EosNativeSupport.unsupportedReason
        ?: CONSENT_REQUIRED.takeUnless { PrivacyConsent.allowsOnlineServices() }
        ?: RESTART_REQUIRED.takeIf { shutDownForConsent }
        ?: "Multiplayer services aren't ready yet - try again shortly"

    private suspend fun handleAcceptedInvite(invite: SessionInvite) {
        val bridge = this.bridge
        if (bridge == null) {
            LOGGER.error("Accepted session invite {} but the P2P transport isn't installed", invite.id)
            _joinFailures.tryEmit(unavailableReason())
            return
        }

        if (_status.value != EosStatus.Ready) {
            LOGGER.info("Waiting for EOS Connect to be ready before joining session {}", invite.sessionId)
            val ready = withTimeoutOrNull(AUTH_READY_TIMEOUT_MS.milliseconds) {
                status.first { it == EosStatus.Ready || it is EosStatus.Failed }
            }
            if (ready != EosStatus.Ready) {
                LOGGER.error("Gave up waiting for EOS Connect readiness; cannot join session {}", invite.sessionId)
                _joinFailures.tryEmit(unavailableReason())
                return
            }
        }

        val hostPuid = invite.eosProductUserId
        if (hostPuid == null) {
            LOGGER.error(
                "Host {} hasn't linked an EOS ProductUserId yet (they need to complete " +
                    "EOS Connect login, which calls POST /account/link-puid); cannot join over P2P",
                invite.sender,
            )
            _joinFailures.tryEmit("Host hasn't finished connecting yet - try again shortly")
            return
        }

        val socket = socketFor(invite.eosSessionId ?: invite.sessionId)
        val target = JoinTarget(EosProductUserId(hostPuid), socket)
        LOGGER.info("Resolved join target for session {}: host={} socket={}", invite.sessionId, hostPuid, socket)
        attemptJoin(bridge, target)
    }

    private suspend fun attemptJoin(bridge: EosSdkBridge, target: JoinTarget) {
        val established = AtomicBoolean(false)
        val handle = bridge.addConnectionStateHandler(target.socket) { event ->
            if (event.remote != target.host) return@addConnectionStateHandler
            if (event is EosSdkBridge.ConnectionStateEvent.Established) established.set(true)
        }

        LOGGER.info("Joining session socket {}", target.socket)
        onJoinTargetResolved(target)

        delay(JOIN_HANDSHAKE_TIMEOUT_MS.milliseconds)
        bridge.removeNotificationHandler(handle)

        if (established.get()) return

        LOGGER.error("The host never accepted our P2P connection on socket {} within {}ms", target.socket, JOIN_HANDSHAKE_TIMEOUT_MS)
        P2PConnectionContext.clearPendingJoin()
        bridge.closeConnection(target.socket, target.host)
        _joinFailures.tryEmit("Couldn't establish a P2P connection with the host - check your network/firewall and try again")
    }

    var onJoinTargetResolved: (JoinTarget) -> Unit = { target ->
        P2PConnectionContext.setPendingJoin(target)
        PackHttpBridge.setPackSource(target.host)

        val minecraft = Minecraft.getInstance()
        val address = ServerAddress.parseString("$P2P_PLACEHOLDER_IP:2")
        val serverData = ServerData(
            "PolyPlus P2P session",
            P2P_PLACEHOLDER_IP,
            ServerData.Type.OTHER,
        )

        minecraft.execute {
            ConnectScreen.startConnecting(
                TitleScreen(),
                minecraft,
                address,
                serverData,
                false,
                null,
            )
        }

    }
}
