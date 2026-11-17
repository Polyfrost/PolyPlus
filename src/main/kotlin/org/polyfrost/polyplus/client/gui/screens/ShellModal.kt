package org.polyfrost.polyplus.client.gui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal object ShellModal {
    val MaxWidth = 980.dp
    val MaxHeight = 640.dp
    val Radius = 20.dp
    const val WIDTH_SHARE = 0.7f
    const val HEIGHT_SHARE = 0.8f
    const val UI_SCALE = 1.3f
}

internal fun Modifier.shellModalSize(): Modifier = this
    .fillMaxWidth(ShellModal.WIDTH_SHARE)
    .widthIn(max = ShellModal.MaxWidth)
    .fillMaxHeight(ShellModal.HEIGHT_SHARE)
    .heightIn(max = ShellModal.MaxHeight)
