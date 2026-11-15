@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.compat.LinkedModMenus
import org.polyfrost.polyplus.compat.LinkedModMenus.Entry
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private const val NOTICE_POLL_MILLIS = 250L
private const val MENU_WIDTH = 250
private const val FALLBACK_ICON = "link-external-01"
private val LOGO_SIZE = 18.dp

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
    val hover = rememberHoverState()
    Box(
        modifier
            .height(LobbyLayout.BarHeight)
            .panel()
            .border(1.dp, if (hover.hovered) SocialHoverBorder else Color.Transparent, OneConfigTheme.shape(20.dp))
            .hoverable(hover.interactionSource)
            .oneConfigClickable(LinkedModMenus.displayName ?: LINKED_MOD_CAPTION, { open = !open }, shape = OneConfigTheme.shape(20.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkedModLogo()
            Text(LinkedModMenus.displayName ?: LINKED_MOD_CAPTION, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        if (open) LobbyUserTheme { LinkedModMenu(entries) { open = false } }
    }
}

@Composable
private fun LinkedModMenu(entries: List<Entry>, onClose: () -> Unit) {
    val items = entries.map { entry ->
        MenuAction(entry.label, { LinkedModMenus.open(entry) }, icon = entry.icon, size = MenuItemSize.Regular)
    }
    DropdownMenu(true, onClose, items, Modifier.width(MENU_WIDTH.dp), Placement.AboveStart, gap = 10.dp, flip = false)
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
