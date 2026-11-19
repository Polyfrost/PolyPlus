@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.staticCompositionLocalOf
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigPrefs

internal val LocalScreenFit = staticCompositionLocalOf { 1f }

internal object MenuScale {
    private const val MIN_FACTOR = 0.5f
    private const val MAX_FACTOR = 2f
    private const val MIN_SCALE = 0.25f

    val factor: Float
        get() {
            val scale = runCatching { OneConfigPrefs.uiScale }.getOrDefault(1f)
            if (!scale.isFinite() || scale <= 0f) return 1f
            return scale.coerceIn(MIN_FACTOR, MAX_FACTOR)
        }

    fun scale(fit: Float, base: Float = fit): Float {
        val safeFit = if (fit.isFinite() && fit > 0f) fit else 1f
        val safeBase = if (base.isFinite() && base > 0f) base else safeFit
        return (safeBase * factor).coerceIn(minOf(MIN_SCALE, safeFit), safeFit)
    }
}
