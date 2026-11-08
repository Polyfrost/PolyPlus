@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.internal.ui.shell.ShellController
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.surfaceOpacity
import kotlin.math.cos
import kotlin.math.sin

internal const val SOCIAL_ASSETS = "assets/polyplus/mainmenu/"

internal val SocialScrim = Color(0xB3000000)
internal val SocialWindowScrim = Color(0x40000000)
internal val SocialWarnColor = Color(0xFFF5A623)
internal val SocialDangerColor = Color(0xFFFF5A5A)
internal val SocialSuccessColor = Color(0xFF239A60)

private const val MIN_TEXT_CONTRAST = 4.5f
private const val LUMINANCE_OFFSET = 0.05f
private const val READABLE_STEPS = 10

private fun luminanceContrast(first: Color, second: Color): Float {
    val a = first.luminance() + LUMINANCE_OFFSET
    val b = second.luminance() + LUMINANCE_OFFSET
    return maxOf(a, b) / minOf(a, b)
}

private fun contrastOver(scene: Color, text: Color, surface: Color): Float {
    val backdrop = surface.compositeOver(scene)
    return luminanceContrast(text.compositeOver(backdrop), backdrop)
}

internal fun textContrast(text: Color, surface: Color): Float =
    minOf(contrastOver(Color.Black, text, surface), contrastOver(Color.White, text, surface))

internal fun meetsTextContrast(text: Color, surface: Color): Boolean = textContrast(text, surface) >= MIN_TEXT_CONTRAST

@Composable
private fun readableText(color: Color): Color {
    val theme = OneConfigTheme.current
    val step = (0..READABLE_STEPS).firstOrNull { meetsTextContrast(lerp(color, theme.textColor, it / READABLE_STEPS.toFloat()), theme.popupBackground) } ?: READABLE_STEPS
    return lerp(color, theme.textColor, step / READABLE_STEPS.toFloat())
}

internal val SocialDangerText: Color
    @Composable get() = readableText(SocialDangerColor)

internal val SocialSuccessText: Color
    @Composable get() = readableText(SocialSuccessColor)

internal const val DISABLED_ALPHA = 0.4f

internal val SocialBorderWidth = 1.dp
internal val SocialIndicatorBorderWidth = 1.5.dp
internal val SocialPanelBorderWidth = 1.5.dp

private const val PANEL_BORDER_ANGLE_DEG = 20.0

internal val SocialPanelBorderBrush: Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(PANEL_BORDER_ANGLE_DEG)
        val ux = cos(radians).toFloat()
        val uy = sin(radians).toFloat()
        val len = size.width * ux + size.height * uy
        return LinearGradientShader(
            from = Offset.Zero,
            to = Offset(ux * len, uy * len),
            colors = listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.5f),
            ),
            colorStops = listOf(0f, 0.5f, 1f),
        )
    }
}

internal val SocialTextPrimary: Color
    @Composable get() = OneConfigTheme.current.textColor

internal val SocialTextSecondary: Color
    @Composable get() = OneConfigTheme.current.textColorSecondary

internal val SocialBorderColor: Color
    @Composable get() = OneConfigTheme.current.borderColor

internal val SocialWindowShape: Shape
    @Composable
    get() = OneConfigTheme.current.backgroundShape

internal val OneConfigUITheme.translucentSurfaces: Boolean
    get() = popupBackground.alpha < 1f

internal val liquidGlassTheme: Boolean
    @Composable get() = LocalTheme.current.liquidGlass

private const val DEFAULT_PAGE_OPACITY = 88f
private const val FULL_OPACITY = 100f

@Composable
private fun surfaceOpacity(color: Color, percent: Float, defaultPercent: Float): Color {
    val alpha = if (OneConfigTheme.current.translucentSurfaces) color.alpha * percent / defaultPercent else percent / FULL_OPACITY
    return color.copy(alpha = alpha.coerceIn(0f, 1f))
}

internal val SocialWindowBackground: Color
    @Composable get() = surfaceOpacity(OneConfigTheme.current.pageBackground, ShellController.shared.appearance.pageOpacity, DEFAULT_PAGE_OPACITY)

internal val SocialPanelShape: Shape
    @Composable
    get() = OneConfigTheme.current.popupShape

internal val SocialPopupBackground: Color
    @Composable get() = OneConfigTheme.current.popupBackground

internal val SocialCardBackground: Color
    @Composable get() = OneConfigTheme.current.modCardBackground

internal val SocialFieldShape: Shape
    @Composable
    get() = OneConfigTheme.current.buttonShape

internal val SocialControlBackground: Color
    @Composable get() = OneConfigTheme.current.componentBackground

internal val SocialChipBackground: Color
    @Composable get() = OneConfigTheme.current.chipBackground

internal val SocialHoverBorder: Color
    @Composable get() = OneConfigTheme.current.textColorSecondary

internal val SocialHoverOverlay: Color = Color.Black.copy(alpha = 0.16f)

internal val Color.asSocialSelected: Color get() = copy(alpha = 0.22f)

internal const val LIGHT_TEXT_LIMIT = 0.5f
private const val LIGHT_PANEL_ALPHA = 0.92f
private const val PANEL_ALPHA_FLOOR = 0.72f
private const val OFF_STATE_TINT = 0.12f

internal val MutedText: Color
    @Composable get() {
        val theme = OneConfigTheme.current
        return if (meetsTextContrast(theme.textColorSecondary, theme.popupBackground)) theme.textColorSecondary else theme.textColor
    }

internal val PanelSurface: Color
    @Composable get() {
        val theme = OneConfigTheme.current
        val glass = theme.pageBackground
        val ui = LocalTheme.current
        return when {
            ui.liquidGlass -> ui.surfaceOpacity(glass, ShellController.shared.appearance.pageOpacity, DEFAULT_PAGE_OPACITY)
            glass.alpha < 1f && theme.textColor.luminance() < LIGHT_TEXT_LIMIT -> theme.popupBackground.copy(alpha = LIGHT_PANEL_ALPHA)
            glass.alpha < PANEL_ALPHA_FLOOR -> glass.copy(alpha = PANEL_ALPHA_FLOOR)
            else -> glass
        }
    }

internal val CardSurface: Color
    @Composable get() = OneConfigTheme.current.componentBackground.compositeOver(OneConfigTheme.current.popupBackground)

internal val SocialOffStateBackground: Color
    @Composable get() {
        val theme = OneConfigTheme.current
        return if (theme.translucentSurfaces) theme.textColor.copy(alpha = OFF_STATE_TINT).compositeOver(CardSurface) else theme.componentBackground
    }

internal val SocialDropdownBackground: Color
    @Composable get() = if (OneConfigTheme.current.translucentSurfaces) CardSurface else OneConfigTheme.current.componentBackground
