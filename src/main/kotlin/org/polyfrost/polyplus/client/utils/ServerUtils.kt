package org.polyfrost.polyplus.client.utils

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.hypixel.v1.HypixelUtils

object ServerUtils {
    @JvmStatic
    fun isOnHypixel(): Boolean {
        if (HypixelUtils.isHypixel()) return true
        val minecraft = Minecraft.getInstance()
        if (minecraft.connection?.serverBrand()?.contains("hypixel", ignoreCase = true) == true) return true
        val host = minecraft.currentServer?.ip?.substringBefore(':') ?: return false
        return host.equals("hypixel.net", ignoreCase = true) || host.endsWith(".hypixel.net", ignoreCase = true)
    }
}
//?}
