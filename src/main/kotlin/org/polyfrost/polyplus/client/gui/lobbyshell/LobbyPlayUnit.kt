@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.ContextItem
import org.polyfrost.polyplus.client.gui.ContextMenuHost
import org.polyfrost.polyplus.client.gui.HostWorldFlow
import org.polyfrost.polyplus.client.gui.HoverTooltip
import org.polyfrost.polyplus.client.gui.LIGHT_TEXT_LIMIT
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialControlBackground
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.TooltipPlacement
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.contextMenu
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberFavicon
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.DISABLED_ALPHA
import org.polyfrost.polyplus.client.gui.replays.ReplayLibrary
import org.polyfrost.polyplus.client.gui.replays.ReplayThumb
import org.polyfrost.polyplus.client.gui.replays.problem
import org.polyfrost.polyplus.client.gui.replays.replayMenuItems
import org.polyfrost.polyplus.client.gui.replays.summaryText
import org.polyfrost.polyplus.compat.replay.ReplayEntry
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.Collections
import kotlin.math.PI
import kotlin.math.sin

private val PanelGutter = 12.dp
private val HeroIconSize = 96.dp
private val HeroWashHeight = 250.dp
private val SourceHeight = 34.dp
private val TileSize = 36.dp
private val FooterHeight = 56.dp
private val HostingHeight = 34.dp
private val HostTileWidth = 84.dp
private val ScrimHeight = 300.dp

private const val WASH_ALPHA = 0.9f
private const val WASH_TINT_ALPHA = 0.35f
private const val WASH_MID_STOP = 0.45f
private const val WASH_MID_ALPHA = 0.5f
private const val SCRIM_ALPHA = 0.5f
private const val SCRIM_STEPS = 12
private const val HERO_DETAIL_ALPHA = 0.82f
private const val EMPTY_ICON_SCALE = 0.42f
private const val BACKDROP_SIZE = 192
private const val BACKDROP_RADIUS = 12
private const val BACKDROP_PASSES = 3
private const val BACKDROP_CACHE_ENTRIES = 16
private const val RGBA_CHANNELS = 4
private const val ALPHA_CHANNEL = 3
private const val CHANNEL_MAX = 255

internal fun hostWorldAllowed(): Boolean =
    !PolyPlusMainMenuConfig.hideMainMenuHostWorld && PrivacyConsent.allowsOnlineServices()

@Composable
internal fun PlayUnit(lobby: LobbyShell, modifier: Modifier) {
    val source = lobby.source.takeIf { it.shown } ?: PlaySource.Multiplayer
    val singleplayer = source == PlaySource.Singleplayer
    val quick = !PolyPlusMainMenuConfig.hideMainMenuQuickplay
    val target: Any? = when (source) {
        PlaySource.Singleplayer -> lobby.activeWorld()
        PlaySource.Multiplayer -> lobby.activeServer()
        PlaySource.Replays -> lobby.activeReplay()
    }
    if (source == PlaySource.Replays) LaunchedEffect(Unit) { ReplayLibrary.reload() }
    Box((if (quick) modifier.height(LobbyLayout.UnitHeight) else modifier).panel()) {
        ContextMenuHost {
            HeroWash(lobby, target)
            Column(Modifier.fillMaxWidth().then(if (quick) Modifier.fillMaxSize() else Modifier)) {
                SourceSwitch(lobby, Modifier.padding(start = PanelGutter, end = PanelGutter, top = PanelGutter))
                Box(
                    Modifier.fillMaxWidth().then(if (quick) Modifier.weight(1f) else Modifier.padding(vertical = 16.dp)).padding(horizontal = PanelGutter),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Crossfade(target) { shown ->
                        when (shown) {
                            is ServerData -> ServerHero(lobby, shown)
                            is LobbyWorld -> WorldHero(shown)
                            is ReplayEntry -> ReplayHero(shown)
                            else -> EmptyHero(lobby, source)
                        }
                    }
                }
                if (source == PlaySource.Replays) WatchRow(lobby) else PlayRow(lobby, singleplayer)
                HostingStrip(lobby)
                Spacer(Modifier.height(PanelGutter))
                Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
                Footer(lobby, source, quick)
            }
        }
    }
}

