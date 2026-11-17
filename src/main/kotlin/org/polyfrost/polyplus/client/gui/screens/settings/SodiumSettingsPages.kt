@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

//? if >= 26.3 {
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
import net.minecraft.client.gui.screens.Screen
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
//?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

//? if >= 26.3 {
private val SodiumTwoColumnWidth = 620.dp
private val SodiumSectionGap = 20.dp
private const val SODIUM_REFRESH_MILLIS = 500L
private const val SODIUM_PAGE_PREFIX = "sodium:sodium/"
private val GENERIC_LABELS = setOf("settings", "options", "config", "configuration", "general", "main", "mod settings")

internal object SodiumSettingsPages : SettingsEntryProvider {
    @Volatile
    private var pages: Map<String, SodiumPageRef> = emptyMap()

    fun register() {
        if (Mods.loaded("sodium")) SettingsPages.register(this)
    }

    override fun entries(): List<SettingsEntry> = SodiumBridge.guard(emptyList()) { bridge ->
        if (!bridge.usable()) return@guard emptyList()
        val refs = bridge.pages()
        pages = refs.associateBy { it.id }
        val video = SettingsCategory.Video.entryId
        var previous: String? = null
        refs.map { ref ->
            val placement = SettingsPlacement.TabIn(video, previous?.let { SettingsPlacement.After(it) } ?: SettingsPlacement.Replace(video))
            previous = ref.id
            val external = ref.external?.let { consumer -> { host: Screen? -> host?.let(consumer::accept); Unit } }
            SettingsEntry(ref.id, ref.label, ref.icon, ref.owner, SettingsGroup.Game, placement, external, tabLabel = tabLabel(ref))
        }
    }

    override fun opened(categoryId: String) {
        SodiumBridge.guard(Unit) { it.begin() }
    }

    override fun closed(categoryId: String) {
        SodiumBridge.guard(Unit) { it.apply() }
    }

    private fun tabLabel(ref: SodiumPageRef): String {
        val label = ref.label.trim()
        val lower = label.lowercase()
        return when {
            "shader" in lower -> "Shaders"
            ref.id.startsWith(SODIUM_PAGE_PREFIX) -> label
            lower in GENERIC_LABELS || label.equals(ref.owner.name, ignoreCase = true) -> ref.owner.name
            else -> label
        }
    }

    @Composable
    override fun Page(entry: SettingsEntry, parent: () -> Screen?) {
        val ref = pages[entry.id]
        if (ref == null || SodiumBridge.get() == null) {
            SodiumFallback(parent)
            return
        }
        SodiumPage(ref, entry.tabLabel ?: ref.label, parent)
    }
}

@Composable
private fun SodiumPage(ref: SodiumPageRef, title: String, parent: () -> Screen?) {
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SODIUM_REFRESH_MILLIS)
            revision++
        }
    }
    val groups = remember(revision) { SodiumBridge.guard(null) { it.groups(ref.page, parent) } }
    if (groups == null) {
        SodiumFallback(parent)
        return
    }
    val dirty = remember(revision) { SodiumBridge.guard(false) { it.dirty() } }
    val changed: () -> Unit = { revision++ }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= SodiumTwoColumnWidth) 2 else 1
        ScrollContainer(rememberScrollState(), Modifier.fillMaxSize(), fadingEdges = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(SodiumSectionGap)) {
                SodiumHeader(ref, title, dirty, changed)
                groups.forEachIndexed { index, group -> key(index) { SodiumGroupBlock(group, columns, parent, changed) } }
            }
        }
    }
}

@Composable
private fun SodiumHeader(ref: SodiumPageRef, title: String, dirty: Boolean, changed: () -> Unit) {
    val subtitle = if (dirty) "You have changes that are not applied yet." else "Changes apply when you press Apply or leave ${SettingsCategory.Video.label}."
    SettingsPageHeader(title, subtitle, owner = ref.owner) {
        if (SodiumBridge.restartPending) Badge(SettingsPlatform.restartNotice(), tone = BadgeTone.Warning)
        if (dirty) {
            TextButton("Undo", {
                SodiumBridge.guard(Unit) { it.undo() }
                changed()
            }, style = ControlStyle.Ghost, size = ControlSize.Regular)
        }
        TextButton("Apply", {
            SodiumBridge.guard(Unit) { it.apply() }
            changed()
        }, style = ControlStyle.Primary, size = ControlSize.Regular, enabled = dirty)
    }
}

@Composable
private fun SodiumGroupBlock(group: SodiumGroup, columns: Int, parent: () -> Screen?, changed: () -> Unit) {
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

@Composable
private fun SodiumFallback(parent: () -> Screen?) {
    val name = SettingsPlatform.videoModName ?: "Sodium"
    EmptyState(
        "$name handles video settings",
        Modifier.fillMaxSize(),
        icon = SettingsCategory.Video.icon,
        text = "Video settings open in $name's own screen.",
        action = {
            TextButton(
                "Open video settings",
                { parent()?.let { SettingsPlatform.open(SettingsCategory.Video, it) } },
                style = ControlStyle.Primary,
                size = ControlSize.Large,
            )
        },
    )
}
//?} else {
/*internal object SodiumSettingsPages {
    fun register() {}
}
*///?}
