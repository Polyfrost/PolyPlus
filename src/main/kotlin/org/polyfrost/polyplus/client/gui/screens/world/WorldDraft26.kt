package org.polyfrost.polyplus.client.gui.screens.world

//? if > 1.8.9 {
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
//? if >= 26.3 {
import com.mojang.blaze3d.Blaze3D
//?} elif >= 1.21.11 {
/*import net.minecraft.util.Util
*///?} else {
/*import net.minecraft.Util
*///?}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.AlertScreen
import net.minecraft.client.gui.screens.BackupConfirmScreen
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.DirectJoinServerScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen
import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.world.Difficulty
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.WorldDataConfiguration
//? if >= 1.21.11 {
import net.minecraft.util.FileUtil
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleType
import net.minecraft.world.level.gamerules.GameRules
//?} else {
/*import net.minecraft.FileUtil
import net.minecraft.world.level.GameRules
*///?}
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.LevelSummary
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.apache.commons.io.FileUtils
import org.polyfrost.polyplus.client.utils.ClientPlatform
//? if < 1.21.11
/*import org.polyfrost.polyplus.mixin.client.access.ScreenBindAccessor*/
import org.polyfrost.polyplus.mixin.client.access.WorldAlertAccessor
import org.polyfrost.polyplus.mixin.client.access.WorldConfirmAccessor
import org.polyfrost.polyplus.mixin.client.access.WorldCreateScreenAccessor
import org.polyfrost.polyplus.mixin.client.access.WorldDirectJoinAccessor
import org.polyfrost.polyplus.mixin.client.access.WorldEditScreenAccessor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private val LOGGER = LogManager.getLogger("PolyPlus/WorldDraft")
private const val VANILLA_PACK = "vanilla"

internal fun bindVanilla(screen: Screen) {
    //? if < 1.21.11 {
    /*val mc = Minecraft.getInstance()
    val access = screen as ScreenBindAccessor
    access.polyplusSetMinecraft(mc)
    access.polyplusSetFont(mc.font)
    *///?}
}

private fun openPath(path: Path) {
    //? if >= 26.3 {
    Blaze3D.openPath(path)
    //?} else {
    /*Util.getPlatform().openPath(path)
    *///?}
}

internal class WorldDraft26(private val screen: CreateWorldScreen) : WorldDraft {
    private val ui: WorldCreationUiState = screen.uiState
    private val access = screen as WorldCreateScreenAccessor
    private val tick = mutableIntStateOf(0)

    init {
        bindVanilla(screen)
        ui.addListener { tick.intValue++ }
    }

    private inline fun <T> read(block: () -> T): T {
        tick.intValue
        return block()
    }

    override val sections: Set<WorldSection> = WorldSection.entries.toSet()

    override val recreated: Boolean get() = access.polyplusRecreated()

    override var name: String
        get() = read { ui.name }
        set(value) = ui.setName(value)

    override val folder: String get() = read { ui.targetFolder }

    override var seed: String
        get() = read { ui.seed }
        set(value) = ui.setSeed(value)

    override val gameModes: List<WorldGameMode>
        get() = read { if (ui.isDebug) listOf(WorldGameMode.Spectator) else listOf(WorldGameMode.Survival, WorldGameMode.Hardcore, WorldGameMode.Creative) }

    override var gameMode: WorldGameMode
        get() = read {
            when (ui.gameMode) {
                WorldCreationUiState.SelectedGameMode.HARDCORE -> WorldGameMode.Hardcore
                WorldCreationUiState.SelectedGameMode.CREATIVE -> WorldGameMode.Creative
                WorldCreationUiState.SelectedGameMode.DEBUG -> WorldGameMode.Spectator
                else -> WorldGameMode.Survival
            }
        }
        set(value) {
            val mode = when (value) {
                WorldGameMode.Survival -> WorldCreationUiState.SelectedGameMode.SURVIVAL
                WorldGameMode.Hardcore -> WorldCreationUiState.SelectedGameMode.HARDCORE
                WorldGameMode.Creative -> WorldCreationUiState.SelectedGameMode.CREATIVE
                WorldGameMode.Spectator -> return
            }
            ui.setGameMode(mode)
        }

