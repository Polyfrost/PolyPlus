package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import kotlin.math.cos
import kotlin.math.sin

internal const val SOCIAL_ASSETS = "assets/polyplus/mainmenu/"

internal val SocialScrim = Color(0xB3000000)
internal val SocialWindowScrim = Color(0x40000000)
internal val SocialWarnColor = Color(0xFFF5A623)
internal val SocialDangerColor = Color(0xFFFF5A5A)
internal val SocialSuccessColor = Color(0xFF4ADE80)

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
    @Composable get() = LocalTheme.current.textColor

internal val SocialTextSecondary: Color
    @Composable get() = LocalTheme.current.textColorSecondary

internal val SocialBorderColor: Color
    @Composable get() = LocalTheme.current.borderColor

internal val SocialWindowShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = ppShape(20.dp)

internal val SocialWindowBackground: Color
    @Composable get() = LocalTheme.current.pageBackground.copy(alpha = 0.88f)

internal val SocialSidebarBackground: Color
    @Composable get() = LocalTheme.current.sidebarBackground.copy(alpha = 0.80f)

internal val SocialPanelShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = ppShape(16.dp)

internal val SocialPopupBackground: Color
    @Composable get() = LocalTheme.current.popupBackground

internal val SocialCardBackground: Color
    @Composable get() = LocalTheme.current.modCardBackground

internal val SocialFieldShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = ppShape(8.dp)

internal val SocialControlBackground: Color
    @Composable get() = LocalTheme.current.componentBackground

internal val SocialChipBackground: Color
    @Composable get() = LocalTheme.current.chipBackground

internal val SocialHoverBorder: Color
    @Composable get() = LocalTheme.current.textColorSecondary

internal val SocialHoverOverlay: Color = Color.Black.copy(alpha = 0.16f)

internal val Color.asSocialSelected: Color get() = copy(alpha = 0.22f)
