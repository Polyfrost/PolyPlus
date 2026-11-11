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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPopup
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.CosmeticSwatches
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.SwatchArt
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitInfo
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.PlayerSkinInfo
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinModel
import org.polyfrost.polyplus.client.gui.DISABLED_ALPHA
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentStep
import org.polyfrost.polyplus.client.gui.lobbyshell.sceneFrost
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.ppShapeOf
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

@Composable
internal fun WardrobeGrid(
    entries: List<WardrobeEntry>,
    state: LazyGridState,
    ownedIds: Set<Int>,
    cartKeys: Set<String>,
    selection: WardrobeSelection?,
    variantPicks: Map<Int, Int>,
    storeVariantPicks: Map<Int, Int>,
    mojangBusy: Boolean,
    modifier: Modifier,
    coverFor: (CosmeticUiItem) -> Int?,
    onOwned: (CosmeticUiItem) -> Unit,
    onClickOwned: (CosmeticUiItem) -> Unit,
    onPreviewOwned: (CosmeticUiItem) -> Unit,
    onSelectVariant: (Int, Int) -> Unit,
    onShop: (CosmeticStoreInfo) -> Unit,
    onPickStoreVariant: (Int, Int) -> Unit,
    onAddToCart: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onBuyNow: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onMojang: (MojangCape) -> Unit,
    onPreviewMojang: (MojangCape) -> Unit,
    onBundle: (BundleInfo) -> Unit,
    onBuyBundle: (BundleInfo) -> Unit,
    onLibraryAction: (WardrobeSection) -> Unit,
    onSelectOutfit: (OutfitInfo) -> Unit,
    onSelectSkin: (PlayerSkinInfo) -> Unit,
    onStatus: (String) -> Unit,
    columns: Int = 4,
    onConsent: () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            entries,
            key = { it.key },
            span = { if (it.fullSpan) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { entry ->
            when (entry) {
                is WardrobeEntry.Divider -> WardrobeDivider(entry.label)
                is WardrobeEntry.Group -> CapeGroupHeading(entry.title, entry.note, entry.forceToggle)
                is WardrobeEntry.Note -> if (entry.id == CONSENT_NOTE_ID) ConsentNote(entry.text, onConsent) else CapeSectionNote(entry.text)
                is WardrobeEntry.Banner -> WardrobeBanner(entry.text, entry.action, onAction = { OneConfigUI.open("oneconfig:keybinds") })
                is WardrobeEntry.Owned -> {
                    val item = entry.item
                    val variantId = selectedVariantId(item, variantPicks)
                    WardrobeCard(
                        name = item.name,
                        detail = { CardDetailText(item.collection) },
                        action = equipAction(item.equipped),
                        onAction = { onOwned(item) },
                        equipped = item.equipped,
                        selected = selection == WardrobeSelection.Owned(item.groupId, item.type),
                        onClick = { onClickOwned(item) },
                        variants = item.variants,
                        selectedVariantId = variantId,
                        onPickVariant = { onSelectVariant(item.groupId, it) },
                        menu = listOf(
                            CardMenuAction("Preview") { onPreviewOwned(item) },
                            CardMenuAction(if (item.equipped) "Unequip" else "Equip") { onOwned(item) },
                        ),
                    ) { modifier ->
                        val (source, loadTick) = if (variantId != null) {
                            rememberCosmeticPreviewSource(variantId, item.type)
                        } else {
                            null to 0
                        }
                        CosmeticThumbnail(
                            source,
                            item.type,
                            "card-${item.groupId}-$variantId-$loadTick",
                            modifier,
                            checker = false,
                            coverAssetId = coverFor(item),
                        )
                    }
                }
                is WardrobeEntry.Shop -> {
                    val info = entry.info
                    val variant = selectedStoreVariant(info, storeVariantPicks, ownedIds)
                    val owned = info.fullyOwned(ownedIds)
                    val inCart = cosmeticCartKey(variant.id) in cartKeys
                    val variants = info.uiVariants()
                    val variantCount = variants.size
                    WardrobeCard(
                        name = info.name,
                        detail = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PriceLabel(info)
                                if (variantCount > 1) {
                                    SocialText("$variantCount variants", color = OneConfigTheme.current.textColorSecondary, fontSize = 11.sp)
                                }
                            }
                        },
                        action = when {
                            owned || variant.id in ownedIds -> CardAction("Owned", "tick", enabled = false)
                            inCart -> CardAction("In cart", "tick", enabled = false)
                            else -> CardAction("Add to cart", "plus")
                        },
                        onAction = { onAddToCart(info, variant) },
                        equipped = false,
                        selected = selection == WardrobeSelection.Shop(info.id),
                        badge = when {
                            owned -> null
                            info.discounted -> CardBadge("${info.discountRate}% OFF", CardBadgeStyle.Sale)
                            isNewItem(info.createdAt) -> CardBadge("NEW", CardBadgeStyle.New)
                            else -> null
                        },
                        onClick = { onShop(info) },
                        variants = variants,
                        selectedVariantId = variant.id,
                        onPickVariant = { onPickStoreVariant(info.id, it) },
                        menu = listOf(
                            CardMenuAction("Preview") { onShop(info) },
                            CardMenuAction("Add to cart", enabled = !inCart && !owned && variant.id !in ownedIds) { onAddToCart(info, variant) },
                            CardMenuAction("Buy now", enabled = !owned && variant.id !in ownedIds) { onBuyNow(info, variant) },
                        ),
                    ) { modifier ->
                        val (source, loadTick) = rememberCosmeticPreviewSource(variant.id, info.type)
                        CosmeticThumbnail(
                            source,
                            info.type,
                            "shop-${variant.id}-$loadTick",
                            modifier,
                            checker = false,
                            coverAssetId = info.coverAssetId,
                        )
                    }
                }
                is WardrobeEntry.Mojang -> {
                    val cape = entry.cape
                    WardrobeCard(
                        name = cape.name,
                        detail = { CardDetailText(MOJANG_CAPE_COLLECTION) },
                        action = equipAction(cape.active),
                        onAction = { onMojang(cape) },
                        equipped = cape.active,
                        selected = selection == WardrobeSelection.Mojang(cape.id),
                        enabled = !mojangBusy,
                        onClick = { onPreviewMojang(cape) },
                        menu = listOf(
                            CardMenuAction("Preview") { onPreviewMojang(cape) },
                            CardMenuAction(if (cape.active) "Remove" else "Equip") { onMojang(cape) },
                        ),
                    ) { modifier ->
                        val textureVersion = MojangCapeService.textureVersion
                        val texture = remember(cape.textureHash, textureVersion) { MojangCapeService.texture(cape) }
                        val source = remember(texture) {
                            texture?.let { PlayerPreviewSource.Override(CosmeticEquipment(), mojangCape = it) }
                        }
                        CosmeticThumbnail(
                            source,
                            CosmeticType.Cape,
                            "mojang-${cape.textureHash}-${texture != null}",
                            modifier,
                            checker = false,
                        )
                    }
                }
                is WardrobeEntry.Bundle -> {
                    val bundle = entry.bundle
                    WardrobeCard(
                        name = bundle.name,
                        detail = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PriceLabel(bundle.basePrice, bundle.finalPrice, bundle.discounted)
                            }
                        },
                        action = CardAction("View bundle", "right-arrow"),
                        onAction = { onBundle(bundle) },
                        equipped = false,
                        selected = selection == WardrobeSelection.Bundle(bundle.id),
                        badge = if (bundle.discounted) {
                            CardBadge("SAVE ${bundle.discountRate}%", CardBadgeStyle.Sale)
                        } else {
                            CardBadge("BUNDLE", CardBadgeStyle.Accent)
                        },
                        onClick = { onBundle(bundle) },
                        menu = listOf(
                            CardMenuAction("View bundle") { onBundle(bundle) },
                            CardMenuAction("Buy now") { onBuyBundle(bundle) },
                        ),
                    ) { modifier ->
                        CoverThumbnail(bundle.assetId, modifier)
                    }
                }
                is WardrobeEntry.LibraryHeader -> LibraryIntro(
                    note = entry.note,
                    action = entry.action,
                    onAction = { onLibraryAction(entry.section) },
                )
                is WardrobeEntry.Outfit -> {
                    val outfit = entry.outfit
                    var renaming by remember { mutableStateOf(false) }
                    var deleting by remember { mutableStateOf(false) }
                    WardrobeCard(
                        name = outfit.name,
                        detail = {
                            CardDetailText(if (entry.active) "Active" else cosmeticCountLabel(outfit.equipped.size))
                            if (renaming) {
                                RenamePopup(
                                    current = outfit.name,
                                    onSave = { renameOutfit(outfit, it, onStatus) },
                                    onDismiss = { renaming = false },
                                )
                            }
                            if (deleting) {
                                ConfirmDialog(
                                    title = "Delete ${outfit.name}?",
                                    text = "This outfit will be removed from your wardrobe.",
                                    confirmLabel = "Delete",
                                    onConfirm = {
                                        deleting = false
                                        deleteOutfit(outfit, onStatus)
                                    },
                                    onDismiss = { deleting = false },
                                )
                            }
                        },
                        action = wearAction(entry.active),
                        onAction = { wearOutfit(outfit, onStatus) },
                        equipped = entry.active,
                        selected = selection == WardrobeSelection.Outfit(outfit.id),
                        enabled = !OutfitLibrary.busy,
                        badge = favoriteBadge(outfit.favorite),
                        onClick = { onSelectOutfit(outfit) },
                        menu = listOf(
                            CardMenuAction("Wear", enabled = !entry.active) { wearOutfit(outfit, onStatus) },
                            CardMenuAction("Rename") { renaming = true },
                            CardMenuAction(favoriteMenuLabel(outfit.favorite)) { toggleOutfitFavorite(outfit, onStatus) },
                            CardMenuAction("Delete") { deleting = true },
                        ),
                    ) { modifier ->
                        OutfitThumbnail(outfit, modifier)
                    }
                }
                is WardrobeEntry.Skin -> {
                    val skin = entry.skin
                    var renaming by remember { mutableStateOf(false) }
                    var deleting by remember { mutableStateOf(false) }
                    WardrobeCard(
                        name = skin.name,
                        detail = {
                            CardDetailText(skin.model.label)
                            if (renaming) {
                                RenamePopup(
                                    current = skin.name,
                                    onSave = { renameSkin(skin, it, onStatus) },
                                    onDismiss = { renaming = false },
                                )
                            }
                            if (deleting) {
                                ConfirmDialog(
                                    title = "Delete ${skin.name}?",
                                    text = "This skin will be removed from your wardrobe.",
                                    confirmLabel = "Delete",
                                    onConfirm = {
                                        deleting = false
                                        deleteSkin(skin, onStatus)
                                    },
                                    onDismiss = { deleting = false },
                                )
                            }
                        },
                        action = wearAction(entry.worn),
                        onAction = { wearSkin(skin, onStatus) },
                        equipped = entry.worn,
                        selected = selection == WardrobeSelection.Skin(skin.id),
                        enabled = !SkinLibrary.busy,
                        badge = favoriteBadge(skin.favorite),
                        onClick = { onSelectSkin(skin) },
                        menu = listOf(
                            CardMenuAction("Wear", enabled = !entry.worn) { wearSkin(skin, onStatus) },
                            CardMenuAction("Rename") { renaming = true },
                            CardMenuAction(favoriteMenuLabel(skin.favorite)) { toggleSkinFavorite(skin, onStatus) },
                            CardMenuAction(
                                if (skin.model == SkinModel.Slim) "Switch to classic arms" else "Switch to slim arms",
                            ) { switchSkinModel(skin, onStatus) },
                            CardMenuAction("Delete") { deleting = true },
                        ),
                    ) { modifier ->
                        SkinThumbnail(skin, modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun WardrobeDivider(label: String) {
    val theme = OneConfigTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(theme.borderColor))
        SocialText(label.uppercase(), color = theme.textColorSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.weight(1f).height(1.dp).background(theme.borderColor))
    }
}

private data class CardAction(val label: String, val icon: String, val enabled: Boolean = true)

private class CardMenuAction(val label: String, val enabled: Boolean = true, val onSelect: () -> Unit)

private enum class CardBadgeStyle { New, Sale, Accent }

private data class CardBadge(val label: String, val style: CardBadgeStyle, val icon: String? = null)

private fun equipAction(equipped: Boolean): CardAction =
    if (equipped) CardAction("Equipped", "tick") else CardAction("Equip", "profiles")

private fun wearAction(wearing: Boolean): CardAction =
    if (wearing) CardAction("Wearing", "tick", enabled = false) else CardAction("Wear", "profiles")

private fun favoriteBadge(favorite: Boolean): CardBadge? =
    if (favorite) CardBadge("", CardBadgeStyle.Accent, icon = "star-filled") else null

private fun favoriteMenuLabel(favorite: Boolean): String =
    if (favorite) "Remove from favourites" else "Add to favourites"

private fun cosmeticCountLabel(count: Int): String = when (count) {
    0 -> "No cosmetics"
    1 -> "1 cosmetic"
    else -> "$count cosmetics"
}

private val CARD_SUCCESS = Color(0xFF239A60)
private val CARD_NEUTRAL_CHIP = Color(0xFFD9D9D9)
private val CARD_HIGHLIGHT = Color(0x0DFFFFFF)

@Composable
private fun CardDetailText(text: String) {
    CardLabel(text, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun WardrobeCard(
    name: String,
    detail: @Composable () -> Unit,
    action: CardAction,
    onAction: () -> Unit,
    equipped: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    badge: CardBadge? = null,
    enabled: Boolean = true,
    variants: List<CosmeticVariantUi> = emptyList(),
    selectedVariantId: Int? = null,
    onPickVariant: (Int) -> Unit = {},
    menu: List<CardMenuAction> = emptyList(),
    thumbnail: @Composable (Modifier) -> Unit,
) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val outline = when {
        equipped -> OneConfigTheme.accent
        selected || (hovered && enabled) -> theme.textColorSecondary
        else -> theme.borderColor
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .clip(ppShape(12.dp))
            .background(theme.modCardBackground)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, CARD_HIGHLIGHT),
                        center = Offset(size.width / 2f, size.height * 0.8f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }
            .border(1.dp, outline, ppShape(12.dp))
            .hoverable(interaction)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(maxWidth)
                        .clip(ppShape(5.dp))
                        .background(OneConfigTheme.current.chipBackground)
                        .border(1.dp, OneConfigTheme.current.borderColor, ppShape(5.dp)),
                ) {
                    thumbnail(Modifier.fillMaxSize())
                    if (hovered && variants.size > 1) {
                        VariantSwatches(
                            variants = variants,
                            selectedVariantId = selectedVariantId,
                            onPick = onPickVariant,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CardLabel(name, color = theme.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (menu.isNotEmpty() && (hovered || menuOpen)) {
                        Box(
                            modifier = Modifier.padding(start = 6.dp).size(width = 22.dp, height = 0.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CardMenuButton(
                                actions = menu,
                                enabled = enabled,
                                open = menuOpen,
                                onOpenChange = { menuOpen = it },
                            )
                        }
                    }
                }
                detail()
            }
            Spacer(Modifier.height(12.dp))
            CardActionStrip(action, active = equipped, enabled = enabled && action.enabled, onAction = onAction)
        }
        if (badge != null) {
            CardBadgeTab(badge, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun VariantSwatches(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        variants.forEach { variant ->
            VariantSwatch(variant, selected = variant.id == selectedVariantId, onClick = { onPick(variant.id) })
        }
    }
}

@Composable
private fun VariantSwatch(variant: CosmeticVariantUi, selected: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val swatchVersion = CosmeticSwatches.version
    val art = remember(variant.id, swatchVersion) { CosmeticSwatches.get(variant.id) }
    Box(
        modifier = Modifier.size(width = 14.dp, height = 22.dp)
            .clip(ppShape(5.dp))
            .background(theme.chipBackground)
            .hoverable(interaction)
            .clickable(onClick = onClick),
    ) {
        when (art) {
            is SwatchArt.Picture -> Image(
                bitmap = art.bitmap,
                contentDescription = variant.name,
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
        Box(
            Modifier.fillMaxSize().border(
                1.dp,
                when {
                    selected -> OneConfigTheme.accent
                    hovered -> theme.textColorSecondary
                    else -> OneConfigTheme.current.borderColor
                },
                ppShape(5.dp),
            ),
        )
        if (hovered) {
            TooltipPopup(variant.name, TooltipPlacement.AboveStart)
        }
    }
}

@Composable
private fun CardMenuButton(
    actions: List<CardMenuAction>,
    enabled: Boolean,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
) {
    val theme = OneConfigTheme.current
    Box(
        modifier = Modifier.requiredSize(22.dp)
            .clip(ppShape(6.dp))
            .background(theme.chipBackground)
            .clickable { onOpenChange(!open) },
        contentAlignment = Alignment.Center,
    ) {
        Icon("dots-vertical", color = theme.textColor, modifier = Modifier.size(14.dp))
        if (open) {
            CardMenuPopup(actions, enabled = enabled, onDismiss = { onOpenChange(false) })
        }
    }
}

@Composable
internal fun rememberDropdownPosition(): PopupPositionProvider {
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    return remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val below = anchorBounds.bottom + gap
                val y = if (below + popupContentSize.height <= windowSize.height) {
                    below
                } else {
                    (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
                }
                return IntOffset(x, y)
            }
        }
    }
}

@Composable
private fun CardMenuPopup(actions: List<CardMenuAction>, enabled: Boolean, onDismiss: () -> Unit) {
    val theme = OneConfigTheme.current
    EscapeCloses(onDismiss)
    Popup(
        popupPositionProvider = rememberDropdownPosition(),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 160.dp).width(IntrinsicSize.Max)
                .sceneFrost(16.dp)
                .clip(ppShape(16.dp))
                .background(theme.popupBackground)
                .border(1.dp, theme.borderColor, ppShape(16.dp))
                .padding(6.dp),
        ) {
            actions.forEach { action ->
                CardMenuRow(action.label, enabled = enabled && action.enabled) {
                    onDismiss()
                    action.onSelect()
                }
            }
        }
    }
}

@Composable
private fun CardMenuRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .clip(ppShape(8.dp))
            .background(if (hovered && enabled) theme.chipBackground else Color.Transparent)
            .hoverable(interaction)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SocialText(label, color = theme.textColor, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun RenamePopup(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val theme = OneConfigTheme.current
    var value by remember { mutableStateOf(current) }
    val focusRequester = remember { FocusRequester() }
    val save = {
        val name = value.trim()
        if (name.isNotEmpty()) {
            onDismiss()
            if (name != current) onSave(name)
        }
    }
    EscapeCloses(onDismiss)
    Popup(
        popupPositionProvider = rememberDropdownPosition(),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier.width(240.dp)
                .sceneFrost(16.dp)
                .clip(ppShape(16.dp))
                .background(theme.popupBackground)
                .border(1.dp, theme.borderColor, ppShape(16.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SocialText("Rename", color = theme.textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            WardrobeTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = current,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                maxLength = MAX_LIBRARY_NAME_LENGTH,
                onSubmit = save,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SmallButton("Cancel", iconPath = "close", primary = false, onClick = onDismiss)
                SmallButton("Save", iconPath = "tick", primary = true, onClick = save)
            }
        }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}

@Composable
private fun CardActionStrip(action: CardAction, active: Boolean, enabled: Boolean, onAction: () -> Unit) {
    val theme = OneConfigTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (active) theme.accentTextColor else theme.textColor
    Box(Modifier.fillMaxWidth().height(36.dp)) {
        Row(
            modifier = Modifier.fillMaxSize()
                .background(if (active) OneConfigTheme.accent else OneConfigTheme.current.chipBackground)
                .hoverable(interaction)
                .clickable(enabled = enabled, onClick = onAction),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(action.icon, color = color, modifier = Modifier.size(16.dp))
            SocialText(action.label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (!active) {
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(if (hovered && enabled) theme.textColorSecondary else theme.borderColor),
            )
        }
    }
}

@Composable
private fun CardBadgeTab(badge: CardBadge, modifier: Modifier) {
    val theme = OneConfigTheme.current
    val color = if (badge.style == CardBadgeStyle.New) theme.textColor else theme.accentTextColor
    Row(
        modifier = modifier.widthIn(min = if (badge.label.isEmpty()) 40.dp else 81.dp).height(21.dp)
            .clip(ppShapeOf(bottomStart = 7.dp, bottomEnd = 7.dp))
            .background(
                when (badge.style) {
                    CardBadgeStyle.New -> CARD_SUCCESS
                    CardBadgeStyle.Sale -> CARD_NEUTRAL_CHIP
                    CardBadgeStyle.Accent -> OneConfigTheme.accent
                },
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (badge.icon != null) {
            Icon(badge.icon, color = color, modifier = Modifier.size(12.dp))
        }
        if (badge.label.isNotEmpty()) {
            SocialText(
                badge.label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun WardrobeBanner(text: String, action: String, onAction: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().wardrobePanel().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon("alert-circle", color = theme.textColor, modifier = Modifier.size(16.dp))
        SocialText(text, color = theme.textColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
        SmallButton(action, iconPath = "keyboard", primary = false, onClick = onAction)
    }
}

@Composable
private fun LibraryIntro(note: String, action: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SocialText(note, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            SmallButton(action, iconPath = "plus", primary = false, onClick = onAction)
        }
    }
}

@Composable
private fun ConsentNote(text: String, onConsent: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (open) {
        ConsentStep("PolyPlus capes and cosmetics need online services.", Modifier.fillMaxWidth(), { open = false }) {
            open = false
            onConsent()
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(57.dp)
            .clip(ppShape(16.dp))
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(16.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SocialText(text, 12.sp, Modifier.weight(1f), color = OneConfigTheme.current.textColorSecondary)
        TextButton("Turn on online services", Modifier.height(38.dp), ButtonStyle.Primary) { open = true }
    }
}
