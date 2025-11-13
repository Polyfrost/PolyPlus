package org.polyfrost.polyplus.client.cosmetics

import dev.deftu.omnicore.api.client.player
import net.minecraft.network.play.server.S38PacketPlayerListItem
import net.minecraftforge.event.world.WorldEvent
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.impl.Subscribe
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.utils.Batcher
import java.time.Duration
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

object ApplyCosmetics {
    private val LOGGER = LogManager.getLogger()
    private val BATCHER = Batcher(Duration.ofMillis(200), HashSet<String>()) { players ->
        PolyConnection.sendPacket(ServerboundPacket.GetActiveCosmetics(players.toList()))
    }

    @Subscribe
    fun onWorldLoad(event: WorldEvent.Load) {
        PolyCosmetics.reset()
    }

    @Subscribe
    fun onPlayerList(event: PacketEvent.Receive) {
        // Not sure how to cleanly make this version agnostic atm.
        val packet = try { event.getPacket<S38PacketPlayerListItem>() ?: return } catch (e: Exception) { return }
        when (packet.action) {
            S38PacketPlayerListItem.Action.ADD_PLAYER -> {
                packet.entries.forEach {
                    // mojang doesnt use uuidv2 so if it is, its a bot and wont have a cape.
                    if (it.profile.id.version() != 2) BATCHER.add(it.profile.id.toString())
                }
            }

            S38PacketPlayerListItem.Action.REMOVE_PLAYER -> {
                for (entry in packet.entries) {
                    PolyCosmetics.removeFromCache(entry.profile.id)
                }
            }

            else -> return
        }
    }

    @Subscribe
    fun onRecieveCosmetics(event: WebSocketMessage) {
        val cosmeticInfo = event.packet as? ClientboundPacket.CosmeticsInfo ?: return
        // todo: have a map of type to valid ids? or ask ty to include type in the returned info. for now theyre all capes.
        for ((uuid, active) in cosmeticInfo.all) {
            active.forEach {
                PolyCosmetics.cacheActive(UUID.fromString(uuid), "cape", it)
                LOGGER.info("Cached cosmetic for player $uuid: cape -> $it")
            }
        }
    }
}