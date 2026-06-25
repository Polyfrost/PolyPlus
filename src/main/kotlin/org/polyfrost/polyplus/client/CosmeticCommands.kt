//? if >= 1.21.1 {
package org.polyfrost.polyplus.client

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform

object CosmeticCommands {
    private typealias Source = net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
    private typealias commands = PolyPlusCommands.commands

    fun build(): LiteralArgumentBuilder<Source> =
        commands.literal("cosmetics")
            .then(
                commands.literal("list")
                    .executes { list(it.source, ownedOnly = true) }
                    .then(commands.literal("all").executes { list(it.source, ownedOnly = false) }),
            )
            .then(commands.literal("active").executes { active(it.source) })
            .then(
                commands.literal("equip")
                    .then(
                        commands.argument("id", IntegerArgumentType.integer(1))
                            .suggests { _, builder ->
                                for (id in CosmeticCatalog.ownedCosmeticGroupIds().sorted()) {
                                    builder.suggest(id)
                                }
                                for (id in CosmeticCatalog.ownedEmoteIds().sorted()) {
                                    builder.suggest(id)
                                }
                                builder.buildFuture()
                            }
                            .executes { equip(it.source, IntegerArgumentType.getInteger(it, "id"), variant = null) }
                            .then(
                                commands.argument("variant", StringArgumentType.greedyString())
                                    .suggests { ctx, builder ->
                                        val id = IntegerArgumentType.getInteger(ctx, "id")
                                        CosmeticCatalog.groupContaining(id)?.let { group ->
                                            for ((label, _) in CosmeticCatalog.userFacingVariants(group)) {
                                                builder.suggest(label)
                                            }
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes {
                                        equip(
                                            it.source,
                                            IntegerArgumentType.getInteger(it, "id"),
                                            StringArgumentType.getString(it, "variant"),
                                        )
                                    },
                            ),
                    ),
            )
            .then(
                commands.literal("clear")
                    .then(commands.literal("cape").executes { clear(it.source, BodySlot.Cape) })
                    .then(commands.literal("backpack").executes { clear(it.source, BodySlot.Backpack) })
                    .then(commands.literal("glasses").executes { clear(it.source, BodySlot.Glasses) })
                    .then(commands.literal("wings").executes { clear(it.source, BodySlot.Wings) })
                    .then(commands.literal("left_hand").executes { clear(it.source, BodySlot.LeftHand) })
                    .then(commands.literal("right_hand").executes { clear(it.source, BodySlot.RightHand) })
                    .then(commands.literal("hat").executes { clear(it.source, BodySlot.Hat) })
                    .then(commands.literal("aura").executes { clear(it.source, BodySlot.Aura) })
                    .then(commands.literal("boots").executes { clear(it.source, BodySlot.Boots) })
                    .then(commands.literal("shoulder").executes { clear(it.source, BodySlot.Shoulder) })
                    .then(commands.literal("emote").executes { clearEmote(it.source) }),
            )
            .then(
                commands.literal("play")
                    .executes { play(it.source, cosmeticId = null) }
                    .then(
                        commands.argument("id", IntegerArgumentType.integer(1))
                            .suggests { _, builder ->
                                for (id in ownedEmoteIds().sorted()) {
                                    builder.suggest(id)
                                }
                                builder.buildFuture()
                            }
                            .executes { play(it.source, IntegerArgumentType.getInteger(it, "id")) },
                    ),
            )
            .then(commands.literal("stop").executes { stop(it.source) })

    private fun list(source: Source, ownedOnly: Boolean): Int {
        if (Minecraft.getInstance().player != null) {
            PolyPlusClient.refreshCosmeticsIfNeeded()
        }

        val ownedCosmetics = CosmeticCatalog.ownedCosmeticIds()
        val groups = CosmeticCatalog.cosmeticGroupViews()
            .let { views ->
                if (ownedOnly) views.filter { group -> group.variants.any { it.id in ownedCosmetics } } else views
            }
            .sortedWith(compareBy({ it.type.ordinal }, { it.groupId }))

        val emotes = if (ownedOnly) {
            CosmeticCatalog.ownedEmoteIds().mapNotNull(CosmeticCatalog::getEmoteDefinition)
        } else {
            CosmeticCatalog.allEmoteDefinitions().toList()
        }.sortedBy(CosmeticDefinition::id)

        if (groups.isEmpty() && emotes.isEmpty()) {
            source.sendFeedback(
                Component.literal(
                    if (ownedOnly) {
                        "No owned cosmetics yet. Data loads when you join a world — try again in a moment."
                    } else {
                        "Cosmetic catalog is empty. Data loads when you join a world — try again in a moment."
                    },
                ).withStyle(ChatFormatting.GRAY),
            )
            return Command.SINGLE_SUCCESS
        }

        source.sendFeedback(
            Component.literal(if (ownedOnly) "Owned cosmetics:" else "Cosmetic catalog:")
                .withStyle(ChatFormatting.GRAY),
        )
        for (group in groups) {
            val typeLabel = group.type.name.lowercase()
            source.sendFeedback(
                Component.literal("${group.name} ($typeLabel) — #${group.groupId}")
                    .withStyle(ChatFormatting.GREEN),
            )
            val labels = CosmeticCatalog.userFacingVariants(group)
                .let { variants ->
                    if (ownedOnly) variants.filter { (_, id) -> id in ownedCosmetics } else variants
                }
                .map { it.first }
            if (labels.size > 1) {
                source.sendFeedback(
                    Component.literal("  ${labels.joinToString(", ")}")
                        .withStyle(ChatFormatting.GRAY),
                )
            }
        }

        if (emotes.isNotEmpty()) {
            source.sendFeedback(Component.literal("Emotes:").withStyle(ChatFormatting.GRAY))
            for (emote in emotes) {
                source.sendFeedback(formatDefinition(emote))
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun active(source: Source): Int {
        val active = CosmeticCatalog.localEquipped()
        source.sendFeedback(Component.literal("Active cosmetics:").withStyle(ChatFormatting.GRAY))
        for (slot in BodySlot.equippableSlots) {
            source.sendFeedback(formatActiveSlot(slot.displayName, active.equipped[slot]))
        }
        source.sendFeedback(formatActiveSlot("Selected emote", CosmeticCatalog.selectedEmoteId()))
        return Command.SINGLE_SUCCESS
    }

    private fun equip(source: Source, id: Int, variant: String?): Int {
        PolyPlusClient.refreshCosmeticsIfNeeded()

        if (variant == null && CosmeticCatalog.getCosmeticDefinition(id) == null && CosmeticCatalog.getEmoteDefinition(id) != null) {
            return selectEmote(source, id)
        }

        val group = CosmeticCatalog.groupContaining(id)
        if (group == null) {
            source.sendFeedback(
                Component.literal("Cosmetic #$id is not in the catalog.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        val targetId = if (variant != null) {
            val match = CosmeticCatalog.userFacingVariants(group)
                .firstOrNull { it.first.equals(variant, ignoreCase = true) }
            if (match == null) {
                val options = CosmeticCatalog.userFacingVariants(group).joinToString(", ") { it.first }
                source.sendFeedback(
                    Component.literal("Unknown variant '$variant' for ${group.name}. Options: $options")
                        .withStyle(ChatFormatting.RED),
                )
                return Command.SINGLE_SUCCESS
            }
            match.second
        } else {
            if (group.variants.any { it.id == id }) id else group.variants.firstOrNull()?.id
        }

        if (targetId == null) {
            source.sendFeedback(
                Component.literal("${group.name} has no variant to equip.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        if (targetId !in CosmeticCatalog.ownedCosmeticIds()) {
            source.sendFeedback(
                Component.literal("${group.name} is not in your locker.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        val label = CosmeticCatalog.getCosmeticDefinition(targetId)?.let { def ->
            if (def.groupName == def.variantName) def.groupName else "${def.groupName} — ${def.variantName}"
        } ?: "cosmetic #$targetId"

        source.sendFeedback(
            Component.literal("Equipping $label...").withStyle(ChatFormatting.GRAY),
        )

        PolyPlusClient.SCOPE.launch {
            val result = CosmeticService.equip(targetId)
            ClientPlatform.runOnMain {
                result.fold(
                    onSuccess = {
                        source.sendFeedback(
                            Component.literal("Equipped $label.").withStyle(ChatFormatting.GREEN),
                        )
                    },
                    onFailure = { error ->
                        source.sendFeedback(
                            Component.literal("Failed to equip $label: ${error.message}")
                                .withStyle(ChatFormatting.RED),
                        )
                    },
                )
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun selectEmote(source: Source, emoteId: Int): Int {
        if (emoteId !in CosmeticCatalog.ownedEmoteIds()) {
            source.sendFeedback(
                Component.literal("Emote #$emoteId is not in your locker.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        CosmeticCatalog.setSelectedEmote(emoteId)
        source.sendFeedback(
            Component.literal("Selected emote #$emoteId.")
                .withStyle(ChatFormatting.GREEN),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun clear(source: Source, slot: BodySlot): Int {
        val label = slot.displayName.lowercase()
        source.sendFeedback(
            Component.literal("Clearing active $label...")
                .withStyle(ChatFormatting.GRAY),
        )

        PolyPlusClient.SCOPE.launch {
            val result = CosmeticService.clearSlot(slot)
            ClientPlatform.runOnMain {
                result.fold(
                    onSuccess = {
                        source.sendFeedback(
                            Component.literal("Cleared active $label.")
                                .withStyle(ChatFormatting.GREEN),
                        )
                    },
                    onFailure = { error ->
                        source.sendFeedback(
                            Component.literal("Failed to clear $label: ${error.message}")
                                .withStyle(ChatFormatting.RED),
                        )
                    },
                )
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun clearEmote(source: Source): Int {
        CosmeticCatalog.setSelectedEmote(null)
        CosmeticService.stopEmote()
        source.sendFeedback(
            Component.literal("Cleared selected emote.")
                .withStyle(ChatFormatting.GREEN),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun play(source: Source, cosmeticId: Int?): Int {
        val player = Minecraft.getInstance().player
        if (player == null) {
            source.sendFeedback(
                Component.literal("You must be in a world to play an emote.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        val id = cosmeticId ?: CosmeticCatalog.selectedEmoteId()
        if (id == null) {
            source.sendFeedback(
                Component.literal("No selected emote. Use /polyplus cosmetics equip <id> or play <id>.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        if (id !in CosmeticCatalog.ownedEmoteIds()) {
            source.sendFeedback(
                Component.literal("Emote #$id is not in your locker.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        val definition = CosmeticCatalog.getEmoteDefinition(id)
        if (definition?.type != CosmeticType.Emote) {
            source.sendFeedback(
                Component.literal("Emote #$id is not in the emote catalog.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        val started = EmoteApi.playOwnedEmote(player, id)
        if (started) {
            source.sendFeedback(
                Component.literal("Playing emote #$id.")
                    .withStyle(ChatFormatting.GREEN),
            )
        } else {
            source.sendFeedback(
                Component.literal("Could not play emote #$id.")
                    .withStyle(ChatFormatting.RED),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun stop(source: Source): Int {
        val player = Minecraft.getInstance().player
        if (player == null) {
            source.sendFeedback(
                Component.literal("You must be in a world to stop an emote.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        CosmeticService.stopEmote()
        EmoteApi.stop(player)
        source.sendFeedback(
            Component.literal("Stopped emote playback.")
                .withStyle(ChatFormatting.GREEN),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun formatDefinition(definition: CosmeticDefinition): Component {
        val typeLabel = definition.type.name.lowercase()
        val label = if (definition.groupName == definition.variantName) {
            definition.name
        } else {
            "${definition.groupName} — ${definition.variantName}"
        }
        val model = definition.model?.let { " ($it)" } ?: ""
        return Component.literal("#${definition.id} $typeLabel $label$model")
            .withStyle(ChatFormatting.GREEN)
    }

    private fun formatActiveSlot(label: String, id: Int?): Component {
        val value = id?.let { "#$it" } ?: "none"
        return Component.literal("  $label: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(if (id != null) ChatFormatting.GREEN else ChatFormatting.DARK_GRAY))
    }

    private fun ownedEmoteIds(): List<Int> =
        CosmeticCatalog.ownedEmoteIds()
            .mapNotNull(CosmeticCatalog::getEmoteDefinition)
            .map { it.id }
}
//?}
