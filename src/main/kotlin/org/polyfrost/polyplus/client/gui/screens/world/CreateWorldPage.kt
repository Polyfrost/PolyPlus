@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.DrillRow
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelHint
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageSwitch
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelToggle
import org.polyfrost.polyplus.client.gui.lobbyshell.Segmented
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

private val STEPS = listOf("World", "Generation", "Advanced")
private val ADVANCED_ONLY = setOf(WorldSection.GameRules, WorldSection.DataPacks, WorldSection.Experiments)
private const val TYPE_COLUMNS = 3
private const val MAX_NAME = 128

private val MODE_ICONS = mapOf(
    WorldGameMode.Survival to "minecraft-block",
    WorldGameMode.Hardcore to "alert-triangle",
    WorldGameMode.Creative to "diamond-01",
    WorldGameMode.Spectator to "user-01",
)

private val TYPE_INFO = mapOf(
    "minecraft:normal" to "The standard world with hills, oceans and caves.",
    "minecraft:flat" to "Flat layers of blocks. Customize the layers and biome.",
    "minecraft:large_biomes" to "The standard world with much bigger biomes.",
    "minecraft:amplified" to "Huge mountains and deep valleys. Needs a strong computer.",
    "minecraft:single_biome_surface" to "One biome everywhere. Pick it with Customize.",
    "minecraft:debug_all_block_states" to "Every block state laid out in a grid. For testing.",
    "customized" to "The standard world with your own ores, caves and biome sizes.",
    "custom" to "Custom settings from a data pack or an older world.",
)

private val TYPE_ICONS = mapOf(
    "minecraft:normal" to "globe-01",
    "minecraft:flat" to "minecraft-block",
    "minecraft:large_biomes" to "globe-01",
    "minecraft:amplified" to "alert-triangle",
    "minecraft:single_biome_surface" to "image-01",
    "minecraft:debug_all_block_states" to "settings-02",
    "customized" to "settings-02",
)

class CreateWorldShell(shell: ScreenShell, private val draft: WorldDraft) : WorldShellScreen(shell) {
    private val step = mutableIntStateOf(0)
    private val invalidRules = mutableStateMapOf<String, String>()

    override val title: String get() = if (draft.recreated) "Recreate world" else "Create world"

    override fun back() = draft.cancel()

    @Composable
    override fun Body() {
        CreateWorldContent(draft, step.intValue, invalidRules) { step.intValue = it }
    }
}

@Composable
fun CreateWorldContent(draft: WorldDraft, step: Int, invalidRules: MutableMap<String, String>, onStep: (Int) -> Unit) {
    var previous by remember { mutableIntStateOf(step) }
    val merged = draft.sections.none { it in ADVANCED_ONLY }
    val steps = if (merged) STEPS.take(2) else STEPS
    val forward = step >= previous
    val go: (Int) -> Unit = {
        previous = step
        onStep(it.coerceIn(0, steps.size - 1))
    }
    val rulesStep = if (merged) -1 else steps.size - 1
    val errorSteps = if (invalidRules.isNotEmpty() && rulesStep >= 0 && !draft.gameRulesLocked) setOf(rulesStep) else emptySet()
    WorldContentColumn {
        StepBar(steps, step, go, errorSteps)
        Divider(Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).fillMaxWidth()) {
            PanelPageSwitch(step, forward) { page ->
                PanelPageColumn {
                    when (page) {
                        0 -> WorldStep(draft) { go(1) }
                        1 -> GenerationStep(draft, merged)
                        else -> AdvancedStep(draft, invalidRules)
                    }
                }
            }
        }
        CreateFooter(draft, step, steps.size, go, if (errorSteps.isEmpty()) null else "Fix the game rules marked in ${steps[rulesStep]} first.")
    }
}

