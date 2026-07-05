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
            .then(commands.literal("mainmenu").executes { _ ->
                val client = net.minecraft.client.Minecraft.getInstance()
                if (client.isSameThread) {
                    client.setScreen(org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen())
                } else {
                    client.execute {
                        client.setScreen(org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen())
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
