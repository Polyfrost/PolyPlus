package org.polyfrost.polyplus.client.gui.screens.worldoptions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.client.gui.screens.settings.SettingKind
import org.polyfrost.polyplus.client.gui.screens.settings.SettingRow
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsLabels

internal enum class OptionsPage { Main, Rules }

internal sealed interface OptionsItem {
    val key: String

    class Setting(val row: SettingRow) : OptionsItem {
        override val key: String get() = row.key
    }

    class Action(
        override val key: String,
        val label: String,
        val button: String,
        val hint: String?,
        val enabled: Boolean,
        val onClick: () -> Unit,
    ) : OptionsItem

    class Number(
        override val key: String,
        val label: String,
        val hint: String?,
        val value: String,
        val placeholder: String,
        val enabled: Boolean,
        val invalid: Boolean,
        val onChange: (String) -> Unit,
    ) : OptionsItem

    class Info(override val key: String, val label: String, val lines: List<String>) : OptionsItem
}

internal class OptionsSection(val title: String, val items: List<OptionsItem>)

internal class RuleGroup(val title: String, val rules: List<RuleEntry>)

internal class WorldOptionsSession(private val start: WorldOptionsSnapshot, private val onHost: () -> Unit) {
    private val multiplayer = start.multiplayer
    private val suggestedPort = MultiplayerPlatform.suggestedPort()

    private var difficulty by mutableIntStateOf(start.difficulty)
    private var locked by mutableStateOf(start.difficultyLocked)
    private var defaultMode by mutableIntStateOf(start.defaultMode ?: 0)
    private var personalMode by mutableIntStateOf(start.personalMode ?: 0)
    private var commands by mutableStateOf(start.allowCommands ?: false)
    private var lan by mutableStateOf(multiplayer?.published == true)
    private var portText by mutableStateOf(if (multiplayer?.published == true && multiplayer.port > 0) multiplayer.port.toString() else "")
    private var portProblem by mutableStateOf<String?>(null)
    private var guest by mutableStateOf(multiplayer?.guestCommands ?: false)
    private var force by mutableStateOf(multiplayer?.forceMode ?: false)
    private val ruleEdits = mutableStateMapOf<String, Any>()
    private val ruleTexts = mutableStateMapOf<String, String>()

    var page by mutableStateOf(OptionsPage.Main)

    var ruleQuery by mutableStateOf("")

    val lockPending: Boolean get() = locked && !start.difficultyLocked

    val difficultyName: String get() = difficultyNames()[difficulty]

    private val commandsOn: Boolean get() = if (start.allowCommands == null) start.canChange else commands

    private val guestEnabled: Boolean
        get() = multiplayer?.guestCommands != null && lan && commandsOn

    private val effectiveGuest: Boolean get() = guestEnabled && guest

    private val forceEnabled: Boolean
        get() = multiplayer?.forceMode != null && lan && !start.hardcore && !effectiveGuest

    private val shownForce: Boolean
        get() = if (start.hardcore && multiplayer?.forceMode != null) true else if (effectiveGuest) false else force

    private val rulesEnabled: Boolean get() = start.owner && !start.hardcore && commandsOn && start.rules.isNotEmpty()

    private val portRelevant: Boolean
        get() = multiplayer != null && lan && multiplayer.portEditable && (!multiplayer.published || multiplayer.canStop)

    private val effectivePort: Int
        get() = portText.trim().toIntOrNull() ?: suggestedPort

    private fun lanChange(): LanChange? {
        val state = multiplayer ?: return null
        return when {
            lan && !state.published -> LanChange(true, effectivePort, defaultMode, effectiveGuest)
            !lan && state.published && state.canStop -> LanChange(false, 0, defaultMode, false)
            lan && state.published && state.canStop && state.portEditable && effectivePort != state.port ->
                LanChange(true, effectivePort, defaultMode, effectiveGuest)
            else -> null
        }
    }

    private fun guestChange(lanChange: LanChange?): Boolean? {
        val initial = multiplayer?.guestCommands ?: return null
        if (lanChange?.open == true) return null
        if (!guestEnabled && (start.allowCommands == null || commands == start.allowCommands)) return null
        return effectiveGuest.takeIf { it != initial }
    }

    private fun forceChange(): Boolean? {
        val initial = multiplayer?.forceMode ?: return null
        val target = if (forceEnabled) force else if (effectiveGuest) false else return null
        return target.takeIf { it != initial }
    }

    val dirty: Boolean
        get() = difficulty != start.difficulty ||
            lockPending ||
            (start.defaultMode != null && defaultMode != start.defaultMode) ||
            (start.personalMode != null && personalMode != start.personalMode) ||
            (start.allowCommands != null && commands != start.allowCommands) ||
            (rulesEnabled && ruleEdits.isNotEmpty()) ||
            lanChange() != null ||
            guestChange(lanChange()) != null ||
            forceChange() != null

