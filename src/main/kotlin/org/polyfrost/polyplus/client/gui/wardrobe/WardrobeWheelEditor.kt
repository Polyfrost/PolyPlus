@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.emotes.EmoteWheels
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

private val WHEEL_GRID_ORDER: List<Pair<Int, Int>> = listOf(
    0 to 1, 0 to 2, 1 to 2, 2 to 2, 2 to 1, 2 to 0, 1 to 0, 0 to 0,
)

@Composable
internal fun EmoteWheelEditor(
    selectedSlot: Int?,
    coverForEmote: (Int) -> Int?,
    onSelectSlot: (Int?) -> Unit,
    modifier: Modifier,
) {
    val theme = OneConfigTheme.current
    val wheelIndex = EmoteWheels.activeIndex
    val wheelCount = EmoteWheels.wheelCount
    val slots = EmoteWheels.slots(wheelIndex)

    Column(
        modifier = modifier.wardrobePanel().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WheelIconButton("left-arrow", enabled = wheelCount > 1, onClick = {
                EmoteWheels.cycle(-1)
                onSelectSlot(null)
            })
            SocialText(
                "Wheel ${wheelIndex + 1} of $wheelCount",
                color = theme.textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            WheelIconButton("right-arrow", enabled = wheelCount > 1, onClick = {
                EmoteWheels.cycle(1)
                onSelectSlot(null)
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wheelCount < EmoteWheels.MAX_WHEELS) {
                SmallButton("New wheel", iconPath = "plus", primary = false, onClick = {
                    if (EmoteWheels.addWheel()) onSelectSlot(null)
                })
            }
            if (wheelCount > 1) {
                SmallButton("Remove", iconPath = "trash", primary = false, onClick = {
                    EmoteWheels.removeWheel(wheelIndex)
                    onSelectSlot(null)
                })
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - 16.dp) / 3
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0..2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0..2) {
                            val slot = WHEEL_GRID_ORDER.indexOf(row to col)
                            if (slot < 0) {
                                Spacer(Modifier.size(cell))
                            } else {
                                WheelSlotCell(
                                    emoteId = slots.getOrNull(slot),
                                    selected = selectedSlot == slot,
                                    coverForEmote = coverForEmote,
                                    modifier = Modifier.size(cell),
                                    onClick = { onSelectSlot(if (selectedSlot == slot) null else slot) },
                                    onClear = { EmoteWheels.assign(wheelIndex, slot, null) },
                                )
                            }
                        }
                    }
                }
            }
        }

        SocialText(
            if (selectedSlot == null) {
                "Click an emote to add it to the wheel, or pick a slot first to choose where it goes."
            } else {
                "Click an emote to put it in the selected slot."
            },
            color = theme.textColorSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WheelSlotCell(
    emoteId: Int?,
    selected: Boolean,
    coverForEmote: (Int) -> Int?,
    modifier: Modifier,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val name = emoteId?.let { CosmeticCatalog.getEmoteDefinition(it)?.name }
    Box(
        modifier = modifier
            .clip(ppShape(8.dp))
            .background(theme.chipBackground)
            .border(
                1.dp,
                when {
                    selected -> OneConfigTheme.accent
                    hovered -> theme.textColorSecondary
                    else -> theme.borderColor
                },
                ppShape(8.dp),
            )
            .hoverable(interaction)
            .clickable(onClick = onClick),
    ) {
        if (emoteId != null) {
            CosmeticThumbnail(
                source = null,
                type = CosmeticType.Emote,
                previewKey = "wheel-$emoteId",
                modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                checker = false,
                coverAssetId = coverForEmote(emoteId),
            )
            if (name != null) {
                CardLabel(
                    name,
                    color = theme.textColor,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            if (hovered) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp)
                        .clip(ppShape(6.dp))
                        .background(theme.popupBackground)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon("close", color = theme.textColor, modifier = Modifier.size(12.dp))
                }
            }
        } else {
            SocialText(
                "Empty",
                color = theme.textColorSecondary,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
internal fun WheelIconButton(iconPath: String, enabled: Boolean, tooltip: String? = null, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier.size(28.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(ppShape(8.dp))
            .background(theme.chipBackground)
            .border(1.dp, theme.borderColor, ppShape(8.dp))
            .hoverable(interaction)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconPath, color = theme.textColor, modifier = Modifier.size(14.dp))
        if (tooltip != null && hovered) TooltipPopup(tooltip)
    }
}
