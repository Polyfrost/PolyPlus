@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleControl
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.lobby.LobbyFraming
import kotlin.math.roundToInt

internal object CustomizeKit {
    val PanelWidth = 380.dp
    val Inset = 16.dp
    val SectionGap = 10.dp
    val RowHeight = 44.dp
    val RowRadius = 10.dp
    val ValueWidth = 150.dp
    val SegmentHeight = 32.dp
}

private const val PANEL_MILLIS = 240
private const val PAGE_MILLIS = 180
private const val MAX_INSET = 0.45f
private val PAGE_SHIFT = 24.dp
private val HEADER_HEIGHT = 56.dp
private val PANEL_RADIUS = 20.dp

@Composable
internal fun DockedPanel(
    open: Boolean,
    title: String,
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
    onEscape: () -> Unit = onClose,
    content: @Composable BoxScope.() -> Unit,
) {
    val progress by Motion.animateFloatAsState(if (open) 1f else 0f, tween(PANEL_MILLIS, easing = FastOutSlowInEasing))
    DisposableEffect(Unit) { onDispose { LobbyFraming.panelInset = 0f } }
    LaunchedEffect(open) { if (!open) LobbyFraming.panelInset = 0f }
    if (!open && progress <= 0f) return
    if (open) EscapeCloses(onEscape)
    val root = LocalRootSize.current
    val marginPx = with(LocalDensity.current) { LobbyLayout.Margin.toPx() }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = LobbyLayout.Top, end = LobbyLayout.Margin, bottom = LobbyLayout.Margin)
                .width(CustomizeKit.PanelWidth)
                .fillMaxHeight()
                .offset { IntOffset(((1f - progress) * (CustomizeKit.PanelWidth + LobbyLayout.Margin).toPx()).roundToInt(), 0) }
                .graphicsLayer { alpha = progress }
                .onGloballyPositioned {
                    if (open && root.width > 0f) LobbyFraming.panelInset = ((it.size.width + marginPx) / root.width).coerceIn(0f, MAX_INSET)
                }
                .panel(PANEL_RADIUS)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            PanelHeader(title, onClose, onBack)
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }
}

@Composable
internal fun PanelHeader(title: String, onClose: () -> Unit, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(start = if (onBack != null) 8.dp else CustomizeKit.Inset, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) IconButton(SOCIAL_ASSETS + "chevron-left.svg", "Back", onBack, tooltip = null)
        Text(
            title,
            Modifier.weight(1f),
            color = OneConfigTheme.current.textColor,
            style = OneConfigTheme.current.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        IconButton(SOCIAL_ASSETS + "x-close.svg", "Close", onClose, tooltip = null)
    }
}

@Composable
internal fun <T> PanelPageSwitch(page: T, forward: Boolean, content: @Composable (T) -> Unit) {
    key(page) {
        val enter = remember { Animatable(if (Motion.reduced) 1f else 0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(PAGE_MILLIS, easing = FastOutSlowInEasing)) }
        val direction = if (forward) 1f else -1f
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = enter.value
                translationX = (1f - enter.value) * direction * PAGE_SHIFT.toPx()
            },
        ) { content(page) }
    }
}

@Composable
internal fun PanelPageColumn(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ScrollContainer(scroll, modifier.fillMaxSize(), scrollbar = false, fadingEdges = true) {
        Column(
            Modifier.fillMaxWidth().padding(start = CustomizeKit.Inset, end = CustomizeKit.Inset, top = 4.dp, bottom = CustomizeKit.Inset),
            verticalArrangement = Arrangement.spacedBy(CustomizeKit.SectionGap),
            content = content,
        )
    }
}

@Composable
internal fun PanelSection(
    title: String?,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = OneConfigTheme.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (divider) Divider(Modifier.fillMaxWidth().padding(bottom = 6.dp))
        if (title != null || trailing != null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title.orEmpty(),
                    Modifier.weight(1f),
                    color = theme.textColor,
                    style = theme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                trailing?.invoke(this)
            }
        }
        content()
    }
}

@Composable
internal fun PanelHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth(), color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
}

@Composable
internal fun DrillRow(
    icon: String?,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(CustomizeKit.RowRadius)
    Row(
        modifier
            .fillMaxWidth()
            .height(CustomizeKit.RowHeight)
            .selectableRow(false, onClick, if (value == null) label else "$label, $value", shape, enabled, role = A11yRole.Button)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) LobbyIcon(icon, theme.textColorSecondary, 18.dp)
        Text(label, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        if (value != null) {
            Text(
                value,
                Modifier.widthIn(max = CustomizeKit.ValueWidth),
                color = theme.textColorSecondary,
                style = theme.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                textAlign = TextAlign.End,
            )
        }
        LobbyIcon("chevron-right", theme.textColorSecondary, 16.dp)
    }
}

@Composable
internal fun PanelToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    prominent: Boolean = false,
) {
    ToggleRow(
        label,
        checked,
        onCheckedChange,
        modifier.fillMaxWidth(),
        description = description,
        enabled = enabled,
        control = ToggleControl.Switch,
        framed = prominent,
        size = if (prominent) ControlSize.Large else ControlSize.Regular,
        contentPadding = if (prominent) null else PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
internal fun PanelSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    step: Float = 0f,
    enabled: Boolean = true,
    valueText: ((Float) -> String)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp),
        range = range,
        step = step,
        enabled = enabled,
        label = label,
        valueText = valueText,
        onValueChangeFinished = onValueChangeFinished,
        sound = null,
    )
}

@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    columns: Int = options.size,
    enabled: Boolean = true,
) {
    val theme = OneConfigTheme.current
    val perRow = columns.coerceAtLeast(1)
    val itemShape = OneConfigTheme.shape(8.dp)
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else theme.disabledAlpha)
            .card(11.dp)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { option ->
                    val on = option == selected
                    val text = label(option)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(CustomizeKit.SegmentHeight)
                            .selectableRow(on, { if (!on) onSelect(option) }, text, itemShape, enabled, ItemSelectionStyle.Accent, A11yRole.RadioButton),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text,
                            Modifier.padding(horizontal = 6.dp),
                            color = SelectionDefaults.itemContentColor(on, ItemSelectionStyle.Accent),
                            style = theme.typography.small.copy(fontWeight = if (on) FontWeight.Medium else FontWeight.Normal),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun ResetRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Reset to default",
    enabled: Boolean = true,
) {
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(CustomizeKit.RowRadius)
    Row(
        modifier
            .fillMaxWidth()
            .height(CustomizeKit.RowHeight - 6.dp)
            .selectableRow(false, onClick, label, shape, enabled, role = A11yRole.Button)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LobbyIcon("refresh-cw-01", theme.textColorSecondary, 16.dp)
        Text(label, Modifier.weight(1f), color = theme.textColorSecondary, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}
