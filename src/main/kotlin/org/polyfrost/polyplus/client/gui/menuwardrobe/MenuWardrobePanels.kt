@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.BundleCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.FeaturedBundle
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.CustomizeKit
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelSection
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.rememberRemoteImage
import org.polyfrost.polyplus.client.gui.wardrobe.BACK_YAW_DEG
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.OutfitSwitcher
import org.polyfrost.polyplus.client.gui.wardrobe.PriceLabel
import org.polyfrost.polyplus.client.gui.wardrobe.createOutfitFromCurrent
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private const val MOJANG_CAPE_SCALE = 0.68f
private const val OFFER_COUNT = 2
internal val RailWidth = 232.dp
private val SlotRowHeight = 40.dp
private val SlotArtSize = 30.dp
private val RailHeaderHeight = 48.dp
private val BundleRowHeight = 76.dp

private val MAIN_SLOTS = listOf(
    CosmeticType.Hat,
    CosmeticType.Cape,
    CosmeticType.Wings,
    CosmeticType.Aura,
    CosmeticType.Pet,
    CosmeticType.Emote,
)

private val ACCESSORY_SLOTS = listOf(
    CosmeticType.Glasses,
    CosmeticType.Backpack,
    CosmeticType.Shoulder,
    CosmeticType.Glove,
    CosmeticType.Boots,
)

internal class RailSlot(val type: CosmeticType, val item: CosmeticUiItem?, val cape: MojangCape?) {
    val name: String? get() = item?.name ?: cape?.name
}

internal class RailGroup(val title: String, val slots: List<RailSlot>)

internal fun railGroups(items: List<CosmeticUiItem>, mojangCapes: List<MojangCape>, online: Boolean): List<RailGroup> {
    fun slot(type: CosmeticType) = RailSlot(
        type,
        items.firstOrNull { it.type == type && it.equipped },
        if (type == CosmeticType.Cape) mojangCapes.firstOrNull { it.active } else null,
    )
    fun allowed(type: CosmeticType) = online || type == CosmeticType.Cape
    return listOf(
        RailGroup("Main", MAIN_SLOTS.filter(::allowed).map(::slot)),
        RailGroup("Accessories", ACCESSORY_SLOTS.filter(::allowed).map(::slot)),
    ).filter { it.slots.isNotEmpty() }
}

