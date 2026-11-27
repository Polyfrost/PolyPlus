@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.Image
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.PaddingValues
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeColors
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.transparencyCheckerboard
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.rememberCoverImage
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.pets.PetAssets

@Composable
internal fun CosmeticThumbnail(
    source: PlayerPreviewSource?,
    type: CosmeticType,
    previewKey: String,
    modifier: Modifier,
    checker: Boolean = true,
    coverAssetId: Int? = null,
) {
    val framing = cosmeticPreviewFraming(type)
    Box(modifier) {
        if (checker) CheckerThumbnail(Modifier.fillMaxSize())
        if (source == null && coverAssetId != null) {
            CoverThumbnail(coverAssetId, Modifier.fillMaxSize())
        }
        if (source != null) {
            PlayerPreview(
                Modifier.fillMaxSize(),
                source = source,
                autoSpin = false,
                allowDrag = false,
                modelScale = framing.modelScale,
                verticalAnchor = framing.verticalAnchor,
                initialYaw = framing.yawDeg,
                previewKey = previewKey,
                live = needsLivePreview(type, source),
            )
        }
    }
}

private fun needsLivePreview(type: CosmeticType, source: PlayerPreviewSource): Boolean {
    if (type == CosmeticType.Aura) return true
    val override = source as? PlayerPreviewSource.Override ?: return false
    if (override.capeCosmeticId?.let(CosmeticAssetCache::isCapeAnimated) == true) return true
    if (override.pet?.isAnimated == true) return true
    return override.equipment.equipped().any { it.cosmetic.scene.isAnimated }
}

private data class PreviewFraming(val yawDeg: Float, val modelScale: Float, val verticalAnchor: Float)

internal const val FRONT_YAW_DEG = 180f + 22.9f
internal const val BACK_YAW_DEG = 22.9f
internal const val STORE_PREVIEW_FADE_FRACTION = 0.22f

private fun cosmeticPreviewFraming(type: CosmeticType): PreviewFraming = when (type) {
    CosmeticType.Hat, CosmeticType.Glasses -> PreviewFraming(FRONT_YAW_DEG, 1.0f, 1.5f)
    CosmeticType.Boots -> PreviewFraming(FRONT_YAW_DEG, 0.85f, 0.26f)
    CosmeticType.Shoulder -> PreviewFraming(FRONT_YAW_DEG, 0.58f, 0.62f)
    CosmeticType.Wings -> PreviewFraming(BACK_YAW_DEG, 0.5f, 0.5f)
    CosmeticType.Backpack -> PreviewFraming(BACK_YAW_DEG, 0.55f, 0.55f)
    CosmeticType.Cape -> PreviewFraming(BACK_YAW_DEG, 0.68f, 0.5f)
    CosmeticType.Aura -> PreviewFraming(FRONT_YAW_DEG, 0.5f, 0.5f)
    CosmeticType.Glove -> PreviewFraming(FRONT_YAW_DEG, 0.7f, 0.42f)
    else -> PreviewFraming(FRONT_YAW_DEG, 0.42f, 0.52f)
}

@Composable
internal fun rememberCosmeticPreviewSource(cosmeticId: Int, type: CosmeticType): Pair<PlayerPreviewSource?, Int> {
    val isCape = type == CosmeticType.Cape
    val isPet = type == CosmeticType.Pet

    var previewId by remember(cosmeticId) { mutableIntStateOf(cosmeticId) }
    val loaded = CosmeticAssetCache.installs.let { isPreviewAssetLoaded(previewId, isCape, isPet) }
    // a trim can evict a preview's assets while the screen is closed, so it reloads them when shown again
    val slim = rememberLocalSkinSlim()
    LaunchedEffect(cosmeticId, loaded, slim) {
        val id = CosmeticCatalog.resolveVariantForSkin(cosmeticId, slim)
        previewId = id
        if (!isPreviewAssetLoaded(id, isCape, isPet)) CosmeticAssetCache.ensureCosmeticLoaded(id)
    }

    val loadTick = (if (loaded) 1 else 0) + (if (isCape && CosmeticAssetCache.isCapeAnimated(previewId)) 2 else 0)

    val source = remember(previewId, loadTick, isCape, isPet) {
        when {
            isCape -> CosmeticAssetCache.getAttachedCosmetic(previewId)?.takeIf { it.slot == BodySlot.Cape }
                ?.let { attached -> PlayerPreviewSource.Override(CosmeticEquipment().apply { equip(attached) }) }
                ?: PlayerPreviewSource.Override(CosmeticEquipment(), capeCosmeticId = previewId)
            isPet -> {
                val attached = CosmeticAssetCache.getAttachedCosmetic(previewId)
                if (attached != null) {
                    val equipment = CosmeticEquipment()
                    equipment.equip(attached)
                    PlayerPreviewSource.Override(equipment)
                } else {
                    val pet = PetAssets.get(previewId) ?: return@remember null
                    PlayerPreviewSource.Override(CosmeticEquipment(), pet = pet)
                }
            }
            else -> {
                val attached = CosmeticAssetCache.getAttachedCosmetic(previewId) ?: return@remember null
                val equipment = CosmeticEquipment()
                equipment.equip(attached)
                PlayerPreviewSource.Override(equipment)
            }
        }
    }

    return source to loadTick
}

@Composable
internal fun BoxScope.OwnershipBadge(owned: Boolean, createdAt: String, modifier: Modifier) {
    val label = when {
        owned -> "OWNED"
        isNewItem(createdAt) -> "NEW"
        else -> return
    }
    val theme = OneConfigTheme.current
    Badge(
        label,
        modifier.align(Alignment.TopStart),
        tone = if (owned) BadgeTone.Success else BadgeTone.Accent,
        style = BadgeStyle.Solid,
        colors = if (owned) BadgeColors(container = SocialSuccessColor, content = Color.White) else BadgeColors(container = OneConfigTheme.accent, content = theme.accentTextColor),
        shape = OneConfigTheme.shape(5.dp),
        labelStyle = TextStyle(fontSize = 10.sp),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
    )
}

internal fun isPreviewAssetLoaded(id: Int, isCape: Boolean, isPet: Boolean): Boolean = when {
    isCape -> CosmeticAssetCache.isCapeLoaded(id) || CosmeticAssetCache.isEngineCape(id)
    isPet -> CosmeticAssetCache.getAttachedCosmetic(id) != null || PetAssets.has(id)
    else -> CosmeticAssetCache.getAttachedCosmetic(id) != null
}

internal fun isNewItem(createdAt: String): Boolean {
    if (createdAt.isBlank()) return false
    return runCatching {
        val created = OffsetDateTime.parse(createdAt).toInstant()
        created.isAfter(Instant.now().minus(Duration.ofDays(7)))
    }.getOrDefault(false)
}

@Composable
internal fun CoverThumbnail(coverAssetId: Int?, modifier: Modifier) {
    val cover = rememberCoverImage(coverAssetId)
    Box(modifier) {
        CheckerThumbnail(Modifier.fillMaxSize())
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(OneConfigTheme.shape(5.dp)),
            )
        }
    }
}

@Composable
private fun CheckerThumbnail(modifier: Modifier) {
    val shape = OneConfigTheme.shape(5.dp)
    Box(
        modifier.clip(shape)
            .border(1.dp, OneConfigTheme.current.borderColor, shape)
            .transparencyCheckerboard(9.dp),
    )
}
