@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.sceneFrost
import java.util.UUID

@Composable
internal fun SocialModalScrim(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        SocialScrimBox(onDismiss, content)
    }
}

@Composable
internal fun SocialScrimBox(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SocialScrim)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun Modifier.swallowClicks(): Modifier =
    clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}

@Composable
internal fun ModalPanel(
    width: Dp,
    height: Dp = Dp.Unspecified,
    padding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .then(if (height == Dp.Unspecified) Modifier else Modifier.height(height))
            .sceneFrost(16.dp)
            .clip(SocialPanelShape)
            .background(SocialPopupBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .swallowClicks()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
internal fun SocialText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = SocialTextPrimary,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily = OneConfigTheme.current.fontFamily,
    textDecoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = maxLines != 1,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        onTextLayout = onTextLayout,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            fontFamily = fontFamily,
            textAlign = textAlign,
            textDecoration = textDecoration,
        ),
    )
}

@Composable
internal fun SocialAvatar(playerId: String, size: Dp, modifier: Modifier = Modifier) {
    val uuid = remember(playerId) { runCatching { UUID.fromString(playerId) }.getOrNull() }
    val head = uuid?.let { MenuHeadCache.get(it) }
    val shape = ppShape(size / 4)
    if (head != null) {
        Image(head, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(avatarFallbackColor(playerId)),
            contentAlignment = Alignment.Center,
        ) {
            SocialText(playerId.take(1).uppercase(), fontSize = size.value.sp * 0.4f, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun avatarFallbackColor(seed: String): Color {
    val hue = (seed.hashCode().mod(360)).toFloat()
    return Color.hsv(hue, 0.45f, 0.55f)
}

@Composable
internal fun SocialGroupAvatar(memberIds: List<String>, size: Dp, modifier: Modifier = Modifier, ringColor: Color = SocialCardBackground, grid: Boolean = false) {
    val shape = ppShape(size / 4)
    val shown = memberIds.take(4)

    if (shown.size <= 1) {
        val id = shown.firstOrNull()
        if (id != null) {
            SocialAvatar(id, size, modifier)
        } else {
            Box(modifier.size(size).clip(shape).background(SocialControlBackground))
        }
        return
    }

    val gap = size * 0.07f
    Box(modifier.size(size).clip(shape).background(ringColor)) {
        when (shown.size) {
            2 -> {
                SocialAvatar(shown[0], size * 0.62f, Modifier.align(Alignment.TopStart).padding(top = gap, start = gap))
                SocialAvatar(shown[1], size * 0.62f, Modifier.align(Alignment.BottomEnd).padding(bottom = gap, end = gap))
            }
            3 -> if (grid) {
                SocialAvatar(shown[0], size * 0.46f, Modifier.align(Alignment.TopStart).padding(top = gap, start = gap))
                SocialAvatar(shown[1], size * 0.46f, Modifier.align(Alignment.TopEnd).padding(top = gap, end = gap))
                SocialAvatar(shown[2], size * 0.46f, Modifier.align(Alignment.BottomStart).padding(bottom = gap, start = gap))
            } else {
                SocialAvatar(shown[0], size * 0.5f, Modifier.align(Alignment.TopCenter).padding(top = gap))
                SocialAvatar(shown[1], size * 0.5f, Modifier.align(Alignment.BottomStart).padding(bottom = gap, start = gap))
                SocialAvatar(shown[2], size * 0.5f, Modifier.align(Alignment.BottomEnd).padding(bottom = gap, end = gap))
            }
            else -> {
                SocialAvatar(shown[0], size * 0.46f, Modifier.align(Alignment.TopStart).padding(top = gap, start = gap))
                SocialAvatar(shown[1], size * 0.46f, Modifier.align(Alignment.TopEnd).padding(top = gap, end = gap))
                SocialAvatar(shown[2], size * 0.46f, Modifier.align(Alignment.BottomStart).padding(bottom = gap, start = gap))
                SocialAvatar(shown[3], size * 0.46f, Modifier.align(Alignment.BottomEnd).padding(bottom = gap, end = gap))
            }
        }
    }
}

@Composable
internal fun rememberSocialHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}

@Composable
internal fun SocialButton(
    label: String,
    icon: String? = null,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val (interaction, hovered) = rememberSocialHover()
    val contentColor = if (filled) OneConfigTheme.current.accentTextColor else SocialTextPrimary
    val fill = when {
        filled && danger -> SocialDangerColor
        filled -> OneConfigTheme.accent
        else -> SocialControlBackground
    }
    val background by animateColorAsState(if (filled && hovered && enabled) lerp(fill, Color.White, FILLED_HOVER_LIFT) else fill)
    val borderColor by animateColorAsState(
        when {
            filled -> background
            hovered && enabled -> SocialHoverBorder
            else -> SocialBorderColor
        },
    )
    Row(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .height(38.dp)
            .clip(SocialFieldShape)
            .background(background)
            .border(SocialBorderWidth, borderColor, SocialFieldShape)
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) ThemedIcon(icon, contentColor, 16.dp)
        SocialText(label, fontSize = 14.sp, color = contentColor, maxLines = 1)
    }
}

@Composable
internal fun SocialIconButton(
    icon: String,
    modifier: Modifier = Modifier,
    background: Color? = null,
    tint: Color = SocialTextPrimary,
    tooltip: String? = null,
    onClick: () -> Unit = {},
) {
    SocialIconButton(modifier, background, tooltip, onClick) { ThemedIcon(icon, tint, 16.dp) }
}

@Composable
internal fun SocialIconButton(
    modifier: Modifier = Modifier,
    background: Color? = null,
    tooltip: String? = null,
    onClick: () -> Unit = {},
    glyph: @Composable () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    val resolvedBackground by animateColorAsState(background ?: Color.Transparent)
    val hairline by animateColorAsState(SocialBorderColor.copy(alpha = if (hovered) SocialBorderColor.alpha else 0f))
    val edge = with(LocalDensity.current) { TOOLTIP_EDGE.roundToPx() }
    val placement = remember(edge) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width - edge).coerceAtLeast(edge)
                return IntOffset(x, anchorBounds.bottom + TOOLTIP_GAP)
            }
        }
    }

    Box(
        modifier = modifier
            .size(SOCIAL_ICON_BUTTON_SIZE)
            .clip(SocialFieldShape)
            .background(resolvedBackground)
            .border(SocialBorderWidth, hairline, SocialFieldShape)
            .hoverable(interaction)
            .clickableWithSound(onClick),
        contentAlignment = Alignment.Center,
    ) {
        glyph()
        if (tooltip != null && hovered) {
            Popup(
                popupPositionProvider = placement,
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                SocialTooltipBubble(tooltip)
            }
        }
    }
}

