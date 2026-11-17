@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource

private val SkinPreviewMaxWidth = 320.dp
private val SkinTwoColumnWidth = 620.dp
private const val SKIN_PREVIEW_SHARE = 0.38f

@Composable
internal fun SkinSettingsPage(sections: () -> List<SettingSection>) {
    val refresh = rememberRefresh()
    var revision by remember { mutableIntStateOf(0) }
    val current = remember(refresh, revision) { sections() }
    val changed: () -> Unit = { revision++ }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsPageHeader(SettingsCategory.Skin.label, "Pick which skin layers others see.")
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerPreview(
                Modifier.fillMaxWidth(SKIN_PREVIEW_SHARE).widthIn(max = SkinPreviewMaxWidth).fillMaxHeight().card(14.dp),
                source = PlayerPreviewSource.LocalLive,
                autoSpin = false,
                initialYaw = 180f + 22.9f,
                live = true,
                modelScale = 0.35f,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val columns = if (maxWidth >= SkinTwoColumnWidth) 2 else 1
                ScrollContainer(rememberScrollState(), Modifier.fillMaxSize(), fadingEdges = true) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        current.forEach { SectionBlock(it, columns, changed) }
                    }
                }
            }
        }
    }
}
