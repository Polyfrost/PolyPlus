package org.polyfrost.polyplus.client.party

import org.polyfrost.oneconfig.api.notifications.v1.Notification
import org.polyfrost.oneconfig.api.notifications.v1.NotificationAction
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.oneconfig.api.notifications.v1.NotificationsManager
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.SocialOverlayScreen
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal object PartyNotices {
    const val INVITE_MS = 30_000f
    const val MOVE_MS = 60_000f

    fun send(
        message: String,
        duration: Float,
        join: String,
        onJoin: () -> Unit,
        dismiss: String? = null,
        onDismiss: () -> Unit = {},
    ): Notification {
        val builder = Notifications.builder("PolyPlus", message)
            .duration(duration)
            .action(NotificationAction(join, primary = true, onClick = { ClientPlatform.runOnMain(onJoin) }))
        if (dismiss != null) builder.action(NotificationAction(dismiss, onClick = { ClientPlatform.runOnMain(onDismiss) }))
        return builder.send()
    }

    fun retire(notification: Notification) = NotificationsManager.remove(notification)

    fun showDialogs() = ClientPlatform.runOnMain {
        when (ClientPlatform.currentScreen()) {
            is SocialOverlayScreen, is PolyPlusLobbyScreen -> Unit
            else -> SocialOverlay.open()
        }
    }
}