    val valid: Boolean get() = (!portRelevant || portProblem == null) && (!rulesEnabled || ruleTexts.keys.none { ruleInvalid(it) })

    fun changes(): WorldOptionsChanges {
        val lanChange = lanChange()
        return WorldOptionsChanges(
            difficulty.takeIf { it != start.difficulty },
            lockPending,
            defaultMode.takeIf { start.defaultMode != null && it != start.defaultMode },
            personalMode.takeIf { start.personalMode != null && it != start.personalMode },
            commands.takeIf { start.allowCommands != null && it != start.allowCommands },
            if (rulesEnabled) ruleEdits.toMap() else emptyMap(),
            lanChange,
            guestChange(lanChange),
            forceChange(),
        )
    }

    fun ruleGroups(): List<RuleGroup> {
        val query = ruleQuery.trim()
        return start.rules
            .filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
            .groupBy { it.category }
            .map { (title, rules) -> RuleGroup(title, rules) }
    }

    val ruleCount: Int get() = start.rules.size

    fun ruleBool(rule: RuleEntry): Boolean = (ruleEdits[rule.id] ?: rule.value) as Boolean

    fun setRuleBool(rule: RuleEntry, on: Boolean) {
        if (on == rule.value) ruleEdits.remove(rule.id) else ruleEdits[rule.id] = on
    }

    fun ruleText(rule: RuleEntry): String = ruleTexts[rule.id] ?: (ruleEdits[rule.id] ?: rule.value).toString()

    fun setRuleText(rule: RuleEntry, text: String) {
        val cleaned = text.filterIndexed { index, char -> char.isDigit() || (char == '-' && index == 0) }
        ruleTexts[rule.id] = cleaned
        val number = cleaned.toIntOrNull()
        if (number == null || number < rule.min || number > rule.max) {
            ruleEdits.remove(rule.id)
        } else if (number == rule.value) {
            ruleEdits.remove(rule.id)
        } else {
            ruleEdits[rule.id] = number
        }
    }

    fun ruleInvalid(rule: RuleEntry): Boolean {
        val text = ruleTexts[rule.id] ?: return false
        val number = text.trim().toIntOrNull() ?: return true
        return number < rule.min || number > rule.max
    }

    private fun ruleInvalid(id: String): Boolean = start.rules.firstOrNull { it.id == id }?.let { ruleInvalid(it) } == true

    fun ruleHint(rule: RuleEntry): String? = when {
        rule.bool -> null
        rule.min > Int.MIN_VALUE && rule.max < Int.MAX_VALUE -> "A whole number from ${rule.min} to ${rule.max}."
        rule.min > Int.MIN_VALUE -> "A whole number, ${rule.min} or higher."
        else -> "A whole number."
    }

    fun sections(): List<OptionsSection> = listOfNotNull(
        OptionsSection("General", general()),
        OptionsSection("Game rules", listOf(rulesRow())),
        multiplayer?.let { OptionsSection("Multiplayer", multiplayerItems(it)) },
        OptionsSection("Restrictions", restrictionItems()),
    )

    private fun rulesRow(): OptionsItem {
        val hint = when {
            !start.owner -> "Game rules can only be edited here in your own world. On other servers, use the /gamerule command."
            start.hardcore -> "Game rules can't be changed in hardcore worlds."
            !commandsOn -> "Turn on Allow commands to edit game rules."
            start.rules.isEmpty() -> "This world has no game rules to show."
            else -> "${start.rules.size} rules, grouped by topic."
        }
        return OptionsItem.Action("world.gameRules", "Game rules", "Edit", hint, rulesEnabled) { page = OptionsPage.Rules }
    }

    private fun general(): List<OptionsItem> {
        val modeLocked = start.hardcore || !(if (start.owner) commands else start.canChange)
        val modeHint = when {
            start.hardcore -> "Game modes can't be changed in hardcore worlds."
            modeLocked -> "Turn on Allow commands to change game modes."
            else -> null
        }
        val difficultyEnabled = start.canChange && !start.difficultyLocked
        val difficultyHint = when {
            !start.canChange -> "Only operators can change the difficulty."
            start.hardcore -> "Hardcore worlds are always on hard."
            start.difficultyLocked -> "The difficulty is locked in this world."
            else -> null
        }
        val commandsEnabled = !start.demo && (!start.hardcore || start.commandsOwner)
        val commandsHint = when {
            start.demo -> "Demo worlds don't allow commands."
            !commandsEnabled -> "Commands can't be turned on in hardcore worlds."
            else -> "Lets you and other players use commands in this world."
        }
        return listOfNotNull(
            start.defaultMode?.let {
                SettingRow("world.defaultMode", "Default game mode", modeHint ?: "The game mode new players start in.", choice(modeNames(), defaultMode) { defaultMode = it }, !modeLocked, wide = true)
            },
            start.personalMode?.let {
                SettingRow("world.personalMode", "Your game mode", modeHint ?: "The game mode only you play in.", choice(modeNames(), personalMode) { personalMode = it }, !modeLocked, wide = true)
            },
            start.allowCommands?.let {
                SettingRow("world.commands", "Allow commands", commandsHint, SettingKind.Toggle(commands) { on -> updateCommands(on) }, commandsEnabled, wide = true)
            },
            SettingRow("world.difficulty", "Difficulty", difficultyHint, choice(difficultyNames(), difficulty) { difficulty = it }, difficultyEnabled, wide = true),
            SettingRow(
                "world.lockDifficulty",
                "Lock difficulty",
                difficultyHint ?: "Nobody can change the difficulty again, ever.",
                SettingKind.Toggle(locked) { locked = it },
                difficultyEnabled,
                wide = true,
            ),
        ).map { OptionsItem.Setting(it) }
    }