    override val gameModeLocked: Boolean get() = read { ui.isDebug }

    override var difficulty: WorldDifficulty
        get() = read { if (ui.isDebug) WorldDifficulty.Peaceful else WorldDifficulty.entries[ui.difficulty.ordinal.coerceIn(0, WorldDifficulty.entries.size - 1)] }
        set(value) = ui.setDifficulty(Difficulty.entries[value.ordinal])

    override val difficultyLocked: Boolean get() = read { ui.isHardcore || ui.isDebug }

    override val gameRulesLocked: Boolean get() = read { ui.isDebug }

    override var allowCommands: Boolean
        get() = read { ui.isAllowCommands }
        set(value) = ui.setAllowCommands(value)

    override val allowCommandsLocked: Boolean get() = read { ui.isDebug || ui.isHardcore }

    override var bonusChest: Boolean
        get() = read { ui.isBonusChest }
        set(value) = ui.setBonusChest(value)

    override val bonusChestLocked: Boolean get() = read { ui.isHardcore || ui.isDebug }

    override var generateStructures: Boolean
        get() = read { ui.isGenerateStructures }
        set(value) = ui.setGenerateStructures(value)

    override val generateStructuresLocked: Boolean get() = read { ui.isDebug }

    private fun option(entry: WorldCreationUiState.WorldTypeEntry): WorldTypeOption {
        //? if >= 1.21.11 {
        val id = entry.preset()?.unwrapKey()?.map { it.identifier().toString() }?.orElse(null) ?: "custom"
        //?} else {
        /*val id = entry.preset()?.unwrapKey()?.map { it.location().toString() }?.orElse(null) ?: "custom"
        *///?}
        return WorldTypeOption(id, entry.describePreset().string)
    }

    private fun entries(): List<WorldCreationUiState.WorldTypeEntry> {
        val list = ui.normalPresetList.toMutableList()
        if (ui.worldType !in list) list.add(ui.worldType)
        return list
    }

    private fun extraEntries(): List<WorldCreationUiState.WorldTypeEntry> {
        val normal = entries()
        return ui.altPresetList.filter { it !in normal }
    }

    override val worldTypes: List<WorldTypeOption> get() = read { entries().map(::option) }

    override val extraWorldTypes: List<WorldTypeOption> get() = read { extraEntries().map(::option) }

    override var worldType: WorldTypeOption
        get() = read { option(ui.worldType) }
        set(value) {
            val entry = (entries() + extraEntries()).firstOrNull { option(it).id == value.id } ?: return
            if (entry.preset() != null) ui.setWorldType(entry)
        }

    override val worldTypeLocked: Boolean get() = read { ui.worldType.preset() == null }

    override val canCustomizeType: Boolean get() = read { !ui.isDebug && ui.presetEditor != null }

    override fun customizeType() {
        val editor = ui.presetEditor ?: return
        ClientPlatform.setScreen(editor.createEditScreen(screen, ui.settings))
    }

    override fun editRules(): WorldRules = WorldRules26(ui)

    override val dataPackCount: Int
        get() = read { ui.settings.dataConfiguration().dataPacks().enabled.count { it != VANILLA_PACK } }

    override val experimental: Boolean
        get() = read { FeatureFlags.isExperimental(ui.settings.dataConfiguration().enabledFeatures()) }

    override fun openDataPacks() = access.polyplusOpenDataPacks(ui.settings.dataConfiguration())

    override fun editExperiments(): WorldExperiments? = experimentsFor(ui.settings.dataConfiguration())

    private fun experimentsFor(configuration: WorldDataConfiguration): WorldExperiments? {
        val repository = access.polyplusDataPackSettings(configuration)?.second ?: return null
        return WorldExperiments26(repository)
    }