@Composable
internal fun EquippedRail(
    groups: List<RailGroup>,
    title: String,
    activeType: CosmeticType?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (CosmeticType) -> Unit,
    modifier: Modifier,
) {
    val slots = remember(groups) { groups.flatMap { it.slots } }
    val requesters = remember(slots.size) { List(slots.size) { FocusRequester() } }
    val scope = rememberCoroutineScope()
    fun step(delta: Int): Boolean {
        if (slots.isEmpty()) return false
        val current = slots.indexOfFirst { it.type == activeType }
        val next = when {
            current < 0 -> if (delta > 0) 0 else slots.lastIndex
            else -> (current + delta).coerceIn(0, slots.lastIndex)
        }
        if (next != current) onSelect(slots[next].type)
        scope.launch {
            withFrameNanos {}
            withFrameNanos {}
            runCatching { requesters[next].requestFocus() }
        }
        return true
    }
    Column(modifier.panel()) {
        Row(
            Modifier.fillMaxWidth().height(RailHeaderHeight).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlineIconButton("left-arrow", onClick = onBack)
            Text(title, Modifier.weight(1f), style = OneConfigTheme.current.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            IconButton(icon = LobbyIcons.REFRESH, contentDescription = "Refresh", onClick = onRefresh)
        }
        PanelPageColumn(
            Modifier.weight(1f).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> step(1)
                    Key.DirectionUp -> step(-1)
                    else -> false
                }
            },
        ) {
            var index = 0
            groups.forEachIndexed { groupIndex, group ->
                PanelSection(group.title, divider = groupIndex > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (slot in group.slots) {
                            val requester = requesters[index++]
                            SlotRow(slot, slot.type == activeType, requester) { onSelect(slot.type) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(slot: RailSlot, active: Boolean, requester: FocusRequester, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val name = slot.name
    Row(
        Modifier
            .fillMaxWidth()
            .height(SlotRowHeight)
            .focusRequester(requester)
            .selectableRow(active, onClick, "${slot.type.displayName}, ${name ?: "Empty"}", OneConfigTheme.shape(CustomizeKit.RowRadius))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(SlotArtSize).clip(OneConfigTheme.shape(5.dp)).background(theme.componentBackground), contentAlignment = Alignment.Center) {
            val item = slot.item
            val cape = slot.cape
            when {
                item != null -> CosmeticArt(item, item.equippedVariantId, Modifier.fillMaxSize().padding(2.dp))
                cape != null -> MojangCapeArt(cape, Modifier.fillMaxSize())
                else -> SlotPlaceholder()
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(slot.type.displayName, color = SelectionDefaults.itemContentColor(active), style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(name ?: "Empty", color = SelectionDefaults.itemSecondaryColor(active), style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

@Composable
internal fun ItemArt(item: CosmeticUiItem, variantId: Int?, modifier: Modifier, alignment: Alignment = Alignment.Center) {
    val id = item.artId(variantId) ?: item.variants.firstOrNull()?.id
    val cover = rememberRemoteImage(id?.let { CosmeticCatalog.getDefinition(it)?.coverUrl })
    Box(modifier, contentAlignment = Alignment.Center) {
        if (cover != null) {
            Image(
                cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alignment = alignment,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
            )
        } else {
            ThemedIcon("assets/polyplus/ico/stars.svg", OneConfigTheme.current.textColorSecondary, 18.dp)
        }
    }
}

@Composable
internal fun MojangCapeArt(cape: MojangCape, modifier: Modifier) {
    val texture = remember(cape.textureHash, MojangCapeService.textureVersion) { MojangCapeService.texture(cape) }
    val source = remember(texture) { texture?.let { PlayerPreviewSource.Override(CosmeticEquipment(), mojangCape = it, skin = bareSkin()) } }
    if (source == null) {
        Box(modifier)
        return
    }
    PlayerPreview(
        modifier,
        source = source,
        autoSpin = false,
        allowDrag = false,
        modelScale = MOJANG_CAPE_SCALE,
        initialYaw = BACK_YAW_DEG,
        previewKey = cape.textureHash,
        live = true,
    )
}

private class BundleOffer(val label: String, val name: String, val description: String?, val info: BundleInfo?, val rows: List<CosmeticUiItem>)

private fun bundleOffer(items: List<CosmeticUiItem>, live: FeaturedBundle?): BundleOffer {
    val unowned = items.filter { !it.owned && it.type != CosmeticType.Emote }
    fun within(ids: Collection<Int>) = items.filter { item -> !item.owned && item.variants.any { it.id in ids } }.take(OFFER_COUNT)
    return when {
        live != null -> BundleOffer("Bundle", live.info.name, live.info.description, live.info, within(live.cosmetics + live.emotes))
        else -> BundleOffer("Marketplace", "Not in your collection yet", null, null, unowned.take(OFFER_COUNT))
    }
}

@Composable
internal fun SeasonalBundlePanel(items: List<CosmeticUiItem>, onMarketplace: () -> Unit, modifier: Modifier) {
    val live by produceState<FeaturedBundle?>(null) { value = BundleCatalog.featured() }
    val offer = remember(items, live) { bundleOffer(items, live) }
    val secondary = OneConfigTheme.current.textColorSecondary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(offer.label, color = secondary, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(offer.name, Modifier.weight(1f), style = OneConfigTheme.current.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                offer.info?.let { PriceLabel(it.basePrice, it.finalPrice, it.discounted) }
            }
            offer.description?.let { Text(it, color = secondary, style = OneConfigTheme.current.typography.small) }
        }
        for (row in offer.rows) {
            BundleRow(row, Modifier.fillMaxWidth().height(BundleRowHeight))
        }
        TextButton(text = "Open Marketplace", onClick = onMarketplace, modifier = Modifier.fillMaxWidth(), style = ControlStyle.Primary, size = ControlSize.Large)
    }
}

@Composable
private fun BundleRow(item: CosmeticUiItem, modifier: Modifier) {
    val theme = OneConfigTheme.current
    Row(
        modifier.themedSurface(SurfaceKind.Card, OneConfigTheme.shape(12.dp), glass = false).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(5.dp), glass = false),
        ) {
            CosmeticArt(item, item.variants.firstOrNull()?.id, Modifier.fillMaxSize().padding(6.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(item.collection, color = theme.textColorSecondary, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

@Composable
internal fun CapesOnlyPanel(onTurnOn: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("Minecraft capes only")
        Text("Cosmetics and outfits need online services. Your Minecraft capes still work.", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body)
        TextButton(text = "Turn on online services", onClick = onTurnOn, modifier = Modifier.fillMaxWidth(), style = ControlStyle.Primary, size = ControlSize.Large)
    }
}

@Composable
internal fun OutfitPanel(onStatus: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.card(14.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { OutfitSwitcher(onStatus) }
        TextButton(text = "Save this look", onClick = { createOutfitFromCurrent(onStatus) }, modifier = Modifier, size = ControlSize.Large)
    }
}
