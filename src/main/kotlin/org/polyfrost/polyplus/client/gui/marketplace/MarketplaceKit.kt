@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.marketplace

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuHeading
import org.polyfrost.oneconfig.api.ui.v1.components.MenuRadioGroup
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.UiSound
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticVariantUi
import org.polyfrost.polyplus.client.gui.wardrobe.PriceLabel
import org.polyfrost.polyplus.client.gui.wardrobe.StoreColours
import org.polyfrost.polyplus.client.gui.wardrobe.coloursSaving
import org.polyfrost.polyplus.client.gui.wardrobe.VariantSwatch
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeCartPane
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeGrid
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeEntry
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSort
import org.polyfrost.polyplus.client.gui.wardrobe.TryInfo
import org.polyfrost.polyplus.client.gui.wardrobe.TryOnLines
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSelection
import org.polyfrost.polyplus.client.gui.wardrobe.rememberTryInfo
import org.polyfrost.polyplus.client.gui.wardrobe.rememberTryOn
import org.polyfrost.polyplus.client.gui.wardrobe.uiVariants
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private const val CART_ICON = "assets/polyplus/ico/shopping-cart/0.svg"
private const val BAG_ICON = "assets/polyplus/ico/shopping-bag.svg"

private val SearchWidth = 250.dp
private const val MAX_QUERY_LENGTH = 64

internal class RailEntry(val label: String, val count: Int, val active: Boolean, val onClick: () -> Unit)

