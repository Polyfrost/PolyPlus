package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

@Composable
internal fun PreviewPanel(
    selected: CosmeticUiItem?,
    status: String?,
    modifier: Modifier,
    heading: Pair<String, String>? = null,
    selectedVariantId: Int? = null,
    onSelectVariant: (Int) -> Unit = {},
    rotation: PreviewRotation? = null,
    showAuraColor: Boolean = false,
    auraColor: Int? = null,
    onPreviewAuraColor: (Int) -> Unit = {},
    onSelectAuraColor: (Int?) -> Unit = {},
) {
    Box(
        modifier = modifier.clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp)),
    ) {
        val hasHeadCosmetic = CosmeticCatalog.localEquipped().equipped
            .containsKey(BodySlot.Hat)
        PlayerPreview(
            Modifier.align(Alignment.Center).fillMaxWidth().height(330.dp),
            source = PlayerPreviewSource.LocalLive,
            autoSpin = false,
            verticalAnchor = if (hasHeadCosmetic) 0.64f else 0.5f,
            initialYaw = FRONT_YAW_DEG,
            live = true,
            rotation = rotation,
        )
        Column(Modifier.align(Alignment.TopStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (title, subtitle) = heading ?: selected?.let { it.name to it.collection } ?: (null to null)
            if (title != null) {
                Column {
                    SocialText(title, color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    subtitle?.let { SocialText(it, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp) }
                }
            }
            if (status != null) {
                SocialText(status, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
            }
        }
        val variants = if (selected != null && selected.hasVariants) selected.variants else emptyList()
        if (variants.isNotEmpty() || showAuraColor) {
            PreviewPill(
                variants = variants,
                selectedVariantId = selectedVariantId,
                onSelectVariant = onSelectVariant,
                showAuraColor = showAuraColor,
                auraColor = auraColor,
                onPreviewAuraColor = onPreviewAuraColor,
                onSelectAuraColor = onSelectAuraColor,
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
            )
        }
    }
}

@Composable
private fun PreviewPill(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelectVariant: (Int) -> Unit,
    showAuraColor: Boolean,
    auraColor: Int?,
    onPreviewAuraColor: (Int) -> Unit,
    onSelectAuraColor: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showAuraColor) { if (!showAuraColor) customOpen = false }

    var pillHeight by remember { mutableIntStateOf(0) }
    val popoverGapPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(modifier) {
        Row(
            modifier = Modifier
                .onSizeChanged { pillHeight = it.height }
                .clip(ppShape(PILL_RADIUS))
                .background(LocalTheme.current.chipBackground)
                .border(1.dp, LocalTheme.current.borderColor, ppShape(PILL_RADIUS))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (variants.isNotEmpty()) {
                VariantStrip(
                    variants = variants,
                    selectedVariantId = selectedVariantId,
                    onSelect = onSelectVariant,
                )
            }
            if (variants.isNotEmpty() && showAuraColor) {
                Box(Modifier.width(1.dp).height(20.dp).background(LocalTheme.current.borderColor))
            }
            if (showAuraColor) {
                AuraSwatchRow(
                    selectedColor = auraColor,
                    customOpen = customOpen,
                    onToggleCustom = { customOpen = !customOpen },
                    onCommit = onSelectAuraColor,
                )
            }
        }
        if (customOpen && showAuraColor) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, pillHeight + popoverGapPx),
                onDismissRequest = { customOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                AuraCustomPopover(
                    selectedColor = auraColor,
                    onPreview = onPreviewAuraColor,
                    onCommit = onSelectAuraColor,
                )
            }
        }
    }
}

@Composable
private fun RowScope.VariantStrip(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelect: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { VARIANT_WHEEL_STEP.toPx() }
    val arrowStep = with(LocalDensity.current) { VARIANT_ARROW_STEP.toPx() }
    val overflows = scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE

    if (overflows) {
        ScrollArrow(
            iconName = "left-arrow",
            enabled = scrollState.value > 0,
            onClick = { scope.launch { scrollState.animateScrollBy(-arrowStep) } },
        )
    }
    Row(
        modifier = Modifier.weight(1f, fill = false)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val delta = event.changes.first().scrollDelta
                        if (delta.x == 0f && delta.y != 0f) {
                            scope.launch { scrollState.scrollBy(delta.y * wheelStep) }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (variant in variants) {
            val isSelected = variant.id == selectedVariantId
            Box(
                modifier = Modifier
                    .clip(ppShape(6.dp))
                    .background(if (isSelected) Accent else LocalTheme.current.componentBackground)
                    .border(1.dp, if (isSelected) Accent else LocalTheme.current.borderColor, ppShape(6.dp))
                    .clickable { onSelect(variant.id) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                SocialText(
                    variant.name,
                    color = if (isSelected) LocalTheme.current.accentTextColor else LocalTheme.current.textColor,
                    fontSize = 12.sp,
                )
            }
        }
    }
    if (overflows) {
        ScrollArrow(
            iconName = "right-arrow",
            enabled = scrollState.value < scrollState.maxValue,
            onClick = { scope.launch { scrollState.animateScrollBy(arrowStep) } },
        )
    }
}

@Composable
private fun ScrollArrow(iconName: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(18.dp)
            .clip(ppShape(6.dp))
            .background(LocalTheme.current.componentBackground)
            .border(1.dp, LocalTheme.current.borderColor, ppShape(6.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconName, color = LocalTheme.current.textColor, modifier = Modifier.size(10.dp))
    }
}

@Composable
internal fun VariantPicker(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelect: (Int) -> Unit,
    ownedVariantIds: Set<Int> = emptySet(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(220.dp)) {
        SocialText("Variant", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
        for (row in variants.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (variant in row) {
                    val isSelected = variant.id == selectedVariantId
                    Box(
                        modifier = Modifier
                            .alpha(if (variant.id in ownedVariantIds && !isSelected) 0.55f else 1f)
                            .clip(ppShape(6.dp))
                            .background(if (isSelected) Accent else LocalTheme.current.chipBackground)
                            .border(1.dp, if (isSelected) Accent else LocalTheme.current.borderColor, ppShape(6.dp))
                            .clickable { onSelect(variant.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        SocialText(
                            variant.name,
                            color = if (isSelected) LocalTheme.current.accentTextColor else LocalTheme.current.textColor,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private val PILL_RADIUS = 18.dp

private val VARIANT_WHEEL_STEP = 48.dp

private val VARIANT_ARROW_STEP = 96.dp
