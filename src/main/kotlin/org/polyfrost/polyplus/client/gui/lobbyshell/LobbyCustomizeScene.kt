@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.lobby.LobbyLook
import org.polyfrost.polyplus.client.lobby.LobbyPacks
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.lobby.SceneMode
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyScene

private const val TILE_COLUMNS = 3
private const val OWN_LIMIT = 6
private val TileHeight = 84.dp
private val TileGap = 8.dp
private val TileRadius = 11.dp
private val TileLabelInset = 8.dp
private val TileText = Color.White
private val TileSubText = Color.White.copy(alpha = 0.82f)
private val TileBorder = Color.White.copy(alpha = 0.16f)
private val TileChip = Color.Black.copy(alpha = 0.42f)
private val TileScrim = Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.68f))
private val TileTextShadow = Shadow(Color(0x8C000000), Offset(0f, 1f), 4f)
private val ServerIconSize = 28.dp
private val ServerSelectWidth = 150.dp

private fun modeLabel(mode: SceneMode): String = when (mode) {
    SceneMode.AUTOMATIC -> "Automatic"
    SceneMode.PICK -> "Pick"
    SceneMode.FOLLOW_SERVER -> "Follow server"
}

@Composable
internal fun SceneSection(lobby: LobbyShell, state: CustomizeState) {
    val party by PartyRepository.party.collectAsState()
    val following = PartyScene.isMember(party) && !PartyScene.keepOwnBackdrop
    val mode = LobbySceneStore.mode
    val pinned = LobbySceneStore.pinned
    val revision = LobbyPacks.revision
    val servers = lobby.servers.recent
    val serverCount = servers.count { LobbySceneStore.serverScene(it.data.ip) != null }
    Segmented(
        SceneMode.entries,
        mode,
        {
            LobbySceneStore.mode = it
            LobbyScene.reload()
        },
        label = ::modeLabel,
        enabled = !following,
    )
    PanelHint(modeHint(mode, pinned, following))
    key(revision) {
        val selected = if (mode == SceneMode.PICK) pinned else null
        SceneGroupLabel("Your scenes")
        val own = ownTiles(selected)
        val bundled = LobbySceneStore.bundledScenes
        if (own.isEmpty()) PanelHint("Spots you capture show up here") else SceneTileGrid(own, selected, !following)
        SceneGroupLabel("Polyfrost scenes")
        SceneTileGrid(bundled, selected, !following)
    }
    ViewRows()
    DrillRow("image-01", "See all", plural(LobbySceneStore.captures.size + LobbySceneStore.bundledScenes.size, "scene"), { state.open(CustomizePage.AllScenes) })
    DrillRow("globe-01", "Server scenes", if (serverCount == 0) "None set" else "$serverCount set", { state.open(CustomizePage.ServerScenes) })
    DrillRow("video-recorder", "Capture", if (LobbySceneStore.captureEnabled) "On" else "Off", { state.open(CustomizePage.Capture) })
    PanelHint("Capture spots from the pause menu")
    ResetRow(
        {
            LobbySceneStore.resetChoice()
            LobbyScene.reload()
        },
        enabled = mode != SceneMode.AUTOMATIC || pinned != null || serverCount > 0,
    )
}

@Composable
private fun ViewRows() {
    val current = LobbyScene.currentId ?: return
    var saved by remember(current) { mutableStateOf(LobbySceneStore.view(current) != null) }
    SceneGroupLabel("Camera angle")
    DrillRow(
        "video-recorder",
        if (saved) "Update saved angle" else "Save this angle",
        if (saved) "Saved" else null,
        {
            LobbySceneStore.setView(current, LobbyLook.current())
            saved = true
        },
    )
    if (saved) {
        ResetRow(
            {
                LobbySceneStore.setView(current, null)
                saved = false
                LobbyLook.reset()
            },
            label = "Use the scene's own angle",
        )
    }
    PanelHint("Drag and scroll the lobby to aim the camera. The lobby always opens on the saved angle; double click goes back to it.")
}

private fun modeHint(mode: SceneMode, pinned: String?, following: Boolean): String = when {
    following -> "Your party leader picks the scene right now"
    mode == SceneMode.AUTOMATIC -> "Shows your best recent spot"
    mode == SceneMode.PICK -> sceneLabel(pinned)?.let { "Always shows $it" } ?: "Pick a scene below"
    else -> LobbySceneStore.lastWorld?.let { "Following ${worldLabel(it)}" } ?: "Follows the last server you played"
}

private fun sceneLabel(id: String?): String? =
    id?.let { want -> (LobbySceneStore.captures + LobbySceneStore.bundledScenes).firstOrNull { it.id == want }?.label }

private fun worldLabel(world: String): String {
    PolyPlusRecentServers.recent().firstOrNull { LobbySceneStore.worldKey(it.ip) == world }
        ?.let { return PolyPlusRecentServers.listLabel(it.name, it.ip) }
    LobbySceneStore.captures.firstOrNull { it.world == world }?.let { return LobbySceneStore.worldName(it) }
    return world.substringAfter(':')
}

private fun ownTiles(pinned: String?): List<LobbySceneEntry> {
    val usable = LobbySceneStore.usableScenes.sortedByDescending { it.capturedAt }
    val ordered = (usable.filter { it.favourite } + usable.filterNot { it.favourite }).distinctBy { it.id }
    val shown = ordered.take(OWN_LIMIT)
    if (pinned == null || shown.any { it.id == pinned }) return shown
    val chosen = ordered.firstOrNull { it.id == pinned } ?: return shown
    return shown.take(OWN_LIMIT - 1) + chosen
}

