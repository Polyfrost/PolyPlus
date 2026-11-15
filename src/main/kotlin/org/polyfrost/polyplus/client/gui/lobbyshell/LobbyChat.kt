@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.emoji.EmojiRegistry
import org.polyfrost.polyplus.client.gui.EmojiPickerButton
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbysocial.ChatLines
import org.polyfrost.polyplus.client.gui.lobbysocial.ConversationChip
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.lobbysocial.cooldownReason
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberPartyConversation
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private const val MESSAGE_LIMIT = 500

private val BarPadding = 12.dp
private val BarGap = 12.dp
private val BarHeadSize = 30.dp
private val DockCloseSize = 28.dp

@Composable
internal fun PlayerBar(lobby: LobbyShell, modifier: Modifier, fill: Boolean = false) {
    val party by PartyRepository.party.collectAsState()
    Row(
        modifier.height(LobbyLayout.BarHeight).panel().padding(start = (LobbyLayout.BarHeight - BarHeadSize) / 2, end = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        BoxWithConstraints(Modifier.weight(1f, fill = fill)) {
            val current = party
            if (current != null) PartyRoster(lobby, current, maxWidth) else SoloRoster()
        }
        PartyControls(lobby, party)
    }
}

@Composable
private fun SoloRoster() {
    BarEntry(ClientPlatform.localPlayerName(), "You") { PlayerHead(playerId = ClientPlatform.localPlayerUuid(), size = BarHeadSize, modifier = Modifier.centerVerticallyExact(), shape = headShape(BarHeadSize)) }
}

@Composable
private fun BarEntry(name: String, detail: String, head: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        head()
        Column(lobbyHover(name).weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

@Composable
internal fun ChatPanel(open: Boolean, width: Dp, fill: Boolean) {
    var draft by remember { mutableStateOf("") }
    val party by PartyRepository.party.collectAsState()
    val partyChat = party?.chatGroupId?.let { rememberPartyConversation(it) }
    val active = SocialChatLink.active ?: partyChat
    val conversation = active.takeIf { open }
    LaunchedEffect(active?.id) { draft = "" }
    val lines = conversation?.lines().orEmpty()
    val resize = Motion.spec(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntSize.VisibilityThreshold))
    val size = if (conversation != null) {
        Modifier.width(width).height(LobbyLayout.ChatHeight)
    } else {
        Modifier.then(if (fill) Modifier.width(width) else Modifier.widthIn(max = width)).height(LobbyLayout.BarHeight)
    }
    Column(
        Modifier.panel().springResize(resize, followWidth = fill).then(size).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (conversation == null) {
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemedIcon("text", OneConfigTheme.current.mutedText, 16.dp)
                Text("Press Enter to open Social", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConversationChip(conversation)
            if (conversation !== partyChat) {
                Spacer(Modifier.weight(1f))
                DockCloseButton()
            }
        }
        ChatLines(lines, Modifier.weight(1f).fillMaxWidth())
        val cooldown = cooldownReason(conversation.id)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextField(
                value = draft,
                onValueChange = { if (cooldown == null) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = cooldown ?: "Message ${conversation.title}",
                maxLength = MESSAGE_LIMIT,
                size = ControlSize.Large,
                fontSize = 14.sp,
                onSubmit = {
                    val text = draft.trim()
                    if (cooldown == null && text.isNotEmpty()) {
                        conversation.send(EmojiRegistry.toShortcodes(text))
                        draft = ""
                    }
                },
            )
            EmojiPickerButton { alias ->
                val insert = ":$alias:"
                if (cooldown == null && draft.length + insert.length <= MESSAGE_LIMIT) draft += insert
            }
        }
    }
}

private class PanelSize {
    var anim: Animatable<IntSize, AnimationVector2D>? = null
}

@Composable
private fun Modifier.springResize(spec: AnimationSpec<IntSize>, followWidth: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val state = remember { PanelSize() }
    return clipToBounds().layout { measurable, constraints ->
        val targetWidth = constraints.constrainWidth(measurable.maxIntrinsicWidth(constraints.maxHeight))
        val anim = state.anim
        val shownWidth = if (followWidth) targetWidth else constraints.constrainWidth(anim?.value?.width ?: targetWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = shownWidth, maxWidth = shownWidth))
        val target = IntSize(if (followWidth) 0 else targetWidth, placeable.height)
        val current = if (anim == null) {
            state.anim = Animatable(target, IntSize.VectorConverter, IntSize.VisibilityThreshold)
            IntSize(shownWidth, placeable.height)
        } else {
            if (anim.targetValue != target) scope.launch { anim.animateTo(target, spec) }
            IntSize(shownWidth, anim.value.height)
        }
        val shown = constraints.constrain(current)
        layout(shown.width, shown.height) { placeable.place(0, shown.height - placeable.height) }
    }
}

@Composable
private fun DockCloseButton() {
    IconButton(
        icon = LobbyIcons.CLOSE,
        contentDescription = "Close conversation",
        onClick = SocialChatLink::close,
        style = IconButtonStyle.Framed,
        danger = true,
        dangerColor = SocialDangerText,
        tooltipPlacement = TooltipPlacement.BelowStart,
        restTint = SocialTextPrimary,
        frameSize = DockCloseSize,
    )
}
