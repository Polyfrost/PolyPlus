package org.polyfrost.polyplus.client.gui.screens.worldoptions

//? if >= 1.21.11 {
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleType
import net.minecraft.world.level.gamerules.GameRules
//?} elif > 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameRules
*///?} else {
/*import net.minecraft.client.Minecraft
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket
import net.minecraft.world.GameRules
*///?}

internal class RuleEntry(
    val id: String,
    val label: String,
    val category: String,
    val categoryOrder: Int,
    val bool: Boolean,
    val value: Any,
    val min: Int,
    val max: Int,
)

internal object GameRulesPlatform {
    //? if >= 1.21.11 {
    private val categoryOrder = listOf("player", "mobs", "spawning", "drops", "updates", "chat", "misc")

    fun read(): List<RuleEntry> {
        val server = Minecraft.getInstance().singleplayerServer ?: return emptyList()
        val rules = rulesOf(server)
        return rules.availableRules().map { entryOf(rules, it) }.toList().sortedWith(compareBy({ it.categoryOrder }, { it.label }))
    }

    fun write(values: Map<String, Any>) {
        val server = Minecraft.getInstance().singleplayerServer ?: return
        server.execute {
            val rules = rulesOf(server)
            rules.availableRules().forEach { rule -> values[rule.id()]?.let { assign(rules, rule, it, server) } }
        }
    }

    //? if >= 26.1 {
    private fun rulesOf(server: MinecraftServer): GameRules = server.gameRules
    //?} else {
    /*private fun rulesOf(server: MinecraftServer): GameRules = server.overworld().gameRules
    *///?}

    private fun <T : Any> entryOf(rules: GameRules, rule: GameRule<T>): RuleEntry {
        val argument = rule.argument() as? IntegerArgumentType
        return RuleEntry(
            rule.id(),
            labelOf(rule.descriptionId, rule.id()),
            rule.category().label().string,
            categoryOrder.indexOf(rule.category().id().path).let { if (it < 0) categoryOrder.size else it },
            rule.gameRuleType() == GameRuleType.BOOL,
            rules.get(rule),
            argument?.minimum ?: Int.MIN_VALUE,
            argument?.maximum ?: Int.MAX_VALUE,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> assign(rules: GameRules, rule: GameRule<T>, value: Any, server: MinecraftServer) {
        rules.set(rule, value as T, server)
    }

    private fun labelOf(key: String, fallback: String): String =
        if (Language.getInstance().has(key)) Language.getInstance().getOrDefault(key) else fallback
    //?} elif > 1.8.9 {
    /*fun read(): List<RuleEntry> {
        val server = Minecraft.getInstance().singleplayerServer ?: return emptyList()
        val rules = server.gameRules
        val out = mutableListOf<RuleEntry>()
        visit(rules, object : GameRules.GameRuleTypeVisitor {
            override fun visitBoolean(key: GameRules.Key<GameRules.BooleanValue>, type: GameRules.Type<GameRules.BooleanValue>) {
                out += entryOf(key.id, key.descriptionId, key.category, true, rules.getRule(key).get())
            }

            override fun visitInteger(key: GameRules.Key<GameRules.IntegerValue>, type: GameRules.Type<GameRules.IntegerValue>) {
                out += entryOf(key.id, key.descriptionId, key.category, false, rules.getRule(key).get())
            }
        })
        return out.sortedWith(compareBy({ it.categoryOrder }, { it.label }))
    }

    fun write(values: Map<String, Any>) {
        val server = Minecraft.getInstance().singleplayerServer ?: return
        server.execute {
            val rules = server.gameRules
            visit(rules, object : GameRules.GameRuleTypeVisitor {
                override fun visitBoolean(key: GameRules.Key<GameRules.BooleanValue>, type: GameRules.Type<GameRules.BooleanValue>) {
                    (values[key.id] as? Boolean)?.let { rules.getRule(key).set(it, server) }
                }

                override fun visitInteger(key: GameRules.Key<GameRules.IntegerValue>, type: GameRules.Type<GameRules.IntegerValue>) {
                    (values[key.id] as? Int)?.let { rules.getRule(key).set(it, server) }
                }
            })
        }
    }

    //? if >= 1.21.4 {
    private fun visit(rules: GameRules, visitor: GameRules.GameRuleTypeVisitor) = rules.visitGameRuleTypes(visitor)
    //?} else {
    /*private fun visit(rules: GameRules, visitor: GameRules.GameRuleTypeVisitor) = GameRules.visitGameRuleTypes(visitor)
    *///?}

    private fun entryOf(id: String, descriptionId: String, category: GameRules.Category, bool: Boolean, value: Any): RuleEntry =
        RuleEntry(
            id,
            labelOf(descriptionId, id),
            Component.translatable(category.descriptionId).string,
            category.ordinal,
            bool,
            value,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
        )

    private fun labelOf(key: String, fallback: String): String =
        if (Language.getInstance().has(key)) Language.getInstance().getOrDefault(key) else fallback
    *///?} else {
    /*fun read(): List<RuleEntry> {
        val server = Minecraft.getInstance().server ?: return emptyList()
        val rules = server.getWorld(0).data.gameRules
        return rules.all.mapNotNull { name ->
            when {
                rules.isType(name, GameRules.Type.BOOLEAN_VALUE) -> RuleEntry(name, name, CATEGORY, 0, true, rules.getBoolean(name), 0, 1)
                rules.isType(name, GameRules.Type.NUMERICAL_VALUE) -> RuleEntry(name, name, CATEGORY, 0, false, rules.getInt(name), Int.MIN_VALUE, Int.MAX_VALUE)
                else -> null
            }
        }.sortedBy { it.label }
    }

    fun write(values: Map<String, Any>) {
        val server = Minecraft.getInstance().server ?: return
        server.executeTask(Runnable {
            val rules = server.getWorld(0).data.gameRules
            values.forEach { (name, value) -> rules.set(name, value.toString()) }
            if (REDUCED_DEBUG_INFO in values) {
                val status: Byte = if (rules.getBoolean(REDUCED_DEBUG_INFO)) REDUCED_DEBUG_ON else REDUCED_DEBUG_OFF
                server.playerManager.all.forEach { it.networkHandler.sendPacket(EntityEventS2CPacket(it, status)) }
            }
        })
    }

    private const val REDUCED_DEBUG_INFO = "reducedDebugInfo"
    private const val REDUCED_DEBUG_ON: Byte = 22
    private const val REDUCED_DEBUG_OFF: Byte = 23

    private const val CATEGORY = "Game rules"
    *///?}
}
