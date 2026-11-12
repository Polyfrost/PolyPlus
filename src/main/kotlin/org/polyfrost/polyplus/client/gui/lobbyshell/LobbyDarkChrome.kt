@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.ReduceTransparency
import org.polyfrost.oneconfig.internal.ui.themes.UITheme
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.LocalShapeTheme
import org.polyfrost.polyplus.client.gui.familyVariant
import org.polyfrost.polyplus.client.gui.isLightTheme

private val LocalUserTheme = compositionLocalOf<UITheme?> { null }

private val OneConfigUITheme.darkVariant: OneConfigUITheme
    get() = if (isLightTheme) familyVariant(light = false) ?: OnboardingFeatures.uiStyleTheme(OnboardingFeatures.uiStyleOf(this), light = false) else this

@Composable
internal fun LobbyDarkChrome(content: @Composable () -> Unit) {
    val user = LocalTheme.current
    val active = OneConfigTheme.active ?: user
    val sibling = active.darkVariant as? UITheme
    val opaque = ReduceTransparency.enabled
    val dark = remember(sibling, active, user.accentTextColor, opaque) {
        if (sibling == null || sibling === active) null else sibling.shownLike(user, opaque)
    }
    CompositionLocalProvider(
        LocalUserTheme provides user,
        LocalShapeTheme provides user,
        LocalTheme provides (dark ?: user),
        content = content,
    )
}

private fun UITheme.shownLike(user: UITheme, opaque: Boolean): UITheme {
    val page = if (opaque) pageBackground.copy(alpha = 1f) else pageBackground
    fun surface(color: Color) = if (opaque) color.compositeOver(page) else color
    return copy(
        accentTextColor = user.accentTextColor,
        pageBackground = page,
        sidebarBackground = surface(sidebarBackground),
        chipBackground = surface(chipBackground),
        modCardBackground = surface(modCardBackground),
        componentBackground = surface(componentBackground),
        popupBackground = surface(popupBackground),
    ).withControlTrackColor(controlTrackColor)
}

@Composable
internal fun LobbyUserTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTheme provides (LocalUserTheme.current ?: LocalTheme.current), content = content)
}
