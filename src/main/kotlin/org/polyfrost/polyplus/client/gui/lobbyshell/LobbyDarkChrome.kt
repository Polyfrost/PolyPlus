@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ProvideThemeVariant
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

private val LocalUserLight = compositionLocalOf<Boolean?> { null }

@Composable
internal fun LobbyDarkChrome(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUserLight provides OneConfigTheme.current.isLight) {
        ProvideThemeVariant(dark = true, keepAccent = true, content = content)
    }
}

@Composable
internal fun LobbyUserTheme(content: @Composable () -> Unit) {
    val light = LocalUserLight.current
    if (light == null) content() else ProvideThemeVariant(dark = !light, keepAccent = true, content = content)
}
