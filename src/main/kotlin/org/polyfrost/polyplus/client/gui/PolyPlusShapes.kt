@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

internal val isMinecraftTheme: Boolean
    @Composable
    get() = OneConfigTheme.current.name.let { it == OneConfigTheme.MINECRAFT_DARK.name || it == OneConfigTheme.MINECRAFT_LIGHT.name }

@Composable
internal fun ppShape(radius: Dp): Shape =
    if (isMinecraftTheme) RectangleShape else RoundedCornerShape(radius)

@Composable
internal fun ppShapeOf(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape =
    if (isMinecraftTheme) RectangleShape
    else RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
