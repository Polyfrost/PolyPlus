package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.social.SocialErrors
import java.util.concurrent.atomic.AtomicLong

private val nextToastId = AtomicLong(0)

private data class SocialToast(val id: Long, val message: String)

@Composable
internal fun SocialToastHost(modifier: Modifier = Modifier) {
    var toasts by remember { mutableStateOf(listOf<SocialToast>()) }

    LaunchedEffect(Unit) {
        SocialErrors.events.collect { message ->
            toasts = toasts + SocialToast(nextToastId.incrementAndGet(), message)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        toasts.forEach { toast ->
            key(toast.id) {
                LaunchedEffect(toast.id) {
                    delay(5000)
                    toasts = toasts.filterNot { it.id == toast.id }
                }
                SocialToastBubble(toast.message) { toasts = toasts.filterNot { it.id == toast.id } }
            }
        }
    }
}

@Composable
private fun SocialToastBubble(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .clip(SocialPanelShape)
            .background(SocialPopupBackground)
            .border(SocialBorderWidth, SocialDangerColor, SocialPanelShape)
            .clickableWithSound(onDismiss)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SOCIAL_ASSETS + "x-close.svg", SocialDangerColor, Modifier.padding(top = 1.dp).size(16.dp))
        SocialText(message, fontSize = 13.sp, color = LocalTheme.current.textColor)
    }
}
