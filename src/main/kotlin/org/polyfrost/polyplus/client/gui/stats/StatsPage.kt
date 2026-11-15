@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.stats

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.resources.language.I18n
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.PanelHeader
import org.polyfrost.oneconfig.api.ui.v1.components.ProgressBar
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyShell
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.pageTitleStyle
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.ServerIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rightColumn
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.rememberFavicon
import org.polyfrost.polyplus.client.stats.DayPlay
import org.polyfrost.polyplus.client.stats.LocalStats
import org.polyfrost.polyplus.client.stats.RankedPlace
import org.polyfrost.polyplus.client.stats.StatsReport
import org.polyfrost.polyplus.client.stats.VanillaStat
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import androidx.compose.ui.text.style.TextOverflow

private val SheetGap = 12.dp
private val IconSize = 34.dp
private val HeroHeight = 146.dp
private val WeekBarHeight = 118.dp
private val DayBarWidth = 30.dp
private const val MAX_ROWS = 20
private const val MAX_TOTALS = 8
private const val MIN_SHARE = 0.02f
private const val MIN_BAR = 0.03f
private const val PAST_DAY_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.5f

private class TotalRow(val label: String, val value: String, val detail: String? = null)

@Composable
internal fun BoxScope.StatsPage(lobby: LobbyShell, onBack: () -> Unit) {
    LaunchedEffect(lobby.profileVersion) { LocalStats.refresh() }
    val report = LocalStats.report
    PageScrim()
    val main = Modifier
        .padding(start = LobbyLayout.Margin, end = LobbyLayout.BarEnd, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
        .fillMaxSize()
    if (report == null || report.empty) {
        StatusSheet(report == null, onBack, main)
        return
    }
    Column(main, verticalArrangement = Arrangement.spacedBy(SheetGap)) {
        HeroSheet(report, onBack, Modifier.fillMaxWidth())
        PlacesSheet(lobby, report, Modifier.fillMaxWidth().weight(1f))
    }
    TotalsSheet(report, rightColumn())
}

@Composable
private fun StatusSheet(loading: Boolean, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.panel().padding(horizontal = 18.dp, vertical = 12.dp)) {
        PageHeader(onBack)
        if (loading) {
            EmptyState("Loading stats", Modifier.fillMaxWidth().weight(1f))
        } else {
            EmptyState(
                "No stats yet",
                Modifier.fillMaxWidth().weight(1f),
                "Play a world or join a server and your time shows up here. Server time is counted from now on.",
            )
        }
    }
}

@Composable
private fun PageHeader(onBack: () -> Unit) {
    PanelHeader(
        "Stats",
        titleStyle = pageTitleStyle(),
        leading = { BackButton(onBack) },
        actions = { Text("Saved only on this PC", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) },
        spacing = 8.dp,
    )
}

@Composable
private fun HeroSheet(report: StatsReport, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.panel().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(onBack)
        Row(Modifier.fillMaxWidth().height(HeroHeight), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
            TotalFigure(report, Modifier.weight(1f))
            Divider(vertical = true)
            WeekFigure(report, Modifier.width(190.dp))
            WeekBars(report.days, Modifier.width(310.dp))
        }
        SupportingFigures(report)
    }
}

