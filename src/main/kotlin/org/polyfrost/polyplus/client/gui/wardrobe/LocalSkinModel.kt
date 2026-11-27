package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal object LocalSkinModel {
    private const val POLL_MS = 250L

    private var current by mutableStateOf<Boolean?>(null)
    private var watchers = 0
    @Volatile
    private var poller: Job? = null

    val slim: Boolean
        get() = current ?: ClientPlatform.runOnMainSync { ClientPlatform.localSkinSlim() }

    @Synchronized
    fun watch() {
        watchers++
        if (poller != null) return
        poller = PolyPlusClient.SCOPE.launch {
            while (true) {
                runCatching { ClientPlatform.runOnMainSync(::refresh) }
                delay(POLL_MS)
            }
        }
    }

    @Synchronized
    fun unwatch() {
        if (--watchers > 0) return
        watchers = 0
        poller?.cancel()
        poller = null
        ClientPlatform.runOnMain { if (poller == null) current = null }
    }

    private fun refresh() {
        if (poller == null) return
        val slim = ClientPlatform.localSkinSlim()
        if (current != slim) current = slim
    }
}

@Composable
internal fun rememberLocalSkinSlim(): Boolean {
    DisposableEffect(Unit) {
        LocalSkinModel.watch()
        onDispose { LocalSkinModel.unwatch() }
    }
    return LocalSkinModel.slim
}
