@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.marketplace

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticSwatches
import org.polyfrost.polyplus.client.cosmetics.SwatchArt
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.rowInk
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticVariantUi
import org.polyfrost.polyplus.client.gui.wardrobe.PriceLabel
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeCartPane
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeGrid
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeEntry
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSort
import org.polyfrost.polyplus.client.gui.wardrobe.uiVariants
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo

private const val NAV_REST_ALPHA = 0.8f
private const val CART_ICON = "assets/polyplus/ico/shopping-cart/0.svg"
private const val BAG_ICON = "assets/polyplus/ico/shopping-bag.svg"

private val SearchWidth = 250.dp
private val FilterColumn = 180.dp

internal class RailEntry(val label: String, val count: Int, val active: Boolean, val onClick: () -> Unit)

@Composable
internal fun CategoryRail(entries: List<RailEntry>, modifier: Modifier) {
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("BROWSE", 11.sp, Modifier.padding(start = 10.dp, bottom = 6.dp), weight = FontWeight.Medium)
        if (entries.isEmpty()) Label("Nothing to browse", 12.sp, Modifier.padding(start = 10.dp), color = MutedText)
        entries.forEach { RailRow(it) }
    }
}

@Composable
private fun RailRow(entry: RailEntry) {
    val (interaction, hovered) = rememberSocialHover()
    val theme = OneConfigTheme.current
    val ink = when {
        entry.active -> theme.accentTextColor
        hovered -> theme.textColor
        else -> theme.textColor.copy(alpha = NAV_REST_ALPHA)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(33.dp)
            .rowFrame(entry.active, hovered)
            .hoverable(interaction)
            .clickableWithSound(entry.onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Label(entry.label, 14.sp, Modifier.weight(1f), color = ink)
        Label(entry.count.toString(), 12.sp, color = if (entry.active) ink else MutedText)
    }
}

@Composable
internal fun MarketBody(state: MarketplaceState, grid: LazyGridState, entries: List<WardrobeEntry>, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.panel().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackButton(onBack)
            PageTitle("Marketplace", Modifier.weight(1f))
            TextField(
                value = state.query,
                onValueChange = { state.query = it },
                modifier = Modifier.width(SearchWidth).height(38.dp),
                placeholder = "Search cosmetics",
                leadingIcon = themedIcon(LobbyIcons.SEARCH),
                maxLength = 64,
                invalid = false,
            )
            FilterButton(state)
            TextButton("Cart (${state.cart.size})", themedIcon(CART_ICON), Modifier.height(38.dp), ButtonStyle.Primary, true) { state.showCart = !state.showCart }
        }
        if (entries.isEmpty()) {
            MarketEmpty(state, Modifier.weight(1f).fillMaxWidth())
            return@Column
        }
        WardrobeGrid(
            entries = entries,
            state = grid,
            ownedIds = state.ownedIds,
            cartKeys = state.cart.map { it.key }.toSet(),
            selection = state.selection,
            variantPicks = emptyMap(),
            storeVariantPicks = state.variantPicks,
            mojangBusy = false,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            coverFor = { null },
            onOwned = {},
            onClickOwned = {},
            onPreviewOwned = {},
            onSelectVariant = { _, _ -> },
            onShop = state::select,
            onPickStoreVariant = state::pickVariant,
            onAddToCart = state::addToCart,
            onBuyNow = state::buy,
            onMojang = {},
            onPreviewMojang = {},
            onBundle = state::selectBundle,
            onBuyBundle = state::buyBundle,
            onLibraryAction = {},
            onSelectOutfit = {},
            onSelectSkin = {},
            onStatus = { state.notice = it },
        )
    }
}

@Composable
private fun MarketEmpty(state: MarketplaceState, modifier: Modifier) {
    if (state.narrowed) {
        EmptyState("No cosmetics match that filter", modifier) {
            TextButton("Clear filters", themedIcon(LobbyIcons.CLOSE), Modifier.height(38.dp), ButtonStyle.Secondary, true, state::clearSearch)
        }
    } else {
        EmptyState("The store is empty", modifier, "There are no cosmetics for sale right now.")
    }
}

@Composable
private fun FilterButton(state: MarketplaceState) {
    var open by remember { mutableStateOf(false) }
    val count = state.activeFilters
    Box(Modifier) {
        TextButton(if (count > 0) "Filter ($count)" else "Filter", themedIcon(LobbyIcons.FILTER), Modifier.height(38.dp), ButtonStyle.Secondary, true) { open = !open }
        if (open) {
            EscapeCloses { open = false }
            FilterPopup(state) { open = false }
        }
    }
}

@Composable
private fun rememberDropPosition(): PopupPositionProvider {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    return remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                return IntOffset(x, anchorBounds.bottom + gap)
            }
        }
    }
}

@Composable
private fun FilterPopup(state: MarketplaceState, onDismiss: () -> Unit) {
    Popup(popupPositionProvider = rememberDropPosition(), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.popup().padding(10.dp).width(FilterColumn * 2 + 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(FilterColumn), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterGroup("Sort") {
                        WardrobeSort.entries.forEach { sort -> FilterRow(sort.label, state.sort == sort) { state.sort = sort } }
                    }
                    FilterGroup("Show") {
                        OwnershipFilter.entries.forEach { entry -> FilterRow(entry.label, state.ownership == entry) { state.ownership = entry } }
                    }
                }
                Column(Modifier.width(FilterColumn), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterGroup("Theme") {
                        FilterRow("All themes", state.theme == null) { state.theme = null }
                        state.themes().forEach { theme -> FilterRow(theme.titled(), state.theme == theme) { state.theme = theme } }
                    }
                }
            }
            if (state.activeFilters > 0) {
                TextButton("Clear filters", themedIcon(LobbyIcons.CLOSE), Modifier.fillMaxWidth().height(38.dp), ButtonStyle.Secondary, true, state::clearFilters)
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String, rows: @Composable () -> Unit) {
    Column {
        Label(title.uppercase(), 11.sp, Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp), weight = FontWeight.Medium)
        rows()
    }
}

