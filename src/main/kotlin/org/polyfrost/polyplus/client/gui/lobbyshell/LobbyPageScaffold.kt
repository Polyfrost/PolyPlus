@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.KeyValueRow
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.PanelHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import androidx.compose.ui.text.style.TextOverflow

private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 30L
private const val DAYS_IN_YEAR = 365L
private const val STRIP_WASH_ALPHA = 0.35f
private const val MAX_QUERY = 64
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
    PanelHeader(
        title,
        Modifier.height(54.dp).panel(16.dp).padding(horizontal = 12.dp),
        titleStyle = pageTitleStyle(),
        leading = { BackButton(onBack) },
        titleWeight = false,
        spacing = 8.dp,
        actions = {
            SearchField(
                query = query,
                onQueryChange = { onQuery(it.take(MAX_QUERY)) },
                modifier = Modifier.weight(1f).padding(start = 2.dp),
                placeholder = searchPlaceholder,
                clearable = false,
                size = ControlSize.Large,
            )
            actions()
        },
    )
    filters?.invoke()
}

@Composable
internal fun <T> UnderlineTabs(tabs: List<TabItem<T>>, selected: T, onSelect: (T) -> Unit) {
    TabRow(tabs, selected, onSelect, style = TabStyle.Underline, size = ControlSize.Large, fill = false)
}

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
    val hover = rememberHoverState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .cardFrame(selected, hover.hovered)
            .then(wash ?: Modifier.background(Brush.horizontalGradient(listOf(OneConfigTheme.accent.copy(alpha = STRIP_WASH_ALPHA), Color.Transparent))))
            .contextMenu(entries = menu)
            .oneConfigClickable("$tag, $name", onClick, selected = selected.takeIf { it }, shape = OneConfigTheme.shape(16.dp), interactionSource = hover.interactionSource)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        thumb()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(tag.uppercase(), tone = BadgeTone.Accent, style = BadgeStyle.Solid)
                    tags()
                }
                Text(name, Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.heading, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            detail()
        }
        trailing()
        TextButton(text = actionLabel, onClick = onAction, size = ControlSize.Large, style = ControlStyle.Primary, enabled = actionEnabled)
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    KeyValueRow(label, value)
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
