package org.polyfrost.polyplus.client.ping

import net.minecraft.client.Minecraft

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
*///?} else {
/*import net.minecraft.client.gui.GuiElement
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.platform.Lighting
import net.minecraft.item.ItemStack
*///?}

//? if >= 26.1 {
typealias PingGraphics = GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*typealias PingGraphics = GuiGraphics
*///?} else {
/*typealias PingGraphics = Any
*///?}

internal class PingCanvas(private val graphics: PingGraphics?) {
    val lineHeight: Int = 9

    fun fill(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        //? if > 1.8.9 {
        graphics?.fill(x0, y0, x1, y1, color)
        //?} else {
        /*GuiElement.fill(x0, y0, x1, y1, color)
        *///?}
    }

    fun text(value: String, x: Int, y: Int, color: Int) {
        //? if >= 26.1 {
        graphics?.text(Minecraft.getInstance().font, value, x, y, color)
        //?} elif > 1.8.9 {
        /*graphics?.drawString(Minecraft.getInstance().font, value, x, y, color)
        *///?} else {
        /*GlStateManager.enableBlend()
        Minecraft.getInstance().textRenderer.drawWithShadow(value, x.toFloat(), y.toFloat(), color)
        *///?}
    }

    fun width(value: String): Int {
        //? if > 1.8.9 {
        return Minecraft.getInstance().font.width(value)
        //?} else {
        /*return Minecraft.getInstance().textRenderer.getWidth(value)
        *///?}
    }

    fun item(stack: ItemStack, x: Int, y: Int) {
        //? if >= 26.1 {
        graphics?.item(stack, x, y)
        //?} elif > 1.8.9 {
        /*graphics?.renderItem(stack, x, y)
        *///?} else {
        /*Lighting.turnOnGui()
        Minecraft.getInstance().itemRenderer.renderGuiItem(stack, x, y)
        Lighting.turnOff()
        GlStateManager.enableBlend()
        *///?}
    }

    fun push(x: Float, y: Float, scale: Float) {
        //? if >= 1.21.6 {
        val pose = graphics?.pose() ?: return
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        //?} elif > 1.8.9 {
        /*val pose = graphics?.pose() ?: return
        pose.pushPose()
        pose.translate(x.toDouble(), y.toDouble(), 0.0)
        pose.scale(scale, scale, 1f)
        *///?} else {
        /*GlStateManager.pushMatrix()
        GlStateManager.translatef(x, y, 0f)
        GlStateManager.scalef(scale, scale, 1f)
        *///?}
    }

    fun raise() {
        //? if >= 1.21.8 {
        graphics?.nextStratum()
        //?}
    }

    fun pop() {
        //? if >= 1.21.6 {
        graphics?.pose()?.popMatrix()
        //?} elif > 1.8.9 {
        /*graphics?.pose()?.popPose()
        *///?} else {
        /*GlStateManager.popMatrix()
        *///?}
    }
}
