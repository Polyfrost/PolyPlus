package org.polyfrost.polyplus.client.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.utils.v1.Multithreading
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerModelType
//?} else {
/*import net.minecraft.client.resources.PlayerSkin
*///?}
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

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

    fun <T> runOnMainSync(action: () -> T): T {
        val client = Minecraft.getInstance()
        if (client.isSameThread) {
            return action()
        }
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        client.execute {
            try {
                result.set(action())
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        error.get()?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result.get() as T
    }

    fun currentScreen(): Screen? {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        return mc.gui.screen()
        //?} else {
        /*return mc.screen
        *///?}
    }

    fun openUri(uri: String) {
        Multithreading.submit { DesktopHelper.browse(URI(uri)) }
    }

    fun localPlayerUuid(): UUID = Minecraft.getInstance().user.profileId

    fun localPlayerName(): String = Minecraft.getInstance().user.name

    fun localSkinSlim(): Boolean =
        //? if >= 1.21.10 {
        Minecraft.getInstance().player?.skin?.model() == PlayerModelType.SLIM
        //?} else {
        /*Minecraft.getInstance().player?.skin?.model() == PlayerSkin.Model.SLIM
        *///?}
}
