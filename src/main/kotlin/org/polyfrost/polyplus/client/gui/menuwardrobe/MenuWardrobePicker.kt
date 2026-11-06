@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.Tag
import org.polyfrost.polyplus.client.gui.lobbyshell.TagTone
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.wardrobe.AuraCustomPopover
import org.polyfrost.polyplus.client.gui.wardrobe.AuraSwatchRow
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.ForceCapesToggle
import org.polyfrost.polyplus.client.gui.wardrobe.rememberDropdownPosition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import kotlin.math.abs

private const val OC_ICONS = "/assets/oneconfig/ico/"
private val CarouselHeaderHeight = 38.dp
private val CarouselPadding = 10.dp
private val CarouselRowGap = 8.dp
private val CarouselTileHeight = 100.dp
private val CarouselTileWidth = 100.dp
private val CarouselTileGap = 8.dp
private val CarouselWidth = 700.dp
private val FadingEdge = 32.dp
private val CarouselWheelStep = 108.dp
private val NoteTileWidth = 260.dp
internal val CarouselHeight = CarouselPadding * 2 + CarouselHeaderHeight + CarouselRowGap + CarouselTileHeight

internal class PickerEntry(val key: String, val name: String, val equipped: Boolean, val item: CosmeticUiItem?, val cape: MojangCape?)

internal class AuraChoice(val color: Int?, val onPreview: (Int) -> Unit, val onCommit: (Int?) -> Unit)

internal class PickerModel(
    val type: CosmeticType,
    val entries: List<PickerEntry>,
    val selected: PickerEntry?,
    val target: PickerEntry?,
    val unequips: Boolean,
    val variantOf: (CosmeticUiItem) -> Int?,
    val onHover: (PickerEntry, Boolean) -> Unit,
    val onPick: (PickerEntry) -> Unit,
    val onPickVariant: (CosmeticUiItem, Int) -> Unit,
    val onApply: () -> Unit,
    val onClose: () -> Unit,
    val onMarketplace: (() -> Unit)?,
    val aura: AuraChoice?,
    val forceToggle: Boolean,
    val note: String?,
)

@Composable
private fun EntryArt(entry: PickerEntry, model: PickerModel, modifier: Modifier) {
    val item = entry.item
    val cape = entry.cape
    when {
        item != null -> CosmeticArt(item, model.variantOf(item), modifier)
        cape != null -> MojangCapeArt(cape, modifier)
    }
}

@Composable
private fun TryOnWhileHovered(entry: PickerEntry, hovered: Boolean, model: PickerModel) {
    LaunchedEffect(entry.key, hovered) { model.onHover(entry, hovered) }
}

@Composable
internal fun Modifier.listRow(selected: Boolean, hovered: Boolean): Modifier {
    val shape = OneConfigTheme.current.sideBarNavigationEntryShape
    val background by animateColorAsState(if (selected) OneConfigTheme.accent else Color.Transparent)
    val border by animateColorAsState(if (selected || hovered) SocialBorderColor else Color.Transparent)
    return clip(shape).background(background).border(1.dp, border, shape)
}

@Composable
internal fun rowText(selected: Boolean, hovered: Boolean): Color {
    val theme = OneConfigTheme.current
    return when {
        selected -> theme.accentTextColor
        hovered -> theme.textColor
        else -> theme.textColor.copy(alpha = 0.8f)
    }
}

@Composable
internal fun OutlineIconButton(icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val text = OneConfigTheme.current.textColor
    IconButton(themedIcon(OC_ICONS + icon), modifier.size(26.dp), text.copy(alpha = 0.7f), text, true, true, onClick)
}

@Composable
internal fun CarouselPicker(model: PickerModel, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = CarouselWidth).fillMaxWidth().panel(16.dp).padding(CarouselPadding),
            verticalArrangement = Arrangement.spacedBy(CarouselRowGap),
        ) {
            Row(Modifier.fillMaxWidth().height(CarouselHeaderHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(model.type.displayName, 16.sp, Modifier.padding(start = 2.dp), weight = FontWeight.Medium)
                Box(Modifier.weight(1f)) { CarouselVariants(model) }
                model.aura?.let { AuraControl(it) }
                if (model.forceToggle) ForceCapesToggle()
                model.target?.let { target ->
                    Label(target.name, 12.sp, Modifier.widthIn(max = 130.dp), color = OneConfigTheme.current.textColorSecondary)
                    SocialButton(if (model.unequips) "Unequip" else "Equip", filled = !model.unequips, onClick = model.onApply)
                }
                OutlineIconButton("close.svg", Modifier, model.onClose)
            }
            CarouselStrip(model)
        }
    }
}

