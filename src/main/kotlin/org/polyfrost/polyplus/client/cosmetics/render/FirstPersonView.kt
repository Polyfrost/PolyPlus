package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.client.Minecraft

internal object FirstPersonView {
    @JvmStatic
    fun ownEyes(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        //? if > 1.8.9 {
        return mc.options.cameraType.isFirstPerson && mc.cameraEntity === player
        //?} else {
        /*return mc.options.perspective == 0 && mc.camera === player
        *///?}
    }
}
