@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.BundleCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.FeaturedBundle
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.rememberRemoteImage
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.wardrobe.BACK_YAW_DEG
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.OutfitSwitcher
import org.polyfrost.polyplus.client.gui.wardrobe.PriceLabel
import org.polyfrost.polyplus.client.gui.wardrobe.createOutfitFromCurrent
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

private const val MOJANG_CAPE_SCALE = 0.68f
private const val OFFER_COUNT = 2
internal val RailWidth = 232.dp
internal val SlotRowHeight = 46.dp
private val SlotRowGap = 2.dp
private val RailHeaderHeight = 40.dp
private val CompactRowHeight = 33.dp

private val RAIL_SLOTS = listOf(
    CosmeticType.Wings,
    CosmeticType.Cape,
    CosmeticType.Hat,
    CosmeticType.Aura,
    CosmeticType.Pet,
    CosmeticType.Emote,
)

private val COMPACT_SLOTS = listOf(
    CosmeticType.Glasses,
    CosmeticType.Backpack,
    CosmeticType.Boots,
    CosmeticType.Shoulder,
    CosmeticType.Glove,
)

@Composable
internal fun StatusChip(text: String) {
    Box(Modifier.panel(16.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        SocialText(text, 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal class RailSlot(val type: CosmeticType, val item: CosmeticUiItem?, val cape: MojangCape?) {
    val name: String? get() = item?.name ?: cape?.name
}

internal fun railSlots(items: List<CosmeticUiItem>, mojangCapes: List<MojangCape>): List<RailSlot> = RAIL_SLOTS.map { type ->
    RailSlot(
        type,
        items.firstOrNull { it.type == type && it.equipped },
        if (type == CosmeticType.Cape) mojangCapes.firstOrNull { it.active } else null,
    )
}

internal fun compactSlots(items: List<CosmeticUiItem>): List<RailSlot> = COMPACT_SLOTS
    .filter { type -> items.any { it.type == type && it.owned } }
    .map { type -> RailSlot(type, items.firstOrNull { it.type == type && it.equipped }, null) }

@Composable
internal fun EquippedRail(
    slots: List<RailSlot>,
    compact: List<RailSlot>,
    title: String,
    activeType: CosmeticType?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (CosmeticType) -> Unit,
    modifier: Modifier,
) {
    var moreOpen by remember { mutableStateOf(false) }
    Column(
        modifier.panel().padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(SlotRowGap),
    ) {
        Row(Modifier.fillMaxWidth().height(RailHeaderHeight).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlineIconButton("left-arrow.svg", onClick = onBack)
            Label(title, 20.sp, Modifier.weight(1f), weight = FontWeight.Medium)
            SocialIconButton(LobbyIcons.REFRESH, tooltip = "Refresh", onClick = onRefresh)
        }
        for (slot in slots) {
            SlotRow(slot, active = slot.type == activeType, onClick = { onSelect(slot.type) })
        }
        if (compact.isNotEmpty()) {
            val showMore = moreOpen || compact.any { it.type == activeType }
            MoreRow(showMore, onClick = { moreOpen = !showMore })
            if (showMore) {
                for (slot in compact) {
                    CompactRow(slot, active = slot.type == activeType, onClick = { onSelect(slot.type) })
                }
            }
        }
    }
}

@Composable
private fun SlotRow(slot: RailSlot, active: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val name = slot.name
    Row(
        Modifier
            .fillMaxWidth()
            .height(SlotRowHeight)
            .listRow(active, hovered)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(ppShape(5.dp)).background(OneConfigTheme.current.componentBackground), contentAlignment = Alignment.Center) {
            val item = slot.item
            val cape = slot.cape
            when {
                item != null -> CosmeticArt(item, item.equippedVariantId, Modifier.fillMaxSize().padding(2.dp))
                cape != null -> MojangCapeArt(cape, Modifier.fillMaxSize())
                else -> SlotPlaceholder()
            }
        }
        if (name == null) {
            Label(slot.type.displayName, 14.sp, Modifier.weight(1f), color = rowText(active, hovered))
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(name, 14.sp, color = rowText(active, hovered))
                Label(slot.type.displayName, 12.sp, color = if (active) OneConfigTheme.current.accentTextColor else OneConfigTheme.current.textColorSecondary)
            }
        }
    }
}

@Composable
private fun MoreRow(open: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val color = rowText(selected = false, hovered = hovered)
    Row(
        Modifier
            .fillMaxWidth()
            .height(CompactRowHeight)
            .listRow(selected = false, hovered = hovered)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("More slots", 14.sp, Modifier.weight(1f), color = color)
        ThemedIcon(if (open) "/assets/oneconfig/ico/up.svg" else "/assets/oneconfig/ico/down.svg", color, 14.dp)
    }
}

@Composable
private fun CompactRow(slot: RailSlot, active: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .height(CompactRowHeight)
            .listRow(active, hovered)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(slot.type.displayName, 14.sp, Modifier.weight(1f), color = rowText(active, hovered))
        slot.name?.let { Label(it, 12.sp, Modifier.widthIn(max = 110.dp), color = if (active) OneConfigTheme.current.accentTextColor else OneConfigTheme.current.textColorSecondary) }
    }
}

@Composable
internal fun ItemArt(item: CosmeticUiItem, variantId: Int?, modifier: Modifier, alignment: Alignment = Alignment.Center) {
    val id = variantId ?: item.variants.firstOrNull()?.id
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
    fun within(ids: Collection<Int>) = unowned.filter { item -> item.variants.any { it.id in ids } }.take(OFFER_COUNT)
    return when {
        live != null -> BundleOffer("Bundle", live.info.name, live.info.description, live.info, within(live.cosmetics))
        else -> BundleOffer("Marketplace", "Not in your collection yet", null, null, unowned.take(OFFER_COUNT))
    }
}

@Composable
internal fun SeasonalBundlePanel(items: List<CosmeticUiItem>, onMarketplace: () -> Unit, modifier: Modifier) {
    val live by produceState<FeaturedBundle?>(null) { value = BundleCatalog.featured() }
    val offer = remember(items, live) { bundleOffer(items, live) }
    val secondary = OneConfigTheme.current.textColorSecondary
    Column(modifier.panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(offer.label, 12.sp, color = secondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(offer.name, 20.sp, Modifier.weight(1f), weight = FontWeight.Medium)
                offer.info?.let { PriceLabel(it.basePrice, it.finalPrice, it.discounted) }
            }
            offer.description?.let { SocialText(it, 12.sp, color = secondary) }
        }
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in offer.rows) {
                BundleRow(row, Modifier.fillMaxWidth().weight(1f))
            }
        }
        SocialButton("Open Marketplace", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onMarketplace)
    }
}