@Composable
private fun BoxScope.HeroWash(lobby: LobbyShell, target: Any?) {
    @Suppress("UNUSED_EXPRESSION") lobby.servers.revision
    Box(Modifier.matchParentSize()) {
        Crossfade(target, Modifier.fillMaxWidth().height(HeroWashHeight)) { shown ->
            when (shown) {
                is ServerData -> WashImage(rememberBackdrop(shown.iconBytes, fallbackArt(shown.ip)))
                is LobbyWorld -> WashImage(rememberBackdrop(shown.icon, fallbackArt("")))
                is ReplayEntry -> WashImage(rememberBackdrop(shown.icon, fallbackArt("")))
                else -> WashImage(null)
            }
        }
        val scrim = if (SocialTextPrimary.luminance() > LIGHT_TEXT_LIMIT) Color.Black else Color.White
        Box(Modifier.fillMaxWidth().height(ScrimHeight).background(Brush.verticalGradient(*scrimStops(scrim))))
    }
}

private fun scrimStops(color: Color): Array<Pair<Float, Color>> = Array(SCRIM_STEPS + 1) { step ->
    val position = step / SCRIM_STEPS.toFloat()
    val bell = sin(position * PI.toFloat())
    position to color.copy(alpha = SCRIM_ALPHA * bell * bell)
}

@Composable
private fun WashImage(image: ImageBitmap?) {
    val accent = OneConfigTheme.accent
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, WASH_MID_STOP to Color.Black.copy(alpha = WASH_MID_ALPHA), 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            .background(accent.copy(alpha = WASH_TINT_ALPHA)),
    ) {
        if (image != null) {
            Image(
                image,
                null,
                Modifier.fillMaxSize().alpha(WASH_ALPHA),
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
        }
    }
}

private val BackdropCache: MutableMap<Int, ImageBitmap> = Collections.synchronizedMap(
    object : LinkedHashMap<Int, ImageBitmap>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?): Boolean = size > BACKDROP_CACHE_ENTRIES
    },
)

private fun fallbackArt(address: String): String =
    SOCIAL_ASSETS + if (address.contains("hypixel", true)) "hypixel.png" else "server.png"

@Composable
private fun rememberBackdrop(bytes: ByteArray?, fallback: String): ImageBitmap? {
    val key = bytes?.contentHashCode() ?: fallback.hashCode()
    val backdrop by produceState(BackdropCache[key], key) {
        value = withContext(Dispatchers.IO) { backdropFor(key, bytes, fallback) }
    }
    return backdrop
}

private fun backdropFor(key: Int, bytes: ByteArray?, fallback: String): ImageBitmap? {
    BackdropCache[key]?.let { return it }
    val bitmap = runCatching { bytes?.let(PolyBitmap::decode) ?: PolyBitmap.fromResource(fallback) }.getOrNull() ?: return null
    return blurred(bitmap).asComposeImageBitmap().also { BackdropCache[key] = it }
}

