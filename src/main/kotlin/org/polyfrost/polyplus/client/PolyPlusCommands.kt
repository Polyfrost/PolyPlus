package org.polyfrost.polyplus.client

import com.mojang.brigadier.Command
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants

object PolyPlusCommands {
    private val LOGGER = LogManager.getLogger(PolyPlusConstants.NAME)

    internal typealias commands =
        //? if >= 26.1 {
        net.fabricmc.fabric.api.client.command.v2.ClientCommands
        //?} else {
        /*net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
        *///?}

    fun register() {
        //? if fabric {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(buildFabricRoot())
        }
        //?}
    }

    //? if fabric {
    private fun buildFabricRoot():
        com.mojang.brigadier.builder.LiteralArgumentBuilder
        <net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> {
        var root = commands.literal(PolyPlusConstants.ID)
            .then(commands.literal("refresh").executes { ctx ->
                PolyPlusClient.refresh()
                LOGGER.info("PolyPlus Client refresh triggered via command.")
                ctx.source.sendFeedback(
                    Component.literal("PolyPlus will refresh in the background.")
                        .withStyle(ChatFormatting.GREEN),
                )
                Command.SINGLE_SUCCESS
            })
            .then(commands.literal("version").executes { ctx ->
                ctx.source.sendFeedback(
                    Component.literal("PolyPlus Client version: ${PolyPlusConstants.VERSION}")
                        .withStyle(ChatFormatting.AQUA),
                )
                Command.SINGLE_SUCCESS
            })
            .then(commands.literal("sharepack").executes { ctx ->
                val peers = org.polyfrost.polyplus.client.network.p2p.P2PResourcePackShare.connectedPeers()
                if (peers.isEmpty()) {
                    ctx.source.sendFeedback(
                        Component.literal("You're not currently P2P-connected to anyone to share a pack with.")
                            .withStyle(ChatFormatting.RED),
                    )
                } else {
                    peers.forEach { peer -> org.polyfrost.polyplus.client.network.p2p.P2PResourcePackShare.shareEquippedPackWith(peer, force = true) }
                    ctx.source.sendFeedback(
                        Component.literal("Sharing your equipped resource pack with ${peers.size} peer(s)...")
                            .withStyle(ChatFormatting.GREEN),
                    )
                }
                Command.SINGLE_SUCCESS
            })
            .then(commands.literal("mainmenu").executes { _ ->
                val client = net.minecraft.client.Minecraft.getInstance()
                if (client.isSameThread) {
                    //? if >= 26.2 {
                    /*client.gui.setScreen(org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen())
                    *///?} else {
                    client.setScreen(org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen())
                    //?}
                } else {
                    client.execute {
                        //? if >= 26.2 {
                        /*client.gui.setScreen(org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen())
                        *///?} else {
                        client.setScreen(org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen())
                        //?}
                    }
                }
                Command.SINGLE_SUCCESS
            })

        //? if >= 1.21.1 {
        root = root.then(CosmeticCommands.build())
        root = root.then(ParticleCommands.build())
        //?}

        return root
    }
    //?}
}
