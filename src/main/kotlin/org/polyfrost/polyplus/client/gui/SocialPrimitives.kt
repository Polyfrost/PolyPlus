@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.sceneFrost
import java.util.UUID
import androidx.compose.ui.graphics.Shape

@Composable
internal fun SocialModalScrim(onDismiss: () -> Unit, dismissOnScrim: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, dismissOnClickOutside = dismissOnScrim)) {
        CompositionLocalProvider(LocalAnnounceTitles provides true) { SocialScrimBox(onDismiss.takeIf { dismissOnScrim }, content) }
    }
}

@Composable
internal fun SocialScrimBox(onDismiss: (() -> Unit)?, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SocialScrim)
            .then(
                if (onDismiss != null) {
                    Modifier.notFocusable().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

internal class ConfirmRequest(
    val title: String,
    val text: String,
    val confirmLabel: String,
    val icon: String,
    val onConfirm: () -> Unit,
)

internal object ConfirmPrompt {
    var pending by mutableStateOf<ConfirmRequest?>(null)
        private set

    fun ask(title: String, text: String, confirmLabel: String, icon: String = LobbyIcons.TRASH, onConfirm: () -> Unit) {
        pending = ConfirmRequest(title, text, confirmLabel, icon, onConfirm)
    }

    fun dismiss() {
        pending = null
    }

    fun confirm() {
        val request = pending ?: return
        pending = null
        request.onConfirm()
    }
}

@Composable
internal fun ConfirmPromptHost() {
    DisposableEffect(Unit) { onDispose { ConfirmPrompt.dismiss() } }
    val request = ConfirmPrompt.pending ?: return
    ConfirmDialog(request.title, request.text, request.confirmLabel, "Cancel", themedIcon(request.icon), true, true, ConfirmPrompt::confirm, ConfirmPrompt::dismiss)
}

@Composable
internal fun Modifier.swallowClicks(): Modifier =
    notFocusable().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}

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
    val uuid = remember(playerId) { parsePlayerUuid(playerId) }
    val shape = ppShape(size / 4)
    if (uuid != null) {
        PlayerHead(uuid, size, modifier, shape)
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(avatarFallbackColor(playerId)),
            contentAlignment = Alignment.Center,
        ) {
            SocialText(playerId.take(1).uppercase(), fontSize = size.value.sp * 0.4f, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun parsePlayerUuid(id: String): UUID? {
    val trimmed = id.trim()
    val dashed = if (trimmed.length == 32 && '-' !in trimmed) {
        "${trimmed.substring(0, 8)}-${trimmed.substring(8, 12)}-${trimmed.substring(12, 16)}-${trimmed.substring(16, 20)}-${trimmed.substring(20)}"
    } else {
        trimmed
    }
    if (dashed.length != 36) return null
    return runCatching { UUID.fromString(dashed) }.getOrNull()
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
                .clickableWithSound("$label, ${labelFor(selected)}", shape = SocialFieldShape) { expanded = !expanded }
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
                        MenuItem(labelFor(option), null, null, MenuItemSize.Compact, false, option == selected, true, null, Modifier, null) { onSelect(option); expanded = false }
                    }
                }
            }
        }
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
