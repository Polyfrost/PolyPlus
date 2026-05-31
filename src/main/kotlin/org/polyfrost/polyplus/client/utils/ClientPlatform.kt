package org.polyfrost.polyplus.client.utils

import net.minecraft.client.Minecraft
import java.util.UUID

object ClientPlatform {
    val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("windows")

    val isMac: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    val isLinux: Boolean
        get() = System.getProperty("os.name").lowercase().contains("linux")

    fun runOnMain(action: () -> Unit) {
        val client = Minecraft.getInstance()
        if (client.isSameThread) {
            action()
        } else {
            client.execute(action)
        }
    }

    fun localPlayerUuid(): UUID = Minecraft.getInstance().user.profileId

    fun localPlayerName(): String = Minecraft.getInstance().user.name
}
