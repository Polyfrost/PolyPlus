package org.polyfrost.polyplus.client.resourcepack

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress
import org.polyfrost.polyplus.client.social.SocialErrors

//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask
import org.polyfrost.polyplus.mixin.client.network.ServerCommonPacketListenerAccessor
//?}

object HostSharedPack {
    private val LOGGER = LogManager.getLogger("PolyPlus/SharedPack")

    @Volatile var prepared: List<SharedResourcePack.Prepared> = emptyList()
        private set

    @Volatile private var enabled = false

    fun enable() {
        enabled = true
        PolyPlusClient.SCOPE.launch {
            val result = withContext(Dispatchers.IO) { SharedResourcePack.buildFromEquipped() }
            if (!enabled) return@launch

            result
                .onSuccess { packs ->
                    prepared = packs
                    LOGGER.info("Sharing {} pack(s) with everyone who joins this world", packs.size)
                }
                .onFailure { error ->
                    prepared = emptyList()
                    SocialErrors.emit(error.message ?: "Couldn't prepare your resource packs for sharing")
                }
        }
    }

    fun disable() {
        enabled = false
        prepared = emptyList()
    }

    fun packFor(sha1: ByteArray): SharedResourcePack.Prepared? =
        prepared.firstOrNull { it.sha1.contentEquals(sha1) }

    private fun packInfo(pack: SharedResourcePack.Prepared, first: Boolean): MinecraftServer.ServerResourcePackInfo =
        MinecraftServer.ServerResourcePackInfo(
            UUID.nameUUIDFromBytes(pack.sha1),
            PackHttpBridge.placeholderUrl(pack.sha1Hex),
            pack.sha1Hex,
            false,
            if (first) Component.literal("${hostName()} is sharing their resource packs with you.") else null,
        )

    private fun hostName(): String = runCatching { Minecraft.getInstance().user.name }.getOrNull() ?: "The host"

    fun registerConfigurationHook() {
        //? if fabric {
        ServerConfigurationConnectionEvents.CONFIGURE.register { handler, _ ->
            val packs = prepared
            if (packs.isEmpty()) return@register

            val connection = (handler as ServerCommonPacketListenerAccessor).polyplusConnection
            if (connection.remoteAddress !is EosP2PAddress) return@register

            LOGGER.info("Offering {} shared resource pack(s) to a joining P2P guest", packs.size)
            packs.forEachIndexed { index, pack ->
                handler.addTask(ServerResourcePackConfigurationTask(packInfo(pack, first = index == 0)))
            }
        }
        //?}
    }
}
