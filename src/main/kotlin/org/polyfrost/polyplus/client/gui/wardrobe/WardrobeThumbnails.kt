@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.rememberCoverImage
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform

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

private fun needsLivePreview(type: CosmeticType, source: PlayerPreviewSource) = when (type) {
    CosmeticType.Aura -> true
    CosmeticType.Cape ->
        (source as? PlayerPreviewSource.Override)?.capeCosmeticId?.let(CosmeticAssetCache::isCapeAnimated) == true
    else -> false
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
    LaunchedEffect(cosmeticId, loaded) {
        val slim = ClientPlatform.runOnMainSync { ClientPlatform.localSkinSlim() }
        val id = CosmeticCatalog.resolveVariantForSkin(cosmeticId, slim)
        previewId = id
        if (!isPreviewAssetLoaded(id, isCape, isPet)) CosmeticAssetCache.ensureCosmeticLoaded(id)
    }

    val loadTick = (if (loaded) 1 else 0) + (if (isCape && CosmeticAssetCache.isCapeAnimated(previewId)) 2 else 0)

    val source = remember(previewId, loadTick, isCape, isPet) {
        when {
            isCape -> PlayerPreviewSource.Override(CosmeticEquipment(), capeCosmeticId = previewId)
            isPet -> {
                val attached = CosmeticAssetCache.getAttachedCosmetic(previewId)
                if (attached != null) {
                    val equipment = CosmeticEquipment()
                    equipment.equip(attached)
                    PlayerPreviewSource.Override(equipment)
                } else {
                    val pet = CosmeticAssetCache.getPetDefinition(previewId) ?: return@remember null
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
    Box(
        modifier = modifier.align(Alignment.TopStart)
            .clip(ppShape(5.dp))
            .background(if (owned) Color(0xFF239A60) else OneConfigTheme.accent)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        SocialText(label, color = OneConfigTheme.current.accentTextColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

internal fun isPreviewAssetLoaded(id: Int, isCape: Boolean, isPet: Boolean): Boolean = when {
    isCape -> CosmeticAssetCache.isCapeLoaded(id)
    isPet -> CosmeticAssetCache.getAttachedCosmetic(id) != null || CosmeticAssetCache.getPetDefinition(id) != null
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
                modifier = Modifier.fillMaxSize().clip(ppShape(5.dp)),
            )
        }
    }
}

@Composable
private fun CheckerThumbnail(modifier: Modifier) {
    Checkerboard(
        modifier = modifier.clip(ppShape(5.dp))
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(5.dp)),
        cell = 9.dp,
    )
}
