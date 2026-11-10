package org.polyfrost.polyplus.server.apollo

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.server.OptionRegistry
import org.polyfrost.polyplus.server.ServerChannels
import org.polyfrost.polyplus.server.ServerIntegration
import java.util.concurrent.ConcurrentHashMap

object ApolloBridge {
    private val LOGGER = LogManager.getLogger("PolyPlus/Apollo")
    private const val MAX_LOGGED_TYPES = 64
    private val loggedTypes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var handshakeSent = false
    private var malformedLogged = false

    fun initialize() {
        ServerChannels.register(ApolloProtocol.CHANNEL) { handle(it, ApolloProtocol::decode) }
        ServerChannels.register(ApolloProtocol.JSON_CHANNEL) { handle(it, ApolloProtocol::decodeJson) }
        ServerChannels.onServerRegister { channels -> if (ApolloProtocol.CHANNEL in channels) sendHandshake() }
        ServerIntegration.addSessionStartListener {
            handshakeSent = false
            malformedLogged = false
            if (ServerChannels.canSend(ApolloProtocol.CHANNEL)) sendHandshake()
        }
    }

    private fun sendHandshake() {
        if (handshakeSent) return
        handshakeSent = true
        ServerChannels.send(ApolloProtocol.CHANNEL, ApolloProtocol.encodeHandshake(PolyPlusConstants.VERSION, PolyPlusConstants.MINECRAFT_VERSION))
    }

    private fun handle(data: ByteArray, decoder: (ByteArray, Collection<String>) -> ApolloProtocol.Decoded) {
        val decoded = try {
            decoder(data, OptionRegistry.keys())
        } catch (e: Exception) {
            if (!malformedLogged) {
                malformedLogged = true
                LOGGER.warn("Dropping malformed Apollo message ({} bytes): {}", data.size, e.toString())
            }
            return
        }
        val commands = decoded.commands
        if (commands == null) {
            if (loggedTypes.size < MAX_LOGGED_TYPES && loggedTypes.add(decoded.type)) LOGGER.debug("Ignoring Apollo message {}", decoded.type)
            return
        }
        commands.forEach(ServerIntegration::dispatch)
    }
}
