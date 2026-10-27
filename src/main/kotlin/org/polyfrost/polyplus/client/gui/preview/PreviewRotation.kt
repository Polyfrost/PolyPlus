package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PreviewRotation(initialYaw: Float) {
    var yaw by mutableFloatStateOf(initialYaw)
    var pitch by mutableFloatStateOf(0f)
    var userRotated by mutableStateOf(false)
}
