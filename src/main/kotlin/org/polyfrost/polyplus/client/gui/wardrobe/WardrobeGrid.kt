@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.CardSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ControlIndication
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MoreButton
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionRingTone
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.UiSound
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.cardSelection
import org.polyfrost.oneconfig.api.ui.v1.components.controlInteraction
import org.polyfrost.oneconfig.api.ui.v1.components.hiddenUntilHover
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.popupSurface
import org.polyfrost.oneconfig.api.ui.v1.components.rememberAnchoredPosition
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.selectableSwatch
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
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
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentStep
import org.polyfrost.polyplus.client.gui.ppShapeOf
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.polyplus.client.gui.AnimatedPreviewImage
import org.polyfrost.polyplus.client.gui.rememberAnimatedPreview

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
    ScrollContainer(state, modifier, scrollbar = false) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize(),
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
                    is WardrobeEntry.Divider -> SectionHeader(
                        entry.label,
                        contentPadding = PaddingValues(top = 6.dp),
                        title = { style, color ->
                            Divider(Modifier.weight(1f))
                            Text(entry.label.uppercase(), color = color, style = style, maxLines = 1)
                            Divider(Modifier.weight(1f))
                        },
                    )
                    is WardrobeEntry.Group -> CapeGroupHeading(entry.title, entry.note, entry.forceToggle)
                    is WardrobeEntry.Note -> if (entry.id == CONSENT_NOTE_ID) ConsentNote(entry.text, onConsent) else CapeSectionNote(entry.text)
                    is WardrobeEntry.Banner -> WardrobeBanner(entry.text, entry.action, onAction = { OneConfigUI.open("oneconfig:keybinds") })
                    is WardrobeEntry.Owned -> {
                        val item = entry.item
                        val variantId = selectedVariantId(item, variantPicks)
                        val artId = item.artId(variantId)
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
                                MenuAction("Preview", { onPreviewOwned(item) }),
                                MenuAction(if (item.equipped) "Unequip" else "Equip", { onOwned(item) }),
                            ),
                        ) { modifier ->
                            val (source, loadTick) = if (artId != null) {
                                rememberCosmeticPreviewSource(artId, item.type)
                            } else {
                                null to 0
                            }
                            CosmeticThumbnail(
                                source,
                                item.type,
                                "card-${item.groupId}-$artId-$loadTick",
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
                                        Text("$variantCount variants", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.caption)
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
                            previewUrl = info.previewUrlFor(variant.id),
                            menu = listOf(
                                MenuAction("Preview", { onShop(info) }),
                                MenuAction("Add to cart", { onAddToCart(info, variant) }, enabled = !inCart && !owned && variant.id !in ownedIds),
                                MenuAction("Buy now", { onBuyNow(info, variant) }, enabled = !owned && variant.id !in ownedIds),
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
                                MenuAction("Preview", { onPreviewMojang(cape) }),
                                MenuAction(if (cape.active) "Remove" else "Equip", { onMojang(cape) }),
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
                                MenuAction("View bundle", { onBundle(bundle) }),
                                MenuAction("Buy now", { onBuyBundle(bundle) }),
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
                                MenuAction("Wear", { wearOutfit(outfit, onStatus) }, enabled = !entry.active),
                                MenuAction("Rename", { renaming = true }),
                                MenuAction(favoriteMenuLabel(outfit.favorite), { toggleOutfitFavorite(outfit, onStatus) }),
                                MenuAction("Delete", { deleting = true }),
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
                                MenuAction("Wear", { wearSkin(skin, onStatus) }, enabled = !entry.worn),
                                MenuAction("Rename", { renaming = true }),
                                MenuAction(favoriteMenuLabel(skin.favorite), { toggleSkinFavorite(skin, onStatus) }),
                                MenuAction(
                                    if (skin.model == SkinModel.Slim) "Switch to classic arms" else "Switch to slim arms",
                                    { switchSkinModel(skin, onStatus) },
                                ),
                                MenuAction("Delete", { deleting = true }),
                            ),
                        ) { modifier ->
                            SkinThumbnail(skin, modifier)
                        }
                    }
                }
            }
        }
    }
}

