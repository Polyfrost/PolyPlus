@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.worldoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelToggle
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.screens.settings.SettingControl
import org.polyfrost.polyplus.client.gui.screens.settings.SettingsPageHeader

private val RowMinHeight = 44.dp
private val FieldWidth = 150.dp
private const val SEARCH_MAX_LENGTH = 48
private const val NUMBER_MAX_LENGTH = 11

@Composable
internal fun OptionsCard(section: OptionsSection) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(section.title, Modifier.padding(start = 4.dp), contentPadding = PaddingValues(0.dp))
        Column(Modifier.fillMaxWidth().card(14.dp).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            section.items.forEach { item -> key(item.key) { OptionItemRow(item) } }
        }
    }
}

@Composable
private fun OptionItemRow(item: OptionsItem) {
    when (item) {
        is OptionsItem.Setting -> SettingControl(item.row) {}
        is OptionsItem.Action -> ActionRow(item)
        is OptionsItem.Number -> NumberRow(item.label, item.hint, item.value, item.placeholder, item.enabled, item.invalid, item.onChange)
        is OptionsItem.Info -> InfoRow(item)
    }
}

@Composable
private fun ActionRow(item: OptionsItem.Action) {
    val theme = OneConfigTheme.current
    TooltipBox(item.hint, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.label, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            TextButton(item.button, item.onClick, style = ControlStyle.Secondary, size = ControlSize.Regular, enabled = item.enabled)
        }
    }
}

@Composable
private fun NumberRow(
    label: String,
    hint: String?,
    value: String,
    placeholder: String,
    enabled: Boolean,
    invalid: Boolean,
    onChange: (String) -> Unit,
) {
    val theme = OneConfigTheme.current
    TooltipBox(hint, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            TextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.width(FieldWidth),
                placeholder = placeholder,
                maxLength = NUMBER_MAX_LENGTH,
                enabled = enabled,
                invalid = invalid,
            )
        }
    }
}

@Composable
private fun InfoRow(item: OptionsItem.Info) {
    val theme = OneConfigTheme.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.label, color = theme.textColor, style = theme.typography.body)
        item.lines.forEach { Text(it, color = theme.textColorSecondary, style = theme.typography.small) }
    }
}

@Composable
internal fun RulesPage(session: WorldOptionsSession, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsPageHeader("Game rules", "${session.ruleCount} rules. Changes apply when you press Apply.")
        SearchField(
            query = session.ruleQuery,
            onQueryChange = { session.ruleQuery = it.take(SEARCH_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search game rules",
        )
        val groups = session.ruleGroups()
        PanelPageColumn(Modifier.weight(1f)) {
            if (groups.isEmpty()) {
                val theme = OneConfigTheme.current
                Text("No game rules match your search.", color = theme.textColorSecondary, style = theme.typography.body)
            }
            groups.forEach { group -> key(group.title) { RuleCard(session, group) } }
        }
    }
}

@Composable
private fun RuleCard(session: WorldOptionsSession, group: RuleGroup) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(group.title, Modifier.padding(start = 4.dp), contentPadding = PaddingValues(0.dp))
        Column(Modifier.fillMaxWidth().card(14.dp).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            group.rules.forEach { rule ->
                key(rule.id) {
                    if (rule.bool) {
                        TooltipBox(rule.id.takeIf { it != rule.label }, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp) {
                            PanelToggle(rule.label, session.ruleBool(rule), { session.setRuleBool(rule, it) }, Modifier.heightIn(min = RowMinHeight))
                        }
                    } else {
                        NumberRow(rule.label, session.ruleHint(rule), session.ruleText(rule), rule.value.toString(), true, session.ruleInvalid(rule)) { session.setRuleText(rule, it) }
                    }
                }
            }
        }
    }
}