private fun blurred(source: PolyBitmap): PolyBitmap {
    val size = BACKDROP_SIZE
    val channels = Array(RGBA_CHANNELS) { IntArray(size * size) }
    for (y in 0 until size) {
        for (x in 0 until size) {
            val from = (y * source.height / size * source.width + x * source.width / size) * RGBA_CHANNELS
            val alpha = source.pixels[from + ALPHA_CHANNEL].toInt() and CHANNEL_MAX
            for (channel in 0 until ALPHA_CHANNEL) {
                channels[channel][y * size + x] = (source.pixels[from + channel].toInt() and CHANNEL_MAX) * alpha / CHANNEL_MAX
            }
            channels[ALPHA_CHANNEL][y * size + x] = alpha
        }
    }
    val soft = channels.map(::softened)
    val pixels = ByteArray(size * size * RGBA_CHANNELS)
    for (i in 0 until size * size) {
        val alpha = soft[ALPHA_CHANNEL][i]
        for (channel in 0 until ALPHA_CHANNEL) {
            pixels[i * RGBA_CHANNELS + channel] = (soft[channel][i] * CHANNEL_MAX / alpha.coerceAtLeast(1)).coerceAtMost(CHANNEL_MAX).toByte()
        }
        pixels[i * RGBA_CHANNELS + ALPHA_CHANNEL] = alpha.toByte()
    }
    return PolyBitmap(size, size, pixels)
}

private fun softened(channel: IntArray): IntArray {
    var current = channel
    var spare = IntArray(channel.size)
    repeat(BACKDROP_PASSES * 2) {
        boxBlurTransposed(current, spare)
        current = spare.also { spare = current }
    }
    return current
}

private fun boxBlurTransposed(from: IntArray, to: IntArray) {
    val size = BACKDROP_SIZE
    val last = size - 1
    val window = BACKDROP_RADIUS * 2 + 1
    for (line in 0 until size) {
        val start = line * size
        var sum = (-BACKDROP_RADIUS..BACKDROP_RADIUS).sumOf { from[start + it.coerceIn(0, last)] }
        for (i in 0 until size) {
            to[i * size + line] = sum / window
            sum += from[start + (i + BACKDROP_RADIUS + 1).coerceAtMost(last)] - from[start + (i - BACKDROP_RADIUS).coerceAtLeast(0)]
        }
    }
}

@Composable
private fun SourceSwitch(lobby: LobbyShell, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(SourceHeight)
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .border(1.dp, SocialBorderColor, SocialFieldShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PlaySource.entries.filter { it.shown }.forEach { entry ->
            SourceTab(entry.name, entry == lobby.source, Modifier.weight(1f)) { lobby.chooseSource(entry) }
        }
    }
}

private fun LobbyShell.chooseSource(entry: PlaySource) {
    source = entry
    if (PlaySource.entries.any { it.page == page }) show(entry.page)
}

@Composable
private fun SourceTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val shape = ppShape(6.dp)
    val fill by animateColorAsState(if (selected) OneConfigTheme.accent else Color.Transparent)
    val ink by animateColorAsState(
        when {
            selected -> OneConfigTheme.current.accentTextColor
            hovered -> SocialTextPrimary
            else -> MutedText
        },
    )
    Box(
        modifier.fillMaxSize().clip(shape).background(fill).hoverable(interaction).clickableWithSound(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, 14.sp, color = ink, weight = FontWeight.Medium)
    }
}

@Composable
private fun HeroLayout(icon: @Composable () -> Unit, text: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(if (OneConfigTheme.current.shadowEnabled) Modifier.shadow(10.dp, ppShape(12.dp), ambientColor = OneConfigTheme.current.shadowColor, spotColor = OneConfigTheme.current.shadowColor) else Modifier) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = text)
    }
}

