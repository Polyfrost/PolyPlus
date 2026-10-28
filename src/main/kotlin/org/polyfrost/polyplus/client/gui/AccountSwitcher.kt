// OneConfig's public UI API is still marked experimental
@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.launcher.OneLauncherAccounts

@Composable
internal fun AccountSwitcherContent(actions: AccountActions, onClose: () -> Unit) {
    var offlineEntry by remember { mutableStateOf(false) }
    // list() is in add order, newest last
    val accounts = actions.accounts
    val idle = actions.idle

    LaunchedEffect(Unit) { actions.reload() }

    CompositionLocalProvider(LocalDensity provides scaledDensity(SOCIAL_UI_SCALE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialWindowScrim)
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
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SocialText(text = "Accounts", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    SocialIconButton(SOCIAL_ASSETS + "x-close.svg", tooltip = "Close", onClick = onClose)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
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
                    Box(Modifier.width(1.dp).fillMaxHeight().background(SocialBorderColor))
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            when {
                                accounts == null -> SocialText(
                                    "Loading…",
                                    fontSize = 14.sp,
                                    color = SocialTextSecondary
                                )

                                accounts.isEmpty() -> SocialText(
                                    "No accounts found in the launcher",
                                    fontSize = 14.sp,
                                    color = SocialTextSecondary,
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
                            AddAccountButton(
                                icon = ASSETS + "globe-01.svg",
                                text = "Add Microsoft account",
                                enabled = idle,
                                onClick = actions::addMicrosoft,
                            )
                            AddAccountButton(
                                icon = ASSETS + "plus.svg",
                                text = "Add offline account",
                                enabled = idle,
                                onClick = {
                                    actions.clearError()
                                    offlineEntry = true
                                },
                            )
                        }

                    }
                }
            }
        }

        if (offlineEntry) {
            OfflineAccountPopup(
                busy = actions.busy,
                error = actions.error,
                onSubmit = { username -> actions.addOffline(username) { offlineEntry = false } },
                onCancel = {
                    actions.clearError()
                    offlineEntry = false
                },
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
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var confirmRemove by remember { mutableStateOf(false) }
    val clickable = enabled && !account.active && !confirmRemove
    val borderColor = when {
        account.expired -> SocialDangerColor
        account.active -> OneConfigTheme.accent
        else -> OneConfigTheme.current.borderColor
    }

    Row(
        modifier = Modifier
            .clip(ppShape(8.dp))
            .background(if (hovered && clickable) OneConfigTheme.current.componentBackground.copy(alpha = 0.4f) else Color.Transparent)
            .hoverable(interaction)
            .then(if (clickable) Modifier.clickableWithSound(onClick) else Modifier)
            .alpha(if (refreshing) 0.7f else if (enabled || account.active) 1f else 0.5f)
            .fillMaxWidth()
            .border(1.dp, borderColor, ppShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialAvatar(account.id.toString(), 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SocialText(
                    account.username,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f, fill = false),
                    color = OneConfigTheme.current.textColor,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (account.active) AccountBadge(ASSETS + "check-circle.svg", "Default", OneConfigTheme.accent)
                if (account.expired) AccountBadge(ASSETS + "alert-triangle.svg", "Expired", SocialDangerColor)
            }
            SocialText(
                if (confirmRemove) "Remove this account?"
                else (if (account.microsoft) "Microsoft" else "Offline") + " · " + account.id,
                fontSize = 13.sp,
                color = if (confirmRemove) SocialDangerColor else OneConfigTheme.current.textColorSecondary,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (confirmRemove) {
            AccountActionIcon(ASSETS + "check-circle.svg", SocialDangerColor, enabled) {
                confirmRemove = false
                onRemove()
            }
            AccountActionIcon(ASSETS + "x-close.svg", SocialTextSecondary, enabled) { confirmRemove = false }
        } else {
            if (account.refreshable) {
                RefreshAccountButton(
                    color = if (account.expired) SocialDangerColor else SocialTextSecondary,
                    enabled = enabled,
                    spinning = refreshing,
                    onClick = onRefresh,
                )
            }
            AccountActionIcon(ASSETS + "trash-01.svg", SocialTextSecondary, enabled) { confirmRemove = true }
        }
    }
}

@Composable
private fun AccountBadge(icon: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(OneConfigTheme.current.componentBackground)
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, color, Modifier.size(12.dp))
        SocialText(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun RefreshAccountButton(color: Color, enabled: Boolean, spinning: Boolean, onClick: () -> Unit) {
    val rotation = if (spinning) {
        val transition = rememberInfiniteTransition()
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(REFRESH_SPIN_MS, easing = LinearEasing)),
        )
        angle
    } else 0f
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(ppShape(6.dp))
            .alpha(if (enabled || spinning) 1f else 0.4f)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ASSETS + "refresh-cw-01.svg", color, Modifier.size(18.dp).rotate(rotation))
    }
}

private const val REFRESH_SPIN_MS = 800

private const val SOCIAL_UI_SCALE = 1.3f
