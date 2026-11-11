@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSurface
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.compat.LinkedModMenus
import org.polyfrost.polyplus.compat.LinkedModMenus.Entry
import kotlin.math.roundToInt

private const val NOTICE_POLL_MILLIS = 250L
private const val MENU_WIDTH = 250
private const val FALLBACK_ICON = "link-external-01"
private val LOGO_SIZE = 18.dp
private val POPUP_GAP = 10.dp

internal val Entry.label: String
    get() = when (this) {
        Entry.Friends -> "Friends & invites"
        Entry.Wardrobe -> "Emotes & outfits"
        Entry.Screenshots -> "Screenshots"
        Entry.Host -> "Invite to a world"
        Entry.FriendsWorlds -> "Friends' worlds"
        Entry.Accounts -> "Saved accounts"
        Entry.Settings -> "Settings"
    }

internal val Entry.icon: String
    get() = SOCIAL_ASSETS + when (this) {
        Entry.Friends -> "users-01.svg"
        Entry.Wardrobe -> "diamond-01.svg"
        Entry.Screenshots -> "image-01.svg"
        Entry.Host -> "user-plus-01.svg"
        Entry.FriendsWorlds -> "globe-01.svg"
        Entry.Accounts -> "log-in-04.svg"
        Entry.Settings -> "settings-02.svg"
    }

internal const val LINKED_MOD_CAPTION = "More from your mods"

@Composable
internal fun LinkedModNotices(screen: Screen, waiting: Boolean) {
    LaunchedEffect(waiting) {
        if (waiting || LinkedModMenus.entries.isEmpty()) return@LaunchedEffect
        while (LobbyScene.overlayAlpha < 1f && LobbyScene.phase != LobbyScene.Phase.FAILED) delay(NOTICE_POLL_MILLIS)
        LinkedModMenus.showStartupNotices(screen)
    }
}

@Composable
internal fun LinkedModButton(modifier: Modifier) {
    val entries = LinkedModMenus.entries
    if (entries.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val lift = with(LocalDensity.current) { -(LobbyLayout.BarHeight + POPUP_GAP).roundToPx() }
    val (interaction, hovered) = rememberSocialHover()
    Box(
        modifier
            .height(LobbyLayout.BarHeight)
            .panel()
            .border(1.dp, if (hovered) SocialHoverBorder else Color.Transparent, ppShape(20.dp))
            .hoverable(interaction)
            .clickableWithSound(LinkedModMenus.displayName ?: LINKED_MOD_CAPTION, shape = ppShape(20.dp)) { open = !open }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkedModLogo()
            Label(LinkedModMenus.displayName ?: LINKED_MOD_CAPTION, 14.sp)
        }
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(0, lift),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                LobbyUserTheme { LinkedModMenu(entries) { open = false } }
            }
        }
    }
}

@Composable
private fun LinkedModMenu(entries: List<Entry>, onClose: () -> Unit) {
    MenuSurface(Modifier.width(MENU_WIDTH.dp)) {
        entries.forEach { entry ->
            MenuItem(entry.label, themedIcon(entry.icon), null, MenuItemSize.Regular, false, false, true, null, Modifier, null) {
                onClose()
                LinkedModMenus.open(entry)
            }
        }
    }
}

@Composable
private fun LinkedModLogo() {
    val logo = remember { decodeModLogo(LinkedModMenus.logo) }
    if (logo == null) {
        LobbyIcon(FALLBACK_ICON, size = LOGO_SIZE)
        return
    }
    ModLogo(logo, LOGO_SIZE, SocialTextPrimary)
}

internal fun decodeModLogo(bytes: ByteArray?): ImageBitmap? =
    bytes?.let { runCatching { PolyBitmap.decode(it)?.asComposeImageBitmap() }.getOrNull() }

@Composable
internal fun ModLogo(logo: ImageBitmap, size: Dp, tint: Color? = null) {
    val snapped = with(LocalDensity.current) {
        val cell = (size.toPx() / logo.width).roundToInt().coerceAtLeast(1)
        (cell * logo.width).toDp()
    }
    Image(
        logo,
        null,
        Modifier.size(snapped),
        colorFilter = tint?.let { ColorFilter.tint(it) },
        filterQuality = FilterQuality.None,
    )
}