@Composable
private fun BundleRow(item: CosmeticUiItem, modifier: Modifier) {
    val theme = OneConfigTheme.current
    val shape = ppShape(12.dp)
    val thumbShape = ppShape(5.dp)
    Row(
        modifier.clip(shape).background(theme.modCardBackground).border(1.dp, theme.borderColor, shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(thumbShape)
                .background(theme.chipBackground)
                .border(1.dp, theme.borderColor, thumbShape),
        ) {
            CosmeticArt(item, item.variants.firstOrNull()?.id, Modifier.fillMaxSize().padding(6.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(item.name, 14.sp, weight = FontWeight.Medium)
            Label(item.collection, 12.sp, color = theme.textColorSecondary)
        }
    }
}

@Composable
internal fun CapesOnlyPanel(onTurnOn: () -> Unit, modifier: Modifier) {
    Column(modifier.panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("Minecraft capes only")
        SocialText("Cosmetics and outfits need online services. Your Minecraft capes still work.", 14.sp, color = MutedText)
        SocialButton("Turn on online services", modifier = Modifier.fillMaxWidth(), filled = true, onClick = onTurnOn)
    }
}

@Composable
internal fun OutfitPanel(onStatus: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.panel(16.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { OutfitSwitcher(onStatus) }
        SocialButton("Save this look", onClick = { createOutfitFromCurrent(onStatus) })
    }
}
