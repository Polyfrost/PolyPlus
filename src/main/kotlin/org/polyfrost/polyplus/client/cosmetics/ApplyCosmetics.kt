package org.polyfrost.polyplus.client.cosmetics

import dev.deftu.eventbus.on
import dev.deftu.omnicore.api.eventBus
import net.minecraft.network.play.server.S38PacketPlayerListItem
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.events.LevelLoadEvent
import org.polyfrost.polyplus.client.events.ReceivePacketEvent
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.client.utils.Batcher
import org.polyfrost.polyplus.utils.EarlyInitializable
import java.time.Duration
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

//#if MC >= 1.20.1
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
//#endif

object ApplyCosmetics : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()
    private val BATCHER = Batcher(Duration.ofMillis(200), HashSet<String>()) { players ->
        PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(players.toList()))
    }

    override fun earlyInitialize() {
        eventBus.on<LevelLoadEvent> {
            PolyCosmetics.reset()
        }

        eventBus.on<WebSocketMessage> {
            val cosmeticInfo = packet as? ClientboundPacket.CosmeticsInfo ?: return@on

            // todo: have a map of type to valid ids? or ask ty to include type in the returned info. for now theyre all capes.
            for ((uuid, active) in cosmeticInfo.all) {
                active.forEach {
                    PolyCosmetics.cacheActive(UUID.fromString(uuid), "cape", it)
                    LOGGER.info("Cached cosmetic for player $uuid: cape -> $it")
                }
            }
        }

        eventBus.on<ReceivePacketEvent> {
            val packet = packet as? S38PacketPlayerListItem ?: return@on

            //#if MC >= 1.20.1
            //$$ for (action in packet.actions()) {
            //$$     processPlayerInfoAction(action, packet.entries())
            //$$ }
            //#else
            processPlayerInfoAction(packet.action, packet.entries)
            //#endif
        }

        //#if MC >= 1.20.1
        //$$ eventBus.on<ReceivePacketEvent> {
        //$$     val packet = packet as? ClientboundPlayerInfoRemovePacket ?: return@on
        //$$     for (uuid in packet.profileIds) {
        //$$         PolyCosmetics.removeFromCache(uuid)
        //$$     }
        //$$ }
        //#endif
    }

    private fun processPlayerInfoAction(action: S38PacketPlayerListItem.Action, entries: List<S38PacketPlayerListItem.AddPlayerData>) {
        when (action) {
            S38PacketPlayerListItem.Action.ADD_PLAYER -> {
                entries.forEach { playerData ->
                    val uuid = playerData.profile?.id ?: return@forEach
                    if (uuid.isRealPlayer()) {
                        BATCHER.add(uuid.toString())
                    }
                }
            }

            //#if MC < 1.20.1
            S38PacketPlayerListItem.Action.REMOVE_PLAYER -> {
                for (entry in entries) {
                    PolyCosmetics.removeFromCache(entry.profile.id)
                }
            }
            //#endif

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