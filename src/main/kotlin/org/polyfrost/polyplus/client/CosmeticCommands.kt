package org.polyfrost.polyplus.client

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticGroupView
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticVariantUi
import org.polyfrost.polyplus.client.gui.wardrobe.variantChips
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import kotlinx.coroutines.launch

object CosmeticCommands {
    private typealias Source = FabricClientCommandSource
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
                        commands.literal("emote")
                            .then(
                                commands.argument("number", IntegerArgumentType.integer(1))
                                    .suggests { _, builder ->
                                        for (id in ownedEmoteIds().sorted()) {
                                            builder.suggest(id)
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes { selectEmote(it.source, IntegerArgumentType.getInteger(it, "number")) },
                            ),
                    )
                    .then(
                        commands.argument("number", IntegerArgumentType.integer(1))
                            .suggests { _, builder ->
                                val owned = CosmeticCatalog.ownedCosmeticIds()
                                for (id in CosmeticCatalog.ownedCosmeticGroups().map { commandNumber(it, owned) }.sorted()) {
                                    builder.suggest(id)
                                }
                                builder.buildFuture()
                            }
                            .executes { equip(it.source, IntegerArgumentType.getInteger(it, "number"), variant = null) }
                            .then(
                                commands.argument("variant", StringArgumentType.greedyString())
                                    .suggests { ctx, builder ->
                                        val id = IntegerArgumentType.getInteger(ctx, "number")
                                        CosmeticCatalog.groupContaining(id)?.let { group ->
                                            for (chip in lockerChips(group, CosmeticCatalog.ownedCosmeticIds(), equippedIds())) {
                                                builder.suggest(chip.name)
                                            }
                                        }
                                        builder.buildFuture()
                                    }
                                    .executes {
                                        equip(
                                            it.source,
                                            IntegerArgumentType.getInteger(it, "number"),
                                            StringArgumentType.getString(it, "variant"),
                                        )
                                    },
                            ),
                    ),
            )
            .then(
                BodySlot.equippableSlots.fold(commands.literal("clear")) { node, slot ->
                    node.then(commands.literal(slot.serializedName).executes { clear(it.source, slot) })
                }.then(commands.literal("emote").executes { clearEmote(it.source) }),
            )
            .then(
                commands.literal("play")
                    .executes { play(it.source, cosmeticId = null) }
                    .then(
                        commands.argument("number", IntegerArgumentType.integer(1))
                            .suggests { _, builder ->
                                for (id in ownedEmoteIds().sorted()) {
                                    builder.suggest(id)
                                }
                                builder.buildFuture()
                            }
                            .executes { play(it.source, IntegerArgumentType.getInteger(it, "number")) },
                    ),
            )
            .then(commands.literal("stop").executes { stop(it.source) })

