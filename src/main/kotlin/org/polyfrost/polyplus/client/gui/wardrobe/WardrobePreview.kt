@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.CosmeticSwatches
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.SwatchArt
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private val PREVIEW_HEIGHT = 330.dp
private const val BODY_MODEL_SCALE = 0.5f
private const val BODY_ANCHOR = 0.5f
private const val HAT_MODEL_SCALE = 0.4f
private const val HAT_ANCHOR = 0.59f

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
    var panelHeight by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableIntStateOf(0) }
    val (title, subtitle) = heading ?: selected?.let { it.name to it.collection } ?: (null to null)
    val hasHeader = title != null || status != null
    val previewHeightPx = with(LocalDensity.current) { PREVIEW_HEIGHT.roundToPx() }
    val previewShift = if (hasHeader) (headerHeight - (panelHeight - previewHeightPx) / 2).coerceAtLeast(0) else 0
    Box(
        modifier = modifier.themedSurface(SurfaceKind.Card, OneConfigTheme.shape(12.dp), glass = false)
            .onSizeChanged { panelHeight = it.height },
    ) {
        val hasHeadCosmetic = selectionPreview?.hasHeadCosmetic
            ?: CosmeticCatalog.localEquipped().equipped.containsKey(BodySlot.Hat)
        PlayerPreview(
            Modifier.align(Alignment.Center).offset { IntOffset(0, previewShift) }.fillMaxWidth().height(PREVIEW_HEIGHT),
            source = selectionPreview?.source ?: PlayerPreviewSource.LocalLive,
            autoSpin = false,
            modelScale = if (hasHeadCosmetic) HAT_MODEL_SCALE else BODY_MODEL_SCALE,
            verticalAnchor = if (hasHeadCosmetic) HAT_ANCHOR else BODY_ANCHOR,
            initialYaw = FRONT_YAW_DEG,
            live = true,
            rotation = rotation,
        )
        ResetRotationButton(rotation, onResetRotation)
        Column(
            Modifier.align(Alignment.TopStart).onSizeChanged { headerHeight = it.height }.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Column {
                    Text(title, color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium))
                    subtitle?.let { Text(it, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small) }
                }
            }
            if (status != null) {
                Text(status, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
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
                            .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(12.dp), glass = false)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Variants", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
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
internal fun rememberSelectionPreview(owned: CosmeticUiItem?, variantId: Int?, mojang: MojangCape?, previewRequest: Int = 0): SelectionPreview? {
    val slot = owned?.type?.defaultSlot()
    val selectedKey = owned?.let { it.type to it.groupId }
    val equippedNow = owned?.equipped == true
    var wasEquipped by remember(selectedKey) { mutableStateOf(equippedNow) }
    var droppedVariant by remember(selectedKey, previewRequest) { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedKey, equippedNow) {
        droppedVariant = if (wasEquipped && !equippedNow) variantId else droppedVariant.takeUnless { equippedNow }
        wasEquipped = equippedNow
    }
    val unequippedHere = (wasEquipped && !equippedNow) || (droppedVariant != null && droppedVariant == variantId)
    val previewsOwned = owned != null && slot != null && variantId != null && !unequippedHere &&
        !(owned.equipped && owned.equippedVariantId == variantId)
    val installs = CosmeticAssetCache.installs
    val textureVersion = MojangCapeService.textureVersion
    val mojangState = MojangCapeService.state

    val pieceIds = owned?.variant(variantId)?.pieces ?: listOfNotNull(variantId)
    var previewPieces by remember(pieceIds, slot) {
        mutableStateOf(
            if (slot == null) {
                emptyMap()
            } else {
                pieceIds.associateBy { CosmeticCatalog.getCosmeticDefinition(it)?.preferredSlot() ?: slot }
            },
        )
    }
    LaunchedEffect(pieceIds, previewsOwned, CosmeticCatalog.version) {
        if (!previewsOwned || slot == null) return@LaunchedEffect
        val pieces = pieceIds.associate { pieceId ->
            CosmeticCatalog.getCosmeticDefinition(pieceId)
                ?.let { CosmeticService.equipTarget(it).getOrNull() }
                ?: run {
                    val slim = ClientPlatform.runOnMainSync { ClientPlatform.localSkinSlim() }
                    slot to CosmeticCatalog.resolveVariantForSkin(pieceId, slim)
                }
        }
        previewPieces = pieces
        for ((pieceSlot, id) in pieces) {
            if (!isPreviewAssetLoaded(id, pieceSlot == BodySlot.Cape, pieceSlot == BodySlot.Pet)) CosmeticAssetCache.ensureCosmeticLoaded(id)
        }
    }

    val base = CosmeticCatalog.localEquipped().equipped
    val look = remember(base, owned?.memberIds, previewPieces, previewsOwned, mojang?.id) {
        base.toMutableMap().apply {
            if (previewsOwned && previewPieces.isNotEmpty()) {
                owned?.memberIds?.let { members -> values.removeAll { it in members } }
                putAll(previewPieces)
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
        TextButton("Reset view", "refresh", onReset, sound = null)
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

    Box(modifier) {
        Row(
            modifier = Modifier
                .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(PILL_RADIUS), glass = false)
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
            AuraColorPopup(
                selectedColor = auraColor,
                onPreview = onPreviewAuraColor,
                onCommit = onSelectAuraColor,
                onDismiss = { customOpen = false },
                gap = 8.dp,
            )
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
        Text("Variant", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
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
    val scroll = rememberScrollState()
    ScrollContainer(scroll, Modifier.fillMaxWidth().heightIn(max = MAX_CHIP_HEIGHT), scrollbar = false) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
}

@Composable
private fun VariantChip(variant: CosmeticVariantUi, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val swatchVersion = CosmeticSwatches.version
    val art = remember(variant.artId, swatchVersion) { CosmeticSwatches.get(variant.artId) }
    val dimAlpha = if (dimmed) theme.disabledAlpha else 1f
    Chip(
        label = variant.name,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.widthIn(max = MAX_CHIP_WIDTH).graphicsLayer { alpha = dimAlpha },
        size = ControlSize.Compact,
        sound = null,
        leading = {
            Box(Modifier.size(width = 13.dp, height = 20.dp).clip(OneConfigTheme.shape(4.dp)).background(theme.chipBackground)) {
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
        },
    )
}

private val MAX_CHIP_HEIGHT = 96.dp

private val MAX_CHIP_WIDTH = 168.dp

private val PILL_RADIUS = 18.dp
