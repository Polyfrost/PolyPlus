package org.polyfrost.polyplus.client

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.DevBundles
import org.polyfrost.polyplus.client.cosmetics.DevCrowd
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform

object DevBundleCommands {
    private typealias Source = FabricClientCommandSource
    private typealias commands = PolyPlusCommands.commands

    private const val ALL = "all"

    fun build(): LiteralArgumentBuilder<Source> =
        commands.literal("dev")
            .then(commands.literal("bundles").executes { list(it.source) })
            .then(
                commands.literal("load")
                    .then(
                        commands.argument("name", StringArgumentType.word())
                            .suggests { _, builder ->
                                builder.suggest(ALL)
                                for (name in DevBundles.names()) builder.suggest(name)
                                builder.buildFuture()
                            }
                            .executes { load(it.source, StringArgumentType.getString(it, "name"), null) }
                            .then(
                                commands.argument("slot", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        for (slot in BodySlot.equippableSlots) builder.suggest(slot.serializedName)
                                        builder.buildFuture()
                                    }
                                    .executes {
                                        load(it.source, StringArgumentType.getString(it, "name"), StringArgumentType.getString(it, "slot"))
                                    }
                                    .then(
                                        commands.argument("variant", StringArgumentType.word())
                                            .suggests { context, builder ->
                                                for (variant in DevBundles.textureVariants(StringArgumentType.getString(context, "name"))) {
                                                    builder.suggest(variant)
                                                }
                                                builder.buildFuture()
                                            }
                                            .executes {
                                                load(
                                                    it.source,
                                                    StringArgumentType.getString(it, "name"),
                                                    StringArgumentType.getString(it, "slot"),
                                                    StringArgumentType.getString(it, "variant"),
                                                )
                                            },
                                    ),
                            ),
                    ),
            )
            .then(
                commands.literal("play")
                    .then(
                        commands.argument("name", StringArgumentType.word())
                            .suggests { _, builder ->
                                for (name in DevBundles.loadedNames()) builder.suggest(name)
                                builder.buildFuture()
                            }
                            .executes { play(it.source, StringArgumentType.getString(it, "name")) },
                    ),
            )
            .then(commands.literal("clear").executes { clear(it.source) })
            .then(
                commands.literal("crowd")
                    .then(commands.literal("clear").executes { clearCrowd(it.source) })
                    .then(
                        commands.argument("count", IntegerArgumentType.integer(1, DevCrowd.MAX))
                            .executes { crowd(it.source, IntegerArgumentType.getInteger(it, "count"), DevCrowd.ALL) }
                            .then(
                                commands.argument("bundle", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        builder.suggest(DevCrowd.ALL)
                                        builder.suggest(DevCrowd.RANDOM)
                                        for (name in DevBundles.loadedNames()) builder.suggest(name)
                                        builder.buildFuture()
                                    }
                                    .executes {
                                        crowd(it.source, IntegerArgumentType.getInteger(it, "count"), StringArgumentType.getString(it, "bundle"))
                                    },
                            ),
                    ),
            )

    private fun list(source: Source): Int {
        val names = DevBundles.names()
        if (names.isEmpty()) {
            source.sendFeedback(gray("No bundles in ${DevBundles.folder}. Put bundle folders or zips there."))
            return Command.SINGLE_SUCCESS
        }
        val loaded = DevBundles.loadedNames().toSet()
        source.sendFeedback(gray("Bundles in ${DevBundles.folder}:"))
        for (name in names) {
            val mark = if (name in loaded) " (loaded)" else ""
            source.sendFeedback(Component.literal("  $name$mark").withStyle(if (name in loaded) ChatFormatting.GREEN else ChatFormatting.WHITE))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun load(source: Source, name: String, slot: String?, variant: String? = null): Int {
        val names = if (name == ALL) DevBundles.names() else listOf(name)
        if (names.isEmpty()) {
            source.sendFeedback(red("No bundles in ${DevBundles.folder}."))
            return Command.SINGLE_SUCCESS
        }
        PolyPlusClient.SCOPE.launch {
            for (each in names) {
                val result = DevBundles.load(each, slot.takeIf { name != ALL }, variant.takeIf { name != ALL })
                ClientPlatform.runOnMain { source.sendFeedback(feedback(each, result)) }
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun feedback(name: String, result: Result<DevBundles.Loaded>): Component {
        val loaded = result.getOrElse { return red("Couldn't load $name: ${it.message ?: it.javaClass.simpleName}") }
        val where = loaded.slot?.let { " in ${it.serializedName}" }.orEmpty()
        val variant = loaded.definition.variantKey?.let { " ($it variant)" }.orEmpty()
        return if (loaded.installed) {
            green("Loaded $name$variant as #${loaded.definition.id} (${loaded.definition.type.name.lowercase()})$where.")
        } else {
            red("$name didn't load. The log has the bundle's issues.")
        }
    }

    private fun play(source: Source, name: String): Int {
        val loaded = DevBundles.get(name)
        if (loaded == null || loaded.definition.type != CosmeticType.Emote) {
            source.sendFeedback(red("Load an emote bundle called $name first."))
            return Command.SINGLE_SUCCESS
        }
        val player = Minecraft.getInstance().player
        if (player == null) {
            source.sendFeedback(red("Join a world to play an emote."))
            return Command.SINGLE_SUCCESS
        }
        val id = loaded.definition.id
        if (EmoteApi.playOwnedEmote(player, id)) {
            source.sendFeedback(green("Playing $name."))
            return Command.SINGLE_SUCCESS
        }
        val emote = CosmeticAssetCache.getEmote(id)
        if (emote == null) {
            source.sendFeedback(red("$name isn't ready yet. Try again in a moment."))
            return Command.SINGLE_SUCCESS
        }
        (player as PlayerEmotesAccess).`polyplus$emoteController`().play(emote)
        source.sendFeedback(green("Playing $name on this client only."))
        return Command.SINGLE_SUCCESS
    }

    private fun clear(source: Source): Int {
        val removed = DevBundles.clear()
        source.sendFeedback(green("Removed $removed dev bundle${if (removed == 1) "" else "s"}."))
        return Command.SINGLE_SUCCESS
    }

    private fun crowd(source: Source, count: Int, pick: String): Int {
        val spawned = DevCrowd.spawn(count, pick).getOrElse {
            source.sendFeedback(red(it.message ?: "Couldn't spawn the crowd."))
            return Command.SINGLE_SUCCESS
        }
        val players = "${spawned.players} test player${if (spawned.players == 1) "" else "s"}"
        val emoting = if (spawned.emoting > 0) ", ${spawned.emoting} of them emoting" else ""
        source.sendFeedback(green("Spawned $players wearing ${spawned.worn} cosmetics between them$emoting. Only this client can see them."))
        return Command.SINGLE_SUCCESS
    }

    private fun clearCrowd(source: Source): Int {
        val removed = DevCrowd.clear()
        source.sendFeedback(
            if (removed == 0) gray("There's no crowd to remove.")
            else green("Removed $removed test player${if (removed == 1) "" else "s"}."),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun gray(text: String): Component = Component.literal(text).withStyle(ChatFormatting.GRAY)

    private fun green(text: String): Component = Component.literal(text).withStyle(ChatFormatting.GREEN)

    private fun red(text: String): Component = Component.literal(text).withStyle(ChatFormatting.RED)
}
