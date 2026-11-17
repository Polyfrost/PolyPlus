package org.polyfrost.polyplus.client.gui.screens.world

//? if = 1.8.9 {
/*import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen
import net.minecraft.client.gui.screen.menu.world.CustomizeFlatWorldScreen
import net.minecraft.client.gui.screen.menu.world.CustomizeWorldScreen
import net.minecraft.client.gui.screen.menu.world.EditWorldScreen
import net.minecraft.client.resource.language.I18n
import net.minecraft.world.WorldData
import net.minecraft.world.WorldSettings
import net.minecraft.world.gen.WorldGeneratorType
import org.apache.commons.lang3.StringUtils
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.utils.v1.Multithreading
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.WorldLegacyCreateAccessor
import org.polyfrost.polyplus.mixin.client.access.WorldLegacyEditAccessor
import java.io.File
import java.util.Random

private val TYPE_IDS = mapOf(
    "default" to "minecraft:normal",
    "flat" to "minecraft:flat",
    "largeBiomes" to "minecraft:large_biomes",
    "amplified" to "minecraft:amplified",
    "customized" to "customized",
    "debug_all_block_states" to "minecraft:debug_all_block_states",
)

private fun typeOption(type: WorldGeneratorType): WorldTypeOption =
    WorldTypeOption(TYPE_IDS[type.getKey()] ?: type.getKey(), I18n.translate(type.getTranslationKey()))

private object NoWorldRules : WorldRules {
    override val rules: List<WorldRule> = emptyList()
    override fun value(rule: WorldRule): String = rule.defaultValue
    override fun accepts(rule: WorldRule, value: String): Boolean = false
    override fun set(rule: WorldRule, value: String): Boolean = false
    override fun apply() {}
}

internal class WorldDraft189(private val screen: CreateWorldScreen) : WorldDraft {
    private val mc = Minecraft.getInstance()
    private val access = screen as WorldLegacyCreateAccessor
    private var creating = false
    private var cheatsEnabled = false
    private var typeId by mutableIntStateOf(access.polyplusGeneratorType())
    private var modeName by mutableStateOf(access.polyplusGameModeName())
    private var commandsValue by mutableStateOf(access.polyplusAllowCommands())
    private var bonusChestValue by mutableStateOf(false)
    private var folderKey: String? = null
    private var folderValue = ""

    private val type: WorldGeneratorType get() = WorldGeneratorType.BY_ID[typeId] ?: WorldGeneratorType.DEFAULT

    private val debug: Boolean get() = type == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES

    private val hardcore: Boolean get() = !debug && modeName == "hardcore"

    override val sections: Set<WorldSection> = setOf(
        WorldSection.Name,
        WorldSection.Seed,
        WorldSection.GameMode,
        WorldSection.AllowCommands,
        WorldSection.BonusChest,
        WorldSection.GenerateStructures,
        WorldSection.WorldType,
    )

    override val recreated: Boolean = access.polyplusSeed().isNotEmpty()

    override var name: String by mutableStateOf(access.polyplusWorldName())

    override val folder: String
        get() {
            val display = name
            if (display != folderKey) {
                folderKey = display
                folderValue = saveName(display)
            }
            return folderValue
        }

    private fun saveName(display: String): String {
        var folder = display.trim()
        for (c in SharedConstants.INVALID_FILE_CHARS) folder = folder.replace(c, '_')
        if (StringUtils.isEmpty(folder)) folder = I18n.translate("selectWorld.newWorld")
        return CreateWorldScreen.getWorldSaveName(mc.getWorldStorageSource(), folder)
    }

    override var seed: String by mutableStateOf(access.polyplusSeed())

    override val gameModes: List<WorldGameMode>
        get() = if (debug) listOf(WorldGameMode.Spectator) else listOf(WorldGameMode.Survival, WorldGameMode.Hardcore, WorldGameMode.Creative)

    override var gameMode: WorldGameMode
        get() = if (debug) {
            WorldGameMode.Spectator
        } else {
            when (modeName) {
                "hardcore" -> WorldGameMode.Hardcore
                "creative" -> WorldGameMode.Creative
                else -> WorldGameMode.Survival
            }
        }
        set(value) {
            if (debug) return
            val next = when (value) {
                WorldGameMode.Survival -> "survival"
                WorldGameMode.Hardcore -> "hardcore"
                WorldGameMode.Creative -> "creative"
                WorldGameMode.Spectator -> return
            }
            if (!cheatsEnabled) commandsValue = next == "creative"
            modeName = next
        }

    override val gameModeLocked: Boolean get() = debug

    override var difficulty: WorldDifficulty
        get() = WorldDifficulty.Normal
        set(_) {}

    override val difficultyLocked: Boolean get() = true

    override var allowCommands: Boolean
        get() = commandsValue && !hardcore
        set(value) {
            cheatsEnabled = true
            commandsValue = value
        }

    override val allowCommandsLocked: Boolean get() = debug || hardcore

    override var bonusChest: Boolean
        get() = bonusChestValue && !hardcore
        set(value) {
            bonusChestValue = value
        }

    override val bonusChestLocked: Boolean get() = debug || hardcore

    override var generateStructures: Boolean by mutableStateOf(access.polyplusStructures())

    override val generateStructuresLocked: Boolean get() = debug || type == WorldGeneratorType.CUSTOMIZED

    private fun shownTypes(): List<WorldGeneratorType> =
        WorldGeneratorType.BY_ID.filterNotNull().filter { it.isVisible() && it != WorldGeneratorType.DEBUG_ALL_BLOCK_STATES }

    override val worldTypes: List<WorldTypeOption>
        get() {
            val shown = shownTypes()
            val current = type
            val list = if (current !in shown && current != WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) shown + current else shown
            return list.map(::typeOption)
        }

    override val extraWorldTypes: List<WorldTypeOption> = listOf(typeOption(WorldGeneratorType.DEBUG_ALL_BLOCK_STATES))

    override var worldType: WorldTypeOption
        get() = typeOption(type)
        set(value) {
            val next = WorldGeneratorType.BY_ID.filterNotNull().firstOrNull { typeOption(it).id == value.id } ?: return
            if (next.getId() == typeId) return
            typeId = next.getId()
            screen.generatorOptions = ""
        }

    override val worldTypeLocked: Boolean get() = false

    override val canCustomizeType: Boolean
        get() = type == WorldGeneratorType.FLAT || type == WorldGeneratorType.CUSTOMIZED

    override fun customizeType() {
        val options = screen.generatorOptions
        when (type) {
            WorldGeneratorType.FLAT -> ClientPlatform.setScreen(CustomizeFlatWorldScreen(screen, options))
            WorldGeneratorType.CUSTOMIZED -> ClientPlatform.setScreen(CustomizeWorldScreen(screen, options))
            else -> {}
        }
    }

    override fun editRules(): WorldRules = NoWorldRules

    override val dataPackCount: Int get() = 0

    override val experimental: Boolean get() = false

    override fun openDataPacks() {}

    override fun openExperiments() {}

    override fun create() {
        ClientPlatform.setScreen(null)
        if (creating) return
        creating = true
        var seedValue = Random().nextLong()
        val text = seed
        if (!StringUtils.isEmpty(text)) {
            try {
                val parsed = text.toLong()
                if (parsed != 0L) seedValue = parsed
            } catch (_: NumberFormatException) {
                seedValue = text.hashCode().toLong()
            }
        }
        val hardcore = hardcore
        val mode = WorldSettings.GameMode.byKey(if (debug) "spectator" else modeName)
        val settings = WorldSettings(seedValue, mode, generateStructures, hardcore, type)
        settings.setGeneratorOptions(screen.generatorOptions)
        if (bonusChestValue && !hardcore) settings.enableBonusChest()
        if (commandsValue && !hardcore) settings.enableCommands()
        mc.startGame(folder, name.trim().ifEmpty { folder }, settings)
    }

    override fun cancel() = ClientPlatform.setScreen(access.polyplusParent())

    override fun useVanilla() {
        access.polyplusSetWorldName(name)
        access.polyplusSetSeed(seed)
        access.polyplusSetStructures(generateStructures)
        access.polyplusSetBonusChest(bonusChestValue)
        access.polyplusSetAllowCommands(commandsValue)
        access.polyplusSetCheatsEnabled(cheatsEnabled)
        if (!debug) {
            access.polyplusSetGeneratorType(typeId)
            access.polyplusSetGameModeName(modeName)
            access.polyplusSetHardcore(hardcore)
        }
        WorldRoutes.openVanilla(screen)
    }
}

internal class EditWorldDraft189(screen: EditWorldScreen) : EditWorldDraft {
    private val mc = Minecraft.getInstance()
    private val access = screen as WorldLegacyEditAccessor
    private val storage = mc.getWorldStorageSource()

    override val folder: String = access.polyplusWorldName()

    private val data: WorldData? = runCatching { storage.getData(folder) }.getOrNull()

    override val name: String = data?.getName() ?: folder

    override val hasIcon: Boolean get() = false

    override val icon: ImageBitmap? get() = null

    override val lastPlayed: Long get() = data?.getLastPlayed() ?: 0L

    override val hardcore: Boolean get() = data?.isHardcore() == true

    override val mode: String?
        get() = data?.let {
            if (it.isHardcore()) I18n.translate("selectWorld.gameMode.hardcore")
            else I18n.translate("selectWorld.gameMode." + it.getDefaultGamemode().getKey())
        }

    override val version: String? get() = null

    override val commands: Boolean get() = data?.allowCommands() == true

    override val canResetIcon: Boolean get() = false

    override val canBackup: Boolean get() = false

    override val canOptimize: Boolean get() = false

    override fun save(name: String) {
        if (name.trim().isEmpty()) return
        storage.rename(folder, name.trim())
        ClientPlatform.setScreen(access.polyplusParent())
    }

    override fun cancel() = ClientPlatform.setScreen(access.polyplusParent())

    override fun resetIcon() {}

    override fun openFolder() {
        val dir = File(File(mc.gameDir, "saves"), folder)
        Multithreading.submit { DesktopHelper.open(dir) }
    }

    override fun backup() {}

    override fun openBackups() {}

    override fun optimize() {}

    override fun delete() {
        storage.flush()
        storage.delete(folder)
        ClientPlatform.setScreen(access.polyplusParent())
    }
}
*///?}
