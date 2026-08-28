package org.polyfrost.polyplus.client

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform

object ParticleCommands {
    private typealias Source = net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
    private typealias commands = PolyPlusCommands.commands

    private fun channel(name: String) =
        commands.argument(name, IntegerArgumentType.integer(0, 255))

    fun build(): LiteralArgumentBuilder<Source> =
        commands.literal("particle")
            .then(commands.literal("reset").executes { reset(it.source) })
            .then(
                channel("r").then(
                    channel("g").then(
                        channel("b")
                            .executes {
                                setColor(
                                    it.source,
                                    IntegerArgumentType.getInteger(it, "r"),
                                    IntegerArgumentType.getInteger(it, "g"),
                                    IntegerArgumentType.getInteger(it, "b"),
                                    a = 255,
                                )
                            }
                            .then(
                                channel("a").executes {
                                    setColor(
                                        it.source,
                                        IntegerArgumentType.getInteger(it, "r"),
                                        IntegerArgumentType.getInteger(it, "g"),
                                        IntegerArgumentType.getInteger(it, "b"),
                                        IntegerArgumentType.getInteger(it, "a"),
                                    )
                                },
                            ),
                    ),
                ),
            )

    private fun setColor(source: Source, r: Int, g: Int, b: Int, a: Int): Int {
        val color = (a shl 24) or (r shl 16) or (g shl 8) or b
        CosmeticCatalog.setParticleColor(ClientPlatform.localPlayerUuid(), color)

        val result = PolyConnection.sendPacket(ServerboundPacket.SetParticleColor(color))
        result.fold(
            onSuccess = {
                source.sendFeedback(
                    Component.literal("Particle color set to RGBA($r, $g, $b, $a).")
                        .withStyle(ChatFormatting.GREEN),
                )
            },
            onFailure = { error ->
                source.sendFeedback(
                    Component.literal("Particle color applied locally, but failed to sync: ${error.message}")
                        .withStyle(ChatFormatting.RED),
                )
            },
        )
        return Command.SINGLE_SUCCESS
    }

    private fun reset(source: Source): Int {
        CosmeticCatalog.clearParticleColor(ClientPlatform.localPlayerUuid())

        val result = PolyConnection.sendPacket(ServerboundPacket.SetParticleColor(null))
        result.fold(
            onSuccess = {
                source.sendFeedback(
                    Component.literal("Particle color reset to default.")
                        .withStyle(ChatFormatting.GREEN),
                )
            },
            onFailure = { error ->
                source.sendFeedback(
                    Component.literal("Particle color reset locally, but failed to sync: ${error.message}")
                        .withStyle(ChatFormatting.RED),
                )
            },
        )
        return Command.SINGLE_SUCCESS
    }
}
