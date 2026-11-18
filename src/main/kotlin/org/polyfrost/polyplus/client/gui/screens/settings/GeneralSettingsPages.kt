@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.lobbyshell.DrillRow
import org.polyfrost.polyplus.client.gui.lobbyshell.card

private val GeneralTwoColumnWidth = 620.dp
private val GeneralSectionGap = 20.dp
private const val GENERAL_REFRESH_MILLIS = 500L

internal object GeneralSettingsPages : SettingsPageProvider {
    override fun handles(category: SettingsCategory): Boolean = GeneralOptions.supports(category)

    override fun search(category: SettingsCategory, parent: () -> Screen?): List<SettingsSearchItem> =
        GeneralOptions.sections(category, parent).flatMap { block ->
            listOf(block.section).searchItems() +
                block.actions.map { SettingsSearchItem(SettingsLabels.label(it.key, it.label), it.hint, block.section.title, actionFocusKey(it.key)) }
        }

    @Composable
    override fun Page(category: SettingsCategory, parent: () -> Screen?) {
        var revision by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(GENERAL_REFRESH_MILLIS)
                revision++
            }
        }
        DisposableEffect(Unit) {
            onDispose { GeneralOptions.save() }
        }
        val sections = remember(revision) { GeneralOptions.sections(category, parent) }
        val changed: () -> Unit = { revision++ }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (maxWidth >= GeneralTwoColumnWidth) 2 else 1
            ScrollContainer(rememberScrollState(), Modifier.fillMaxSize(), fadingEdges = true) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(GeneralSectionGap)) {
                    GeneralHeader(category.label)
                    sections.forEach { GeneralBlock(it, columns, changed) }
                }
            }
        }
    }
}

@Composable
private fun GeneralHeader(title: String) {
    SettingsPageHeader(title, "Changes apply as you make them.")
}

@Composable
private fun GeneralBlock(block: GeneralSection, columns: Int, changed: () -> Unit) {
    val section = block.section
    if (block.actions.isEmpty()) {
        SectionBlock(section, columns, changed)
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(section.title, Modifier.padding(start = 4.dp), contentPadding = PaddingValues(0.dp))
        Column(
            Modifier.fillMaxWidth().card(14.dp).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            lines(section.rows, columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    line.forEach { row ->
                        Box(Modifier.weight(1f)) { key(row.key) { SettingControl(row, changed) } }
                    }
                    if (!line.first().wide) repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            block.actions.forEach { action ->
                key(action.key) {
                    SettingsFocusTarget(actionFocusKey(action.key)) {
                        TooltipBox(action.hint, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp) {
                            DrillRow(null, SettingsLabels.label(action.key, action.label), null, {
                                action.run()
                                changed()
                            }, enabled = action.enabled)
                        }
                    }
                }
            }
        }
    }
}
