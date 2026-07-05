package org.polyfrost.polyplus.client

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import java.io.File

@Serializable
data class RecentServer(val name: String, val ip: String)

object PolyPlusRecentServers {
    private const val MAX = 3

    @Serializable
    private data class State(
        val recents: List<RecentServer> = emptyList(),
        val lastDirect: RecentServer? = null,
    )

    private val file: File by lazy { File(Minecraft.getInstance().gameDirectory, "polyplus/recent_servers.json") }
    private val recents = mutableListOf<RecentServer>()
    private var lastDirect: RecentServer? = null
    private var loaded = false
    private var cachedDisplayServers: List<ServerData>? = null

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) {
                val state = PolyPlusClient.JSON.decodeFromString(State.serializer(), file.readText())
                recents.clear()
                recents.addAll(state.recents)
                lastDirect = state.lastDirect
            }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(PolyPlusClient.JSON.encodeToString(State.serializer(), State(recents.toList(), lastDirect)))
        }
    }

    @JvmStatic
    @Synchronized
    fun record(name: String?, ip: String?) {
        val address = ip?.trim().orEmpty()
        if (address.isEmpty()) return
        ensureLoaded()

        val entry = RecentServer(name?.trim().takeUnless { it.isNullOrEmpty() } ?: address, address)
        recents.removeAll { it.ip.equals(address, ignoreCase = true) }
        recents.add(0, entry)
        while (recents.size > MAX) recents.removeAt(recents.lastIndex)

        if (!isSaved(address)) lastDirect = entry

        cachedDisplayServers = null
        persist()
    }

    @Synchronized
    fun displayServers(): List<ServerData> {
        cachedDisplayServers?.let { return it }

        ensureLoaded()
        val saved = runCatching { loadServerList() }.getOrNull()
        val savedByIp = HashMap<String, ServerData>()
        if (saved != null) {
            for (i in 0 until saved.size()) {
                val data = saved.get(i)
                savedByIp.putIfAbsent(data.ip.lowercase(), data)
            }
        }

        val out = LinkedHashMap<String, ServerData>()
        fun add(name: String, ip: String) {
            val key = ip.lowercase()
            if (key in out || out.size >= MAX) return
            out[key] = savedByIp[key] ?: ServerData(name, ip, ServerData.Type.OTHER)
        }

        recents.forEach { add(it.name, it.ip) }
        if (saved != null) {
            var i = 0
            while (i < saved.size() && out.size < MAX) {
                val data = saved.get(i)
                add(data.name, data.ip)
                i++
            }
        }
        lastDirect?.let { add(it.name, it.ip) }

        return out.values.toList().also { cachedDisplayServers = it }
    }

    private fun isSaved(ip: String): Boolean = runCatching {
        val list = loadServerList()
        (0 until list.size()).any { list.get(it).ip.equals(ip, ignoreCase = true) }
    }.getOrDefault(false)

    private fun loadServerList(): ServerList =
        ServerList(Minecraft.getInstance()).apply { load() }
}