@Composable
private fun AuraControl(aura: AuraChoice) {
    var customOpen by remember { mutableStateOf(false) }
    Box {
        AuraSwatchRow(aura.color, customOpen, { customOpen = !customOpen }, aura.onCommit)
        if (customOpen) {
            EscapeCloses { customOpen = false }
            Popup(
                popupPositionProvider = rememberDropdownPosition(),
                onDismissRequest = { customOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                AuraCustomPopover(aura.color, aura.onPreview, aura.onCommit)
            }
        }
    }
}

@Composable
private fun CarouselVariants(model: PickerModel) {
    val item = (model.selected ?: model.target)?.item?.takeIf { it.hasVariants } ?: return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (variant in item.variants) {
            Box(Modifier) {
                Chip(variant.name, selected = variant.id == model.variantOf(item)) { model.onPickVariant(item, variant.id) }
            }
        }
    }
}

@Composable
private fun CarouselStrip(model: PickerModel) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { CarouselWheelStep.toPx() }
    val edge = OneConfigTheme.current.pageBackground
    val startAlpha by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f)
    val endAlpha by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(CarouselTileHeight)
            .pointerInput(scroll) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val delta = change.scrollDelta
                        val amount = (if (abs(delta.x) > abs(delta.y)) delta.x else delta.y) * wheelStep
                        scope.launch { scroll.animateScrollBy(amount) }
                        change.consume()
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val length = FadingEdge.toPx()
                drawRect(
                    Brush.horizontalGradient(listOf(edge, Color.Transparent), startX = 0f, endX = length),
                    size = Size(length, size.height),
                    alpha = startAlpha,
                )
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, edge), startX = size.width - length, endX = size.width),
                    topLeft = Offset(size.width - length, 0f),
                    size = Size(length, size.height),
                    alpha = endAlpha,
                )
            },
    ) {
        Row(
            Modifier.fillMaxHeight().horizontalScroll(scroll).widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.spacedBy(CarouselTileGap, Alignment.CenterHorizontally),
        ) {
            model.note?.let { NoteTile(it) }
            for (entry in model.entries) CarouselTile(entry, model)
            model.onMarketplace?.let { MarketplaceTile(it) }
        }
    }
}

@Composable
private fun CarouselTile(entry: PickerEntry, model: PickerModel) {
    val (interaction, hovered) = rememberSocialHover()
    TryOnWhileHovered(entry, hovered, model)
    val selected = entry.key == model.selected?.key
    val theme = OneConfigTheme.current
    val accent = OneConfigTheme.accent
    val shape = ppShape(12.dp)
    val thumbShape = ppShape(5.dp)
    val background by animateColorAsState(
        when {
            selected -> accent
            hovered -> theme.chipBackground
            else -> theme.modCardBackground
        },
    )
    val border by animateColorAsState(
        when {
            selected || entry.equipped -> accent
            hovered -> theme.textColorSecondary
            else -> theme.borderColor
        },
    )
    Column(
        Modifier
            .width(CarouselTileWidth)
            .fillMaxHeight()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .hoverable(interaction)
            .clickableWithSound { model.onPick(entry) }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(thumbShape)
                .background(theme.chipBackground)
                .border(1.dp, theme.borderColor, thumbShape),
        ) {
            EntryArt(entry, model, Modifier.fillMaxSize().padding(6.dp))
            if (entry.equipped) Box(Modifier.align(Alignment.TopStart).padding(2.dp)) { Tag("Equipped", TagTone.Accent) }
        }
        Label(entry.name, 12.sp, color = if (selected) theme.accentTextColor else SocialTextPrimary, weight = FontWeight.Medium)
    }
}

@Composable
private fun NoteTile(text: String) {
    Box(Modifier.width(NoteTileWidth).fillMaxHeight().padding(6.dp), contentAlignment = Alignment.Center) {
        SocialText(text, 12.sp, color = MutedText, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MarketplaceTile(onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val color = rowText(selected = false, hovered = hovered)
    Column(
        Modifier
            .width(CarouselTileWidth)
            .fillMaxHeight()
            .listRow(selected = false, hovered = hovered)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        ThemedIcon(OC_ICONS + "plus.svg", color, 14.dp)
        SocialText("Get more in Marketplace", 12.sp, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SlotPlaceholder() {
    ThemedIcon(OC_ICONS + "plus.svg", OneConfigTheme.current.textColorSecondary, 14.dp)
}
