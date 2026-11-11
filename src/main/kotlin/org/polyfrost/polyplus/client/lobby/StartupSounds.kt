package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

//? if > 1.8.9 {
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.InactiveProfiler
import org.polyfrost.polyplus.mixin.client.access.SimplePreparableReloadListenerInvoker
//?}

//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} elif > 1.8.9 {
/*import net.minecraft.Util
*///?}

object StartupSounds {
    @Volatile
    private var waiting = false

    @Volatile
    private var loaded = false

    @Volatile
    private var vanilla = false

    fun prepare() {
        if (!StartupOverlay.enabled()) return
        runCatching { Files.createDirectories(Paths.get("oneconfig", "sounds", "assets", "oneconfig", "sounds")) }
            .onFailure { LOBBY_LOGGER.warn("Could not prepare the OneConfig sound pack folder", it) }
    }

    fun gameLoaded() {
        loaded = true
        if (waiting) {
            waiting = false
            reloadSounds(Minecraft.getInstance())
        }
    }

    //? if > 1.8.9 {
    @JvmStatic
    fun reload(mc: Minecraft): CompletableFuture<Void> {
        if (!StartupOverlay.enabled() || vanilla) return mc.reloadResourcePacks()
        if (loaded) reloadSounds(mc) else waiting = true
        return CompletableFuture.completedFuture(null)
    }

    fun abandon() {
        vanilla = true
        if (!waiting) return
        waiting = false
        Minecraft.getInstance().reloadResourcePacks()
    }

    private fun reloadSounds(mc: Minecraft) {
        val listener = mc.soundManager as SimplePreparableReloadListenerInvoker
        val manager: ResourceManager = mc.resourceManager
        CompletableFuture
            .supplyAsync({ listener.`polyplus$prepare`(manager, InactiveProfiler.INSTANCE) }, Util.ioPool())
            .thenAcceptAsync({ listener.`polyplus$apply`(it, manager, InactiveProfiler.INSTANCE) }, LobbyCompat.mainExecutor(mc))
            .exceptionally {
                LOBBY_LOGGER.warn("Could not reload sounds after downloading them", it)
                null
            }
    }
    //?} else {
    /*@JvmStatic
    fun reload(mc: Minecraft) {
        if (StartupOverlay.enabled()) reloadSounds(mc) else mc.reloadResources()
    }

    private fun reloadSounds(mc: Minecraft) {
        runCatching { mc.soundManager.reload(mc.resourceManager) }
            .onFailure { LOBBY_LOGGER.warn("Could not reload sounds after downloading them", it) }
    }
    *///?}
}
