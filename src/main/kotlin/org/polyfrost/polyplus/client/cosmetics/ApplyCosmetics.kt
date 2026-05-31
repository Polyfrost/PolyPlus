package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.utils.Batcher
import org.polyfrost.polyplus.utils.EarlyInitializable
import java.time.Duration
import java.util.UUID

object ApplyCosmetics : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()
    private val BATCHER = Batcher(Duration.ofMillis(200), HashSet<String>()) { players ->
        PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(players.toList()))
    }

    override fun earlyInitialize() {
        eventHandler<WorldEvent.Load> {
            PolyCosmetics.reset()
        }.register()

        eventHandler<WebSocketMessage> { event ->
            val cosmeticInfo = event.packet as? ClientboundPacket.CosmeticsInfo ?: return@eventHandler

            for ((uuid, active) in cosmeticInfo.all) {
                active.forEach {
                    PolyCosmetics.cacheActive(UUID.fromString(uuid), "cape", it)
                    LOGGER.info("Cached cosmetic for player $uuid: cape -> $it")
                }
            }
        }.register()

        eventHandler<PacketEvent.Receive> { event ->
            val packet = event.getPacket<Any>() as? ClientboundPlayerInfoUpdatePacket ?: return@eventHandler
            for (action in packet.actions()) {
                processPlayerInfoAction(action, packet.entries())
            }
        }

        eventHandler<PacketEvent.Receive> { event ->
            val packet = event.getPacket<Any>() as? ClientboundPlayerInfoRemovePacket ?: return@eventHandler
            for (uuid in packet.profileIds()) {
                PolyCosmetics.removeFromCache(uuid)
            }
        }
    }

    private fun processPlayerInfoAction(
        action: ClientboundPlayerInfoUpdatePacket.Action,
        entries: List<ClientboundPlayerInfoUpdatePacket.Entry>,
    ) {
        when (action) {
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER -> {
                entries.forEach { entry ->
                    val uuid = entry.profileId()
                    if (uuid.isRealPlayer()) {
                        BATCHER.add(uuid.toString())
                    }
                }
            }

            else -> return
        }
    }

    /**
     * Mojang doesn't use UUID v2 so, if it is, it's a bot and won't have cosmetics
     */
    private fun UUID.isRealPlayer(): Boolean {
        return this.version() != 2
    }
}
