package org.polyfrost.polyplus.client.gui

import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.component.impl.Group
import org.polyfrost.polyui.unit.Vec2

private const val CARD_WIDTH = 180f
private const val CARD_HEIGHT = 258f

fun CosmeticList(size: Vec2): Drawable {
    val columnCount = (size.x / CARD_WIDTH).toInt()
    val rowCount = (size.y / CARD_HEIGHT).toInt()
    return Group(
        size = size,
    ).named("CosmeticList")
}
