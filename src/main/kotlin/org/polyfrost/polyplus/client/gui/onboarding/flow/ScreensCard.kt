@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayButton
import org.polyfrost.polyplus.client.gui.lobbyshell.cardFrame

private class ScreenTile(
    val label: String,
    val part: ScreenPart?,
    val ours: Boolean,
    val onOurs: (Boolean) -> Unit,
)

@Composable
internal fun ScreensCard(
    topic: CardTopic.Screens,
    choices: OnboardingChoices,
    frame: CardFlowFrame,
    motion: CardMotion,
    onContinue: () -> Unit,
) {
    val tiles = topic.parts.map { part ->
        ScreenTile(part.label, part, choices.ours(part)) { choices.pick(part, it) }
    } + if (topic.watermark) {
        listOf(ScreenTile(WATERMARK_LABEL, null, choices.watermark) { choices.watermark = it })
    } else {
        emptyList()
    }
    val gridWidth = frame.width - CARD_INSET * 2f
    val tileWidth = (gridWidth - TILE_GAP * (COLUMNS - 1)) / COLUMNS
    val aspect = windowAspect()
    Column(
        Modifier
            .offset(CARD_INSET.dp, CARD_TOP.dp)
            .width(gridWidth.dp)
            .graphicsLayer { alpha = (motion.shown() * (1f - motion.gone())).coerceIn(0f, 1f) },
        verticalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tiles.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dp)) {
                row.forEach { ScreenTileView(it, tileWidth, aspect) }
            }
        }
    }
    Box(
        Modifier
            .offset(0.dp, (frame.height - CARD_INSET - CONTINUE_HEIGHT).dp)
            .width(frame.width.dp)
            .graphicsLayer { alpha = (motion.shown() * (1f - motion.gone())).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center,
    ) {
        PlayButton(Modifier.width(CONTINUE_WIDTH.dp), "Continue", null, zoom = false, onClick = onContinue)
    }
}

@Composable
private fun ScreenTileView(tile: ScreenTile, width: Float, aspect: Float) {
    val previewHeight = width * aspect
    Column(Modifier.width(width.dp)) {
        ScreenMiniature(tile.part, tile.ours, Modifier.size(width.dp, previewHeight.dp).cardFrame(selected = false, hovered = false))
        Spacer(Modifier.height(LABEL_GAP.dp))
        Text(
            tile.label,
            style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(LABEL_GAP.dp))
        TabRow(
            listOf(TabItem(false, VANILLA_NAME), TabItem(true, brandName)),
            tile.ours,
            { if (it != tile.ours) tile.onOurs(it) },
            Modifier.fillMaxWidth(),
            style = TabStyle.Segmented,
            size = ControlSize.Compact,
        )
    }
}

private fun windowAspect(): Float {
    val window = Minecraft.getInstance().window
    val aspect = if (window.width > 0) window.height.toFloat() / window.width else DEFAULT_ASPECT
    return aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
}

private const val WATERMARK_LABEL = "Watermark"
private const val COLUMNS = 4
private const val TILE_GAP = 12f
private const val LABEL_GAP = 8f
private const val CONTINUE_HEIGHT = 60f
private const val CONTINUE_WIDTH = 240f
private const val DEFAULT_ASPECT = 9f / 16f
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 0.6f