    private inner class WorldExperiments26(private val repository: PackRepository) : WorldExperiments {
        private val packs = repository.availablePacks.filter { it.packSource == PackSource.FEATURE }

        override val entries: List<WorldExperiment> = packs.map { pack ->
            val key = "dataPack.${pack.id}.name"
            val description = pack.description.string
            WorldExperiment(
                id = pack.id,
                label = if (Language.getInstance().has(key)) Component.translatable(key).string else pack.title.string,
                description = description.takeIf { it.isNotBlank() },
                enabled = pack in repository.selectedPacks,
            )
        }

        override fun apply(enabled: Set<String>, onRetry: (WorldExperiments?) -> Unit) {
            val selected = ArrayList(repository.selectedPacks)
            val features = ArrayList<Pack>()
            packs.forEach { pack ->
                selected.remove(pack)
                if (pack.id in enabled) features.add(pack)
            }
            selected.addAll(features.asReversed())
            repository.setSelected(selected.map { it.id })
            access.polyplusTryApplyNewDataPacks(repository, false) { configuration ->
                onRetry(experimentsFor(configuration))
                ClientPlatform.setScreen(screen)
            }
        }
    }

    override fun create() = access.polyplusOnCreate()

    override fun cancel() = screen.popScreen()

    override fun useVanilla() = WorldRoutes.openVanilla(screen)
}

//? if >= 1.21.11 {
private class WorldRules26(private val ui: WorldCreationUiState) : WorldRules {
    private val copy: GameRules = ui.gameRules.copy(ui.settings.dataConfiguration().enabledFeatures())
    private val byId = LinkedHashMap<String, GameRule<Any>>()

    override val rules: List<WorldRule>

    init {
        @Suppress("UNCHECKED_CAST")
        val all = copy.availableRules().toList().map { it as GameRule<Any> }
        rules = all
            .sortedWith(compareBy({ it.category().descriptionId.toString() }, { Component.translatable(it.descriptionId).string }))
            .map { rule ->
                byId[rule.id()] = rule
                val description = Component.translatable(rule.descriptionId + ".description")
                WorldRule(
                    id = rule.id(),
                    label = Component.translatable(rule.descriptionId).string,
                    description = if (ComponentUtils.isTranslationResolvable(description)) description.string else null,
                    category = rule.category().label().string,
                    kind = if (rule.gameRuleType() == GameRuleType.BOOL) WorldRuleKind.Toggle else WorldRuleKind.Number,
                    defaultValue = rule.serialize(rule.defaultValue()),
                )
            }
    }

    override fun value(rule: WorldRule): String = byId[rule.id]?.let { copy.getAsString(it) } ?: rule.defaultValue

    override fun accepts(rule: WorldRule, value: String): Boolean =
        byId[rule.id]?.deserialize(value)?.result()?.isPresent == true

    override fun set(rule: WorldRule, value: String): Boolean {
        val key = byId[rule.id] ?: return false
        val parsed = key.deserialize(value).result().orElse(null) ?: return false
        copy.set(key, parsed, null)
        return true
    }