@Composable
private fun TotalFigure(report: StatsReport, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Total playtime", contentPadding = PaddingValues(0.dp))
        Text(formatDuration(report.totalMillis), style = OneConfigTheme.current.typography.body.copy(fontSize = 64.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(playSplit(report), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

private fun playSplit(report: StatsReport): String = listOfNotNull(
    report.worldMillis.takeIf { it > 0L }?.let { "${formatDuration(it)} in worlds" },
    report.serverMillis.takeIf { it > 0L }?.let { "${formatDuration(it)} on servers" },
).joinToString(", ")

@Composable
private fun WeekFigure(report: StatsReport, modifier: Modifier) {
    val played = report.weekMillis > 0L
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("This week", contentPadding = PaddingValues(0.dp))
        if (played) Text(formatDuration(report.weekMillis), style = OneConfigTheme.current.typography.body.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(if (played) "Last 7 days" else "Counted from now on", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun WeekBars(days: List<DayPlay>, modifier: Modifier) {
    val peak = days.maxOf { it.millis }.coerceAtLeast(1L)
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val share = (day.millis.toFloat() / peak).coerceAtLeast(MIN_BAR)
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${day.label}: ${formatDuration(day.millis)}"
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayBar(day, share)
                Text(day.label, color = if (day.today) OneConfigTheme.current.textColor else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
    }
}

@Composable
private fun DayBar(day: DayPlay, share: Float) {
    val fill = if (day.today) OneConfigTheme.accent else OneConfigTheme.accent.copy(alpha = PAST_DAY_ALPHA)
    ProgressBar(
        share,
        Modifier.height(WeekBarHeight),
        tint = fill,
        trackColor = OneConfigTheme.current.componentBackground.copy(alpha = TRACK_ALPHA),
        thickness = DayBarWidth,
        orientation = Orientation.Vertical,
    )
}

@Composable
private fun SupportingFigures(report: StatsReport) {
    val sessions = report.sessions.toLong()
    val worlds = report.worlds.size.toLong()
    val servers = report.servers.size.toLong()
    val figures = listOfNotNull(
        sessions.takeIf { it > 0L }?.let { formatCount(it) to plural(it, "session", "sessions") },
        worlds.takeIf { it > 0L }?.let { formatCount(it) to plural(it, "world played", "worlds played") },
        servers.takeIf { it > 0L }?.let { formatCount(it) to plural(it, "server played", "servers played") },
        sessions.takeIf { it > 0L }?.let { formatDuration(report.sessionMillis / it) to "average session" },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Divider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            figures.forEach { (value, label) ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = OneConfigTheme.current.typography.body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    Text(label, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun PlacesSheet(lobby: LobbyShell, report: StatsReport, modifier: Modifier) {
    Row(modifier.panel().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlaceColumn("Most played worlds", Modifier.weight(1f)) {
            if (report.worlds.isEmpty()) {
                EmptyState("No worlds yet", Modifier.fillMaxSize(), "Play a singleplayer world and it shows up here.")
            } else {
                WorldRows(lobby, report.worlds)
            }
        }
        Divider(vertical = true)
        PlaceColumn("Most played servers", Modifier.weight(1f)) {
            if (report.servers.isEmpty()) {
                EmptyState("No servers yet", Modifier.fillMaxSize(), "Vanilla doesn't save server time, so it's counted from now on. Join a server to start.")
            } else {
                ServerRows(lobby, report.servers)
            }
        }
    }
}

@Composable
private fun PlaceColumn(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelTitle(title)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun WorldRows(lobby: LobbyShell, worlds: List<RankedPlace>) {
    val saved = lobby.worlds.saved
    val peak = worlds.first().millis
    ScrollFade(Modifier.fillMaxSize(), 4.dp) {
        worlds.take(MAX_ROWS).forEachIndexed { index, place ->
            val world = saved.firstOrNull { it.id == place.id }
            RankRow(index + 1, world?.name ?: place.name, place.millis, peak) { WorldIcon(world?.icon, IconSize) }
        }
    }
}

@Composable
private fun ServerRows(lobby: LobbyShell, servers: List<RankedPlace>) {
    val entries = lobby.servers.saved
    val revision = lobby.servers.revision
    val hideAddress = Minecraft.getInstance().options.hideServerAddress
    val unnamed = remember { Platform.i18n().getUnformattedText(I18n.get("selectServer.defaultName")) }
    val peak = servers.first().millis
    ScrollFade(Modifier.fillMaxSize(), 4.dp) {
        servers.take(MAX_ROWS).forEachIndexed { index, place ->
            val data = entries.firstOrNull { it.data.ip.equals(place.id, ignoreCase = true) }?.data
                ?: remember(place.id) { ServerData(place.name, place.id, ServerData.Type.OTHER) }
            val name = if (place.name == unnamed && !hideAddress) place.id else place.name
            RankRow(index + 1, name, place.millis, peak) { ServerIcon(data, revision, IconSize) }
        }
    }
}

@Composable
private fun WorldIcon(bytes: ByteArray?, size: Dp) {
    val icon = rememberFavicon(bytes)
    val shape = OneConfigTheme.shape(8.dp)
    val frame = Modifier.size(size).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape)
    if (icon != null) {
        Image(icon, null, frame, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
    } else {
        Box(frame, contentAlignment = Alignment.Center) {
            ThemedIcon(SOCIAL_ASSETS + "minecraft-block.svg", OneConfigTheme.current.mutedText, size / 2)
        }
    }
}

@Composable
private fun RankRow(rank: Int, name: String, millis: Long, peak: Long, icon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.CenterEnd) { Text(rank.toString(), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, Modifier.weight(1f), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Text(formatDuration(millis), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            ShareBar((millis.toFloat() / peak.coerceAtLeast(1L)).coerceIn(MIN_SHARE, 1f))
        }
    }
}

@Composable
private fun ShareBar(share: Float) {
    ProgressBar(share)
}

@Composable
private fun TotalsSheet(report: StatsReport, modifier: Modifier) {
    val rows = totalRows(report)
    if (rows.isEmpty()) {
        EmptyState("No world stats yet", modifier.panel(), "Blocks mined, mobs killed and more come from your singleplayer worlds.")
        return
    }
    Column(modifier.panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelTitle("Totals")
        ScrollFade(Modifier.fillMaxWidth().weight(1f), 0.dp) {
            rows.forEachIndexed { index, row ->
                if (index > 0) Divider()
                TotalLine(row)
            }
        }
    }
}

private fun totalRows(report: StatsReport): List<TotalRow> {
    val totals = report.totals
    fun count(label: String, value: Long, detail: String? = null) = value.takeIf { it > 0L }?.let { TotalRow(label, formatCount(it), detail) }
    fun stat(label: String, stat: VanillaStat) = count(label, totals[stat] ?: 0L)
    return listOfNotNull(
        count("Blocks mined", report.blocksMined, report.topMined?.let { "Mostly $it" }),
        count("Mobs killed", totals[VanillaStat.MobKills] ?: 0L, report.topKilled?.let { "Mostly $it" }),
        stat("Deaths", VanillaStat.Deaths),
        totals[VanillaStat.WalkedCm]?.takeIf { it > 0L }?.let { TotalRow("Distance walked", formatDistance(it)) },
        count("Items crafted", report.itemsCrafted),
        stat("Jumps", VanillaStat.Jumps),
        stat("Fish caught", VanillaStat.FishCaught),
        stat("Villager trades", VanillaStat.VillagerTrades),
        stat("Animals bred", VanillaStat.AnimalsBred),
        stat("Players killed", VanillaStat.PlayerKills),
    ).take(MAX_TOTALS)
}

@Composable
private fun TotalLine(row: TotalRow) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(row.label, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            row.detail?.let { Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        }
        Text(row.value, style = OneConfigTheme.current.typography.body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}
