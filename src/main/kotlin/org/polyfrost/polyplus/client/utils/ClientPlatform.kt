package org.polyfrost.polyplus.client.utils

import net.minecraft.client.Minecraft
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerModelType
//?} else {
/*import net.minecraft.client.resources.PlayerSkin
*///?}
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

    fun localPlayerUuid(): UUID = Minecraft.getInstance().user.profileId

    fun localPlayerName(): String = Minecraft.getInstance().user.name

    /**
     * Whether the local player's skin uses the slim ("Alex") arm model. Used to
     * auto-pick the matching slim/wide variant of a cosmetic at equip time.
     */
    fun localSkinSlim(): Boolean =
        //? if >= 1.21.10 {
        Minecraft.getInstance().player?.skin?.model() == PlayerModelType.SLIM
        //?} else {
        /*Minecraft.getInstance().player?.skin?.model() == PlayerSkin.Model.SLIM
        *///?}
}