    override fun apply() = ui.setGameRules(copy)
}
//?} else {
/*private class WorldRules26(private val ui: WorldCreationUiState) : WorldRules {
    //? if >= 1.21.4 {
    private val copy: GameRules = ui.gameRules.copy(ui.settings.dataConfiguration().enabledFeatures())
    //?} else {
    /^private val copy: GameRules = ui.gameRules.copy()
    ^///?}
    private val toggles = HashMap<String, GameRules.Key<GameRules.BooleanValue>>()
    private val numbers = HashMap<String, Pair<GameRules.Key<GameRules.IntegerValue>, GameRules.Type<GameRules.IntegerValue>>>()

    override val rules: List<WorldRule>

    init {
        val found = ArrayList<WorldRule>()
        fun add(key: GameRules.Key<*>, kind: WorldRuleKind, defaultValue: String) {
            val description = Component.translatable(key.descriptionId + ".description")
            found += WorldRule(
                id = key.id,
                label = Component.translatable(key.descriptionId).string,
                description = if (ComponentUtils.isTranslationResolvable(description)) description.string else null,
                category = Component.translatable(key.category.descriptionId).string,
                kind = kind,
                defaultValue = defaultValue,
            )
        }
        val visitor = object : GameRules.GameRuleTypeVisitor {
            override fun visitBoolean(key: GameRules.Key<GameRules.BooleanValue>, type: GameRules.Type<GameRules.BooleanValue>) {
                toggles[key.id] = key
                add(key, WorldRuleKind.Toggle, type.createRule().serialize())
            }

            override fun visitInteger(key: GameRules.Key<GameRules.IntegerValue>, type: GameRules.Type<GameRules.IntegerValue>) {
                numbers[key.id] = key to type
                add(key, WorldRuleKind.Number, type.createRule().serialize())
            }
        }
        //? if >= 1.21.4 {
        copy.visitGameRuleTypes(visitor)
        //?} else {
        /^GameRules.visitGameRuleTypes(visitor)
        ^///?}
        val categories = HashMap<String, String>()
        (toggles.values + numbers.values.map { it.first }).forEach { categories[it.id] = it.category.descriptionId }
        rules = found.sortedWith(compareBy({ categories[it.id] }, { it.label }))
    }

    override fun value(rule: WorldRule): String =
        toggles[rule.id]?.let { copy.getRule(it).serialize() }
            ?: numbers[rule.id]?.let { copy.getRule(it.first).serialize() }
            ?: rule.defaultValue

    override fun accepts(rule: WorldRule, value: String): Boolean {
        if (rule.id in toggles) return value.toBooleanStrictOrNull() != null
        val type = numbers[rule.id]?.second ?: return false
        return type.createRule().tryDeserialize(value)
    }

    override fun set(rule: WorldRule, value: String): Boolean {
        toggles[rule.id]?.let { key ->
            val parsed = value.toBooleanStrictOrNull() ?: return false
            copy.getRule(key).set(parsed, null)
            return true
        }
        val key = numbers[rule.id]?.first ?: return false
        return copy.getRule(key).tryDeserialize(value)
    }

    override fun apply() = ui.setGameRules(copy)
}
*///?}

internal class EditWorldDraft26(private val screen: EditWorldScreen) : EditWorldDraft {
    private val mc = Minecraft.getInstance()
    private val access = screen as WorldEditScreenAccessor
    private val level = access.polyplusLevelAccess()

    init {
        bindVanilla(screen)
    }

    //? if >= 26.1 {
    private val summary: LevelSummary? = runCatching { level.fixAndGetSummary() }.getOrNull()
    //?} else {
    /*private val summary: LevelSummary? = runCatching { level.getSummary(level.getDataTag()) }.getOrNull()
    *///?}

    override val name: String = summary?.levelName?.takeIf { it.isNotEmpty() } ?: access.polyplusNameEdit().value

    override val folder: String get() = level.levelId

    override val hasIcon: Boolean get() = level.iconFile.filter { Files.isRegularFile(it) }.isPresent

    override val icon: ImageBitmap? by lazy {
        val path = level.iconFile.filter { Files.isRegularFile(it) }.orElse(null) ?: return@lazy null
        try {
            PolyBitmap.decode(Files.readAllBytes(path))?.asComposeImageBitmap()
        } catch (error: IOException) {
            LOGGER.warn("Couldn't read icon for world {}", level.levelId, error)
            null
        }
    }

    override val lastPlayed: Long get() = summary?.lastPlayed ?: 0L

    override val hardcore: Boolean get() = summary?.isHardcore == true

    override val mode: String?
        get() = summary?.let {
            if (it.isHardcore) Component.translatable("selectWorld.gameMode.hardcore").string
            else Component.translatable("selectWorld.gameMode.${it.gameMode.getName()}").string
        }

    override val version: String? get() = summary?.worldVersionName?.string?.takeIf { it.isNotBlank() }

    override val commands: Boolean get() = summary?.hasCommands() == true

    override fun save(name: String) {
        if (name.isBlank()) return
        access.polyplusOnRename(name)
    }

