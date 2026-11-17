@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.world

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Card
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.card

internal object WorldKit {
    val ContentWidth = 880.dp
    val Gap = 12.dp
    val SectionGap = 22.dp
    val CardRadius = 14.dp
    val TileRadius = 12.dp
}

internal class WorldAction(
    val icon: String,
    val label: String,
    val detail: String?,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun WorldContentColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WorldKit.ContentWidth).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(WorldKit.Gap), content = content)
    }
}

@Composable
internal fun SectionSpace() {
    Spacer(Modifier.height(WorldKit.SectionGap - WorldKit.Gap))
}

@Composable
internal fun WorldSectionTitle(title: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val theme = OneConfigTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = theme.textColor, style = theme.typography.headingMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            subtitle?.let { Text(it, color = theme.textColorSecondary, style = theme.typography.small) }
        }
        trailing?.invoke(this)
    }
}

@Composable
internal fun WorldBlock(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WorldSectionTitle(title, subtitle, trailing)
        content()
    }
}

@Composable
internal fun WorldTile(name: String, image: ImageBitmap?, size: Dp, modifier: Modifier = Modifier, hardcore: Boolean = false) {
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(WorldKit.TileRadius)
    val tint = if (hardcore) theme.error else OneConfigTheme.accent
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.35f))), shape)
            .border(1.dp, theme.borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        } else {
            val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
            Text(
                letter,
                color = Color.White,
                style = theme.typography.title.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun IconChip(icon: String, danger: Boolean, selected: Boolean, size: Dp = 34.dp) {
    val theme = OneConfigTheme.current
    val tone = if (danger) theme.error else OneConfigTheme.accent
    val shape = OneConfigTheme.shape(10.dp)
    Box(
        Modifier.size(size).clip(shape).background(if (selected) tone.copy(alpha = 0.28f) else theme.textColor.copy(alpha = 0.07f), shape),
        contentAlignment = Alignment.Center,
    ) {
        LobbyIcon(icon, if (selected || danger) tone else theme.textColorSecondary, size * 0.53f)
    }
}

@Composable
internal fun ChoiceCard(
    title: String,
    text: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val theme = OneConfigTheme.current
    Card(
        modifier,
        onClick = onClick,
        label = title,
        selected = selected,
        enabled = enabled,
        shape = OneConfigTheme.shape(WorldKit.CardRadius),
        role = A11yRole.RadioButton,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else theme.disabledAlpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) IconChip(icon, danger, selected)
                Text(
                    title,
                    Modifier.weight(1f),
                    color = if (danger && selected) theme.error else theme.textColor,
                    style = theme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                if (selected) LobbyIcon("check", OneConfigTheme.accent, 16.dp)
            }
            if (text != null) Text(text, color = theme.textColorSecondary, style = theme.typography.small)
        }
    }
}

@Composable
internal fun <T> ChoiceGrid(items: List<T>, columns: Int, content: @Composable BoxScope.(T) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> Box(Modifier.weight(1f).fillMaxHeight()) { content(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun ActionGroup(actions: List<WorldAction>) {
    Column(Modifier.fillMaxWidth().card(WorldKit.CardRadius).padding(4.dp)) {
        actions.forEachIndexed { index, action ->
            if (index > 0) Divider(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            ActionRow(action)
        }
    }
}

@Composable
private fun ActionRow(action: WorldAction) {
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectableRow(false, action.onClick, action.label, shape, action.enabled, role = A11yRole.Button)
            .alpha(if (action.enabled) 1f else theme.disabledAlpha)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconChip(action.icon, action.danger, false, 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                action.label,
                color = if (action.danger) theme.error else theme.textColor,
                style = theme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            action.detail?.let { Text(it, color = theme.textColorSecondary, style = theme.typography.small) }
        }
        LobbyIcon("chevron-right", theme.textColorSecondary, 16.dp)
    }
}

@Composable
internal fun StepBar(steps: List<String>, current: Int, onSelect: (Int) -> Unit) {
    val theme = OneConfigTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, label ->
            if (index > 0) Box(Modifier.weight(1f).height(1.dp).background(if (index <= current) OneConfigTheme.accent else theme.borderColor))
            val on = index == current
            val done = index < current
            Row(
                Modifier
                    .selectableRow(on, { onSelect(index) }, "Step ${index + 1}, $label", OneConfigTheme.shape(10.dp), role = A11yRole.Tab)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val circle = OneConfigTheme.shape(12.dp)
                val ink = ButtonDefaults.contentColor(ControlStyle.Primary)
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(circle)
                        .background(if (on || done) OneConfigTheme.accent else Color.Transparent, circle)
                        .border(1.dp, if (on || done) OneConfigTheme.accent else theme.borderColor, circle),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        LobbyIcon("check", ink, 14.dp)
                    } else {
                        Text(
                            "${index + 1}",
                            color = if (on) ink else theme.textColorSecondary,
                            style = theme.typography.smallMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Text(
                    label,
                    color = if (on) theme.textColor else theme.textColorSecondary,
                    style = if (on) theme.typography.bodyMedium else theme.typography.body,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
