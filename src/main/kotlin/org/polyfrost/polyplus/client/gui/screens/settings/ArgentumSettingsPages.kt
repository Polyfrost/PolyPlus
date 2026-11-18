@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

//? if = 1.8.9 {
/*import net.minecraft.client.options.GameOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screen.Screen
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.lobbyshell.DrillRow
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.compat.Mods
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

//? if = 1.8.9 {
/*private val ArgentumTwoColumnWidth = 620.dp
private val ArgentumSectionGap = 20.dp
private const val ARGENTUM_REFRESH_MILLIS = 500L

internal object ArgentumSettingsPages : SettingsEntryProvider {
    fun register() {
        if (Mods.loaded("argentum")) SettingsPages.register(this)
    }

    override fun entries(): List<SettingsEntry> = ArgentumBridge.guard(emptyList()) { bridge ->
        if (!bridge.usable()) return@guard emptyList()
        val video = SettingsCategory.Video.entryId
        var previous: String? = null
        bridge.pages().map { ref ->
            val placement = SettingsPlacement.TabIn(video, previous?.let { SettingsPlacement.After(it) } ?: SettingsPlacement.Replace(video))
            previous = ref.id
            SettingsEntry(ref.id, ref.label, ref.icon, ref.owner, SettingsGroup.Game, placement, tabLabel = ref.label)
        }
    }

    override fun opened(categoryId: String) {
        ArgentumBridge.guard(Unit) { it.begin() }
    }

    override fun closed(categoryId: String) {
        ArgentumBridge.guard(Unit) { it.apply() }
    }

    override fun search(entry: SettingsEntry, parent: () -> Screen?): List<SettingsSearchItem> {
        val groups = argentumGroups(entry.id) ?: return emptyList()
        return groups.flatMap { group ->
            group.rows.map { SettingsSearchItem(SettingsLabels.label(it.key, it.label), it.hint, group.title, it.key) } +
                group.actions.map { SettingsSearchItem(it.label, it.hint, group.title, actionFocusKey(it.key)) }
        }
    }

    @Composable
    override fun Page(entry: SettingsEntry, parent: () -> Screen?) {
        if (ArgentumBridge.get() == null) {
            ArgentumFallback(parent)
            return
        }
        ArgentumPage(entry, parent)
    }
}

private fun argentumGroups(id: String): List<ArgentumGroup>? = ArgentumBridge.guard(null) { bridge ->
    val groups = bridge.groups(id) ?: return@guard null
    if (bridge.pages().firstOrNull()?.id == id) listOf(fovGroup()) + groups else groups
}

private fun fovGroup(): ArgentumGroup =
    ArgentumGroup(null, listOfNotNull(SettingsLegacyOptions.row(GameOptions.Option.FOV, wide = true)), emptyList())

@Composable
private fun ArgentumPage(entry: SettingsEntry, parent: () -> Screen?) {
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ARGENTUM_REFRESH_MILLIS)
            revision++
        }
    }
    val groups = remember(revision) { argentumGroups(entry.id) }
    if (groups == null) {
        ArgentumFallback(parent)
        return
    }
    val dirty = remember(revision) { ArgentumBridge.guard(false) { it.dirty() } }
    val changed: () -> Unit = { revision++ }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= ArgentumTwoColumnWidth) 2 else 1
        ScrollContainer(rememberScrollState(), Modifier.fillMaxSize(), fadingEdges = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(ArgentumSectionGap)) {
                ArgentumHeader(entry, dirty, changed)
                groups.forEachIndexed { index, group -> key(index) { ArgentumGroupBlock(group, columns, parent, changed) } }
            }
        }
    }
}

@Composable
private fun ArgentumHeader(entry: SettingsEntry, dirty: Boolean, changed: () -> Unit) {
    val subtitle = if (dirty) "You have changes that are not applied yet." else "Changes apply when you press Apply or leave ${SettingsCategory.Video.label}."
    SettingsPageHeader(entry.tabLabel ?: entry.label, subtitle, owner = entry.owner) {
        if (ArgentumBridge.restartPending) Badge(SettingsPlatform.restartNotice().ifBlank { "Restart needed" }, tone = BadgeTone.Warning)
        if (dirty) {
            TextButton("Undo", {
                ArgentumBridge.guard(Unit) { it.undo() }
                changed()
            }, style = ControlStyle.Ghost, size = ControlSize.Regular)
        }
        TextButton("Apply", {
            ArgentumBridge.guard(Unit) { it.apply() }
            changed()
        }, style = ControlStyle.Primary, size = ControlSize.Regular, enabled = dirty)
    }
}

@Composable
private fun ArgentumGroupBlock(group: ArgentumGroup, columns: Int, parent: () -> Screen?, changed: () -> Unit) {
    if (group.rows.isEmpty() && group.actions.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        group.title?.let { SectionHeader(it, Modifier.padding(start = 4.dp), contentPadding = PaddingValues(0.dp)) }
        Column(
            Modifier.fillMaxWidth().card(14.dp).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            lines(group.rows, columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    line.forEach { row ->
                        Box(Modifier.weight(1f)) { key(row.key) { SettingControl(row, changed) } }
                    }
                    if (!line.first().wide) repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            group.actions.forEach { action ->
                key(action.key) {
                    SettingsFocusTarget(actionFocusKey(action.key)) {
                        TooltipBox(action.hint, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp) {
                            DrillRow(null, action.label, null, {
                                action.run(parent())
                                changed()
                            }, enabled = action.enabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArgentumFallback(parent: () -> Screen?) {
    val name = ArgentumBridge.modName ?: "Argentum"
    EmptyState(
        "$name handles video settings",
        Modifier.fillMaxSize(),
        icon = SettingsCategory.Video.icon,
        text = "Video settings open in $name's own screen.",
        action = {
            TextButton(
                "Open video settings",
                { ArgentumBridge.openArgentum(parent()) },
                style = ControlStyle.Primary,
                size = ControlSize.Large,
            )
        },
    )
}
*///?} else {
internal object ArgentumSettingsPages {
    fun register() {}
}
//?}
