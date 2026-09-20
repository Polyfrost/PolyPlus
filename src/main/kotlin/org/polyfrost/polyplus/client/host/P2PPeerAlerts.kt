package org.polyfrost.polyplus.client.host

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress
import java.net.SocketAddress
//? if = 1.8.9 {
/*import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import org.polyfrost.polyplus.client.social.execute
*///?}

object P2PPeerAlerts {
    private val LOGGER = LogManager.getLogger("PolyPlus/P2P")

    private const val TIMEOUT_KEY = "disconnect.timeout"

    @JvmStatic
    //? if = 1.8.9 {
    /*fun onPlayerDisconnected(remoteAddress: SocketAddress?, playerName: String, reason: Text) {
        if (remoteAddress !is EosP2PAddress) return

        val key = (reason as? TranslatableText)?.key
    *///?} else {
    fun onPlayerDisconnected(remoteAddress: SocketAddress?, playerName: String, reason: Component) {
        if (remoteAddress !is EosP2PAddress) return

        val key = (reason.contents as? TranslatableContents)?.key
    //?}
        if (key != TIMEOUT_KEY) return

        LOGGER.warn("P2P guest {} timed out", playerName)
        val minecraft = Minecraft.getInstance()
        val message = Component.literal("$playerName timed out, they lost their connection to your world.")
            .withStyle(ChatFormatting.YELLOW)
        minecraft.execute {
            val player = minecraft.player ?: return@execute
            //? if >= 26.1 {
            player.sendSystemMessage(message)
            //?} else if > 1.8.9 {
            /*player.displayClientMessage(message, false)
            *///?} else {
            /*player.addMessage(message)
            *///?}
        }
    }
}
