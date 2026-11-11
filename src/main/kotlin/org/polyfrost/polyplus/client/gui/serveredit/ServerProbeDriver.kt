package org.polyfrost.polyplus.client.gui.serveredit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val PROBE_TICK_MILLIS = 50L
private const val PING_DEBOUNCE_MILLIS = 500L

@Composable
internal fun DriveServerProbe(probe: ServerStatusProbe, address: String, valid: Boolean, debounce: Boolean = true) {
    DisposableEffect(probe) { onDispose(probe::close) }
    LaunchedEffect(probe) {
        while (isActive) {
            probe.tick()
            delay(PROBE_TICK_MILLIS)
        }
    }
    LaunchedEffect(probe, address) {
        probe.reset(address)
        if (!valid) return@LaunchedEffect
        if (debounce) delay(PING_DEBOUNCE_MILLIS)
        probe.ping()
    }
}
