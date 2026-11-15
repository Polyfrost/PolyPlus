@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import org.polyfrost.oneconfig.api.ui.v1.components.ToastStyle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.polyfrost.oneconfig.api.notifications.v1.NotificationType
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ToastHost
import org.polyfrost.oneconfig.api.ui.v1.components.Toasts
import org.polyfrost.polyplus.client.social.SocialErrors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private const val SOCIAL_TOAST_MILLIS = 5000L

private val shownHosts = ArrayList<Any>()

object SocialNotices {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun emit(message: String) {
        _events.tryEmit(message)
    }
}

@Composable
internal fun SocialToastHost(modifier: Modifier = Modifier, alignment: Alignment = Alignment.TopCenter, inPlace: Boolean = false) {
    val token = remember { Any() }
    val successInk by rememberUpdatedState(SocialSuccessText)

    DisposableEffect(token) {
        SocialErrors.hostShown()
        shownHosts += token
        onDispose {
            shownHosts -= token
            SocialErrors.hostHidden()
        }
    }

    LaunchedEffect(token) {
        launch {
            SocialErrors.events.collect { message ->
                if (shownHosts.lastOrNull() === token) Toasts.show(message, NotificationType.ERROR, SOCIAL_TOAST_MILLIS, ToastStyle(icon = SOCIAL_ASSETS + "x-close.svg", iconColor = SocialDangerColor, borderColor = SocialDangerColor))
            }
        }
        SocialNotices.events.collect { message ->
            if (shownHosts.lastOrNull() === token) Toasts.show(message, NotificationType.SUCCESS, SOCIAL_TOAST_MILLIS, ToastStyle(icon = SOCIAL_ASSETS + "check-circle.svg", iconColor = successInk))
        }
    }

    ToastHost(modifier, alignment, inPlace)
}
