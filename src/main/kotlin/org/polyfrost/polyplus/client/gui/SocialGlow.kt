package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import org.polyfrost.oneconfig.internal.ui.themes.Accent

@Composable
internal fun Modifier.socialGlow(opacity: Float = 0.25f): Modifier {
    val accent = Accent
    return this.drawBehind {
        val glowRadius = size.maxDimension * 0.7f
        val topLeft = Offset(size.width * 0.12f, -size.height * 0.15f)
        val bottomRight = Offset(size.width * 0.88f, size.height * 1.15f)
        for (center in listOf(topLeft, bottomRight)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = opacity), accent.copy(alpha = 0f)),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )
        }
    }
}
