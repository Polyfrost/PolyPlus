package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.concurrent.atomic.AtomicInteger

object SocialErrors {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private val hosts = AtomicInteger(0)

    internal fun hostShown() {
        hosts.incrementAndGet()
    }

    internal fun hostHidden() {
        hosts.decrementAndGet()
    }

    private fun hostOnScreen(): Boolean = hosts.get() > 0 && ClientPlatform.currentScreen() != null

    fun emit(message: String) {
        if (hostOnScreen()) {
            _events.tryEmit(message)
        } else {
            runCatching { Notifications.error("PolyPlus", message) }
        }
    }

    fun emit(fallback: String, cause: Throwable) {
        emit(cause.toUserMessage(fallback))
    }
}

private val SERVER_TEXT = Regex("Text: \"(.*)\"$", RegexOption.DOT_MATCHES_ALL)

fun Throwable.toUserMessage(fallback: String): String {
    val extracted = message?.let { SERVER_TEXT.find(it)?.groupValues?.get(1)?.trim() }
    return if (!extracted.isNullOrBlank()) extracted else fallback
}
