@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessText
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

@Composable
internal fun PriceLabel(basePrice: Float?, finalPrice: Float?, discounted: Boolean) {
    val theme = OneConfigTheme.current
    when {
        finalPrice == null -> Text("—", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.body)
        finalPrice <= 0f -> Text("FREE", color = SocialSuccessText, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium))
        discounted && basePrice != null -> {
            Text(money(basePrice), color = SocialDangerText, style = OneConfigTheme.current.typography.body.copy(fontSize = 10.sp, textDecoration = TextDecoration.LineThrough))
            Spacer(Modifier.width(4.dp))
            Text(money(finalPrice), color = SocialSuccessText, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium))
        }
        else -> Text(money(finalPrice), color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium))
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (items.isEmpty()) {
            EmptyState("Your cart is empty.", Modifier.fillMaxSize())
            return@Column
        }
        val list = rememberLazyListState()
        ScrollContainer(list, Modifier.weight(1f), scrollbar = false) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = list,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    CartRow(item = item, onRemove = { onRemove(item.key) })
                }
            }
        }

        val subtotal = items.sumOf { (it.basePrice ?: it.finalPrice ?: 0f).toDouble() }.toFloat()
        val total = items.sumOf { (it.finalPrice ?: it.basePrice ?: 0f).toDouble() }.toFloat()
        val discount = (subtotal - total).coerceAtLeast(0f)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RowTotals("Subtotal", money(subtotal), OneConfigTheme.current.textColor)
            if (discount > 0f) {
                RowTotals("Discounts", "-${money(discount)}", SocialSuccessText)
            }
            RowTotals("Total", if (total <= 0f) "FREE" else money(total), OneConfigTheme.current.textColor, large = true)
        }
        if (status != null) {
            Text(status, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(
            text = "Checkout ${items.size} item${if (items.size == 1) "" else "s"}",
            icon = "assets/polyplus/ico/shopping-cart/0.svg",
            onClick = onCheckout,
            modifier = Modifier.fillMaxWidth(),
            style = ControlStyle.Primary,
            size = ControlSize.Large,
        )
    }
}

@Composable
private fun CartRow(item: CartEntry, onRemove: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(81.dp)
            .themedSurface(SurfaceKind.Card, OneConfigTheme.shape(12.dp), glass = false),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverThumbnail(item.coverAssetId, Modifier.size(58.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PriceLabel(item)
                    if (item.discounted) Text("SAVE ${item.discountRate}%", color = SocialSuccessText, style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
                }
            }
        }
        IconButton(icon = "trash", contentDescription = "Remove", onClick = onRemove, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
    }
}

@Composable
private fun RowTotals(label: String, value: String, color: Color, large: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontSize = if (large) 16.sp else 12.sp, fontWeight = if (large) FontWeight.Medium else FontWeight.Normal))
        Text(value, color = color, style = OneConfigTheme.current.typography.body.copy(fontSize = if (large) 18.sp else 12.sp, fontWeight = if (large) FontWeight.Medium else FontWeight.Normal))
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
                .themedSurface(SurfaceKind.Card, OneConfigTheme.shape(12.dp), glass = false),
        ) {
            if (info == null || variant == null) {
                EmptyState("Select a cosmetic to preview it.", Modifier.fillMaxSize())
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
                .themedSurface(SurfaceKind.Card, OneConfigTheme.shape(12.dp), glass = false),
        ) {
            if (info == null || variant == null) {
                EmptyState("Browse the store to buy individual cosmetics.", Modifier.fillMaxSize())
            } else {
                val variants = info.uiVariants()
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val scroll = rememberScrollState()
                    ScrollContainer(scroll, Modifier.weight(1f, fill = false), scrollbar = false) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(info.type.displayName, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
                            Text(info.name, color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
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
                                        Badge(tag.replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }
                            info.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
                            }
                        }
                    }
                    if (status != null) {
                        Text(status, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
                    }
                    if (variant.id in ownedIds) {
                        Text(
                            "You already own this cosmetic.",
                            color = OneConfigTheme.current.textColorSecondary,
                            style = OneConfigTheme.current.typography.small,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                if (inCart) "In cart" else "Add to cart",
                                "assets/polyplus/ico/shopping-cart/0.svg",
                                { if (!inCart) onAddToCart(info, variant) },
                                sound = null,
                            )
                            TextButton(
                                "Buy now",
                                "assets/polyplus/ico/shopping-bag.svg",
                                { onBuyNow(info, variant) },
                                style = ControlStyle.Primary,
                                sound = null,
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
    framed: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxSize().then(if (framed) Modifier.themedSurface(SurfaceKind.Card, OneConfigTheme.shape(16.dp)) else Modifier).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cart", Modifier.weight(1f), OneConfigTheme.current.textColor, OneConfigTheme.current.typography.title)
            IconButton(icon = "close", contentDescription = "Close", onClick = onClose)
        }
        CartPanel(items = cart, status = status, modifier = Modifier.weight(1f).fillMaxWidth(), onRemove = onRemove, onCheckout = onCheckout)
    }
}

@Composable
internal fun BundleDetailPanel(bundle: BundleInfo, status: String?, onBuy: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.themedSurface(SurfaceKind.Card, OneConfigTheme.shape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoverThumbnail(bundle.assetId, Modifier.fillMaxWidth().height(200.dp))
        Text("Bundle", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        Text(bundle.name, color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium))
        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(bundle.basePrice, bundle.finalPrice, bundle.discounted) }
        bundle.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        }
        Spacer(Modifier.weight(1f))
        if (status != null) {
            Text(status, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        }
        TextButton("Buy now", "assets/polyplus/ico/shopping-bag.svg", onBuy, style = ControlStyle.Primary, sound = null)
    }
}
