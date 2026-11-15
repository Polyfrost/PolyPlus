@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.ping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuItem
import org.polyfrost.polyplus.client.gui.HudRadialMenu
import org.polyfrost.polyplus.client.gui.WheelOverlay
import org.polyfrost.polyplus.client.gui.WheelSession
import org.polyfrost.polyplus.client.gui.isMinecraftTheme

internal object PingWheel {
    fun open(): Boolean {
        val target = PingTargeting.raycast(1f)
        val shared = PartyPings.sharesPings()
        return WheelOverlay.open(
            WheelSession(
                held = PingInput::isHeld,
                onPick = { index -> PingInput.sendFromWheel(WHEEL_KINDS[index], target) },
                content = { session -> PingWheelContent(session, target, shared) },
            ),
        )
    }
}

internal object PingReplyWheel {
    fun open(ping: Ping): Boolean {
        val current = PartyPings.myReply(ping.id)
        return WheelOverlay.open(
            WheelSession(
                held = PingInput::isHeld,
                onPick = { index -> PingInput.replyFromWheel(ping, WHEEL_REPLIES[index]) },
                content = { session -> PingReplyContent(session, ping, current) },
            ),
        )
    }
}

@Composable
private fun PingReplyContent(session: WheelSession, ping: Ping, current: PingReply?) {
    val pixel = isMinecraftTheme
    val entries = remember(pixel, current) {
        WHEEL_REPLIES.map { reply ->
            RadialMenuItem(
                label = reply.title,
                detail = if (reply == current) "Pick again to take it back" else reply.hint,
                dot = Color(0xFF000000.toInt() or reply.color),
                drawIcon = { size, ink -> drawReplyIcon(reply, size, ink, pixel) },
            )
        }
    }
    HudRadialMenu(
        items = entries,
        session = session,
        title = "Reply",
        subtitle = PingTargeting.localLabel(ping.label) ?: ping.kind.title,
    )
}

@Composable
private fun PingWheelContent(session: WheelSession, target: PingHit?, shared: Boolean) {
    val pixel = isMinecraftTheme
    val entries = remember(pixel) {
        WHEEL_KINDS.map { kind ->
            RadialMenuItem(
                label = kind.title,
                detail = kind.hint,
                dot = Color(0xFF000000.toInt() or kind.color),
                drawIcon = { size, ink -> drawPingIcon(kind, size, ink, pixel) },
            )
        }
    }
    HudRadialMenu(
        items = entries,
        session = session,
        title = target?.label ?: "Ping",
        subtitle = if (shared) "Your party sees this" else "Only you see this",
    )
}
