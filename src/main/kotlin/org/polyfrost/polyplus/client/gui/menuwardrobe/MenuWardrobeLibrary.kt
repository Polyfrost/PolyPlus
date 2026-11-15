@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.wardrobe.AddSkinPanel
import org.polyfrost.polyplus.client.gui.wardrobe.EmoteWheelEditor
import org.polyfrost.polyplus.client.gui.wardrobe.HistoryScreen
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeBanner
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeGrid
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSection
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSelection
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSort
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeStoreData
import org.polyfrost.polyplus.client.gui.wardrobe.buildWardrobeEntries
import org.polyfrost.polyplus.client.gui.wardrobe.emoteKeybindWarning

private const val LIBRARY_COLUMNS = 3
private const val KEYBINDS_PAGE = "oneconfig:keybinds"
private val LibraryWidth = 470.dp
private val WheelWidth = 340.dp

internal enum class WardrobeView(val label: String, val title: String, val width: Dp) {
    Outfits("Outfits", "Your outfits", LibraryWidth),
    Skins("Skins", "Your skins", LibraryWidth),
    Wheel("Emote wheel", "Emote wheel", WheelWidth),
    History("History", "Purchase history", LibraryWidth),
}

@Composable
internal fun ViewTabs(active: WardrobeView?, onSelect: (WardrobeView) -> Unit, modifier: Modifier) {
    Box(modifier.panel(16.dp).padding(6.dp), contentAlignment = Alignment.Center) {
        TabRow(
            tabs = WardrobeView.entries.map { TabItem<WardrobeView?>(it, it.label) },
            selected = active,
            onSelect = { view -> if (view != null) onSelect(view) },
            style = TabStyle.Segmented,
            track = false,
        )
    }
}

internal class DrawerActions(
    val onSelect: (WardrobeSelection) -> Unit,
    val onSelectSlot: (Int?) -> Unit,
    val onStatus: (String) -> Unit,
    val onClose: () -> Unit,
)

@Composable
internal fun LibraryDrawer(
    view: WardrobeView,
    selection: WardrobeSelection?,
    wheelSlot: Int?,
    store: WardrobeStoreData,
    ownedIds: Set<Int>,
    refreshKey: Int,
    historyKey: Int,
    actions: DrawerActions,
    modifier: Modifier,
) {
    Column(modifier.panel(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelTitle(view.title, Modifier.weight(1f))
            OutlineIconButton("close", onClick = actions.onClose)
        }
        when (view) {
            WardrobeView.Outfits -> LibraryGrid(WardrobeSection.Outfits, selection, store, actions)
            WardrobeView.Skins -> LibraryGrid(WardrobeSection.Skins, selection, store, actions)
            WardrobeView.Wheel -> WheelPane(wheelSlot, store, refreshKey, actions)
            WardrobeView.History -> HistoryScreen(ownedIds, historyKey, framed = false)
        }
    }
}

@Composable
private fun LibraryGrid(section: WardrobeSection, selection: WardrobeSelection?, store: WardrobeStoreData, actions: DrawerActions) {
    var adding by remember(section) { mutableStateOf(false) }
    if (adding) {
        EscapeCloses { adding = false }
        AddSkinPanel(onClose = { adding = false }, onStatus = actions.onStatus, modifier = Modifier.fillMaxWidth(), framed = false)
        return
    }
    WardrobeGrid(
        entries = buildWardrobeEntries(section, emptyList(), emptySet(), store, MojangCapeState.Idle, "", WardrobeSort.Popular, true),
        state = rememberLazyGridState(),
        ownedIds = emptySet(),
        cartKeys = emptySet(),
        selection = selection,
        variantPicks = emptyMap(),
        storeVariantPicks = emptyMap(),
        mojangBusy = false,
        modifier = Modifier.fillMaxSize(),
        coverFor = { null },
        onOwned = {},
        onClickOwned = {},
        onPreviewOwned = {},
        onSelectVariant = { _, _ -> },
        onShop = {},
        onPickStoreVariant = { _, _ -> },
        onAddToCart = { _, _ -> },
        onBuyNow = { _, _ -> },
        onMojang = {},
        onPreviewMojang = {},
        onBundle = {},
        onBuyBundle = {},
        onLibraryAction = { adding = true },
        onSelectOutfit = { actions.onSelect(WardrobeSelection.Outfit(it.id)) },
        onSelectSkin = { actions.onSelect(WardrobeSelection.Skin(it.id)) },
        onStatus = actions.onStatus,
        columns = LIBRARY_COLUMNS,
    )
}

@Composable
private fun WheelPane(wheelSlot: Int?, store: WardrobeStoreData, refreshKey: Int, actions: DrawerActions) {
    val warning = remember(refreshKey) { emoteKeybindWarning() }
    val covers = remember(store.byType, store.popular, store.newest) { store.coversByVariant() }
    val scroll = rememberScrollState()
    ScrollContainer(scroll, Modifier.fillMaxSize(), scrollbar = false) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            warning?.let { WardrobeBanner(it, "View keybinds") { OneConfigUI.open(KEYBINDS_PAGE) } }
            EmoteWheelEditor(wheelSlot, { covers[it] }, actions.onSelectSlot, Modifier.fillMaxWidth(), framed = false)
        }
    }
}