private data class CardAction(val label: String, val icon: String, val enabled: Boolean = true)

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
    menu: List<MenuEntry> = emptyList(),
    previewUrl: String? = null,
    thumbnail: @Composable (Modifier) -> Unit,
) {
    val theme = OneConfigTheme.current
    val hover = rememberHoverState()
    val hovered = hover.hovered
    var menuOpen by remember { mutableStateOf(false) }
    val shape = OneConfigTheme.shape(12.dp)
    val highlight = theme.textColor.copy(alpha = theme.textColor.alpha * theme.hoverOverlayAlpha)
    Box(
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else theme.disabledAlpha }
            .themedSurface(SurfaceKind.Card, shape, glass = false)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, highlight),
                        center = Offset(size.width / 2f, size.height * 0.8f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }
            .then(if (equipped) Modifier.border(1.dp, OneConfigTheme.accent, shape) else Modifier)
            .hoverable(hover.interactionSource)
            .cardSelection(selected && !equipped, shape, CardOutline)
            .controlInteraction(hover.interactionSource, shape, if (equipped) NoHoverFill else OutlineOnHover, enabled, focusRing = false, pointerIcon = null)
            .oneConfigClickable(
                name,
                onClick,
                role = A11yRole.Option,
                enabled = enabled,
                selected = selected,
                shape = shape,
                indication = false,
                sound = null,
                interactionSource = hover.interactionSource,
            ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(maxWidth)
                        .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(5.dp), glass = false),
                ) {
                    val preview = rememberAnimatedPreview(previewUrl?.takeIf { hovered })
                    val animating = hovered && preview != null
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (animating) 0f else 1f }) {
                        thumbnail(Modifier.fillMaxSize())
                    }
                    if (animating && preview != null) {
                        AnimatedPreviewImage(preview, Modifier.fillMaxSize().clip(OneConfigTheme.shape(5.dp)))
                    }
                    if (previewUrl != null) AnimatedTag(Modifier.align(Alignment.TopStart).padding(4.dp))
                    if (hovered && variants.size > 1) {
                        VariantSwatches(
                            variants = variants,
                            selectedVariantId = selectedVariantId,
                            onPick = onPickVariant,
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
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
                    if (menu.isNotEmpty()) {
                        Box(
                            modifier = Modifier.padding(start = 6.dp).size(width = 22.dp, height = 0.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MoreButton(
                                entries = if (enabled) menu else menu.map { entry -> if (entry is MenuAction) MenuAction(entry.labelText, entry.onClick, enabled = false) else entry },
                                expanded = menuOpen,
                                onExpandedChange = { menuOpen = it },
                                modifier = Modifier.requiredSize(ControlSize.Compact.height).hiddenUntilHover(hovered || menuOpen),
                                sound = null,
                                tooltip = null,
                                menuSound = null,
                            )
                        }
                    }
                }
                detail()
            }
            Spacer(Modifier.height(12.dp))
            if (action.enabled) {
                TextButton(
                    text = action.label,
                    icon = action.icon,
                    onClick = onAction,
                    modifier = Modifier.height(CardStripHeight),
                    style = if (equipped) ControlStyle.Primary else ControlStyle.Footer,
                    enabled = enabled,
                    fillWidth = true,
                    labelStyle = CardActionLabel,
                    glass = false,
                    sound = null,
                    shape = RectangleShape,
                )
            } else {
                CardStateLabel(action)
            }
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
    val scroll = rememberScrollState()
    ScrollContainer(scroll, modifier, scrollbar = false) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
            variants.forEach { variant ->
                TooltipBox(variant.name, Modifier, TooltipPlacement.AboveStart) {
                    VariantSwatch(variant, variant.artId, variant.id == selectedVariantId, 14.dp, null) { onPick(variant.id) }
                }
            }
        }
    }
}

@Composable
internal fun VariantSwatch(variant: CosmeticVariantUi, artId: Int, selected: Boolean, width: Dp, sound: UiSound?, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val swatchVersion = CosmeticSwatches.version
    val art = remember(artId, swatchVersion) { CosmeticSwatches.get(artId) }
    val shape = theme.shapes.small
    Box(
        modifier = Modifier.selectableSwatch(selected, onClick, variant.name, circle = false, sound = sound)
            .size(width = width, height = 22.dp)
            .clip(shape)
            .background(theme.chipBackground),
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
        Box(Modifier.fillMaxSize().border(1.dp, theme.borderColor, shape))
    }
}

