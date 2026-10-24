package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

internal val isMinecraftTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalTheme.current.previewImage.startsWith("minecraft")

// PolyPlus icons whose look OneConfig's pixel set has, by file name
private val MinecraftIcons = mapOf(
    "settings-02" to "cog",
    "settings-04" to "preferences",
    "users-01" to "profiles",
    "globe-01" to "cloud",
    "message-chat-circle" to "text",
    "diamond-01" to "star",
    "bell-01" to "bell",
    "x-close" to "close",
    "maximize-02" to "maximise",
    "moon-star" to "moon",
    "refresh-cw-01" to "refresh",
    "trash-01" to "trash",
    "copy-01" to "copy",
    "link-external-01" to "external-link",
    "chevron-up" to "up",
    "chevron-right" to "right-arrow",
    "check" to "tick",
    "check-circle" to "check-circle",
    "search-lg" to "search",
    "plus" to "plus",
    "dots-vertical" to "dots-vertical",
)

/** [path], or OneConfig's pixel version of it under the Minecraft themes. */
@Composable
@ReadOnlyComposable
internal fun themedIcon(path: String): String {
    if (!isMinecraftTheme) return path
    val name = MinecraftIcons[path.substringAfterLast('/').removeSuffix(".svg")] ?: return path
    return "/assets/oneconfig/ico/minecraft/$name.svg"
}

@Composable
@ReadOnlyComposable
internal fun ppShape(radius: Dp): Shape =
    if (isMinecraftTheme) RectangleShape else RoundedCornerShape(radius)

@Composable
@ReadOnlyComposable
internal fun ppShapeOf(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape =
    if (isMinecraftTheme) RectangleShape
    else RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