@Composable
internal fun CategoryRail(entries: List<RailEntry>, modifier: Modifier) {
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("BROWSE", Modifier.padding(start = 10.dp, bottom = 6.dp), style = OneConfigTheme.current.typography.caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        if (entries.isEmpty()) Text("Nothing to browse", Modifier.padding(start = 10.dp), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        entries.forEach { RailRow(it) }
    }
}

@Composable
private fun RailRow(entry: RailEntry) {
    val theme = OneConfigTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(33.dp)
            .selectableRow(entry.active, entry.onClick, "${entry.label}, ${entry.count}", theme.sideBarNavigationEntryShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(entry.label, Modifier.weight(1f), color = SelectionDefaults.itemContentColor(entry.active), style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(entry.count.toString(), color = SelectionDefaults.itemSecondaryColor(entry.active, rest = theme.mutedText), style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
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
            SearchField(
                query = state.query,
                onQueryChange = { state.query = it.take(MAX_QUERY_LENGTH) },
                modifier = Modifier.width(SearchWidth),
                placeholder = "Search cosmetics",
                clearable = false,
                size = ControlSize.Large,
            )
            FilterButton(state)
            TextButton(text = "Cart (${state.cart.size})", icon = CART_ICON, onClick = { state.showCart = !state.showCart }, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
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
        EmptyState(
            "No cosmetics match that filter",
            modifier,
            action = {
                TextButton(text = "Clear filters", icon = LobbyIcons.CLOSE, onClick = state::clearSearch, modifier = Modifier, style = ControlStyle.Secondary, size = ControlSize.Large)
            },
        )
    } else {
        EmptyState("The store is empty", modifier, text = "There are no cosmetics for sale right now.")
    }
}

@Composable
private fun FilterButton(state: MarketplaceState) {
    var open by remember { mutableStateOf(false) }
    val count = state.activeFilters
    val themes = state.themes()
    val entries = buildList<MenuEntry> {
        add(MenuHeading("Sort"))
        add(MenuRadioGroup(WardrobeSort.entries, state.sort, { it.label }, { state.sort = it }, closeOnSelect = false))
        add(MenuSeparator)
        add(MenuHeading("Show"))
        add(MenuRadioGroup(OwnershipFilter.entries, state.ownership, { it.label }, { state.ownership = it }, closeOnSelect = false))
        add(MenuSeparator)
        add(MenuHeading("Theme"))
        add(MenuRadioGroup(listOf<String?>(null) + themes, state.theme, { it?.titled() ?: "All themes" }, { state.theme = it }, closeOnSelect = false))
        if (count > 0) {
            add(MenuSeparator)
            add(MenuAction("Clear filters", state::clearFilters, icon = LobbyIcons.CLOSE, dismissOnClick = false))
        }
    }
    Box(Modifier) {
        TextButton(text = if (count > 0) "Filter ($count)" else "Filter", icon = LobbyIcons.FILTER, onClick = { open = !open }, modifier = Modifier, style = ControlStyle.Secondary, size = ControlSize.Large)
        DropdownMenu(open, { open = false }, entries, placement = Placement.BelowEnd)
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
            val variant = state.selectedItem?.let(state::variant)
            val bundle = state.selectedBundle
            val pieces = variant?.pieces ?: bundle?.let { state.fitting.values.map { piece -> piece.variantId } }
            val tryInfo = rememberTryInfo(pieces)
            val tryOn = rememberTryOn(variant?.id ?: bundle?.let { WardrobeSelection.Bundle(it.info.id) })
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TryOnPreview(state, tryInfo, tryOn, Modifier.fillMaxWidth().weight(1f))
                DetailCard(state, tryInfo, onWardrobe)
            }
        }
    }
}

@Composable
private fun DetailCard(state: MarketplaceState, tryInfo: TryInfo, onWardrobe: () -> Unit) {
    val item = state.selectedItem
    val bundle = state.selectedBundle
    Column(Modifier.fillMaxWidth().panel(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            item != null -> ItemDetail(state, item, tryInfo, onWardrobe)
            bundle != null -> BundleDetail(state, bundle, tryInfo)
            else -> Text("Select a cosmetic to try it on your look.", modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
        }
        state.notice?.let { Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun DetailHeading(name: String, detail: String, price: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        price()
    }
}

@Composable
private fun ItemDetail(state: MarketplaceState, info: CosmeticStoreInfo, tryInfo: TryInfo, onWardrobe: () -> Unit) {
    val variant = state.variant(info)
    val variants = info.uiVariants()
    val owned = variant.id in state.ownedIds
    val inCart = state.inCart(variant.id)
    DetailHeading(info.name, info.type.displayName) {
        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(info) }
    }
    info.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    TryOnLines(tryInfo)
    if (variants.size > 1) VariantRow(variants, variant) { state.pickVariant(info.id, it) }
    StoreColours(variant, owned)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (owned) {
            TextButton(text = "Open in Wardrobe", icon = "profiles", onClick = onWardrobe, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
        } else {
            TextButton(
                text = if (inCart) "In cart" else "Add to cart",
                icon = if (inCart) LobbyIcons.TICK else CART_ICON,
                onClick = { state.addToCart(info, variant) },
                modifier = Modifier,
                style = ControlStyle.Secondary,
                enabled = !inCart,
                size = ControlSize.Large,
            )
            TextButton(text = "Buy now", icon = BAG_ICON, onClick = { state.buy(info, variant) }, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantRow(variants: List<CosmeticVariantUi>, selected: CosmeticVariantUi, onPick: (Int) -> Unit) {
    val saving = coloursSaving(variants)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            variants.forEach { VariantSwatch(it, it.id, it.id == selected.id, 16.dp, UiSound.Click, enabled = !saving) { onPick(it.id) } }
        }
        Text(if (saving) "Saving your colors..." else selected.name, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun BundleDetail(state: MarketplaceState, bundle: MarketBundle, tryInfo: TryInfo) {
    val owned = bundle.pieces.count { it.variantId in state.ownedIds }
    DetailHeading(bundle.info.name, bundle.label) {
        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(bundle.info.basePrice, bundle.info.finalPrice, bundle.info.discounted) }
    }
    bundle.info.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    Text(bundle.pieces.joinToString { it.info.name }, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    TryOnLines(tryInfo)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(text = "Buy now", icon = BAG_ICON, onClick = { state.buyBundle(bundle.info) }, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
        if (owned > 0) Text("You own $owned of ${bundle.pieces.size}", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}