@Composable
private fun CreateFooter(draft: WorldDraft, step: Int, count: Int, go: (Int) -> Unit, blocked: String?) {
    val theme = OneConfigTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton("Cancel", draft::cancel, style = ControlStyle.Ghost, size = ControlSize.Large)
        TextButton("Use vanilla screen", draft::useVanilla, style = ControlStyle.Ghost, size = ControlSize.Large)
        Spacer(Modifier.weight(1f))
        if (blocked != null) {
            Text(blocked, color = theme.error, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        if (step > 0) TextButton("Back", { go(step - 1) }, size = ControlSize.Large)
        if (step < count - 1) TextButton("Next", { go(step + 1) }, size = ControlSize.Large)
        TextButton(
            if (draft.recreated) "Recreate world" else "Create world",
            draft::create,
            style = ControlStyle.Primary,
            size = ControlSize.Large,
            enabled = blocked == null,
        )
    }
}

@Composable
private fun WorldStep(draft: WorldDraft, onNext: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier.fillMaxWidth().card(WorldKit.CardRadius).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WorldTile(draft.name, null, 72.dp, hardcore = draft.gameMode == WorldGameMode.Hardcore)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextField(
                value = draft.name,
                onValueChange = { draft.name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "World name",
                placeholder = "New World",
                maxLength = MAX_NAME,
                onSubmit = onNext,
            )
            Text(
                "Saved in the folder \"${draft.folder}\"",
                color = theme.textColorSecondary,
                style = theme.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
    SectionSpace()
    WorldBlock("Game mode", if (draft.gameModeLocked) "This world type always uses Spectator." else "You can change this later with commands if they are allowed.") {
        val modes = draft.gameModes
        ChoiceGrid(modes, modes.size.coerceAtLeast(1)) { mode ->
            ChoiceCard(
                mode.label,
                mode.info,
                draft.gameMode == mode,
                { draft.gameMode = mode },
                Modifier.fillMaxSize(),
                icon = MODE_ICONS[mode],
                danger = mode == WorldGameMode.Hardcore,
                enabled = !draft.gameModeLocked,
            )
        }
    }
    if (WorldSection.Difficulty !in draft.sections) return
    SectionSpace()
    WorldBlock("Difficulty", if (draft.difficultyLocked) (if (draft.gameMode == WorldGameMode.Hardcore) "Hardcore worlds are always on Hard." else "This world type is always on Peaceful.") else draft.difficulty.info) {
        Segmented(WorldDifficulty.entries, draft.difficulty, { draft.difficulty = it }, label = { it.label }, enabled = !draft.difficultyLocked)
    }
}

@Composable
private fun GenerationStep(draft: WorldDraft, merged: Boolean) {
    var more by remember { mutableStateOf(draft.extraWorldTypes.any { it.id == draft.worldType.id }) }
    val selected = draft.worldType
    val locked = draft.worldTypeLocked
    WorldBlock(
        "World type",
        if (locked) "This world uses custom settings, so the type can't be changed here." else "Shapes the terrain of the whole world.",
        trailing = if (draft.canCustomizeType) {
            { TextButton("Customize ${selected.label}", draft::customizeType) }
        } else {
            null
        },
    ) {
        val types = if (locked && draft.worldTypes.none { it.id == selected.id }) draft.worldTypes + selected else draft.worldTypes
        TypeGrid(draft, types, selected, locked)
        val extra = draft.extraWorldTypes
        if (extra.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(if (more) "Hide extra types" else "Show extra types", { more = !more }, style = ControlStyle.Ghost)
            }
            if (more) TypeGrid(draft, extra, selected, locked)
        }
    }
    SectionSpace()
    WorldBlock("Seed", "The same seed and settings always make the same world.") {
        TextField(
            value = draft.seed,
            onValueChange = { draft.seed = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Leave blank for a random seed",
        )
    }
    SectionSpace()
    WorldBlock("Structures") {
        PanelToggle(
            "Generate structures",
            draft.generateStructures,
            { draft.generateStructures = it },
            description = "Villages, shipwrecks, temples and other buildings.",
            enabled = !draft.generateStructuresLocked,
            prominent = true,
        )
    }
    if (merged) {
        SectionSpace()
        OptionsBlock(draft)
    }
}

@Composable
private fun TypeGrid(draft: WorldDraft, types: List<WorldTypeOption>, selected: WorldTypeOption, locked: Boolean) {
    ChoiceGrid(types, TYPE_COLUMNS) { type ->
        val on = type.id == selected.id
        ChoiceCard(
            type.label,
            TYPE_INFO[type.id],
            on,
            { draft.worldType = type },
            Modifier.fillMaxSize(),
            icon = TYPE_ICONS[type.id] ?: "globe-01",
            enabled = !locked || on,
        )
    }
}

@Composable
private fun AdvancedStep(draft: WorldDraft, invalidRules: MutableMap<String, String>) {
    OptionsBlock(draft)
    val packs = WorldSection.DataPacks in draft.sections
    val experiments = WorldSection.Experiments in draft.sections
    if (packs || experiments) {
        SectionSpace()
        WorldBlock("Packs and experiments") {
            Column(Modifier.fillMaxWidth().card(WorldKit.CardRadius).padding(4.dp)) {
                if (packs) {
                    val count = draft.dataPackCount
                    DrillRow("package-01", "Data packs", if (count == 0) "None added" else "$count added", draft::openDataPacks)
                }
                if (experiments) DrillRow("settings-02", "Experiments", if (draft.experimental) "On" else "Off", draft::openExperiments)
            }
        }
    }
    if (WorldSection.GameRules in draft.sections) {
        SectionSpace()
        if (draft.gameRulesLocked) {
            WorldBlock("Game rules", "This world type always uses the default game rules.") {}
        } else {
            GameRulesBlock(draft, invalidRules)
        }
    }
}

@Composable
private fun OptionsBlock(draft: WorldDraft) {
    WorldBlock("Options") {
        Column(Modifier.fillMaxWidth().card(WorldKit.CardRadius).padding(4.dp)) {
            PanelToggle(
                "Allow commands",
                draft.allowCommands,
                { draft.allowCommands = it },
                description = if (draft.allowCommandsLocked && draft.gameMode == WorldGameMode.Hardcore) "Always off in Hardcore." else "Use commands like /gamemode and /tp.",
                enabled = !draft.allowCommandsLocked,
            )
            PanelToggle(
                "Bonus chest",
                draft.bonusChest,
                { draft.bonusChest = it },
                description = if (draft.bonusChestLocked && draft.gameMode == WorldGameMode.Hardcore) "Not available in Hardcore." else "Start next to a chest with a few useful items.",
                enabled = !draft.bonusChestLocked,
            )
        }
    }
}

@Composable
private fun GameRulesBlock(draft: WorldDraft, invalidRules: MutableMap<String, String>) {
    val packs = draft.dataPackCount
    val experimental = draft.experimental
    val rules = remember(draft, packs, experimental) { draft.editRules() }
    val text = remember(rules) { mutableStateMapOf<String, String>().apply { rules.rules.forEach { put(it.id, invalidRules[it.id] ?: rules.value(it)) } } }
    LaunchedEffect(rules) {
        val ids = rules.rules.map { it.id }.toSet()
        invalidRules.keys.retainAll(ids)
    }
    var query by remember { mutableStateOf("") }
    val filter = query.trim().lowercase()
    val shown = rules.rules.filter {
        filter.isEmpty() || it.label.lowercase().contains(filter) || it.id.lowercase().contains(filter) || it.category.lowercase().contains(filter)
    }
    val changed = rules.rules.any { (text[it.id] ?: it.defaultValue) != it.defaultValue }
    val update: (WorldRule, String) -> Unit = { rule, value ->
        text[rule.id] = value
        if (rules.accepts(rule, value) && rules.set(rule, value)) {
            invalidRules.remove(rule.id)
            rules.apply()
        } else {
            invalidRules[rule.id] = value
        }
    }
    WorldBlock(
        "Game rules",
        "Fine-tune how the world behaves. Most can be changed later with /gamerule.",
        trailing = {
            TextButton("Reset all", {
                rules.rules.forEach { rule ->
                    text[rule.id] = rule.defaultValue
                    rules.set(rule, rule.defaultValue)
                }
                invalidRules.clear()
                rules.apply()
            }, style = ControlStyle.Ghost, enabled = changed || invalidRules.isNotEmpty())
        },
    ) {
        SearchField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search game rules", size = ControlSize.Regular)
        val broken = rules.rules.filter { it.id in invalidRules }
        if (broken.isNotEmpty()) {
            Text(
                "Not a valid number: ${broken.joinToString { it.label }}",
                Modifier.padding(start = 4.dp),
                color = OneConfigTheme.current.error,
                style = OneConfigTheme.current.typography.small,
            )
        }
        if (shown.isEmpty()) PanelHint("No game rules match that search.")
        shown.groupBy { it.category }.forEach { (category, list) ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    category,
                    Modifier.padding(start = 4.dp, top = 6.dp),
                    color = OneConfigTheme.current.textColorSecondary,
                    style = OneConfigTheme.current.typography.smallMedium,
                )
                Column(Modifier.fillMaxWidth().card(WorldKit.CardRadius).padding(4.dp)) {
                    list.forEach { rule ->
                        val value = text[rule.id] ?: rule.defaultValue
                        when (rule.kind) {
                            WorldRuleKind.Toggle -> PanelToggle(rule.label, value == "true", { update(rule, it.toString()) }, description = rule.description)
                            WorldRuleKind.Number -> NumberRuleRow(rule, value, rules.accepts(rule, value)) { update(rule, it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberRuleRow(rule: WorldRule, value: String, valid: Boolean, onChange: (String) -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rule.label, color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            rule.description?.let { Text(it, color = theme.textColorSecondary, style = theme.typography.small) }
        }
        TextField(value, onChange, Modifier.width(96.dp), invalid = !valid, maxLength = 11)
    }
}
