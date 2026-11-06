@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.marketplace

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.gui.wardrobe.BACK_VIEW_TYPES
import org.polyfrost.polyplus.client.gui.wardrobe.BACK_YAW_DEG
import org.polyfrost.polyplus.client.gui.wardrobe.FRONT_YAW_DEG
import org.polyfrost.polyplus.client.gui.wardrobe.OwnershipBadge
import org.polyfrost.polyplus.client.gui.wardrobe.ResetRotationButton
import org.polyfrost.polyplus.client.gui.wardrobe.cardBrush
import org.polyfrost.polyplus.client.gui.wardrobe.isPreviewAssetLoaded
import org.polyfrost.polyplus.client.gui.wardrobe.outfitPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.abs

private const val TURN_MILLIS = 300
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val BODY_ANCHOR = 0.5f
private const val HAT_ANCHOR = 0.56f
private const val MIN_MODEL_SCALE = 0.37f
private const val MAX_MODEL_SCALE = 0.44f
private const val WIDTH_ROOM = 0.96f
private const val BODY_KEEP = 0.62f
private const val EDGE_FADE = 0.07f

private class Look(val source: PlayerPreviewSource, val reach: SideReach)

private val EDGE_FADE_BRUSH = Brush.horizontalGradient(
    0f to Color.Transparent,
    EDGE_FADE to Color.White,
    1f - EDGE_FADE to Color.White,
    1f to Color.Transparent,
)

@Composable
private fun rememberLook(wanted: Map<BodySlot, Int>, yawDeg: Float): Look {
    var worn by remember { mutableStateOf(emptyMap<BodySlot, Int>()) }
    LaunchedEffect(wanted) {
        val slim = ClientPlatform.runOnMainSync { ClientPlatform.localSkinSlim() }
        val resolved = wanted.mapValues { CosmeticCatalog.resolveVariantForSkin(it.value, slim) }
        worn = resolved
        resolved.forEach { (slot, id) ->
            if (!isPreviewAssetLoaded(id, slot == BodySlot.Cape, slot == BodySlot.Pet)) CosmeticAssetCache.ensureCosmeticLoaded(id)
        }
    }
    val installs = CosmeticAssetCache.installs
    val textures = MojangCapeService.textureVersion
    return remember(worn, installs, textures, yawDeg) {
        val reach = typicalReach(worn.values.mapNotNull(CosmeticAssetCache::getAttachedCosmetic), yawDeg)
        Look(outfitPreviewSource(worn, MojangCapeService.localCapeTexture()), reach)
    }
}

@Composable
internal fun TryOnPreview(state: MarketplaceState, modifier: Modifier) {
    val targetYaw = if (state.facing in BACK_VIEW_TYPES) BACK_YAW_DEG else FRONT_YAW_DEG
    val rotation = remember { PreviewRotation(targetYaw) }
    val wanted = state.look + state.fitting.mapValues { it.value.variantId }
    val look = rememberLook(wanted, targetYaw)
    LaunchedEffect(targetYaw) {
        if (rotation.userRotated) return@LaunchedEffect
        val start = rotation.yaw
        val delta = ((targetYaw - start) % FULL_TURN + FULL_TURN + HALF_TURN) % FULL_TURN - HALF_TURN
        animate(0f, 1f, animationSpec = tween(TURN_MILLIS)) { progress, _ ->
            if (!rotation.userRotated) rotation.yaw = start + delta * progress
        }
    }
    BoxWithConstraints(
        modifier
            .clip(ppShape(16.dp))
            .background(cardBrush())
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(16.dp)),
    ) {
        val modelScale = (maxWidth.value * WIDTH_ROOM / (maxHeight.value * look.reach.span)).coerceIn(MIN_MODEL_SCALE, MAX_MODEL_SCALE)
        val blockSize = maxHeight.value * modelScale
        val limit = (maxWidth.value / 2f - blockSize * BODY_KEEP).coerceAtLeast(0f)
        val shift = ((look.reach.left - look.reach.right) / 2f * blockSize).coerceIn(-limit, limit)
        val frame = DpSize(maxWidth + (abs(shift) * 2f).dp, maxHeight)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(EDGE_FADE_BRUSH, blendMode = BlendMode.DstIn)
                },
        ) {
            PlayerPreview(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = shift.dp)
                    .requiredSize(frame),
                source = look.source,
                autoSpin = false,
                modelScale = modelScale,
                verticalAnchor = if (BodySlot.Hat in wanted) HAT_ANCHOR else BODY_ANCHOR,
                initialYaw = FRONT_YAW_DEG,
                live = true,
                rotation = rotation,
                fadeEdges = false,
            )
        }
        state.selectedItem?.let { info ->
            OwnershipBadge(state.variant(info).id in state.ownedIds, info.createdAt, Modifier.padding(12.dp))
        }
        ResetRotationButton(rotation) {
            rotation.userRotated = false
            rotation.yaw = targetYaw
            rotation.pitch = 0f
        }
    }
}
