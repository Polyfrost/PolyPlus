@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticSwatches
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.SwatchArt
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform

@Composable
internal fun PreviewPanel(
    selected: CosmeticUiItem?,
    status: String?,
    modifier: Modifier,
    heading: Pair<String, String>? = null,
    selectedVariantId: Int? = null,
    onSelectVariant: (Int) -> Unit = {},
    rotation: PreviewRotation? = null,
    onResetRotation: () -> Unit = {},
    selectionPreview: SelectionPreview? = null,
    showAuraColor: Boolean = false,
    auraColor: Int? = null,
    onPreviewAuraColor: (Int) -> Unit = {},
    onSelectAuraColor: (Int?) -> Unit = {},
) {
    Box(
        modifier = modifier.clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(12.dp)),
    ) {
        val hasHeadCosmetic = selectionPreview?.hasHeadCosmetic
            ?: CosmeticCatalog.localEquipped().equipped.containsKey(BodySlot.Hat)
        PlayerPreview(
            Modifier.align(Alignment.Center).fillMaxWidth().height(330.dp),
            source = selectionPreview?.source ?: PlayerPreviewSource.LocalLive,
            autoSpin = false,
            verticalAnchor = if (hasHeadCosmetic) 0.64f else 0.5f,
            initialYaw = FRONT_YAW_DEG,
            live = true,
            rotation = rotation,
        )
        ResetRotationButton(rotation, onResetRotation)
        Column(Modifier.align(Alignment.TopStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (title, subtitle) = heading ?: selected?.let { it.name to it.collection } ?: (null to null)
            if (title != null) {
                Column {
                    SocialText(title, color = OneConfigTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    subtitle?.let { SocialText(it, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp) }
                }
            }
            if (status != null) {
                SocialText(status, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
            }
        }
        val variants = if (selected != null && selected.hasVariants) selected.variants else emptyList()
        if (variants.isNotEmpty() || showAuraColor) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (variants.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(ppShape(12.dp))
                            .background(OneConfigTheme.current.chipBackground)
                            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SocialText("Variants", color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
                        VariantChips(variants, selectedVariantId, onSelectVariant)
                    }
                }
                if (showAuraColor) {
                    PreviewPill(
                        auraColor = auraColor,
                        onPreviewAuraColor = onPreviewAuraColor,
                        onSelectAuraColor = onSelectAuraColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

internal class SelectionPreview(val source: PlayerPreviewSource, val hasHeadCosmetic: Boolean)

@Composable
internal fun rememberSelectionPreview(owned: CosmeticUiItem?, variantId: Int?, mojang: MojangCape?): SelectionPreview? {
    val slot = owned?.type?.defaultSlot()
    val previewsOwned = owned != null && slot != null && variantId != null &&
        !(owned.equipped && owned.equippedVariantId == variantId)
    val installs = CosmeticAssetCache.installs
    val textureVersion = MojangCapeService.textureVersion
    val mojangState = MojangCapeService.state

    var previewId by remember(variantId) { mutableStateOf(variantId) }
    LaunchedEffect(variantId, previewsOwned) {
        if (!previewsOwned || variantId == null) return@LaunchedEffect
        val slim = ClientPlatform.runOnMainSync { ClientPlatform.localSkinSlim() }
        val id = CosmeticCatalog.resolveVariantForSkin(variantId, slim)
        previewId = id
        if (!isPreviewAssetLoaded(id, slot == BodySlot.Cape, slot == BodySlot.Pet)) CosmeticAssetCache.ensureCosmeticLoaded(id)
    }

    val base = CosmeticCatalog.localEquipped().equipped
    val look = remember(base, owned?.equippedCosmeticId, slot, previewId, previewsOwned, mojang?.id) {
        base.toMutableMap().apply {
            val id = previewId
            if (previewsOwned && slot != null && id != null) {
                owned?.equippedCosmeticId?.let { equippedId -> values.removeAll { it == equippedId } }
                put(slot, id)
            }
            if (mojang != null) remove(BodySlot.Cape)
        }
    }
    val mojangTexture = remember(mojang?.textureHash, textureVersion) { mojang?.let(MojangCapeService::texture) }
    val localMojang = remember(mojangState, textureVersion) { MojangCapeService.localCapeTexture() }
    val source = remember(look, installs, mojangTexture, localMojang) {
        outfitPreviewSource(look, mojangTexture ?: localMojang)
    }
    if (!previewsOwned && mojang == null) return null
    return SelectionPreview(source, BodySlot.Hat in look)
}

@Composable
internal fun BoxScope.ResetRotationButton(rotation: PreviewRotation?, onReset: () -> Unit) {
    if (rotation?.userRotated != true) return
    Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
        SmallButton("Reset view", iconPath = "refresh", primary = false, onClick = onReset)
    }
}

@Composable
private fun PreviewPill(
    auraColor: Int?,
    onPreviewAuraColor: (Int) -> Unit,
    onSelectAuraColor: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customOpen by remember { mutableStateOf(false) }

    var pillHeight by remember { mutableIntStateOf(0) }
    val popoverGapPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(modifier) {
        Row(
            modifier = Modifier
                .onSizeChanged { pillHeight = it.height }
                .clip(ppShape(PILL_RADIUS))
                .background(OneConfigTheme.current.chipBackground)
                .border(1.dp, OneConfigTheme.current.borderColor, ppShape(PILL_RADIUS))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraSwatchRow(
                selectedColor = auraColor,
                customOpen = customOpen,
                onToggleCustom = { customOpen = !customOpen },
                onCommit = onSelectAuraColor,
            )
        }
        if (customOpen) {
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
internal fun VariantPicker(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelect: (Int) -> Unit,
    ownedVariantIds: Set<Int> = emptySet(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        SocialText("Variant", color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
        VariantChips(variants, selectedVariantId, onSelect, ownedVariantIds)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariantChips(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelect: (Int) -> Unit,
    ownedVariantIds: Set<Int> = emptySet(),
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().heightIn(max = MAX_CHIP_HEIGHT).verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (variant in variants) {
            VariantChip(
                variant = variant,
                selected = variant.id == selectedVariantId,
                dimmed = variant.id in ownedVariantIds && variant.id != selectedVariantId,
                onClick = { onSelect(variant.id) },
            )
        }
    }
}

@Composable
private fun VariantChip(variant: CosmeticVariantUi, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val swatchVersion = CosmeticSwatches.version
    val art = remember(variant.id, swatchVersion) { CosmeticSwatches.get(variant.id) }
    Row(
        modifier = Modifier.height(28.dp)
            .alpha(if (dimmed) 0.55f else 1f)
            .clip(ppShape(6.dp))
            .background(if (selected) OneConfigTheme.accent else theme.componentBackground)
            .border(
                1.dp,
                when {
                    selected -> OneConfigTheme.accent
                    hovered -> theme.textColorSecondary
                    else -> theme.borderColor
                },
                ppShape(6.dp),
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 13.dp, height = 20.dp).clip(ppShape(4.dp)).background(theme.chipBackground)) {
            when (art) {
                is SwatchArt.Picture -> Image(
                    bitmap = art.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
                is SwatchArt.Stripes -> Column(Modifier.fillMaxSize()) {
                    for (color in art.colors) {
                        Box(Modifier.fillMaxWidth().weight(1f).background(color))
                    }
                }
                null -> Unit
            }
        }
        SocialText(
            variant.name,
            color = if (selected) theme.accentTextColor else theme.textColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = MAX_CHIP_LABEL_WIDTH),
        )
    }
}

private val MAX_CHIP_HEIGHT = 96.dp

private val MAX_CHIP_LABEL_WIDTH = 120.dp

private val PILL_RADIUS = 18.dp
