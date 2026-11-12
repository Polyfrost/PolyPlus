@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 30L
private const val DAYS_IN_YEAR = 365L
private const val STRIP_WASH_ALPHA = 0.35f
private val StripHeight = 60.dp

internal object PageGrid {
    val Gap = 12.dp
    const val COLUMNS = 3
    const val ROWS = 2

    fun rowHeight(maxHeight: Dp): Dp = (maxHeight - Gap * (ROWS - 1)) / ROWS
}

@Composable
internal fun PageHeader(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    searchPlaceholder: String,
    onBack: () -> Unit,
    filters: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).panel(16.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        PageTitle(title, Modifier.padding(end = 2.dp))
        TextField(query, onQuery, Modifier.weight(1f).height(38.dp), searchPlaceholder, themedIcon(LobbyIcons.SEARCH), 64, false)
        actions()
    }
    filters?.invoke()
}

@Composable
internal fun <T> UnderlineTabs(tabs: List<TabItem<T>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEach { tab -> UnderlineTab(tab.label, tab.value == selected) { onSelect(tab.value) } }
    }
}

@Composable
private fun UnderlineTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val shape = ppShape(8.dp)
    val ink by animateColorAsState(if (selected || hovered) SocialTextPrimary else MutedText)
    val fill by animateColorAsState(if (hovered && !selected) SocialTextPrimary.copy(alpha = TAB_HOVER_ALPHA) else Color.Transparent)
    val bar by animateColorAsState(if (selected) OneConfigTheme.accent else Color.Transparent)
    Box(
        Modifier
            .widthIn(min = TAB_MIN_WIDTH)
            .height(TAB_HEIGHT)
            .clip(shape)
            .background(fill)
            .drawBehind {
                val thickness = TAB_BAR_HEIGHT.toPx()
                drawRect(bar, Offset(0f, size.height - thickness), Size(size.width, thickness))
            }
            .hoverable(interaction)
            .clickableWithSound(label, A11yRole.Tab, selected, shape = shape, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, 15.sp, color = ink, weight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

private val TAB_HEIGHT = 40.dp
private val TAB_BAR_HEIGHT = 2.dp
private val TAB_MIN_WIDTH = 88.dp
private const val TAB_HOVER_ALPHA = 0.08f

@Composable
internal fun NoMatchState(noun: String, filterLabel: String?, query: String, modifier: Modifier) {
    val mode = filterLabel?.let { "$it " }.orEmpty()
    if (query.isBlank()) {
        EmptyState("No $mode$noun", modifier, "Pick another tab to see more $noun.")
    } else {
        EmptyState("No $mode$noun match that search", modifier, "Try a different name.")
    }
}

@Composable
internal fun rememberCardClick(onDouble: () -> Unit, onSelect: () -> Unit): () -> Unit {
    val doubleClick = rememberDoubleClick(onDouble)
    return {
        doubleClick()
        onSelect()
    }
}

@Composable
internal fun LastPlayedStrip(
    tag: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    menu: () -> List<MenuAction>,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    wash: Modifier? = null,
    thumb: @Composable () -> Unit,
    tags: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    detail: @Composable () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .cardFrame(selected, hovered)
            .then(wash ?: Modifier.background(Brush.horizontalGradient(listOf(OneConfigTheme.accent.copy(alpha = STRIP_WASH_ALPHA), Color.Transparent))))
            .contextMenu(menu)
            .hoverable(interaction)
            .clickableWithSound("$tag, $name", selected = selected.takeIf { it }, shape = ppShape(16.dp), onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        thumb()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(tag, TagTone.Accent)
                    tags()
                }
                Label(name, 15.sp, Modifier.weight(1f, fill = false), weight = FontWeight.Medium)
            }
            detail()
        }
        trailing()
        TextButton(actionLabel, null, Modifier.height(38.dp), ButtonStyle.Primary, actionEnabled, onAction)
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 12.sp, Modifier.weight(1f), color = MutedText)
        Label(value, 12.sp)
    }
}

internal fun relativeDayText(epochMillis: Long): String? {
    if (epochMillis <= 0L) return null
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(then, LocalDate.now(zone))
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < DAYS_IN_WEEK -> "$days days ago"
        days < DAYS_IN_MONTH -> ago(days / DAYS_IN_WEEK, "week")
        days < DAYS_IN_YEAR -> ago(days / DAYS_IN_MONTH, "month")
        else -> ago(days / DAYS_IN_YEAR, "year")
    }
}

private fun ago(count: Long, unit: String): String = "$count $unit${if (count == 1L) "" else "s"} ago"
