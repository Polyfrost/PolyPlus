package org.polyfrost.polyplus.client

import com.mojang.brigadier.Command
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
// import org.polyfrost.polyplus.client.gui.FullscreenBrowserUI
object PolyPlusCommands {
    private val LOGGER = LogManager.getLogger(PolyPlusConstants.NAME)

    fun register() {
        //? if fabric {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(buildFabricRoot())
        }
        //?}
    }


    private typealias commands =
            //? if >= 26.1 {
            net.fabricmc.fabric.api.client.command.v2.ClientCommands
            //?} else {
            // net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
            //?}

    //? if fabric {
    private fun buildFabricRoot():
            com.mojang.brigadier.builder.LiteralArgumentBuilder
    <net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> {
        val yuhh = commands.literal(PolyPlusConstants.ID)
            .then(commands.literal("locker").executes { ctx ->
                openLocker()
                Command.SINGLE_SUCCESS
            })
            .then(commands.literal("refresh").executes { ctx ->
                PolyPlusClient.refresh()
                LOGGER.info("PolyPlus Client refresh triggered via command.")
                ctx.source.sendFeedback(
                    Component.literal("PolyPlus will refresh in the background.")
                        .withStyle(ChatFormatting.GREEN)
                )
                Command.SINGLE_SUCCESS
            })
            .then(commands.literal("version").executes { ctx ->
                ctx.source.sendFeedback(
                    Component.literal("PolyPlus Client version: ${PolyPlusConstants.VERSION}")
                        .withStyle(ChatFormatting.AQUA)
                )
                Command.SINGLE_SUCCESS
            })
        return yuhh
    }
    //?}

    private fun openLocker() {
        // val client = Minecraft.getInstance()
        // client.execute { client.setScreen(FullscreenBrowserUI.create()) }
    }
}
