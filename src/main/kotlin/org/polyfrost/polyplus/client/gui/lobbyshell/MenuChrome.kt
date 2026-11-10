@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.polyplus.client.gui.ModIntegrationButton
import org.polyfrost.polyplus.client.gui.ModIntegrationButtons
import org.polyfrost.polyplus.client.gui.Outfit
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.isMinecraftTheme
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover

private const val NAV_INACTIVE_ALPHA = 0.6f
private val NAV_UNDERLINE_WIDTH = 27.dp
private val MORE_MENU_WIDTH = 300.dp
internal val MORE_MENU_INSET = 8.dp

@Composable
internal fun Wordmark(modifier: Modifier = Modifier, mark: Modifier = Modifier, word: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(mark) { LobbyIcon("logo", size = 28.dp) }
        Box(word) { SocialText("ONECLIENT", 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = Outfit, maxLines = 1) }
    }
}

@Composable
internal fun NavTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    unread: Boolean = false,
    underline: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    Box(
        modifier
            .height(38.dp)
            .hoverable(interaction)
            .clickableWithSound(label, A11yRole.Tab, selected = active, shape = SocialFieldShape, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, 14.sp, color = if (active || hovered) SocialTextPrimary else SocialTextPrimary.copy(alpha = NAV_INACTIVE_ALPHA), weight = if (isMinecraftTheme) FontWeight.Bold else FontWeight.Medium)
        if (active) {
            Box(Modifier.align(Alignment.BottomCenter).width(NAV_UNDERLINE_WIDTH).height(2.dp).clip(ppShape(1.dp)).background(OneConfigTheme.accent).then(underline))
        }
        if (unread) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
                    .size(8.dp)
                    .clip(OneConfigTheme.current.circleShape)
                    .background(OneConfigTheme.accent),
            )
        }
    }
}

@Composable
internal fun rememberModMenu(): Pair<ModIntegrationButton, ImageBitmap?>? {
    val button = if (PolyPlusMainMenuConfig.hideMainMenuModButtons) null else ModIntegrationButtons.availableModMenu()
    val logo = remember(button) { button?.let { decodeModLogo(it.logo) } }
    return button?.let { it to logo }
}

@Composable
internal fun MoreMenuPanel(surface: Modifier, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.width(MORE_MENU_WIDTH).then(surface).padding(MORE_MENU_INSET),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}
