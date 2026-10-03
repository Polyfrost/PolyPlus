package org.polyfrost.polyplus.client

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.resourcepack.HostSharedPack
import org.polyfrost.polyplus.client.utils.ClientPlatform

//? if = 1.8.9 {
/*import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
*///?}

//? if >= 26.1 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
*///?}

object PolyPlusCommands {
    private val LOGGER = LogManager.getLogger(PolyPlusConstants.NAME)

    //? if > 1.8.9 {
    internal typealias commands =
        //? if >= 26.1 {
        ClientCommands
        //?} else {
        /*ClientCommandManager
        *///?}
    //?} else {
    /*internal object commands {
        fun literal(name: String): LiteralArgumentBuilder<FabricClientCommandSource> = LiteralArgumentBuilder.literal(name)

        fun <T> argument(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<FabricClientCommandSource, T> =
            RequiredArgumentBuilder.argument(name, type)
    }
    *///?}

    fun register() {
        //? if fabric || ornithe {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(buildFabricRoot())
        }
        //?}
    }

    //? if fabric || ornithe {
    private fun buildFabricRoot():
        LiteralArgumentBuilder
        <FabricClientCommandSource> {
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
                if (P2PSessionManager.currentSessionId == null) {
                    ctx.source.sendFeedback(
                        Component.literal("You're not hosting a Poly+ world, so there's nobody to share a pack with.")
                            .withStyle(ChatFormatting.RED),
                    )
                } else {
                    HostSharedPack.enable()
                    ctx.source.sendFeedback(
                        Component.literal("Your equipped resource pack will be offered to everyone who joins.")
                            .withStyle(ChatFormatting.GREEN),
                    )
                }
                Command.SINGLE_SUCCESS
            })
            .then(commands.literal("mainmenu").executes { _ ->
                //? if = 1.8.9 {
                /*Minecraft.getInstance().tell { ClientPlatform.setScreen(PolyPlusMainMenuScreen()) }
                *///?} else {
                val client = Minecraft.getInstance()
                if (client.isSameThread) {
                    //? if >= 26.2 {
                    client.gui.setScreen(PolyPlusMainMenuScreen())
                    //?} else {
                    /*client.setScreen(PolyPlusMainMenuScreen())
                    *///?}
                } else {
                    client.execute {
                        //? if >= 26.2 {
                        client.gui.setScreen(PolyPlusMainMenuScreen())
                        //?} else {
                        /*client.setScreen(PolyPlusMainMenuScreen())
                        *///?}
                    }
                }
                //?}
                Command.SINGLE_SUCCESS
            })

        //? if >= 1.21.1 || = 1.8.9 {
        root = root.then(CosmeticCommands.build())
        root = root.then(ParticleCommands.build())
        //?}

        return root
    }
    //?}
}
