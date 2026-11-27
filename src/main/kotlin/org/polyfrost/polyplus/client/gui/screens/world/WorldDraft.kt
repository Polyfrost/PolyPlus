package org.polyfrost.polyplus.client.gui.screens.world

import androidx.compose.ui.graphics.ImageBitmap
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.platform.v1.Platform

enum class WorldSection {
    Name,
    Seed,
    GameMode,
    Difficulty,
    AllowCommands,
    BonusChest,
    GenerateStructures,
    WorldType,
    GameRules,
    DataPacks,
    Experiments,
}

enum class WorldGameMode(val label: String, private val key: String) {
    Survival("Survival", "survival"),
    Hardcore("Hardcore", "hardcore"),
    Creative("Creative", "creative"),
    Spectator("Spectator", "spectator"),
    ;

    //? if > 1.8.9 {
    val info: String get() = tr("selectWorld.gameMode.$key.info")
    //?} else {
    /*val info: String get() = "${tr("selectWorld.gameMode.$key.line1")} ${tr("selectWorld.gameMode.$key.line2")}".trim()
    *///?}
}

enum class WorldDifficulty(val label: String) {
    Peaceful("Peaceful"),
    Easy("Easy"),
    Normal("Normal"),
    Hard("Hard"),
    ;

    //? if > 1.8.9 {
    val info: String? get() = tr("options.difficulty.${name.lowercase()}.info")
    //?} else {
    /*val info: String? get() = null
    *///?}
}

internal fun tr(key: String): String = Platform.i18n().getUnformattedText(Component.translatable(key))

data class WorldTypeOption(val id: String, val label: String)

interface WorldDraft {
    val sections: Set<WorldSection>
    val recreated: Boolean

    var name: String
    val folder: String
    var seed: String

    val gameModes: List<WorldGameMode>
    var gameMode: WorldGameMode
    val gameModeLocked: Boolean

    var difficulty: WorldDifficulty
    val difficultyLocked: Boolean

    var allowCommands: Boolean
    val allowCommandsLocked: Boolean

    var bonusChest: Boolean
    val bonusChestLocked: Boolean

    var generateStructures: Boolean
    val generateStructuresLocked: Boolean

    val worldTypes: List<WorldTypeOption>
    val extraWorldTypes: List<WorldTypeOption>
    var worldType: WorldTypeOption
    val worldTypeLocked: Boolean
    val gameRulesLocked: Boolean get() = false
    val canCustomizeType: Boolean
    fun customizeType()

    fun editRules(): WorldRules
    val dataPackCount: Int
    val experimental: Boolean
    fun openDataPacks()
    fun editExperiments(): WorldExperiments?

    fun create()
    fun cancel()
    fun useVanilla()
}

class WorldExperiment(val id: String, val label: String, val description: String?, val enabled: Boolean)

interface WorldExperiments {
    val entries: List<WorldExperiment>

    fun apply(enabled: Set<String>, onRetry: (WorldExperiments?) -> Unit)
}

enum class WorldRuleKind { Toggle, Number }

class WorldRule(
    val id: String,
    val label: String,
    val description: String?,
    val category: String,
    val kind: WorldRuleKind,
    val defaultValue: String,
)

interface WorldRules {
    val rules: List<WorldRule>
    fun value(rule: WorldRule): String
    fun accepts(rule: WorldRule, value: String): Boolean
    fun set(rule: WorldRule, value: String): Boolean
    fun apply()
}

interface EditWorldDraft {
    val name: String
    val folder: String
    val hasIcon: Boolean
    val icon: ImageBitmap?
    val lastPlayed: Long
    val mode: String?
    val version: String?
    val hardcore: Boolean
    val commands: Boolean
    val canResetIcon: Boolean get() = true
    val canBackup: Boolean get() = true
    val canOptimize: Boolean get() = true
    fun save(name: String)
    fun cancel()
    fun resetIcon()
    fun openFolder()
    fun backup()
    fun openBackups()
    fun optimize()
    fun delete()
}

interface DirectJoinDraft {
    val lastAddress: String
    fun isValid(address: String): Boolean
    fun join(address: String)
    fun cancel()
}

class DialogSpec(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String?,
    val danger: Boolean,
    val onResult: (Boolean) -> Unit,
)
