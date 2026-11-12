package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
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

    private const val DEFAULT_NAME_KEY = "selectServer.defaultName"
    private const val DEFAULT_NAME = "Minecraft Server"

    @JvmStatic
    fun isDefaultName(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals(DEFAULT_NAME, ignoreCase = true)) return true
        val translated = runCatching { I18n.get(DEFAULT_NAME_KEY).trim() }.getOrNull()
        return translated != null && translated != DEFAULT_NAME_KEY && trimmed.equals(translated, ignoreCase = true)
    }

    private const val GENERIC_NAME = "Multiplayer server"
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d+)?$""")
    private val IPV6 = Regex("""^\[?[0-9a-fA-F:.]*:[0-9a-fA-F:.]*:[0-9a-fA-F:.]*(%[0-9a-zA-Z]+)?(]:\d+|])?$""")

    @JvmStatic
    fun isRawIp(address: String): Boolean {
        val host = address.trim()
        return IPV4.matches(host) || IPV6.matches(host)
    }

    @JvmStatic
    fun listLabel(name: String?, ip: String?): String {
        if (name != null && !isDefaultName(name) && !isRawIp(name)) return name.trim()
        val address = ip?.trim().orEmpty()
        val hide = runCatching { Minecraft.getInstance().options.hideServerAddress }.getOrDefault(false)
        if (address.isNotEmpty() && !hide && !isRawIp(address)) return address
        return GENERIC_NAME
    }
}
