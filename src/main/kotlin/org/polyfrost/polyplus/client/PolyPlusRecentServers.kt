package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class RecentServer(val name: String, val ip: String, val lastPlayed: Long = 0L)

object PolyPlusRecentServers {
    private const val RECORDED_MAX = 32

    @Serializable
    private data class State(val recents: List<RecentServer> = emptyList())

    //? if > 1.8.9 {
    private val file: File by lazy { File(Minecraft.getInstance().gameDirectory, "polyplus/recent_servers.json") }
    //?} else {
    /*private val file: File by lazy { File(Minecraft.getInstance().gameDir, "polyplus/recent_servers.json") }
    *///?}
    private val recents = mutableListOf<RecentServer>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) {
                val state = PolyPlusClient.JSON.decodeFromString(State.serializer(), file.readText())
                recents.clear()
                recents.addAll(state.recents)
            }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(PolyPlusClient.JSON.encodeToString(State.serializer(), State(recents.toList())))
        }
    }

    @JvmStatic
    @Synchronized
    fun record(name: String?, ip: String?) {
        val address = ip?.trim().orEmpty()
        if (address.isEmpty()) return
        ensureLoaded()

        val entry = RecentServer(name?.trim().takeUnless { it.isNullOrEmpty() } ?: address, address, System.currentTimeMillis())
        recents.removeAll { it.ip.equals(address, ignoreCase = true) }
        recents.add(0, entry)
        while (recents.size > RECORDED_MAX) recents.removeAt(recents.lastIndex)

        persist()
    }

    @Synchronized
    fun recent(): List<RecentServer> {
        ensureLoaded()
        return recents.toList()
    }
}
