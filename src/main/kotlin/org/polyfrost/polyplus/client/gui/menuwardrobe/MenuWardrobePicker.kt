@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlIndication
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.wardrobe.AuraColorPopup
import org.polyfrost.polyplus.client.gui.wardrobe.AuraSwatchRow
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.ForceCapesToggle
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import kotlin.math.abs
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private val CarouselHeaderHeight = 38.dp
private val CarouselPadding = 10.dp
private val CarouselRowGap = 8.dp
private val CarouselTileHeight = 100.dp
private val CarouselTileWidth = 100.dp
private val CarouselTileGap = 8.dp
private val CarouselWidth = 700.dp
private val FadingEdge = 32.dp
private val CarouselWheelStep = 108.dp
private val VariantWheelStep = 40.dp
private const val WheelNotchDp = 150f
private val NoteTileWidth = 260.dp
internal val OutlineOnHover = ControlIndication(hoverAlpha = 0f, hoverBorder = true)
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
internal fun OutlineIconButton(icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val description = when (icon) {
        "left-arrow" -> "Back"
        "close" -> "Close"
        else -> icon.replace('-', ' ')
    }
    val text = OneConfigTheme.current.textColor
    IconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        modifier = modifier,
        style = IconButtonStyle.Hairline,
        tooltip = null,
        restTint = text.copy(alpha = text.alpha * 0.7f),
        frameSize = 26.dp,
        iconSize = 18.dp,
    )
}

@Composable
internal fun CarouselPicker(model: PickerModel, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = CarouselWidth).fillMaxWidth().panel(16.dp).padding(CarouselPadding),
            verticalArrangement = Arrangement.spacedBy(CarouselRowGap),
        ) {
            Row(Modifier.fillMaxWidth().height(CarouselHeaderHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(model.type.displayName, Modifier.padding(start = 2.dp), style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Box(Modifier.weight(1f)) { CarouselVariants(model) }
                model.aura?.let { AuraControl(it) }
                if (model.forceToggle) ForceCapesToggle()
                model.target?.let { target ->
                    Text(target.name, Modifier.widthIn(max = 130.dp), color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    TextButton(text = if (model.unequips) "Unequip" else "Equip", onClick = model.onApply, modifier = Modifier, style = if (model.unequips) ControlStyle.Secondary else ControlStyle.Primary, size = ControlSize.Large)
                }
                OutlineIconButton("close", Modifier, model.onClose)
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
            AuraColorPopup(aura.color, aura.onPreview, aura.onCommit, onDismiss = { customOpen = false })
        }
    }
}

@Composable
private fun CarouselVariants(model: PickerModel) {
    val item = (model.selected ?: model.target)?.item?.takeIf { it.hasVariants } ?: return
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { VariantWheelStep.toPx() }
    ScrollContainer(
        scroll,
        Modifier.wheelSteps(scroll, scope, wheelStep),
        orientation = Orientation.Horizontal,
        scrollbar = false,
        smoothWheel = false,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (variant in item.variants) {
                Box(Modifier) {
                    Chip(label = variant.name, selected = variant.id == model.variantOf(item), onClick = { model.onPickVariant(item, variant.id) })
                }
            }
        }
    }
}

private fun Modifier.wheelSteps(scroll: ScrollState, scope: CoroutineScope, step: Float): Modifier =
    pointerInput(scroll, step) {
        var target = Float.NaN
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta
                val notches = (if (abs(delta.x) > abs(delta.y)) delta.x else delta.y) / WheelNotchDp
                val from = if (target.isNaN() || !scroll.isScrollInProgress) scroll.value.toFloat() else target
                val next = (from + notches * step).coerceIn(0f, scroll.maxValue.toFloat())
                target = next
                scope.launch {
                    scroll.animateScrollTo(next.roundToInt(), Motion.spec(spring()))
                    if (target == next) target = Float.NaN
                }
                change.consume()
            }
        }
    }

@Composable
private fun CarouselStrip(model: PickerModel) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { CarouselWheelStep.toPx() }
    val edge = OneConfigTheme.current.pageBackground
    val startAlpha by Motion.animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f)
    val endAlpha by Motion.animateFloatAsState(if (scroll.canScrollForward) 1f else 0f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(CarouselTileHeight)
            .wheelSteps(scroll, scope, wheelStep)
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
        ScrollContainer(
            scroll,
            Modifier.fillMaxHeight(),
            orientation = Orientation.Horizontal,
            scrollbar = false,
            smoothWheel = false,
        ) {
            Row(
                Modifier.fillMaxHeight().widthIn(min = maxWidth),
                horizontalArrangement = Arrangement.spacedBy(CarouselTileGap, Alignment.CenterHorizontally),
            ) {
                model.note?.let { NoteTile(it) }
                for (entry in model.entries) CarouselTile(entry, model)
                model.onMarketplace?.let { MarketplaceTile(it) }
            }
        }
    }
}

@Composable
private fun CarouselTile(entry: PickerEntry, model: PickerModel) {
    val hover = rememberHoverState()
    TryOnWhileHovered(entry, hover.hovered, model)
    val selected = entry.key == model.selected?.key
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(12.dp)
    Column(
        Modifier
            .width(CarouselTileWidth)
            .fillMaxHeight()
            .themedSurface(SurfaceKind.Card, shape, glass = false)
            .then(if (entry.equipped && !selected) Modifier.border(1.dp, OneConfigTheme.accent, shape) else Modifier)
            .hoverable(hover.interactionSource)
            .selectableRow(selected, { model.onPick(entry) }, entry.name, shape, interactionSource = hover.interactionSource)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(5.dp), glass = false),
        ) {
            EntryArt(entry, model, Modifier.fillMaxSize().padding(6.dp))
            if (entry.equipped) Box(Modifier.align(Alignment.TopStart).padding(2.dp)) { Badge("Equipped", tone = BadgeTone.Accent, style = BadgeStyle.Solid) }
        }
        Text(entry.name, color = SelectionDefaults.itemContentColor(selected), style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun NoteTile(text: String) {
    Box(Modifier.width(NoteTileWidth).fillMaxHeight().padding(6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MarketplaceTile(onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val color = theme.textColor
    val shape = theme.sideBarNavigationEntryShape
    Column(
        Modifier
            .width(CarouselTileWidth)
            .fillMaxHeight()
            .clip(shape)
            .oneConfigClickable("Get more in Marketplace", onClick, shape = shape, recipe = OutlineOnHover)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        ThemedIcon("plus", color, 14.dp)
        Text("Get more in Marketplace", color = color, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SlotPlaceholder() {
    ThemedIcon("plus", OneConfigTheme.current.textColorSecondary, 14.dp)
}
