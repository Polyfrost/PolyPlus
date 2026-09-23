package org.polyfrost.polyplus.client.utils

//? if > 1.8.9
import com.mojang.blaze3d.vertex.PoseStack
//? if = 1.8.9
//import org.polyfrost.polyplus.client.render.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.joml.Quaternionf
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.utils.v1.Multithreading
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerModelType
//?}

//? if < 1.21.10 && > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?}

object ClientPlatform {
    val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("windows")

    val isMac: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    fun monitorRefreshRate(): Int =
        //? if >= 26.3 {
        Minecraft.getInstance().window.activeVideoMode?.refreshRate?.toInt() ?: 0
        //?} else {
        /*Minecraft.getInstance().window.refreshRate
        *///?}

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

    fun setScreen(screen: Screen?) {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        mc.gui.setScreen(screen)
        //?} else {
        /*mc.setScreen(screen)
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
        //?} elif > 1.8.9 {
        /*Minecraft.getInstance().player?.skin?.model() == PlayerSkin.Model.SLIM
        *///?} else {
        /*Minecraft.getInstance().player?.modelType == "slim"
        *///?}
}

//? if = 1.8.9 {
/*private val Minecraft.isSameThread: Boolean get() = isOnSameThread
private fun Minecraft.execute(task: () -> Unit) { executeTask(Runnable(task)) }
private fun Minecraft.setScreen(screen: Screen?) = openScreen(screen)
*///?}

//? if > 1.8.9 {
fun PoseStack.rotateBy(rotation: Quaternionf) {
    //? if >= 26.3 {
    rotate(rotation)
    //?} else {
    /*mulPose(rotation)
    *///?}
}
//?} else {
/*fun PoseStack.rotateBy(rotation: Quaternionf) = mulPose(rotation)
*///?}