private val SOCIAL_ICON_BUTTON_SIZE = 34.dp
private val TOOLTIP_EDGE = 8.dp
private const val TOOLTIP_GAP = 6
private const val FILLED_HOVER_LIFT = 0.1f

@Composable
private fun SocialTooltipBubble(text: String) {
    Box(
        modifier = Modifier
            .sceneFrost(16.dp)
            .clip(SocialPanelShape)
            .background(SocialPopupBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        SocialText(text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
internal fun SocialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: String? = null,
    maxLength: Int = 64,
    invalid: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    bare: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onSubmit: (() -> Unit)? = null,
) {
    val bodyFont = OneConfigTheme.current.fontFamily
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        when {
            invalid -> SocialDangerColor
            focused -> OneConfigTheme.accent
            else -> SocialBorderColor
        },
    )
    val frame = if (bare) Modifier else Modifier.clip(SocialFieldShape).background(SocialControlBackground).border(SocialBorderWidth, border, SocialFieldShape)
    Row(
        modifier = modifier
            .height(38.dp)
            .then(frame)
            .clickable(remember { MutableInteractionSource() }, indication = null) { focusRequester.requestFocus() }
            .padding(start = if (bare) 6.dp else 12.dp, end = if (trailing != null) 2.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingIcon != null) ThemedIcon(leadingIcon, SocialTextSecondary, 16.dp)
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }.trackTextInputFocus(),
                singleLine = true,
                textStyle = TextStyle(color = SocialTextPrimary, fontSize = 14.sp, fontFamily = bodyFont),
                cursorBrush = SolidColor(OneConfigTheme.accent),
                keyboardActions = onSubmit?.let {
                    KeyboardActions(onDone = { it() }, onSend = { it() })
                } ?: KeyboardActions.Default,
                keyboardOptions = if (onSubmit != null) {
                    KeyboardOptions(imeAction = ImeAction.Done)
                } else {
                    KeyboardOptions.Default
                },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        SocialText(placeholder, fontSize = 14.sp, color = SocialTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                },
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun SocialToggle(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val rowBorder by animateColorAsState(if (hovered) SocialHoverBorder else SocialBorderColor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .border(SocialBorderWidth, rowBorder, SocialFieldShape)
            .hoverable(interaction)
            .clickableWithSound(onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialText(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        val theme = OneConfigTheme.current
        val boxColor by animateColorAsState(if (checked) OneConfigTheme.accent else SocialOffStateBackground)
        val boxBorder by animateColorAsState(
            when {
                checked -> OneConfigTheme.accent
                hovered -> theme.textColorSecondary
                else -> theme.borderColor
            },
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(theme.checkBoxShape)
                .background(boxColor)
                .border(SocialIndicatorBorderWidth, boxBorder, theme.checkBoxShape),
            contentAlignment = Alignment.Center,
        ) {
            this@Row.AnimatedVisibility(visible = checked, enter = fadeIn(), exit = fadeOut()) {
                Canvas(Modifier.size(12.dp)) {
                    val w = size.width
                    val h = size.height
                    val tick = Path().apply {
                        moveTo(w * 0.2f, h * 0.52f)
                        lineTo(w * 0.42f, h * 0.72f)
                        lineTo(w * 0.8f, h * 0.3f)
                    }
                    drawPath(tick, color = theme.textColor, style = Stroke(width = w * 0.16f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
internal fun <T> SocialDropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    modifier: Modifier = Modifier,
    open: MutableState<Boolean> = remember { mutableStateOf(false) },
    onSelect: (T) -> Unit,
) {
    var expanded by open
    val (triggerInteraction, triggerHovered) = rememberSocialHover()
    val triggerBorder by animateColorAsState(
        when {
            expanded -> OneConfigTheme.accent
            triggerHovered -> SocialHoverBorder
            else -> SocialBorderColor
        },
    )
    val triggerBackground by animateColorAsState(if (expanded) OneConfigTheme.accent else SocialDropdownBackground)
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .onSizeChanged { triggerSize = it }
                .clip(SocialFieldShape)
                .background(triggerBackground)
                .border(SocialBorderWidth, triggerBorder, SocialFieldShape)
                .hoverable(triggerInteraction)
                .clickableWithSound { expanded = !expanded }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val chevronAngle by animateFloatAsState(if (expanded) 180f else 0f)
            val ink = if (expanded) OneConfigTheme.current.accentTextColor else null
            SocialText(label, fontSize = 12.sp, color = ink ?: SocialTextSecondary, modifier = Modifier.align(Alignment.CenterStart))
            SocialText(labelFor(selected), fontSize = 14.sp, color = ink ?: SocialTextPrimary, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp))
            ThemedIcon(SOCIAL_ASSETS + "chevron-up.svg", ink ?: SocialTextPrimary, 14.dp, Modifier.align(Alignment.CenterEnd).rotate(chevronAngle))
        }
        if (expanded) {
            EscapeCloses { expanded = false }
            val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
            val positionProvider = remember(gap) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val below = anchorBounds.bottom + gap
                        val y = if (below + popupContentSize.height <= windowSize.height) {
                            below
                        } else {
                            (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
                        }
                        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                        return IntOffset(x, y)
                    }
                }
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                SocialDropdownList(triggerSize.width) {
                    options.forEach { option ->
                        SocialMenuItem(
                            label = labelFor(option),
                            color = if (option == selected) OneConfigTheme.current.accentTextColor else SocialTextPrimary,
                            selected = option == selected,
                            horizontalPadding = 10.dp,
                        ) { onSelect(option); expanded = false }
                    }
                }
            }
        }
    }
}

/** A row that gets highlighted on hover, used by dropdowns and overflow menus. */
@Composable
internal fun SocialMenuItem(
    label: String,
    icon: String? = null,
    color: Color = SocialTextPrimary,
    selected: Boolean = false,
    height: Dp = 32.dp,
    horizontalPadding: Dp = 8.dp,
    onClick: () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    val background by animateColorAsState(if (selected) OneConfigTheme.accent else Color.Transparent)
    val hairline by animateColorAsState(SocialBorderColor.copy(alpha = if (hovered && !selected) SocialBorderColor.alpha else 0f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(SocialFieldShape)
            .background(background)
            .border(SocialBorderWidth, hairline, SocialFieldShape)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) ThemedIcon(icon, color, 15.dp)
        SocialText(label, fontSize = 13.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SocialDropdownList(triggerWidthPx: Int, content: @Composable ColumnScope.() -> Unit) {
    val width = with(LocalDensity.current) { triggerWidthPx.toDp() }
    Column(
        modifier = Modifier
            .width(if (triggerWidthPx > 0) width else 220.dp)
            .sceneFrost(16.dp)
            .clip(SocialPanelShape)
            .background(SocialPopupBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun SocialSwitch(checked: Boolean, width: Dp, height: Dp, offColor: Color) {
    val theme = OneConfigTheme.current
    val inset = 3.dp
    val track by animateColorAsState(if (checked) OneConfigTheme.accent else offColor)
    val thumb by animateDpAsState(if (checked) width - height + inset else inset, animationSpec = spring())
    Box(Modifier.size(width = width, height = height).clip(theme.circleShape).background(track), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = thumb).size(height - inset * 2).background(theme.controlThumbColor, theme.circleShape))
    }
}

@Composable
internal fun SocialTextValueField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    invalid: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    var focused by remember { mutableStateOf(false) }
    val accent = OneConfigTheme.accent
    val border by animateColorAsState(
        when {
            invalid -> SocialDangerColor
            focused -> accent
            else -> SocialBorderColor
        },
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .border(SocialBorderWidth, border, SocialFieldShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }.trackTextInputFocus(),
            singleLine = true,
            textStyle = TextStyle(color = SocialTextPrimary, fontSize = 14.sp, fontFamily = OneConfigTheme.current.fontFamily),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                if (value.text.isEmpty()) SocialText(placeholder, 14.sp, color = SocialTextSecondary, maxLines = 1)
                inner()
            },
        )
    }
}