@Composable
private fun CardStateLabel(action: CardAction) {
    val theme = OneConfigTheme.current
    val color = theme.textColor
    Column(Modifier.fillMaxWidth().background(theme.chipBackground)) {
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().height(CardStripHeight - 1.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(name = action.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Text(action.label, color = color, style = theme.typography.body.merge(CardActionLabel), maxLines = 1)
        }
    }
}

@Composable
private fun RenamePopup(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val theme = OneConfigTheme.current
    var value by remember { mutableStateOf(current) }
    val focusRequester = remember { FocusRequester() }
    val save: () -> Unit = {
        val name = value.trim()
        if (name.isNotEmpty()) {
            onDismiss()
            if (name != current) onSave(name)
        }
    }
    EscapeCloses(onDismiss)
    Popup(
        popupPositionProvider = rememberAnchoredPosition(Placement.BelowEnd, 4.dp),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier.width(240.dp).popupSurface().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Rename", color = theme.textColor, style = theme.typography.label)
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = current,
                size = ControlSize.Compact,
                maxLength = MAX_LIBRARY_NAME_LENGTH,
                focusRequester = focusRequester,
                onSubmit = save,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton("Cancel", "close", onDismiss, size = ControlSize.Compact, sound = null)
                TextButton("Save", "tick", save, style = ControlStyle.Primary, size = ControlSize.Compact, enabled = value.isNotBlank(), sound = null)
            }
        }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }
}

private val CardActionLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)

private val CardStripHeight = 36.dp

private val CardOutline = CardSelectionStyle(ringWidth = 1.dp, fillAlpha = 0f, ringTone = SelectionRingTone.Text)

private val OutlineOnHover = ControlIndication(hoverAlpha = 0f, hoverBorder = true)

private val NoHoverFill = ControlIndication(hoverAlpha = 0f)

@Composable
private fun AnimatedTag(modifier: Modifier) {
    Text(
        "ANIMATED",
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), OneConfigTheme.shape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = Color.White,
        style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
    )
}

@Composable
private fun CardBadgeTab(badge: CardBadge, modifier: Modifier) {
    val theme = OneConfigTheme.current
    val favourite = badge.label.isEmpty()
    val color = when (badge.style) {
        CardBadgeStyle.New -> Color.White
        CardBadgeStyle.Sale -> CARD_SALE_TEXT
        CardBadgeStyle.Accent -> theme.accentTextColor
    }
    Row(
        modifier = modifier.widthIn(min = if (favourite) 40.dp else 81.dp).height(21.dp)
            .then(if (favourite) Modifier.semantics { contentDescription = "Favourite" } else Modifier)
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
            Icon(name = badge.icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
        }
        if (!favourite) {
            Text(badge.label, color = color, style = theme.typography.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

private val CARD_SUCCESS = Color(0xFF239A60)

private val CARD_NEUTRAL_CHIP = Color(0xFFD9D9D9)

private val CARD_SALE_TEXT = Color(0xFF101114)

@Composable
internal fun WardrobeBanner(text: String, action: String, onAction: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().themedSurface(SurfaceKind.Card, OneConfigTheme.shape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(name = "alert-circle", contentDescription = null, modifier = Modifier.size(16.dp), tint = theme.warning)
        Text(text, modifier = Modifier.weight(1f), color = theme.textColor, style = OneConfigTheme.current.typography.small)
        TextButton(action, "keyboard", onAction, sound = null)
    }
}

@Composable
private fun LibraryIntro(note: String, action: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(note, modifier = Modifier.weight(1f), color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        if (action != null) {
            TextButton(action, "plus", onAction, sound = null)
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
    val shape = OneConfigTheme.shape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(57.dp)
            .clip(shape)
            .border(1.dp, OneConfigTheme.current.borderColor, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, Modifier.weight(1f), color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        TextButton(text = "Turn on online services", onClick = { open = true }, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
    }
}