@Composable
private fun HeroName(name: String) {
    SocialText(name, 22.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun HeroDetail(text: String, color: Color = SocialTextPrimary.copy(alpha = HERO_DETAIL_ALPHA)) {
    SocialText(text, 12.sp, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ServerHero(lobby: LobbyShell, data: ServerData) {
    val servers = lobby.servers
    val revision = servers.revision
    LaunchedEffect(data) { servers.ping(data) }
    HeroLayout({ ServerIcon(data, revision, HeroIconSize) }) {
        HeroName(data.name)
        ServerStatus(data, revision)
    }
}

@Composable
private fun ServerStatus(data: ServerData, revision: Int) {
    @Suppress("UNUSED_EXPRESSION") revision
    when {
        LobbyServers.failed(data) -> StatusLine(SocialDangerColor, "Offline", SocialDangerText)
        data.state() == ServerData.State.INCOMPATIBLE -> StatusLine(SocialDangerColor, "Incompatible, ${Platform.i18n().getUnformattedText(data.version)}")
        data.state() == ServerData.State.SUCCESSFUL -> StatusLine(
            SocialSuccessColor,
            data.players?.let { "%,d online".format(it.online()) } ?: "Online",
            SocialTextPrimary,
            trailing = "${data.ping} ms",
        )
        else -> StatusLine(MutedText, "Checking status...")
    }
}

@Composable
private fun StatusLine(dot: Color, text: String, color: Color = SocialTextPrimary.copy(alpha = HERO_DETAIL_ALPHA), trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(OneConfigTheme.current.circleShape).background(dot))
        Box(Modifier.weight(1f, fill = false)) { HeroDetail(text, color) }
        trailing?.let { HeroDetail(it) }
    }
}

@Composable
private fun WorldHero(world: LobbyWorld) {
    HeroLayout({ WorldIcon(world, HeroIconSize) }) {
        HeroName(world.name)
        HeroDetail(world.detail)
        if (!world.playable) HeroDetail("Can't be opened", SocialDangerText)
    }
}

@Composable
private fun ReplayHero(entry: ReplayEntry) {
    HeroLayout({ ReplayThumb(entry, HeroIconSize, HeroIconSize) }) {
        HeroName(entry.name)
        entry.world?.takeIf { it.isNotBlank() }?.let { HeroDetail(it) }
        HeroDetail(entry.summaryText)
        if (!entry.playable) HeroDetail(entry.problem, SocialDangerText)
    }
}

@Composable
private fun EmptyHero(lobby: LobbyShell, source: PlaySource) {
    val singleplayer = source == PlaySource.Singleplayer
    val title = when {
        source == PlaySource.Replays -> if (ReplayLibrary.loaded) "No replays yet" else "Loading replays..."
        !singleplayer -> "No servers yet"
        !lobby.worlds.loaded -> "Loading worlds..."
        lobby.worlds.saved.isEmpty() -> "No worlds yet"
        else -> "No playable worlds"
    }
    val detail = when (source) {
        PlaySource.Replays -> "Start recording from the pause menu"
        PlaySource.Singleplayer -> "Create one to start playing"
        PlaySource.Multiplayer -> "Add one to start playing"
    }
    val icon = when (source) {
        PlaySource.Replays -> "video-recorder"
        PlaySource.Singleplayer -> "minecraft-block"
        PlaySource.Multiplayer -> "globe-01"
    }
    HeroLayout({ EmptyIcon(icon) }) {
        HeroName(title)
        HeroDetail(detail)
    }
}

@Composable
private fun EmptyIcon(name: String) {
    val shape = ppShape(12.dp)
    Box(
        Modifier.size(HeroIconSize).clip(shape).background(SocialControlBackground).border(1.dp, SocialBorderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        PlayIcon(name, MutedText, HeroIconSize * EMPTY_ICON_SCALE)
    }
}

@Composable
private fun PlayRow(lobby: LobbyShell, singleplayer: Boolean) {
    val session by P2PSessionManager.currentSessionIdFlow.collectAsState()
    val world = lobby.activeWorld()?.takeIf { it.playable }
    val ready = if (singleplayer) world != null else lobby.activeServer() != null
    val label = when {
        ready -> "Play"
        singleplayer -> "Choose a world"
        else -> "Choose a server"
    }
    val together = ready && canPlayTogether()
    val hosted = leaderHostedWorld()
    val names by PlayerNamesRepository.names.collectAsState()
    Row(Modifier.padding(horizontal = PanelGutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hosted != null) {
            val leader = names[hosted.leader] ?: hosted.name
            PlayButton(Modifier.weight(1f), hosted.status ?: "Join $leader", "$leader is hosting a world", onClick = LeaderFollow::joinHosted)
            if (ready) HostTile(if (singleplayer) "minecraft-block" else "globe-01", "Play", lobby::play)
        } else {
            PlayButton(Modifier.weight(1f), label, null, zoom = ready, onClick = lobby::play)
            if (together) {
                HostTile("users-01", "Play together") { startPlayTogether(if (singleplayer) world else lobby.activeServer()) }
            } else if (singleplayer && world != null && hostable(world, session)) {
                HostTile("log-in-04", "Host world") { lobby.host(world.id) }
            }
        }
    }
}

@Composable
private fun WatchRow(lobby: LobbyShell) {
    val entry = lobby.activeReplay()
    val blocked = entry != null && !entry.playable
    val (interaction, hovered) = rememberSocialHover()
    Row(Modifier.padding(horizontal = PanelGutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).hoverable(interaction)) {
            PlayButton(
                Modifier.fillMaxWidth().graphicsLayer { alpha = if (blocked) DISABLED_ALPHA else 1f },
                if (entry == null) "Choose a replay" else "Watch",
                null,
                zoom = entry != null && !blocked,
            ) { if (!blocked) lobby.play() }
            if (blocked && hovered && entry != null) HoverTooltip(entry.problem, TooltipPlacement.ABOVE_CENTER)
        }
    }
}

private fun hostable(world: LobbyWorld, session: String?): Boolean = world.playable && session == null && hostWorldAllowed()

private fun worldMenuItems(lobby: LobbyShell, world: LobbyWorld): List<ContextItem> = listOfNotNull(
    ContextItem("Play", SOCIAL_ASSETS + "minecraft-block.svg", enabled = world.playable) { lobby.enter(world) },
    ContextItem("Host world", SOCIAL_ASSETS + "log-in-04.svg") { lobby.host(world.id) }.takeIf { hostable(world, P2PSessionManager.currentSessionId) },
)

@Composable
private fun HostTile(icon: String, label: String, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Column(
        Modifier
            .width(HostTileWidth)
            .height(LobbyLayout.PlayHeight)
            .cardFrame(false, hovered)
            .hoverable(interaction)
            .clickableWithSound(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        PlayIcon(icon, SocialTextPrimary, 18.dp)
        Label(label, 12.sp)
    }
}

@Composable
private fun HostingStrip(lobby: LobbyShell) {
    val session by P2PSessionManager.currentSessionIdFlow.collectAsState()
    if (session == null) return
    val (stopInteraction, stopHovered) = rememberSocialHover()
    Row(
        Modifier
            .padding(start = PanelGutter, end = PanelGutter, top = 8.dp)
            .fillMaxWidth()
            .height(HostingHeight)
            .card(8.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(OneConfigTheme.current.circleShape).background(SocialSuccessColor))
        Label("Hosting", 14.sp, weight = FontWeight.Medium)
        Label(lobby.hostedWorldName.orEmpty(), 12.sp, Modifier.weight(1f), color = MutedText)
        Box(
            Modifier.height(26.dp).rowFrame(false, stopHovered, ppShape(6.dp)).hoverable(stopInteraction).clickableWithSound(lobby::stopHosting).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Label("Stop", 12.sp, color = SocialDangerText, weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Footer(lobby: LobbyShell, source: PlaySource, quick: Boolean) {
    val page = source.page
    val servers = lobby.servers
    val worlds = if (quick && source == PlaySource.Singleplayer) lobby.quickWorlds() else emptyList()
    val rows = if (quick && source == PlaySource.Multiplayer) lobby.quickServers() else emptyList()
    val replays = if (quick && source == PlaySource.Replays) lobby.quickReplays() else emptyList()
    val saved = when (source) {
        PlaySource.Singleplayer -> lobby.worlds.saved.size
        PlaySource.Multiplayer -> servers.saved.size
        PlaySource.Replays -> ReplayLibrary.entries.size
    }
    Row(
        Modifier.fillMaxWidth().height(FooterHeight).padding(horizontal = PanelGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val listed = worlds.isNotEmpty() || rows.isNotEmpty() || replays.isNotEmpty()
        if (!listed) {
            Label("$saved saved", 12.sp, Modifier.weight(1f), color = MutedText)
        } else {
            Label("Recent", 12.sp, Modifier.padding(end = 4.dp), color = MutedText)
            worlds.forEach { world ->
                RecentTile(world.name, lobby.isSelected(world), { lobby.select(world) }, { worldMenuItems(lobby, world) }) { WorldIcon(world, TileSize) }
            }
            rows.forEach { entry ->
                LaunchedEffect(entry.data) { servers.ping(entry.data) }
                RecentTile(entry.data.name, lobby.isSelected(entry.data), { lobby.select(entry.data) }, { serverMenuItems(lobby, entry) }) { ServerIcon(entry.data, servers.revision, TileSize) }
            }
            replays.forEach { entry ->
                RecentTile(entry.name, lobby.isSelected(entry), { lobby.select(entry) }, { replayMenuItems(entry, lobby::watch) }) { ReplayThumb(entry, TileSize, TileSize) }
            }
            Spacer(Modifier.weight(1f))
        }
        when {
            lobby.page == page -> if (listed) Label("$saved saved", 12.sp, color = MutedText)
            listed -> AllButton("All", "$saved") { lobby.show(page) }
            source == PlaySource.Singleplayer -> AllButton("All worlds", null) { lobby.show(page) }
            source == PlaySource.Replays -> AllButton("All replays", null) { lobby.show(page) }
            else -> AllButton("All servers", null) { lobby.show(page) }
        }
    }
}

@Composable
private fun RecentTile(name: String, selected: Boolean, onClick: () -> Unit, menu: () -> List<ContextItem>, icon: @Composable () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val ring by animateColorAsState(
        when {
            selected -> OneConfigTheme.accent
            hovered -> SocialHoverBorder
            else -> Color.Transparent
        },
    )
    Box(Modifier.size(TileSize).contextMenu(menu).hoverable(interaction).clickableWithSound(onClick)) {
        icon()
        Box(Modifier.matchParentSize().border(1.dp, ring, SocialFieldShape))
        if (hovered) HoverTooltip(name, TooltipPlacement.ABOVE_CENTER)
    }
}

@Composable
private fun AllButton(label: String, count: String?, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val border by animateColorAsState(if (hovered) SocialHoverBorder else SocialBorderColor)
    Row(
        Modifier
            .height(TileSize)
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .border(1.dp, border, SocialFieldShape)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label(label, 14.sp, weight = FontWeight.Medium)
        count?.let { Label(it, 12.sp, color = MutedText) }
        ThemedIcon("right-arrow", SocialTextPrimary, 14.dp)
    }
}

@Composable
private fun PlayIcon(name: String, tint: Color, size: Dp) {
    ThemedIcon("$SOCIAL_ASSETS$name.svg", tint, size)
}

@Composable
internal fun HostWorldDialog(worldId: String, screen: Screen, onDismiss: () -> Unit) {
    val friends by FriendsRepository.friends.collectAsState()
    val groups by GroupsRepository.groups.collectAsState()
    LaunchedEffect(Unit) {
        if (PrivacyConsent.allowsOnlineServices()) {
            FriendsRepository.refreshAll()
            GroupsRepository.refreshGroups()
        }
    }
    HostWorldFlow(screen, friends, groups, ChatIdentity.selfId(), worldId = worldId, onDismiss = onDismiss)
}

@Composable
private fun WorldIcon(world: LobbyWorld, size: Dp) {
    val shape = ppShape(if (size > 48.dp) 12.dp else 8.dp)
    val icon = rememberFavicon(world.icon)
    Box(Modifier.size(size).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, null, Modifier.size(size), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        } else {
            PlayIcon("minecraft-block", MutedText, size * EMPTY_ICON_SCALE)
        }
    }
}
