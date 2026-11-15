// OneConfig's public UI API is still marked experimental
@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.PaddingValues
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeColors
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.launcher.OneLauncherAccounts
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus
import org.polyfrost.polyplus.client.social.PlayerStatusRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable

@Composable
internal fun AccountSwitcherContent(actions: AccountActions, onClose: () -> Unit) {
    // list() is in add order, newest last
    val accounts = actions.accounts
    val idle = actions.idle

    LaunchedEffect(Unit) { actions.reload() }

    CompositionLocalProvider(LocalDensity provides scaledDensity(SOCIAL_UI_SCALE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialWindowScrim)
                .notFocusable()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 1180.dp)
                    .fillMaxHeight(0.86f)
                    .heightIn(max = 760.dp)
                    .clip(SocialWindowShape)
                    .socialGlow()
                    .background(SocialWindowBackground)
                    .border(SocialBorderWidth, SocialBorderColor, SocialWindowShape)
                    .notFocusable()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Announce("Accounts")
                    Text("Accounts", style = OneConfigTheme.current.typography.body.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        icon = SOCIAL_ASSETS + "x-close.svg",
                        contentDescription = "Close",
                        onClick = onClose,
                        modifier = Modifier.size(34.dp),
                        tint = OneConfigTheme.current.textColor,
                        tooltipPlacement = TooltipPlacement.BelowStart,
                    )
                }
                Divider()
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    // the live preview reads the session user, so rebuild it when the active account changes
                    key(accounts?.firstOrNull { it.active }?.id) {
                        PlayerPreview(
                            Modifier.fillMaxWidth(0.35f).widthIn(max = 320.dp).fillMaxHeight(),
                            source = PlayerPreviewSource.LocalLive,
                            initialYaw = 180f + 22.9f,
                            live = true,
                            modelScale = 0.35F
                        )
                    }
                    Divider(vertical = true)
                    val scroll = rememberScrollState()
                    // scroll the whole column, as errors and the add buttons can outgrow short windows too
                    ScrollContainer(scroll, Modifier.weight(1f).fillMaxHeight(), scrollbar = false) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                when {
                                    accounts == null -> Text(
                                        "Loading…",
                                        color = SocialTextSecondary,
                                        style = OneConfigTheme.current.typography.body,
                                    )

                                    accounts.isEmpty() -> Text(
                                        "No accounts found in the launcher",
                                        color = SocialTextSecondary,
                                        style = OneConfigTheme.current.typography.body,
                                    )

                                    else -> accounts.forEach { account ->
                                        key(account.id) {
                                            AccountButton(
                                                account = account,
                                                enabled = idle,
                                                onClick = { actions.switchTo(account) },
                                                onRemove = { actions.remove(account) },
                                                onRefresh = { actions.refresh(account) },
                                                refreshing = actions.refreshingId == account.id,
                                            )
                                        }
                                    }
                                }
                            }
                            AccountStatusText(actions.busy, actions.error, actions.errorSteps)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    text = "Add Microsoft account",
                                    icon = ASSETS + "globe-01.svg",
                                    onClick = actions::addMicrosoft,
                                    enabled = idle,
                                )
                                TextButton(
                                    text = "Add offline account",
                                    icon = ASSETS + "plus.svg",
                                    onClick = actions::openOfflineEntry,
                                    enabled = idle,
                                )
                            }

                        }
                    }
                }
            }
        }

        if (actions.offlineEntry) {
            OfflineAccountPopup(
                busy = actions.busy,
                error = actions.error,
                onSubmit = actions::addOffline,
                onCancel = actions::cancelOfflineEntry,
            )
        }

        val session = actions.loginSession
        if (session != null) {
            MicrosoftLoginPopup(
                code = session.userCode,
                verificationUri = session.verificationUri,
                browserAuthUrl = session.browserAuthUrl,
                status = actions.busy,
                onCancel = actions::cancelLogin,
            )
        }
    }
}

