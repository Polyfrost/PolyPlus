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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.gui.SocialText
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
    storeVariantPicks: Map<Int, Int>,
    mojangBusy: Boolean,
    modifier: Modifier,
    coverFor: (CosmeticUiItem) -> Int?,
    onOwned: (CosmeticUiItem) -> Unit,
    onShop: (CosmeticStoreInfo) -> Unit,
    onAddToCart: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onMojang: (MojangCape) -> Unit,
    onBundle: (BundleInfo) -> Unit,
    onOpenStore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
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
                is WardrobeEntry.Group -> CapeSectionHeader(
                    title = entry.title,
                    note = entry.note,
                    action = entry.action,
                    onAction = onOpenStore,
                )
                is WardrobeEntry.Note -> CapeSectionNote(entry.text)
                WardrobeEntry.ForceCapes -> ForceCapesControl()
                is WardrobeEntry.Owned -> {
                    val item = entry.item
                    WardrobeCard(
                        name = item.name,
                        detail = { CardDetailText(item.collection) },
                        action = equipAction(item.equipped),
                        onAction = { onOwned(item) },
                        equipped = item.equipped,
                        selected = selection == WardrobeSelection.Owned(item.groupId, item.type),
                        onClick = { onOwned(item) },
                    ) { modifier ->
                        val (source, loadTick) = rememberCosmeticPreviewSource(item)
                        CosmeticThumbnail(
                            source,
                            item.type,
                            "card-${item.groupId}-$loadTick",
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
                    val variantCount = info.uiVariants().size
                    WardrobeCard(
                        name = info.name,
                        detail = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PriceLabel(info)
                                if (variantCount > 1) {
                                    SocialText("$variantCount variants", color = LocalTheme.current.textColorSecondary, fontSize = 11.sp)
                                }
                            }
                        },
                        action = when {
                            owned -> CardAction("Owned", "tick", enabled = false)
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
                        onClick = { onMojang(cape) },
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
                    ) { modifier ->
                        CoverThumbnail(bundle.assetId, modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun WardrobeDivider(label: String) {
    val theme = LocalTheme.current
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

private enum class CardBadgeStyle { New, Sale, Accent }

private data class CardBadge(val label: String, val style: CardBadgeStyle)

private fun equipAction(equipped: Boolean): CardAction =
    if (equipped) CardAction("Equipped", "tick") else CardAction("Equip", "profiles")

private val CARD_SUCCESS = Color(0xFF239A60)
private val CARD_NEUTRAL_CHIP = Color(0xFFD9D9D9)
private val CARD_FRAME = Color(0xFF232D32)
private val CARD_HAIRLINE = Color(0x1AFFFFFF)
private val CARD_STRIP = Color(0xB3232D32)
private val CARD_HIGHLIGHT = Color(0x0DFFFFFF)

@Composable
private fun CardDetailText(text: String) {
    CardLabel(text, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
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
    thumbnail: @Composable (Modifier) -> Unit,
) {
    val theme = LocalTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val outline = when {
        equipped -> Accent
        selected || (hovered && enabled) -> theme.textColorSecondary
        else -> theme.borderColor
    }
    Box(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
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
                        .background(CARD_FRAME)
                        .border(1.dp, CARD_HAIRLINE, ppShape(5.dp)),
                ) {
                    thumbnail(Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CardLabel(name, color = theme.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
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
private fun CardActionStrip(action: CardAction, active: Boolean, enabled: Boolean, onAction: () -> Unit) {
    val theme = LocalTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (active) theme.accentTextColor else theme.textColor
    Box(Modifier.fillMaxWidth().height(36.dp)) {
        Row(
            modifier = Modifier.fillMaxSize()
                .background(if (active) Accent else CARD_STRIP)
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
                    .background(if (hovered && enabled) theme.textColorSecondary else CARD_HAIRLINE),
            )
        }
    }
}

@Composable
private fun CardBadgeTab(badge: CardBadge, modifier: Modifier) {
    val theme = LocalTheme.current
    Box(
        modifier = modifier.widthIn(min = 81.dp).height(21.dp)
            .clip(ppShapeOf(bottomStart = 7.dp, bottomEnd = 7.dp))
            .background(
                when (badge.style) {
                    CardBadgeStyle.New -> CARD_SUCCESS
                    CardBadgeStyle.Sale -> CARD_NEUTRAL_CHIP
                    CardBadgeStyle.Accent -> Accent
                },
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        SocialText(
            badge.label,
            color = if (badge.style == CardBadgeStyle.New) theme.textColor else theme.accentTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