    private fun multiplayerItems(state: MultiplayerState): List<OptionsItem> {
        val stopBlocked = state.published && !state.canStop
        val lanHint = when {
            stopBlocked -> "This version of Minecraft can't stop hosting. Leave the world to close it."
            else -> "Lets players on your network join this world."
        }
        val portEnabled = portRelevant
        val portHint = when {
            !state.portEditable -> "This version of Minecraft picks the port itself."
            state.published && !state.canStop -> "The port can't change while the world is open in this version."
            !lan -> "Turn on Open to LAN to choose a port."
            else -> portProblem ?: "A number from ${MultiplayerPlatform.PORT_MIN} to ${MultiplayerPlatform.PORT_MAX}. Leave it empty to pick a free port."
        }
        val guestHint = when {
            state.guestCommands == null -> "This version of Minecraft can't read or change guest commands."
            !lan -> "Turn on Open to LAN first."
            !commandsOn -> "Turn on Allow commands first."
            else -> "Lets other players use commands in this world."
        }
        val forceHint = when {
            state.forceMode == null -> "This version of Minecraft can't force the game mode for guests."
            start.hardcore -> "Hardcore worlds always force the game mode."
            !lan -> "Turn on Open to LAN first."
            effectiveGuest -> "Not available while guests can use commands."
            else -> "Guests always play in the default game mode."
        }
        val hostHint = if (state.published) "This world is already open to other players." else "Invite friends or open the world to your network."
        val rows = listOf(
            SettingRow("world.lan", "Open to LAN", lanHint, SettingKind.Toggle(lan) { lan = it }, !stopBlocked, wide = true),
            SettingRow("world.guestCommands", "Guest command access", guestHint, SettingKind.Toggle(effectiveGuest) { guest = it }, guestEnabled, wide = true),
            SettingRow("world.forceMode", "Force game mode", forceHint, SettingKind.Toggle(shownForce) { force = it }, forceEnabled, wide = true),
        ).map { OptionsItem.Setting(it) }
        return listOf(
            OptionsItem.Action("world.host", "Host world", "Host", hostHint, !state.published, onHost),
            rows[0],
            OptionsItem.Number("world.port", "Port", portHint, portText, suggestedPort.takeIf { it > 0 }?.toString() ?: "Automatic", portEnabled, portEnabled && portProblem != null, ::setPort),
            rows[1],
            rows[2],
        )
    }

    private fun restrictionItems(): List<OptionsItem> {
        val lines = start.restrictions
        if (lines == null) {
            return listOf(OptionsItem.Action("world.restrictions", "Restrictions", "View", "Chat restrictions only exist in Minecraft 26.3 and later.", false) {})
        }
        return listOf(OptionsItem.Info("world.restrictions", "Your chat access", lines))
    }

    private fun setPort(text: String) {
        portText = text.filter { it.isDigit() }.take(PORT_DIGITS)
        val state = multiplayer ?: return
        val trimmed = portText.trim()
        portProblem = when {
            trimmed.isEmpty() -> null
            else -> {
                val port = trimmed.toIntOrNull()
                when {
                    port == null || port < MultiplayerPlatform.PORT_MIN || port > MultiplayerPlatform.PORT_MAX ->
                        "Use a port from ${MultiplayerPlatform.PORT_MIN} to ${MultiplayerPlatform.PORT_MAX}."
                    port != state.port && !MultiplayerPlatform.portAvailable(port) -> "That port is already in use."
                    else -> null
                }
            }
        }
    }

    private fun updateCommands(on: Boolean) {
        commands = on
        if (!on) guest = false
        val initial = start.defaultMode ?: return
        if (on) return
        defaultMode = initial
        start.personalMode?.let { personalMode = it }
    }

    private fun choice(names: List<String>, selected: Int, set: (Int) -> Unit) = SettingKind.Choice(names, selected, set)

    private fun difficultyNames(): List<String> =
        listOf("peaceful", "easy", "normal", "hard").map { SettingsLabels.text("options.difficulty.$it") }

    private fun modeNames(): List<String> =
        listOf("survival", "creative", "adventure", "spectator").map { SettingsLabels.text("selectWorld.gameMode.$it") }

    private companion object {
        const val PORT_DIGITS = 5
    }
}
