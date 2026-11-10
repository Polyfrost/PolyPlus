package org.polyfrost.polyplus.server

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants

object OneClientApi {
    private val LOGGER = LogManager.getLogger("PolyPlus/OneClientApi")
    private var helloSent = false
    private var malformedLogged = false

    @Volatile
    var serverApiVersion: String? = null
        private set

    fun initialize() {
        ServerChannels.register(OneClientProtocol.CHANNEL, ::handle)
        ServerChannels.onServerRegister { channels -> if (OneClientProtocol.CHANNEL in channels) sendHello() }
    }

    internal fun onSessionStart() {
        if (ServerChannels.canSend(OneClientProtocol.CHANNEL)) sendHello()
    }

    internal fun onSessionEnd() {
        helloSent = false
        malformedLogged = false
        serverApiVersion = null
    }

    private fun sendHello() {
        if (helloSent) return
        helloSent = true
        ServerChannels.send(OneClientProtocol.CHANNEL, OneClientProtocol.encodeHello(PolyPlusConstants.VERSION, PolyPlusConstants.MINECRAFT_VERSION))
    }

    private fun handle(data: ByteArray) {
        val packet = try {
            OneClientProtocol.decode(data)
        } catch (e: RuntimeException) {
            if (!malformedLogged) {
                malformedLogged = true
                LOGGER.warn("Dropping malformed {} packet ({} bytes): {}", OneClientProtocol.CHANNEL, data.size, e.message)
            }
            return
        }
        when (packet) {
            is OneClientProtocol.Welcome -> {
                serverApiVersion = packet.serverApiVersion
                LOGGER.info("Server runs the OneClient server API {} (protocol {})", packet.serverApiVersion, packet.protocolVersion)
                ServerChannels.send(OneClientProtocol.CHANNEL, OneClientProtocol.encodeCapabilities(ServerIntegration.capabilities()))
            }
            is OneClientProtocol.Command -> ServerIntegration.dispatch(packet.command)
            is OneClientProtocol.RawNotification -> ServerIntegration.dispatch(
                ServerCommand.Notify(
                    TextComponents.toPlainText(packet.titleJson),
                    TextComponents.toPlainText(packet.descriptionJson),
                    packet.durationMs,
                    packet.icon,
                ),
            )
            null -> Unit
        }
    }
}