@Composable
private fun SceneGroupLabel(text: String) {
    val theme = OneConfigTheme.current
    Text(
        text,
        Modifier.fillMaxWidth().padding(top = 4.dp),
        color = theme.textColorSecondary,
        style = theme.typography.small.copy(fontWeight = FontWeight.Medium),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@Composable
internal fun SceneTileGrid(
    entries: List<LobbySceneEntry>,
    selected: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TileGap)) {
        entries.chunked(TILE_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                row.forEach { entry ->
                    SceneTile(entry, entry.id == selected, Modifier.weight(1f), enabled) { pickScene(entry.id) }
                }
                repeat(TILE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun SceneTile(
    entry: LobbySceneEntry,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = { pickScene(entry.id) },
) {
    val shape = OneConfigTheme.shape(TileRadius)
    val line = sceneTileLine(entry)
    val hover = rememberHoverState()
    val accent = OneConfigTheme.accent
    val border = when {
        selected -> accent
        hover.hovered && enabled -> SocialHoverBorder
        else -> TileBorder
    }
    val typography = OneConfigTheme.current.typography
    Box(
        modifier
            .height(TileHeight)
            .alpha(if (enabled) 1f else OneConfigTheme.current.disabledAlpha)
            .clip(shape)
            .oneConfigClickable("${entry.label}, $line", onClick, A11yRole.RadioButton, enabled = enabled, selected = selected, shape = shape, interactionSource = hover.interactionSource),
    ) {
        SceneMoodBackdrop(entry, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(TileScrim))
        if (entry.favourite) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { FavouriteStar() }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) { LobbyIcon("check", TileText, 11.dp) }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = TileLabelInset, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                entry.label,
                color = TileText,
                style = typography.label.copy(fontWeight = FontWeight.SemiBold, shadow = TileTextShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tilePlace(entry),
                    Modifier.weight(1f),
                    color = TileSubText,
                    style = typography.caption.copy(shadow = TileTextShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                if (!entry.bundled) shortAge(entry.capturedAt)?.let { AgeChip(it) }
            }
        }
        Box(Modifier.matchParentSize().border(if (selected) 2.dp else 1.dp, border, shape))
    }
}

@Composable
internal fun AllScenesPage(lobby: LobbyShell, state: CustomizeState) {
    val browser = remember { SceneBrowserState() }
    SceneBrowser(
        browser,
        Modifier.fillMaxSize().padding(start = CustomizeKit.Inset, end = CustomizeKit.Inset, top = 4.dp, bottom = CustomizeKit.Inset),
    )
}

@Composable
internal fun ServerScenesPage(lobby: LobbyShell, state: CustomizeState) {
    val servers = lobby.servers.recent
    val revision = LobbyPacks.revision
    PanelPageColumn {
        PanelSection(null, divider = false) {
            PanelHint("In Follow server mode, the lobby shows the scene set for the last server you played. Servers on Automatic use your newest capture from there.")
        }
        PanelSection("Recent servers") {
            if (servers.isEmpty()) {
                PanelHint("Servers you join show up here.")
            } else {
                val options = remember(revision) { listOf<String?>(null) + (LobbySceneStore.usableScenes + LobbySceneStore.bundledScenes).map { it.id }.distinct() }
                servers.forEach { entry -> key(entry.data.ip) { ServerSceneRow(lobby, entry, options) } }
            }
        }
        val anySet = servers.any { LobbySceneStore.serverScene(it.data.ip) != null }
        PanelSection(null) {
            ResetRow(
                {
                    servers.forEach { LobbySceneStore.setServerScene(it.data.ip, null) }
                    if (LobbySceneStore.mode == SceneMode.FOLLOW_SERVER) LobbyScene.reload()
                },
                enabled = anySet,
            )
        }
    }
}

@Composable
private fun ServerSceneRow(lobby: LobbyShell, entry: ServerEntry, options: List<String?>) {
    val theme = OneConfigTheme.current
    val data = entry.data
    val current = LobbySceneStore.serverScene(data.ip)
    Row(
        Modifier.fillMaxWidth().height(CustomizeKit.RowHeight).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ServerIcon(data, lobby.servers.revision, ServerIconSize)
        Text(data.shownName, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Select(
            current,
            options,
            { id ->
                LobbySceneStore.setServerScene(data.ip, id)
                if (LobbySceneStore.mode == SceneMode.FOLLOW_SERVER && LobbySceneStore.lastWorld == LobbySceneStore.worldKey(data.ip)) LobbyScene.reload()
            },
            Modifier.width(ServerSelectWidth),
            label = { id -> if (id == null) "Automatic" else sceneLabel(id) ?: "Missing scene" },
            contentDescription = "Scene for ${data.shownName}",
        )
    }
}

private fun tilePlace(entry: LobbySceneEntry): String =
    scenePlace(entry).ifBlank { if (entry.bundled) "Polyfrost" else LobbySceneStore.worldName(entry) }

@Composable
private fun AgeChip(text: String) {
    Box(
        Modifier
            .clip(OneConfigTheme.shape(4.dp))
            .background(TileChip)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text,
            color = TileText,
            style = OneConfigTheme.current.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            softWrap = false,
        )
    }
}
