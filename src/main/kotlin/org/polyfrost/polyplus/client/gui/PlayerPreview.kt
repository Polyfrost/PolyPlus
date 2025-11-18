package org.polyfrost.polyplus.client.gui

import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.entity.player.EntityPlayer
import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.unit.Vec2

fun PlayerPreview(
    player: EntityPlayer,
    size: Vec2,
): Drawable {
    return object : Drawable(
        size = size
    ) {
        override fun render() {
//            val posX = this.x.toInt()
//            val posY = this.y.toInt()
//            val width = this.width
//            val height = this.height
//            val scale = ((if (width < height) width else height) / 1.5f).toInt()
//            GuiInventory.drawEntityOnScreen(posX, posY, scale, 0f, 0f, player)
        }
    }.named("PlayerPreview")
}
