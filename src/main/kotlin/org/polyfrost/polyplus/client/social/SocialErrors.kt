package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SocialErrors {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun emit(message: String) {
        _events.tryEmit(message)
    }

    fun emit(fallback: String, cause: Throwable) {
        _events.tryEmit(cause.toUserMessage(fallback))
    }
}

private val SERVER_TEXT = Regex("Text: \"(.*)\"$", RegexOption.DOT_MATCHES_ALL)

fun Throwable.toUserMessage(fallback: String): String {
    val extracted = message?.let { SERVER_TEXT.find(it)?.groupValues?.get(1)?.trim() }
    return if (!extracted.isNullOrBlank()) extracted else fallback
}
