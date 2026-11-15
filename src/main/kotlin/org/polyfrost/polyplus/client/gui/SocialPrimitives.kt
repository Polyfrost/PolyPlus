@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import java.util.UUID

@Composable
internal fun SocialAvatar(playerId: String, size: Dp, modifier: Modifier = Modifier) {
    val uuid = remember(playerId) { parsePlayerUuid(playerId) }
    PlayerHead(
        playerId = uuid,
        size = size,
        modifier = modifier,
        name = if (uuid == null) playerId else null,
        shape = OneConfigTheme.shape(size / 4),
        contentDescription = null,
    )
}

private fun parsePlayerUuid(id: String): UUID? {
    val trimmed = id.trim()
    val dashed = if (trimmed.length == 32 && '-' !in trimmed) {
        "${trimmed.substring(0, 8)}-${trimmed.substring(8, 12)}-${trimmed.substring(12, 16)}-${trimmed.substring(16, 20)}-${trimmed.substring(20)}"
    } else {
        trimmed
    }
    if (dashed.length != 36) return null
    return runCatching { UUID.fromString(dashed) }.getOrNull()
}

@Composable
internal fun SocialGroupAvatar(memberIds: List<String>, size: Dp, modifier: Modifier = Modifier, ringColor: Color = SocialCardBackground, grid: Boolean = false) {
    val shape = OneConfigTheme.shape(size / 4)
    val shown = memberIds.take(4)

    if (shown.size <= 1) {
        val id = shown.firstOrNull()
        if (id != null) {
            SocialAvatar(id, size, modifier)
        } else {
            Box(modifier.size(size).clip(shape).background(SocialControlBackground))
        }
        return
    }

    val gap = size * 0.07f
    Box(modifier.size(size).clip(shape).background(ringColor)) {
        when (shown.size) {
            2 -> {
                SocialAvatar(shown[0], size * 0.62f, Modifier.align(Alignment.TopStart).padding(top = gap, start = gap))
                SocialAvatar(shown[1], size * 0.62f, Modifier.align(Alignment.BottomEnd).padding(bottom = gap, end = gap))
            }
            3 -> if (grid) {
                SocialAvatar(shown[0], size * 0.46f, Modifier.align(Alignment.TopStart).padding(top = gap, start = gap))
                SocialAvatar(shown[1], size * 0.46f, Modifier.align(Alignment.TopEnd).padding(top = gap, end = gap))
                SocialAvatar(shown[2], size * 0.46f, Modifier.align(Alignment.BottomStart).padding(bottom = gap, start = gap))
            } else {
                SocialAvatar(shown[0], size * 0.5f, Modifier.align(Alignment.TopCenter).padding(top = gap))
                SocialAvatar(shown[1], size * 0.5f, Modifier.align(Alignment.BottomStart).padding(bottom = gap, start = gap))
                SocialAvatar(shown[2], size * 0.5f, Modifier.align(Alignment.BottomEnd).padding(bottom = gap, end = gap))
            }
            else -> {
                SocialAvatar(shown[0], size * 0.46f, Modifier.align(Alignment.TopStart).padding(top = gap, start = gap))
                SocialAvatar(shown[1], size * 0.46f, Modifier.align(Alignment.TopEnd).padding(top = gap, end = gap))
                SocialAvatar(shown[2], size * 0.46f, Modifier.align(Alignment.BottomStart).padding(bottom = gap, start = gap))
                SocialAvatar(shown[3], size * 0.46f, Modifier.align(Alignment.BottomEnd).padding(bottom = gap, end = gap))
            }
        }
    }
}