    override fun cancel() = access.polyplusCallback().accept(false)

    override fun resetIcon() {
        level.iconFile.ifPresent { FileUtils.deleteQuietly(it.toFile()) }
    }

    override fun openFolder() = openPath(level.getLevelPath(LevelResource.ROOT))

    override fun backup() {
        val callback = access.polyplusCallback()
        //? if >= 26.1 {
        EditWorldScreen.makeBackupAndShowToast(level).thenAcceptAsync({ callback.accept(!it) }, mc)
        //?} else {
        /*callback.accept(!EditWorldScreen.makeBackupAndShowToast(level))
        *///?}
    }

    override fun delete() {
        val callback = access.polyplusCallback()
        try {
            level.deleteLevel()
        } catch (error: IOException) {
            SystemToast.onWorldDeleteFailure(mc, level.levelId)
            LOGGER.error("Failed to delete world {}", level.levelId, error)
        }
        callback.accept(true)
    }

    override fun openBackups() {
        val path = mc.levelSource.backupPath
        try {
            FileUtil.createDirectoriesSafe(path)
        } catch (_: IOException) {
            return
        }
        openPath(path)
    }

    override fun optimize() {
        val callback = access.polyplusCallback()
        //? if >= 26.1 {
        val listener = BackupConfirmScreen.Listener { backup, eraseCache ->
            EditWorldScreen.conditionallyMakeBackupAndShowToast(backup, level).thenAcceptAsync({
                ClientPlatform.setScreen(OptimizeWorldScreen.create(mc, callback, mc.fixerUpper, level, eraseCache))
            }, mc)
        }
        //?} else {
        /*val listener = BackupConfirmScreen.Listener { backup, eraseCache ->
            if (backup) EditWorldScreen.makeBackupAndShowToast(level)
            ClientPlatform.setScreen(OptimizeWorldScreen.create(mc, callback, mc.fixerUpper, level, eraseCache))
        }
        *///?}
        ClientPlatform.setScreen(
            BackupConfirmScreen(
                { ClientPlatform.setScreen(screen) },
                listener,
                Component.translatable("optimizeWorld.confirm.title"),
                Component.translatable("optimizeWorld.confirm.description"),
                //? if >= 1.21.4
                Component.translatable("optimizeWorld.confirm.proceed"),
                true,
            ),
        )
    }
}

internal class DirectJoinDraft26(screen: DirectJoinServerScreen) : DirectJoinDraft {
    private val mc = Minecraft.getInstance()
    private val access = screen as WorldDirectJoinAccessor

    override val lastAddress: String get() = mc.options.lastMpIp

    override fun isValid(address: String): Boolean = ServerAddress.isValidAddress(address)

    override fun join(address: String) {
        access.polyplusServerData().ip = address
        mc.options.lastMpIp = address
        mc.options.save()
        access.polyplusCallback().accept(true)
    }

    override fun cancel() = access.polyplusCallback().accept(false)
}

private val DANGER_TITLES = setOf("selectWorld.deleteQuestion", "selectServer.deleteQuestion")

internal fun confirmSpec(screen: ConfirmScreen): DialogSpec {
    val access = screen as WorldConfirmAccessor
    val key = (screen.title.contents as? TranslatableContents)?.key
    val callback = access.polyplusCallback()
    return DialogSpec(
        title = screen.title.string,
        message = access.polyplusMessage().string,
        confirmLabel = access.polyplusYes().string,
        dismissLabel = access.polyplusNo().string,
        danger = key in DANGER_TITLES,
        onResult = { callback.accept(it) },
    )
}

internal fun alertSpec(screen: AlertScreen): DialogSpec {
    val access = screen as WorldAlertAccessor
    val callback = access.polyplusCallback()
    return DialogSpec(
        title = screen.title.string,
        message = access.polyplusMessage().string,
        confirmLabel = access.polyplusOk().string,
        dismissLabel = null,
        danger = false,
        onResult = { callback.run() },
    )
}
//?}
