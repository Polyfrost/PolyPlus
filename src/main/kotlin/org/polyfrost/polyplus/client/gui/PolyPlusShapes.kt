@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ThemeStyle
import org.polyfrost.oneconfig.internal.ui.themes.CatppuccinFrappe
import org.polyfrost.oneconfig.internal.ui.themes.CatppuccinLatte
import org.polyfrost.oneconfig.internal.ui.themes.CatppuccinMacchiato
import org.polyfrost.oneconfig.internal.ui.themes.CatppuccinMocha
import org.polyfrost.oneconfig.internal.ui.themes.NordDark
import org.polyfrost.oneconfig.internal.ui.themes.NordLight
import org.polyfrost.oneconfig.internal.ui.themes.UITheme
import org.polyfrost.oneconfig.internal.ui.themes.palette

private const val LIGHT_SUFFIX = " Light"
private const val DARK_SUFFIX = " Dark"

internal val OneConfigUITheme.isLightTheme: Boolean
    get() = isLight

internal val OneConfigUITheme.isMinecraftStyle: Boolean
    get() = style == ThemeStyle.Pixel

internal val OneConfigUITheme.isGlassStyle: Boolean
    get() = style == ThemeStyle.Glass

private val OneConfigUITheme.family: String
    get() = (this as? UITheme)?.palette?.group ?: name.removeSuffix(LIGHT_SUFFIX).removeSuffix(DARK_SUFFIX)

private val familyThemes: List<OneConfigUITheme> by lazy {
    OneConfigTheme.builtIn() + listOf(CatppuccinLatte, CatppuccinMocha, CatppuccinMacchiato, CatppuccinFrappe, NordDark, NordLight)
}

internal fun OneConfigUITheme.familyVariant(light: Boolean): OneConfigUITheme? {
    if (isLightTheme == light) return this
    val family = family
    return familyThemes.firstOrNull { it.family == family && it.isLightTheme == light }
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
private val PixelIconDirectories = listOf(
    "assets/polyplus/mainmenu/",
    "assets/polyplus/ico/",
    "assets/polyplus/onboarding/",
)

internal fun registerPolyPlusPixelIcons() {
    val icons = LinkedHashMap<String, String>()
    for (directory in PixelIconDirectories) {
        for ((name, pixel) in MinecraftIcons) icons["$directory$name.svg"] = pixel
        for ((name, pixel) in PolyPlusPixelIcons) icons["$directory$name.svg"] = "assets/polyplus/mainmenu/minecraft/$pixel.svg"
    }
    OneConfigTheme.registerPixelIcons(icons)
}

@Composable
internal fun ThemedIcon(path: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Icon(name = path, contentDescription = null, tint = color, modifier = modifier.size(size))
}

@Composable
internal fun ppShapeOf(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape {
    val start = OneConfigTheme.shape(topStart) as? CornerBasedShape
    val end = OneConfigTheme.shape(topEnd) as? CornerBasedShape
    val endBottom = OneConfigTheme.shape(bottomEnd) as? CornerBasedShape
    val startBottom = OneConfigTheme.shape(bottomStart) as? CornerBasedShape
    if (start == null || end == null || endBottom == null || startBottom == null) return RectangleShape
    return RoundedCornerShape(start.topStart, end.topEnd, endBottom.bottomEnd, startBottom.bottomStart)
}
