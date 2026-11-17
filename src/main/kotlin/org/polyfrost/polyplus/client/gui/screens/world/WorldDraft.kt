package org.polyfrost.polyplus.client.gui.screens.world

import androidx.compose.ui.graphics.ImageBitmap

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

enum class WorldGameMode(val label: String, val info: String) {
    Survival("Survival", "Gather, build and stay alive. Lose your items when you die."),
    //? if > 1.8.9 {
    Hardcore("Hardcore", "Survival on Hard with one life. When you die, you can only spectate."),
    //?} else {
    /*Hardcore("Hardcore", "Survival on Hard with one life. The world is deleted when you die."),
    *///?}
    Creative("Creative", "Every block, free flight and nothing can hurt you."),
    Spectator("Spectator", "Fly through anything and look around without touching it."),
}

enum class WorldDifficulty(val label: String, val info: String) {
    Peaceful("Peaceful", "No hostile mobs and your health comes back on its own."),
    Easy("Easy", "Hostile mobs spawn but deal less damage."),
    Normal("Normal", "Hostile mobs spawn and deal normal damage."),
    Hard("Hard", "Hostile mobs deal more damage and hunger can kill you."),
}

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
    fun openExperiments()

    fun create()
    fun cancel()
    fun useVanilla()
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
