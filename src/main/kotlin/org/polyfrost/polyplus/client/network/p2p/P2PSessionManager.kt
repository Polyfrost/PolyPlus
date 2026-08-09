package org.polyfrost.polyplus.client.network.p2p

import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.eos.EosConnectAuth
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.network.eos.EosSdkBridgeImpl
import org.polyfrost.polyplus.client.network.http.SessionsApi
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.http.responses.SessionResponse
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.utils.EarlyInitializable

object P2PSessionManager : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()

    private const val INBOUND_QUEUE_BYTES = 16L * 1024 * 1024
    private const val OUTBOUND_QUEUE_BYTES = 16L * 1024 * 1024

    @Volatile private var bridge: EosSdkBridge? = null

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionIdFlow = _currentSessionId.asStateFlow()
    val currentSessionId: String? get() = _currentSessionId.value

    private val _status = MutableStateFlow<EosStatus>(EosStatus.Connecting)
    val status = _status.asStateFlow()

    private val _joinFailures = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val joinFailures = _joinFailures.asSharedFlow()

    data class JoinTarget(val host: EosProductUserId, val socket: EosP2PSocketId)

    override fun earlyInitialize() {
        install(EosSdkBridgeImpl().also { it.initialize() })

        SessionsRepository.acceptedInvites
            .onEach { invite -> handleAcceptedInvite(invite) }
            .launchIn(PolyPlusClient.SCOPE)

        eventHandler<WorldEvent.Unload> { stopHosting() }.register()
    }

    private fun install(bridge: EosSdkBridge) {
        this.bridge = bridge
        EosP2PChannel.Holder.bridge = bridge

        bridge.setRelayControl(allowRelays = true)
        bridge.setPacketQueueSize(INBOUND_QUEUE_BYTES, OUTBOUND_QUEUE_BYTES)
        bridge.setInboundPacketHandler { received ->
            val channel = P2PChannelRegistry.get(received.socket, received.remote)
            if (channel == null) {
                LOGGER.warn("Dropping EOS P2P packet on socket {} from unregistered peer {}", received.socket, received.remote)
            } else {
                channel.deliverInbound(received.remote, received.data)
            }
        }

        PolyPlusClient.SCOPE.launch { authenticate(bridge, forceRelogin = false) }
    }

    fun reconnect() {
        val bridge = this.bridge ?: return
        stopHosting()
        _status.value = EosStatus.Connecting
        PolyPlusClient.SCOPE.launch { authenticate(bridge, forceRelogin = true) }
    }

    private suspend fun authenticate(bridge: EosSdkBridge, forceRelogin: Boolean) {
        val user = (if (forceRelogin) EosConnectAuth.forceLogin(bridge) else EosConnectAuth.ensureLoggedIn(bridge))
            .onFailure {
                LOGGER.error("EOS Connect login failed; P2P hosting/joining is unavailable", it)
                _status.value = EosStatus.Failed("Unable to connect to Poly+ multiplayer services")
            }
            .getOrNull()

        if (user != null) {
            org.polyfrost.polyplus.client.network.http.AccountApi.linkPuid(user.raw)
                .onSuccess { _status.value = EosStatus.Ready }
                .onFailure {
                    LOGGER.error("Failed to link EOS ProductUserId with the backend", it)
                    _status.value = EosStatus.Failed("Unable to link your account for multiplayer sessions")
                }
        }

        bridge.queryNatType().onSuccess { LOGGER.info("EOS P2P NAT type: {}", it) }
    }

    fun connectBootstrap(target: JoinTarget): Bootstrap =
        Bootstrap()
            .channel(EosP2PChannel::class.java)
            .remoteAddress(EosP2PAddress(target.host, target.socket))

    fun listenBootstrap(socket: EosP2PSocketId): ServerBootstrap {
        val localUser = requireNotNull(bridge?.localUser) { "Cannot host a P2P session before EOS Connect login completes" }
        return ServerBootstrap()
            .channel(EosP2PServerChannel::class.java)
            .localAddress(EosP2PAddress(localUser, socket))
    }

    fun socketFor(sessionId: String): EosP2PSocketId = EosP2PSocketId(sessionId.replace("-", "").take(32))

    suspend fun beginHostingSession(): Result<SessionResponse> {
        val bridge = this.bridge ?: return Result.failure(IllegalStateException("P2P transport is not installed"))
        val localUser = bridge.localUser
            ?: return Result.failure(IllegalStateException("Not logged into EOS Connect yet"))

        return SessionsApi.create().onSuccess { session ->
            _currentSessionId.value = session.id
            P2PListenContext.setPendingListen(socketFor(session.id), localUser)
        }
    }

    fun stopHosting() {
        val sessionId = currentSessionId ?: return
        _currentSessionId.value = null
        SessionsRepository.close(sessionId)
    }

    private fun handleAcceptedInvite(invite: SessionInvite) {
        if (this.bridge == null) {
            LOGGER.error("Accepted session invite {} but the P2P transport isn't installed", invite.id)
            _joinFailures.tryEmit("Multiplayer services aren't ready yet - try again shortly")
            return
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
        onJoinTargetResolved(target)
    }

    var onJoinTargetResolved: (JoinTarget) -> Unit = { target ->
        P2PConnectionContext.setPendingJoin(target)

        val minecraft = Minecraft.getInstance()
        val address = ServerAddress.parseString("127.6.6.6:2")
        val serverData = ServerData(
            "PolyPlus P2P session",
            "127.6.6.6",
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