@Composable
fun AccountButton(
    account: OneLauncherAccounts.Account,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean = false,
) {
    val theme = OneConfigTheme.current
    var confirmRemove by remember { mutableStateOf(false) }
    val clickable = enabled && !account.active && !confirmRemove
    val accountLabel = account.username + ", " + if (account.microsoft) "Microsoft account" else "Offline account"
    val dimAlpha = theme.disabledAlpha
    val rowAlpha = when {
        refreshing -> REFRESHING_ALPHA
        enabled || account.active -> 1f
        else -> dimAlpha
    }
    val borderColor = when {
        account.expired -> SocialDangerColor
        account.active -> OneConfigTheme.accent
        else -> theme.borderColor
    }
    val shape = OneConfigTheme.shape(8.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .oneConfigClickable(accountLabel, onClick, selected = account.active.takeIf { it }, enabled = clickable, shape = shape)
            .graphicsLayer { alpha = rowAlpha }
            .fillMaxWidth()
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerHead(account.id, 36.dp, Modifier)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    account.username,
                    modifier = Modifier.weight(1f, fill = false),
                    color = theme.textColor,
                    style = OneConfigTheme.current.typography.body.copy(fontSize = 20.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    textAlign = TextAlign.Right,
                )
                if (account.active) AccountBadge(ASSETS + "check-circle.svg", "Default", OneConfigTheme.accent)
                if (account.active && account.microsoft && PrivacyConsent.allowsOnlineServices()) AccountStatusBadge()
                if (account.expired) AccountBadge(ASSETS + "alert-triangle.svg", "Expired", SocialDangerText)
            }
            Text(
                if (confirmRemove) "Remove this account?"
                else (if (account.microsoft) "Microsoft" else "Offline") + " · " + account.id,
                color = if (confirmRemove) SocialDangerText else theme.textColorSecondary,
                style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                textAlign = TextAlign.Right,
            )
        }
        if (confirmRemove) {
            AccountActionIcon(ASSETS + "check-circle.svg", SocialDangerColor, enabled, "Confirm remove ${account.username}") {
                confirmRemove = false
                onRemove()
            }
            AccountActionIcon(ASSETS + "x-close.svg", SocialTextSecondary, enabled, "Keep ${account.username}") { confirmRemove = false }
        } else {
            if (account.refreshable) {
                RefreshAccountButton(
                    color = if (account.expired) SocialDangerText else SocialTextSecondary,
                    enabled = enabled,
                    spinning = refreshing,
                    onClick = onRefresh,
                )
            }
            AccountActionIcon(ASSETS + "trash-01.svg", SocialTextSecondary, enabled, "Remove ${account.username}") { confirmRemove = true }
        }
    }
}

@Composable
private fun AccountActionIcon(icon: String, color: Color, enabled: Boolean, label: String, onClick: () -> Unit) {
    IconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        enabled = enabled,
        tooltip = null,
        tint = color,
        frameSize = 30.dp,
        iconSize = 18.dp,
    )
}

@Composable
private fun AccountStatusBadge() {
    val status by PlayerStatusRepository.status.collectAsState()
    val hidden = status == PlayerStatus.Offline
    val shape = OneConfigTheme.current.shapes.pill
    Badge(
        selfStatusLabel(status),
        Modifier.oneConfigClickable(selfStatusLabel(status), { PlayerStatusRepository.set(if (hidden) PlayerStatus.Online else PlayerStatus.Offline) }, shape = shape),
        tone = if (hidden) BadgeTone.Neutral else BadgeTone.Success,
        dot = true,
    )
}

@Composable
private fun RefreshAccountButton(color: Color, enabled: Boolean, spinning: Boolean, onClick: () -> Unit) {
    val rotation = if (spinning) {
        val angle by Motion.animateInfiniteFloat(
            initial = 0f,
            target = 360f,
            spec = infiniteRepeatable(tween(REFRESH_SPIN_MS, easing = LinearEasing)),
        )
        angle
    } else 0f
    val dimAlpha = OneConfigTheme.current.disabledAlpha
    val shape = OneConfigTheme.shape(6.dp)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(shape)
            .graphicsLayer { alpha = if (enabled || spinning) 1f else dimAlpha }
            .oneConfigClickable("Refresh account", onClick, enabled = enabled, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        ThemedIcon(ASSETS + "refresh-cw-01.svg", color, 18.dp, Modifier.rotate(rotation))
    }
}

@Composable
private fun Modifier.socialGlow(opacity: Float = 0.25f): Modifier {
    val accent = OneConfigTheme.accent
    return this.drawBehind {
        val glowRadius = size.maxDimension * 0.7f
        val topLeft = Offset(size.width * 0.12f, -size.height * 0.15f)
        val bottomRight = Offset(size.width * 0.88f, size.height * 1.15f)
        for (center in listOf(topLeft, bottomRight)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = opacity), accent.copy(alpha = 0f)),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )
        }
    }
}

private const val REFRESH_SPIN_MS = 800

private const val REFRESHING_ALPHA = 0.7f

private const val SOCIAL_UI_SCALE = 1.3f

@Composable
private fun AccountBadge(icon: String, text: String, color: Color) {
    val theme = OneConfigTheme.current
    Badge(
        text,
        style = BadgeStyle.Hairline,
        icon = icon,
        colors = BadgeColors(container = theme.componentBackground, content = color, border = color),
        shape = theme.circleShape,
        labelStyle = TextStyle(fontSize = 10.sp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    )
}