    private fun list(source: Source, ownedOnly: Boolean): Int {
        if (Minecraft.getInstance().player != null) {
            PolyPlusClient.refreshCosmeticsIfNeeded()
        }

        val ownedCosmetics = CosmeticCatalog.ownedCosmeticIds()
        val equipped = equippedIds()
        val groups = CosmeticCatalog.cosmeticGroupViews()
            .let { views ->
                if (ownedOnly) views.filter { group -> group.variants.any { it.id in ownedCosmetics } } else views
            }
            .sortedWith(compareBy({ it.type.ordinal }, { commandNumber(it, ownedCosmetics) }))

        val emotes = if (ownedOnly) {
            CosmeticCatalog.ownedEmoteIds().mapNotNull(CosmeticCatalog::getEmoteDefinition)
        } else {
            CosmeticCatalog.allEmoteDefinitions().toList()
        }.sortedBy(CosmeticDefinition::id)

        if (groups.isEmpty() && emotes.isEmpty()) {
            source.sendFeedback(
                Component.literal(
                    if (ownedOnly) {
                        "Your locker is empty or still loading. It loads when you join a world, so try again in a moment."
                    } else {
                        "The cosmetic catalog is empty or still loading. It loads when you join a world, so try again in a moment."
                    },
                ).withStyle(ChatFormatting.GRAY),
            )
            return Command.SINGLE_SUCCESS
        }

        if (groups.isNotEmpty()) {
            source.sendFeedback(
                Component.literal(
                    if (ownedOnly) {
                        "Your cosmetics (equip one with /polyplus cosmetics equip <number>):"
                    } else {
                        "All cosmetics (equip one with /polyplus cosmetics equip <number>):"
                    },
                ).withStyle(ChatFormatting.GRAY),
            )
        }
        for (group in groups) {
            val typeLabel = group.type.name.lowercase()
            source.sendFeedback(
                Component.literal("#${commandNumber(group, ownedCosmetics)} ${group.name} ($typeLabel)")
                    .withStyle(ChatFormatting.GREEN),
            )
            val labels = if (ownedOnly) {
                lockerChips(group, ownedCosmetics, equipped).filter { chip -> chip.pieces.all { it in ownedCosmetics } }
            } else {
                variantChips(group)
            }.map { it.name }
            if (labels.size > 1) {
                source.sendFeedback(
                    Component.literal("  ${labels.joinToString(", ")}")
                        .withStyle(ChatFormatting.GRAY),
                )
            }
        }

        if (emotes.isNotEmpty()) {
            source.sendFeedback(
                Component.literal("Emotes (pick one with /polyplus cosmetics equip emote <number>):")
                    .withStyle(ChatFormatting.GRAY),
            )
            for (emote in emotes) {
                source.sendFeedback(formatDefinition(emote))
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun active(source: Source): Int {
        val active = CosmeticCatalog.localEquipped()
        val owned = CosmeticCatalog.ownedCosmeticIds()
        source.sendFeedback(Component.literal("Active cosmetics:").withStyle(ChatFormatting.GRAY))
        for (slot in BodySlot.equippableSlots) {
            val id = active.equipped[slot]
            val definition = id?.let(CosmeticCatalog::getCosmeticDefinition)
            val value = when {
                id == null -> null
                definition == null -> "unknown cosmetic"
                else -> "${displayName(definition)} #${CosmeticCatalog.groupContaining(id)?.let { commandNumber(it, owned) } ?: id}"
            }
            source.sendFeedback(formatActiveSlot(slot.labelFor(definition), value))
        }
        val emote = CosmeticCatalog.selectedEmoteId()
        val emoteValue = emote?.let { id -> CosmeticCatalog.getEmoteDefinition(id)?.let { "${displayName(it)} #$id" } ?: "emote #$id" }
        source.sendFeedback(formatActiveSlot("Selected emote", emoteValue))
        return Command.SINGLE_SUCCESS
    }

    private fun equip(source: Source, number: Int, variant: String?): Int {
        PolyPlusClient.refreshCosmeticsIfNeeded()

        val group = CosmeticCatalog.groupContaining(number)
        if (group == null) {
            source.sendFeedback(
                Component.literal("There's no cosmetic #$number. Run /polyplus cosmetics list to see the numbers.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        val owned = CosmeticCatalog.ownedCosmeticIds()
        val target = when (val choice = equipChoice(group, number, variant, owned, equippedIds())) {
            is EquipChoice.Chosen -> choice.chip
            is EquipChoice.NotOwned -> {
                source.sendFeedback(
                    Component.literal("You don't own the ${choice.variant} variant of ${group.name}.")
                        .withStyle(ChatFormatting.RED),
                )
                return Command.SINGLE_SUCCESS
            }
            is EquipChoice.NoSuchVariant -> {
                val options = choice.options.joinToString(", ")
                source.sendFeedback(
                    Component.literal("${group.name} has no '$variant' variant. Try one of these: $options")
                        .withStyle(ChatFormatting.RED),
                )
                return Command.SINGLE_SUCCESS
            }
            EquipChoice.Empty -> {
                source.sendFeedback(
                    Component.literal("${group.name} has no variant to equip.")
                        .withStyle(ChatFormatting.RED),
                )
                return Command.SINGLE_SUCCESS
            }
        }

        val missing = target.pieces.count { it !in owned }
        if (missing > 0) {
            val message = if (missing < target.pieces.size) {
                "You only own one side of ${group.name}."
            } else {
                "${group.name} isn't in your locker."
            }
            source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.RED))
            return Command.SINGLE_SUCCESS
        }

        val label = commandLabel(group, target)

        return announce(source, "Equipping $label...", "Equipped $label.", "Couldn't equip $label") {
            CosmeticService.awaitSkinChangeFor(if (target.sided) target.pieces else listOf(target.id))
            CosmeticService.equipQueue.run {
                if (target.sided) {
                    val members = group.variants.mapTo(HashSet()) { it.id }
                    val worn = CosmeticCatalog.localEquipped().equipped.filterValues { it in members }.keys
                    CosmeticService.equipPieces(target.pieces, worn)
                } else {
                    CosmeticService.equip(target.id)
                }
            }
        }
    }

    private fun commandLabel(group: CosmeticGroupView, chip: CosmeticVariantUi): String {
        val definition = CosmeticCatalog.getCosmeticDefinition(chip.artId)
        val name = definition?.groupName ?: group.name
        val finish = definition?.variantName?.takeIf { it != definition.groupName }
        val hand = when {
            chip.pieces.size > 1 -> "both hands"
            chip.side == BodySlot.LeftHand -> "left hand"
            chip.side == BodySlot.RightHand -> "right hand"
            else -> null
        }
        val details = listOfNotNull(finish, hand)
        return if (details.isEmpty()) name else "$name (${details.joinToString(", ")})"
    }

    /** Reports progress and the outcome of the action on the main thread. */
    private fun announce(
        source: Source,
        pending: String,
        success: String,
        failure: String,
        action: suspend () -> Result<Unit>,
    ): Int {
        source.sendFeedback(Component.literal(pending).withStyle(ChatFormatting.GRAY))

        PolyPlusClient.SCOPE.launch {
            val result = runSuspendCatching { action().getOrThrow() }
            ClientPlatform.runOnMain {
                result.fold(
                    onSuccess = {
                        source.sendFeedback(Component.literal(success).withStyle(ChatFormatting.GREEN))
                    },
                    onFailure = { error ->
                        source.sendFeedback(
                            Component.literal("$failure: ${error.message}").withStyle(ChatFormatting.RED),
                        )
                    },
                )
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun selectEmote(source: Source, emoteId: Int): Int {
        PolyPlusClient.refreshCosmeticsIfNeeded()
        val definition = CosmeticCatalog.getEmoteDefinition(emoteId)
        if (definition == null) {
            source.sendFeedback(
                Component.literal("There's no emote #$emoteId. Run /polyplus cosmetics list to see the numbers.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }
        if (emoteId !in CosmeticCatalog.ownedEmoteIds()) {
            source.sendFeedback(
                Component.literal("${displayName(definition)} isn't in your locker.")
                    .withStyle(ChatFormatting.RED),
            )
            return Command.SINGLE_SUCCESS
        }

        CosmeticCatalog.setSelectedEmote(emoteId)
        source.sendFeedback(
            Component.literal("Selected the ${displayName(definition)} emote.")
                .withStyle(ChatFormatting.GREEN),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun clear(source: Source, slot: BodySlot): Int {
        val equipped = CosmeticCatalog.localEquipped().equipped[slot]?.let(CosmeticCatalog::getCosmeticDefinition)
        val label = if (slot.namedByCatalogue) slot.labelFor(equipped) else slot.displayName.lowercase()
        return announce(
            source,
            "Clearing active $label...",
            "Cleared active $label.",
            "Failed to clear $label",
        ) {
            CosmeticService.equipQueue.run { CosmeticService.clearSlot(slot) }
        }
    }

    private fun clearEmote(source: Source): Int {
        CosmeticService.clearEmote()
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
                Component.literal("You haven't picked an emote. Use /polyplus cosmetics equip emote <number> or play <number>.")
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

        DuoEmotes.stopAll()
        CosmeticService.stopEmote()
        EmoteApi.stop(player)
        source.sendFeedback(
            Component.literal("Stopped emote playback.")
                .withStyle(ChatFormatting.GREEN),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun equippedIds(): Set<Int> = CosmeticCatalog.localEquipped().ids().toHashSet()

    private fun formatDefinition(definition: CosmeticDefinition): Component =
        Component.literal("#${definition.id} ${displayName(definition)}")
            .withStyle(ChatFormatting.GREEN)

    private fun displayName(definition: CosmeticDefinition): String =
        if (definition.groupName == definition.variantName) definition.name else "${definition.groupName} (${definition.variantName})"

    private fun formatActiveSlot(label: String, value: String?): Component =
        Component.literal("  $label: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value ?: "none").withStyle(if (value != null) ChatFormatting.GREEN else ChatFormatting.DARK_GRAY))

    private fun ownedEmoteIds(): List<Int> =
        CosmeticCatalog.ownedEmoteIds()
            .mapNotNull(CosmeticCatalog::getEmoteDefinition)
            .map { it.id }
}
