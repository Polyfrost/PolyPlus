@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.card

private val TwoColumnWidth = 620.dp
private val SectionGap = 20.dp
private const val REFRESH_MILLIS = 500L
private const val MAX_QUERY = 64
internal const val KEYBINDS_TITLE = "Keybinds"

internal object InputSettingsPages : SettingsPageProvider {
    private val categories = setOf(SettingsCategory.Controls, SettingsCategory.Skin, SettingsCategory.Accessibility, SettingsCategory.Language)

    override fun handles(category: SettingsCategory): Boolean = InputSettingsPlatform.available && category in categories

    override fun search(category: SettingsCategory, parent: () -> Screen?): List<SettingsSearchItem> = when (category) {
        SettingsCategory.Controls -> InputSettingsPlatform.controlsSections().searchItems() + keyBindItems()
        SettingsCategory.Skin -> InputSettingsPlatform.skinSections().searchItems()
        SettingsCategory.Accessibility -> InputSettingsPlatform.accessibilitySections().searchItems()
        SettingsCategory.Language -> listOf(InputSettingsPlatform.fontSection()).searchItems()
        else -> emptyList()
    }

    private fun keyBindItems(): List<SettingsSearchItem> = KeyBindsPlatform.rows().map { row ->
        val detail = if (row.unbound) row.owner else row.owner + " " + row.key
        SettingsSearchItem(row.name, detail, row.category, KEYBIND_FOCUS + row.id, row.name)
    }

    @Composable
    override fun Page(category: SettingsCategory, parent: () -> Screen?) {
        when (category) {
            SettingsCategory.Controls -> ControlsPage()
            SettingsCategory.Skin -> SkinSettingsPage { InputSettingsPlatform.skinSections() }
            SettingsCategory.Accessibility -> AccessibilityPage(parent)
            SettingsCategory.Language -> LanguagePage()
            else -> Unit
        }
    }
}

@Composable
internal fun rememberRefresh(): Int {
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MILLIS)
            revision++
        }
    }
    return revision
}

@Composable
internal fun InputPageHeader(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit = {}) {
    SettingsPageHeader(title, subtitle, actions = actions)
}

@Composable
internal fun InputPageScroll(content: @Composable ColumnScope.(columns: Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= TwoColumnWidth) 2 else 1
        ScrollContainer(rememberScrollState(), Modifier.fillMaxSize(), fadingEdges = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
                content(columns)
            }
        }
    }
}

@Composable
private fun OptionsPage(title: String, subtitle: String, sections: () -> List<SettingSection>) {
    val refresh = rememberRefresh()
    var revision by remember { mutableIntStateOf(0) }
    val current = remember(refresh, revision) { sections() }
    val changed: () -> Unit = { revision++ }
    InputPageScroll { columns ->
        InputPageHeader(title, subtitle)
        current.forEach { SectionBlock(it, columns, changed) }
    }
}

@Composable
private fun ControlsPage() {
    val refresh = rememberRefresh()
    var revision by remember { mutableIntStateOf(0) }
    val sections = remember(refresh, revision) { InputSettingsPlatform.controlsSections() }
    val changed: () -> Unit = { revision++ }
    val keys = remember { KeyBindsState() }
    InputPageScroll { columns ->
        InputPageHeader(KEYBINDS_TITLE, "Changes apply as you make them.")
        KeyBindsKeymap(keys, refresh)
        sections.forEach { SectionBlock(it, columns, changed) }
        KeyBindsSection(keys, refresh)
    }
}

@Composable
private fun AccessibilityPage(parent: () -> Screen?) {
    val refresh = rememberRefresh()
    var revision by remember { mutableIntStateOf(0) }
    val sections = remember(refresh, revision) { InputSettingsPlatform.accessibilitySections() }
    val changed: () -> Unit = { revision++ }
    InputPageScroll { columns ->
        InputPageHeader("Accessibility", "Changes apply as you make them.") {
            TextButton(InputSettingsPlatform.accessibilityGuideLabel(), { parent()?.let(InputSettingsPlatform::openAccessibilityGuide) })
        }
        sections.forEach { SectionBlock(it, columns, changed) }
    }
}

@Composable
private fun LanguagePage() {
    val theme = OneConfigTheme.current
    val refresh = rememberRefresh()
    var revision by remember { mutableIntStateOf(0) }
    val current = remember(refresh, revision) { InputSettingsPlatform.currentLanguage() }
    var pending by remember { mutableStateOf(InputSettingsPlatform.currentLanguage()) }
    var query by remember { mutableStateOf("") }
    val languages = remember(revision) { InputSettingsPlatform.languages() }
    val font = remember(refresh, revision) { InputSettingsPlatform.fontSection() }
    val warning = remember(revision) { InputSettingsPlatform.languageWarning() }
    val needle = query.trim().lowercase()
    val shown = if (needle.isEmpty()) languages else languages.filter { needle in it.search }
    InputPageScroll { columns ->
        InputPageHeader("Language", warning) {
            TextButton(
                "Apply",
                {
                    InputSettingsPlatform.applyLanguage(pending)
                    revision++
                },
                style = ControlStyle.Primary,
                enabled = pending != current,
            )
        }
        SectionBlock(font, columns) { revision++ }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(InputSettingsPlatform.text("options.language"), Modifier.padding(start = 4.dp).weight(1f), contentPadding = PaddingValues(0.dp))
                SearchField(
                    query,
                    { query = it.take(MAX_QUERY) },
                    Modifier.weight(1f),
                    placeholder = "Search languages",
                    size = ControlSize.Regular,
                )
            }
            if (shown.isEmpty()) {
                EmptyState("No languages match", Modifier.fillMaxWidth().height(160.dp), text = "Try a different search.")
            } else {
                Column(Modifier.fillMaxWidth().card(14.dp).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    shown.forEach { language ->
                        val active = language.code == pending
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .selectableRow(active, { pending = language.code }, language.label, theme.sideBarNavigationEntryShape)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                language.label,
                                Modifier.weight(1f),
                                color = SelectionDefaults.itemContentColor(active),
                                style = theme.typography.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                            )
                            if (language.code == current) Badge("Current", tone = BadgeTone.Neutral)
                        }
                    }
                }
            }
        }
    }
}
