@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

@Composable
internal fun PriceLabel(basePrice: Float?, finalPrice: Float?, discounted: Boolean) {
    when {
        finalPrice == null -> SocialText("—", color = OneConfigTheme.current.textColorSecondary, fontSize = 14.sp)
        finalPrice <= 0f -> SocialText("FREE", color = Color(0xFF239A60), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        discounted && basePrice != null -> {
            SocialText(money(basePrice), color = Color(0xFFFF4444), fontSize = 10.sp, textDecoration = TextDecoration.LineThrough)
            Spacer(Modifier.width(4.dp))
            SocialText(money(finalPrice), color = Color(0xFF239A60), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        else -> SocialText(money(finalPrice), color = OneConfigTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PriceLabel(info: CosmeticStoreInfo) = PriceLabel(info.basePrice, info.finalPrice, info.discounted)

@Composable
internal fun PriceLabel(entry: CartEntry) = PriceLabel(entry.basePrice, entry.finalPrice, entry.discounted)

@Composable
private fun CartPanel(
    items: List<CartEntry>,
    status: String?,
    modifier: Modifier,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (items.isEmpty()) {
            CenteredNote("Your cart is empty.")
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(items, key = { it.key }) { item ->
                CartRow(item = item, onRemove = { onRemove(item.key) })
            }
        }

        val subtotal = items.sumOf { (it.basePrice ?: it.finalPrice ?: 0f).toDouble() }.toFloat()
        val total = items.sumOf { (it.finalPrice ?: it.basePrice ?: 0f).toDouble() }.toFloat()
        val discount = (subtotal - total).coerceAtLeast(0f)
        RowTotals("Subtotal", money(subtotal), OneConfigTheme.current.textColor)
        if (discount > 0f) {
            RowTotals("Discounts", "-${money(discount)}", Color(0xFF239A60))
        }
        RowTotals("Total", if (total <= 0f) "FREE" else money(total), Color.White, large = true)
        if (status != null) {
            SocialText(status, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
        }
        SmallButton(
            label = "Checkout ${items.size} item${if (items.size == 1) "" else "s"}",
            iconPath = "assets/polyplus/ico/shopping-cart/0.svg",
            primary = true,
            onClick = onCheckout,
        )
    }
}

@Composable
private fun CartRow(item: CartEntry, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(81.dp)
            .clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        CoverThumbnail(item.coverAssetId, Modifier.size(58.dp))
        Column(Modifier.weight(1f)) {
            SocialText(item.name, color = OneConfigTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            item.description?.takeIf { it.isNotBlank() }?.let {
                SocialText(it, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(item) }
            if (item.discounted) SocialText("SAVE ${item.discountRate}%", color = Color(0xFF239A60), fontSize = 12.sp)
            SocialText("Remove", color = Color(0xFFFF4444), fontSize = 12.sp, modifier = Modifier.clickable(onClick = onRemove))
        }
    }
}

@Composable
private fun RowTotals(label: String, value: String, color: Color, large: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SocialText(label, color = OneConfigTheme.current.textColor, fontSize = if (large) 18.sp else 12.sp, modifier = Modifier.weight(1f))
        SocialText(value, color = color, fontSize = if (large) 22.sp else 12.sp, fontWeight = if (large) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
internal fun StoreDetailPanel(
    info: CosmeticStoreInfo?,
    variant: CosmeticVariantUi?,
    status: String?,
    ownedIds: Set<Int>,
    inCart: Boolean,
    onSelectVariant: (Int) -> Unit,
    onAddToCart: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onBuyNow: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    modifier: Modifier,
    rotation: PreviewRotation? = null,
    onResetRotation: () -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(330.dp)
                .clip(ppShape(12.dp))
                .background(cardBrush())
                .border(1.dp, OneConfigTheme.current.borderColor, ppShape(12.dp)),
        ) {
            if (info == null || variant == null) {
                CenteredNote("Select a cosmetic to preview it.")
            } else {
                val (source, _) = rememberCosmeticPreviewSource(variant.id, info.type)
                val fades = info.type != CosmeticType.Boots
                PlayerPreview(
                    Modifier.align(Alignment.Center).fillMaxWidth().height(330.dp),
                    source = source ?: PlayerPreviewSource.LocalLive,
                    autoSpin = false,
                    bottomFadeFraction = if (fades) STORE_PREVIEW_FADE_FRACTION else 0f,
                    verticalAnchor = when (info.type) {
                        CosmeticType.Hat -> 0.70f
                        CosmeticType.Boots -> 0.44f
                        else -> 0.56f
                    },
                    initialYaw = FRONT_YAW_DEG,
                    previewKey = "store-detail-${variant.id}",
                    live = true,
                    rotation = rotation,
                )
                OwnershipBadge(variant.id in ownedIds, info.createdAt, Modifier.padding(12.dp))
                ResetRotationButton(rotation, onResetRotation)
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .clip(ppShape(12.dp))
                .background(cardBrush())
                .border(1.dp, OneConfigTheme.current.borderColor, ppShape(12.dp)),
        ) {
            if (info == null || variant == null) {
                CenteredNote("Browse the store to buy individual cosmetics.")
            } else {
                val variants = info.uiVariants()
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SocialText(info.type.displayName, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
                        SocialText(info.name, color = OneConfigTheme.current.textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(info) }
                        if (variants.size > 1) {
                            VariantPicker(
                                variants = variants,
                                selectedVariantId = variant.id,
                                onSelect = onSelectVariant,
                                ownedVariantIds = ownedIds,
                            )
                        }
                        if (info.tags.all.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (tag in info.tags.all.take(4)) {
                                    Box(
                                        modifier = Modifier.clip(ppShape(5.dp))
                                            .background(OneConfigTheme.current.chipBackground)
                                            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(5.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        SocialText(tag.replaceFirstChar { it.uppercase() }, color = OneConfigTheme.current.textColor, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        info.description?.takeIf { it.isNotBlank() }?.let {
                            SocialText(it, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
                        }
                    }
                    if (status != null) {
                        SocialText(status, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
                    }
                    if (variant.id in ownedIds) {
                        SocialText(
                            "You already own this cosmetic.",
                            color = OneConfigTheme.current.textColorSecondary,
                            fontSize = 12.sp,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(
                                label = if (inCart) "In cart" else "Add to cart",
                                iconPath = "assets/polyplus/ico/shopping-cart/0.svg",
                                primary = false,
                                onClick = { if (!inCart) onAddToCart(info, variant) },
                            )
                            SmallButton(
                                label = "Buy now",
                                iconPath = "assets/polyplus/ico/shopping-bag.svg",
                                primary = true,
                                onClick = { onBuyNow(info, variant) },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun money(amount: Float, currency: String? = null): String {
    val formatted = String.format(Locale.ROOT, "%.2f", amount)
    return if (currency == null || currency.equals("usd", ignoreCase = true)) "$$formatted" else "$formatted ${currency.uppercase()}"
}

@Composable
internal fun WardrobeCartPane(
    cart: List<CartEntry>,
    status: String?,
    onClose: () -> Unit,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().wardrobePanel().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialText("Cart", color = OneConfigTheme.current.textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            SmallButton("Close", iconPath = "close", primary = false, onClick = onClose)
        }
        CartPanel(items = cart, status = status, modifier = Modifier.weight(1f).fillMaxWidth(), onRemove = onRemove, onCheckout = onCheckout)
    }
}

@Composable
internal fun BundleDetailPanel(bundle: BundleInfo, status: String?, onBuy: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.wardrobePanel().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoverThumbnail(bundle.assetId, Modifier.fillMaxWidth().height(200.dp))
        SocialText("Bundle", color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
        SocialText(bundle.name, color = OneConfigTheme.current.textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(bundle.basePrice, bundle.finalPrice, bundle.discounted) }
        bundle.description?.takeIf { it.isNotBlank() }?.let {
            SocialText(it, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (status != null) {
            SocialText(status, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
        }
        SmallButton("Buy now", iconPath = "assets/polyplus/ico/shopping-bag.svg", primary = true, onClick = onBuy)
    }
}
