@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

internal val hudBackdropGlass: Boolean
    get() {
        //? if >= 1.21.8 && < 1.21.11 {
        /*return false
        *///?} else {
        return runCatching { Platform.screen().supportsBackdropGlass() }.getOrDefault(false)
        //?}
    }

internal val hudPixelRatio: Float
    get() = runCatching { Platform.screen().pixelRatio() }.getOrDefault(1f).takeIf { it > 0f } ?: 1f