@Composable
private fun FilterRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .rowFrame(selected, hovered)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label, 12.sp, Modifier.weight(1f), color = rowInk(selected))
    }
}

@Composable
internal fun PreviewColumn(state: MarketplaceState, onWardrobe: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        if (state.showCart) {
            EscapeCloses { state.showCart = false }
            Box(Modifier.fillMaxSize().panel(16.dp)) {
                WardrobeCartPane(
                    cart = state.cart,
                    status = state.notice,
                    onClose = { state.showCart = false },
                    onRemove = { key -> state.cart.removeAll { it.key == key } },
                    onCheckout = state::checkout,
                    framed = false,
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TryOnPreview(state, Modifier.fillMaxWidth().weight(1f))
                DetailCard(state, onWardrobe)
            }
        }
    }
}

@Composable
private fun DetailCard(state: MarketplaceState, onWardrobe: () -> Unit) {
    val item = state.selectedItem
    val bundle = state.selectedBundle
    Column(Modifier.fillMaxWidth().panel(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            item != null -> ItemDetail(state, item, onWardrobe)
            bundle != null -> BundleDetail(state, bundle)
            else -> SocialText("Select a cosmetic to try it on your look.", 12.sp, color = MutedText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        state.notice?.let { SocialText(it, 12.sp, color = MutedText, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun DetailHeading(name: String, detail: String, price: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(name, 16.sp, weight = FontWeight.Medium)
            Label(detail, 12.sp, color = MutedText)
        }
        price()
    }
}

@Composable
private fun ItemDetail(state: MarketplaceState, info: CosmeticStoreInfo, onWardrobe: () -> Unit) {
    val variant = state.variant(info)
    val variants = info.uiVariants()
    val owned = variant.id in state.ownedIds
    val inCart = state.inCart(variant.id)
    DetailHeading(info.name, info.type.displayName) {
        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(info) }
    }
    if (variants.size > 1) VariantRow(variants, variant) { state.pickVariant(info.id, it) }
    info.description?.takeIf { it.isNotBlank() }?.let { SocialText(it, 12.sp, color = MutedText, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (owned) {
            TextButton("Open in Wardrobe", themedIcon("profiles"), Modifier.height(38.dp), ButtonStyle.Primary, true, onWardrobe)
        } else {
            TextButton(
                if (inCart) "In cart" else "Add to cart",
                themedIcon(if (inCart) LobbyIcons.TICK else CART_ICON),
                Modifier.height(38.dp),
                ButtonStyle.Secondary,
                !inCart,
            ) { state.addToCart(info, variant) }
            TextButton("Buy now", themedIcon(BAG_ICON), Modifier.height(38.dp), ButtonStyle.Primary, true) { state.buy(info, variant) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantRow(variants: List<CosmeticVariantUi>, selected: CosmeticVariantUi, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            variants.forEach { VariantSwatch(it, it.id == selected.id) { onPick(it.id) } }
        }
        Label(selected.name, 12.sp, color = MutedText)
    }
}

@Composable
private fun VariantSwatch(variant: CosmeticVariantUi, selected: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val theme = OneConfigTheme.current
    val shape = ppShape(6.dp)
    val version = CosmeticSwatches.version
    val art = remember(variant.id, version) { CosmeticSwatches.get(variant.id) }
    Box(
        Modifier
            .size(width = 16.dp, height = 22.dp)
            .clip(shape)
            .background(theme.chipBackground)
            .hoverable(interaction)
            .clickableWithSound(onClick),
    ) {
        when (art) {
            is SwatchArt.Picture -> Image(art.bitmap, variant.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
            is SwatchArt.Stripes -> Column(Modifier.fillMaxSize()) {
                art.colors.forEach { Box(Modifier.fillMaxWidth().weight(1f).background(it)) }
            }
            null -> Unit
        }
        Box(
            Modifier.fillMaxSize().border(
                1.dp,
                when {
                    selected -> OneConfigTheme.accent
                    hovered -> theme.textColorSecondary
                    else -> theme.borderColor
                },
                shape,
            ),
        )
    }
}

@Composable
private fun BundleDetail(state: MarketplaceState, bundle: MarketBundle) {
    val owned = bundle.pieces.count { it.variantId in state.ownedIds }
    DetailHeading(bundle.info.name, bundle.label) {
        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(bundle.info.basePrice, bundle.info.finalPrice, bundle.info.discounted) }
    }
    bundle.info.description?.takeIf { it.isNotBlank() }?.let { SocialText(it, 12.sp, color = MutedText, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    SocialText(bundle.pieces.joinToString { it.info.name }, 12.sp, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton("Buy now", themedIcon(BAG_ICON), Modifier.height(38.dp), ButtonStyle.Primary, true) { state.buyBundle(bundle.info) }
        if (owned > 0) Label("You own $owned of ${bundle.pieces.size}", 12.sp, color = MutedText)
    }
}
