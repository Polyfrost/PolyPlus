@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigUITheme
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.UITheme
import org.polyfrost.polyplus.client.gui.familyVariant
import org.polyfrost.polyplus.client.gui.isLightTheme

private val LocalUserTheme = compositionLocalOf<UITheme?> { null }

private val OneConfigUITheme.darkVariant: OneConfigUITheme
    get() = if (isLightTheme) familyVariant(light = false) ?: this else this

@Composable
internal fun LobbyDarkChrome(content: @Composable () -> Unit) {
    val user = LocalTheme.current
    val dark = (OneConfigTheme.active ?: user).darkVariant
    CompositionLocalProvider(
        LocalUserTheme provides user,
        LocalTheme provides (dark as? UITheme ?: user),
        content = content,
    )
}

@Composable
internal fun LobbyUserTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTheme provides (LocalUserTheme.current ?: LocalTheme.current), content = content)
}
