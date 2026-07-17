package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

internal val isMinecraftTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalTheme.current.previewImage.startsWith("minecraft")

@Composable
@ReadOnlyComposable
internal fun ppShape(radius: Dp): Shape =
    if (isMinecraftTheme) RectangleShape else RoundedCornerShape(radius)

@Composable
@ReadOnlyComposable
internal fun ppShapeOf(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape =
    if (isMinecraftTheme) RectangleShape
    else RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
