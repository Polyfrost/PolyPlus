@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

internal val isMinecraftTheme: Boolean
    @Composable
    get() = OneConfigTheme.current.name.let { it == OneConfigTheme.MINECRAFT_DARK.name || it == OneConfigTheme.MINECRAFT_LIGHT.name }

// pixel versions of PolyPlus icons, by file name: OneConfig's where it has one, else PolyPlus's own
private val PolyPlusPixelIcons = mapOf(
    "user-01" to "user-01",
    "log-in-04" to "log-in-04",
    "diamond-01" to "diamond-01",
    "minecraft-block" to "minecraft-block",
    "video-recorder" to "video-recorder",
    // Mod Menu's button
    "package-01" to "minecraft-block",
)
private val MinecraftIcons = mapOf(
    "settings-02" to "cog",
    "settings-04" to "preferences",
    "users-01" to "profiles",
    "globe-01" to "cloud",
    "message-chat-circle" to "text",
    "bell-01" to "bell",
    "x-close" to "close",
    "maximize-02" to "maximise",
    "moon-star" to "moon",
    "sun" to "sun",
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
    val name = path.substringAfterLast('/').removeSuffix(".svg")
    PolyPlusPixelIcons[name]?.let { return "assets/polyplus/mainmenu/minecraft/$it.svg" }
    return MinecraftIcons[name]?.let { "/assets/oneconfig/ico/minecraft/$it.svg" } ?: path
}

@Composable
internal fun ppShape(radius: Dp): Shape =
    if (isMinecraftTheme) RectangleShape else RoundedCornerShape(radius)

@Composable
internal fun ppShapeOf(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape =
    if (isMinecraftTheme) RectangleShape
    else RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
