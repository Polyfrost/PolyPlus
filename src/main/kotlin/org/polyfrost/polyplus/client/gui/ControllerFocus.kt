package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import org.polyfrost.compose.host.controller.Controllers

internal fun Modifier.controllerFocusOnly(): Modifier {
    val active = Controllers.isActive
    return focusProperties { canFocus = active }
}

internal fun Modifier.focusableOnController(): Modifier = focusable(enabled = Controllers.isActive)
