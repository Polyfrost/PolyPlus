@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.polyfrost.compose.host.controller.ControllerButton
import org.polyfrost.compose.host.controller.controllerAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.CardSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ControlIndication
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.selectableCard
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.emotes.EmoteWheels
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private val WHEEL_GRID_ORDER: List<Pair<Int, Int>> = listOf(
    0 to 1, 0 to 2, 1 to 2, 2 to 2, 2 to 1, 2 to 0, 1 to 0, 0 to 0,
)

@Composable
internal fun EmoteWheelEditor(
    selectedSlot: Int?,
    coverForEmote: (Int) -> Int?,
    onSelectSlot: (Int?) -> Unit,
    modifier: Modifier,
    framed: Boolean = true,
) {
    val theme = OneConfigTheme.current
    val wheelIndex = EmoteWheels.activeIndex
    val wheelCount = EmoteWheels.wheelCount
    val slots = EmoteWheels.slots(wheelIndex)

    Column(
        modifier = modifier.then(if (framed) Modifier.themedSurface(SurfaceKind.Card, OneConfigTheme.shape(16.dp)).padding(16.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                "left-arrow",
                "Previous wheel",
                {
                    EmoteWheels.cycle(-1)
                    onSelectSlot(null)
                },
                enabled = wheelCount > 1,
                style = IconButtonStyle.Framed,
                size = ControlSize.Compact,
                tooltip = null,
                sound = null,
            )
            Text(
                "Wheel ${wheelIndex + 1} of $wheelCount",
                modifier = Modifier.weight(1f),
                color = theme.textColor,
                style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
            IconButton(
                "right-arrow",
                "Next wheel",
                {
                    EmoteWheels.cycle(1)
                    onSelectSlot(null)
                },
                enabled = wheelCount > 1,
                style = IconButtonStyle.Framed,
                size = ControlSize.Compact,
                tooltip = null,
                sound = null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wheelCount < EmoteWheels.MAX_WHEELS) {
                TextButton("New wheel", "plus", {
                    if (EmoteWheels.addWheel()) onSelectSlot(null)
                }, sound = null)
            }
            if (wheelCount > 1) {
                TextButton("Remove", "trash", {
                    EmoteWheels.removeWheel(wheelIndex)
                    onSelectSlot(null)
                }, sound = null)
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
                                    slotNumber = slot + 1,
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

        Text(
            if (selectedSlot == null) {
                "Click an emote to add it to the wheel, or pick a slot first to choose where it goes."
            } else {
                "Click an emote to put it in the selected slot."
            },
            color = theme.textColorSecondary,
            style = OneConfigTheme.current.typography.small,
        )
    }
}

@Composable
private fun WheelSlotCell(
    slotNumber: Int,
    emoteId: Int?,
    selected: Boolean,
    coverForEmote: (Int) -> Int?,
    modifier: Modifier,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val theme = OneConfigTheme.current
    val hover = rememberHoverState()
    val hovered = hover.hovered
    val name = emoteId?.let { CosmeticCatalog.getEmoteDefinition(it)?.name }
    val shape = OneConfigTheme.shape(8.dp)
    Box(
        modifier = modifier
            .themedSurface(SurfaceKind.Field, shape, glass = false)
            .controllerAction(ControllerButton.X, "Clear slot", enabled = emoteId != null, onPress = onClear)
            .hoverable(hover.interactionSource)
            .selectableCard(
                selected,
                onClick,
                "Wheel slot $slotNumber, ${name ?: "empty"}",
                shape,
                style = SlotOutline,
                sound = null,
                recipe = SlotHover,
                interactionSource = hover.interactionSource,
            ),
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
                IconButton(
                    "close",
                    "Clear slot",
                    onClear,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                    style = IconButtonStyle.Framed,
                    size = ControlSize.Compact,
                    tooltip = null,
                    sound = null,
                )
            }
        } else {
            Text(
                "Empty",
                modifier = Modifier.align(Alignment.Center),
                color = theme.textColorSecondary,
                style = OneConfigTheme.current.typography.body.copy(fontSize = 10.sp),
            )
        }
    }
}

private val SlotOutline = CardSelectionStyle(ringWidth = 1.dp, fillAlpha = 0f)

private val SlotHover = ControlIndication(hoverAlpha = 0f, hoverBorder = true)
