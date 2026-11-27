@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.cosmetics.sets.DiscoveredBonuses
import org.polyfrost.polyplus.client.gui.lobbyshell.CustomizeKit
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelSection
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

private const val LIBRARY_COLUMNS = 2
private const val KEYBINDS_PAGE = "oneconfig:keybinds"
private val WheelPickerHeight = 300.dp

internal enum class WardrobeView(val label: String, val title: String, val icon: String) {
    Outfits("Outfits", "Your outfits", "user-01"),
    Skins("Skins", "Your skins", "image-01"),
    Wheel("Emote wheel", "Emote wheel", "play-circle"),
    History("History", "Purchase history", "clock"),
}

internal class DrawerActions(
    val onSelect: (WardrobeSelection) -> Unit,
    val onSelectSlot: (Int?) -> Unit,
    val onStatus: (String) -> Unit,
)

@Composable
internal fun LibraryPage(
    view: WardrobeView,
    selection: WardrobeSelection?,
    wheelSlot: Int?,
    wheelPicker: PickerModel?,
    store: WardrobeStoreData,
    ownedIds: Set<Int>,
    refreshKey: Int,
    historyKey: Int,
    actions: DrawerActions,
) {
    when (view) {
        WardrobeView.Outfits -> LibraryFrame { LibraryGrid(WardrobeSection.Outfits, selection, store, actions) }
        WardrobeView.Skins -> LibraryFrame { LibraryGrid(WardrobeSection.Skins, selection, store, actions) }
        WardrobeView.Wheel -> WheelPane(wheelSlot, wheelPicker, store, refreshKey, actions)
        WardrobeView.History -> LibraryFrame { HistoryScreen(ownedIds, historyKey, framed = false) }
    }
}

@Composable
private fun LibraryFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = CustomizeKit.Inset, end = CustomizeKit.Inset, top = 4.dp, bottom = CustomizeKit.Inset)) { content() }
}

@Composable
private fun LibraryGrid(section: WardrobeSection, selection: WardrobeSelection?, store: WardrobeStoreData, actions: DrawerActions) {
    var adding by remember(section) { mutableStateOf(false) }
    val discovered by DiscoveredBonuses.bonuses.collectAsState()
    LaunchedEffect(section) { if (section == WardrobeSection.Outfits) DiscoveredBonuses.ensureLoaded() }
    if (adding) {
        EscapeCloses { adding = false }
        AddSkinPanel(onClose = { adding = false }, onStatus = actions.onStatus, modifier = Modifier.fillMaxWidth(), framed = false)
        return
    }
    WardrobeGrid(
        entries = buildWardrobeEntries(
            section,
            emptyList(),
            emptySet(),
            store,
            MojangCapeState.Idle,
            "",
            WardrobeSort.Popular,
            true,
            discovered = discovered,
        ),
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
private fun WheelPane(wheelSlot: Int?, picker: PickerModel?, store: WardrobeStoreData, refreshKey: Int, actions: DrawerActions) {
    val warning = remember(refreshKey) { emoteKeybindWarning() }
    val covers = remember(store.byType, store.popular, store.newest) { store.coversByVariant() }
    PanelPageColumn {
        warning?.let { WardrobeBanner(it, "View keybinds") { OneConfigUI.open(KEYBINDS_PAGE) } }
        EmoteWheelEditor(wheelSlot, { covers[it] }, actions.onSelectSlot, Modifier.fillMaxWidth(), framed = false)
        if (picker != null) {
            PanelSection(if (wheelSlot == null) "Add an emote" else "Pick an emote for this slot") {
                SlotPicker(picker, Modifier.fillMaxWidth().height(WheelPickerHeight))
            }
        }
    }
}
