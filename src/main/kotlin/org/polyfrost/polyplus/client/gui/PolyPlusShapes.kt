@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.internal.ui.themes.UITheme
import org.polyfrost.oneconfig.internal.ui.themes.cornerRadiusOrNull

private const val LIGHT_SUFFIX = " Light"
private const val DARK_SUFFIX = " Dark"

internal val OneConfigUITheme.isLightTheme: Boolean
    get() = pageBackground.luminance() > LIGHT_TEXT_LIMIT && textColor.luminance() < LIGHT_TEXT_LIMIT

internal val OneConfigUITheme.isMinecraftStyle: Boolean
    get() = (this as? UITheme)?.isMinecraftStyle == true || name == OneConfigTheme.MINECRAFT_DARK.name || name == OneConfigTheme.MINECRAFT_LIGHT.name

internal fun OneConfigUITheme.familyVariant(light: Boolean): OneConfigUITheme? {
    val target = name.removeSuffix(LIGHT_SUFFIX).removeSuffix(DARK_SUFFIX) + if (light) LIGHT_SUFFIX else DARK_SUFFIX
    return OneConfigTheme.builtIn().firstOrNull { it.name == target }
}

internal val isMinecraftTheme: Boolean
    @Composable
    get() = OneConfigTheme.current.isMinecraftStyle

// pixel versions of PolyPlus icons, by file name: OneConfig's where it has one, else PolyPlus's own
private val PolyPlusPixelIcons = mapOf(
    "user-01" to "user-01",
    "log-in-04" to "log-in-04",
    "log-out-04" to "log-out-04",
    "diamond-01" to "diamond-01",
    "minecraft-block" to "minecraft-block",
    "video-recorder" to "video-recorder",
    // Mod Menu's button
    "package-01" to "minecraft-block",
)
private val MinecraftIcons = mapOf(
    "settings-02" to "cog",
    "users-01" to "profiles",
    "globe-01" to "cloud",
    "message-chat-circle" to "text",
    "bell-01" to "bell",
    "x-close" to "close",
    "moon-star" to "moon",
    "sun" to "sun",
    "refresh-cw-01" to "refresh",
    "trash-01" to "trash",
    "copy-01" to "copy",
    "link-external-01" to "external-link",
    "chevron-up" to "up",
    "chevron-right" to "right-arrow",
    "chevron-left" to "left-arrow",
    "check" to "tick",
    "check-circle" to "check-circle",
    "search-lg" to "search",
    "search" to "search",
    "plus" to "plus",
    "dots-vertical" to "dots-vertical",
    "alert-triangle" to "alert-circle",
    "check-verified" to "check-circle",
    "left-arrow" to "left-arrow",
    "right-arrow" to "right-arrow",
    "loading-02" to "refresh",
    "stars" to "star",
    "zap" to "lightning-01",
)
private const val ONECONFIG_ICONS = "/assets/oneconfig/ico/"
private const val PIXEL_ICON_CELLS = 12
private const val PIXEL_ICON_ROUND_UP = 0.6f

/** [path], or OneConfig's pixel version of it under the Minecraft themes. */
@Composable
internal fun themedIcon(path: String): String {
    if (!isMinecraftTheme) return path
    val name = path.substringAfterLast('/').removeSuffix(".svg")
    PolyPlusPixelIcons[name]?.let { return "assets/polyplus/mainmenu/minecraft/$it.svg" }
    MinecraftIcons[name]?.let { return "${ONECONFIG_ICONS}minecraft/$it.svg" }
    return if (path.startsWith(ONECONFIG_ICONS)) name else path
}

@Composable
internal fun ThemedIcon(path: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val themed = themedIcon(path)
    val pixelArt = isMinecraftTheme && ("/minecraft/" in themed || '/' !in themed)
    Icon(themed, color, modifier.size(if (pixelArt) crispPixelSize(size) else size))
}

@Composable
private fun crispPixelSize(size: Dp): Dp = with(LocalDensity.current) {
    val scale = (size.toPx() / PIXEL_ICON_CELLS + PIXEL_ICON_ROUND_UP).toInt().coerceAtLeast(1)
    (scale * PIXEL_ICON_CELLS).toDp()
}

@Composable
private fun cornerScale(): Float {
    val radius = OneConfigTheme.current.popupShape.cornerRadiusOrNull() ?: return 1f
    val reference = OneConfigTheme.POLYFROSTED_DARK.popupShape.cornerRadiusOrNull() ?: return 1f
    if (reference <= 0.dp) return 1f
    return (radius / reference).coerceIn(0f, 1f)
}

@Composable
internal fun ppShape(radius: Dp): Shape {
    val scale = cornerScale()
    return if (scale <= 0f) RectangleShape else RoundedCornerShape(radius * scale)
}

@Composable
internal fun ppShapeOf(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape {
    val scale = cornerScale()
    return if (scale <= 0f) RectangleShape
    else RoundedCornerShape(topStart * scale, topEnd * scale, bottomEnd * scale, bottomStart * scale)
}
