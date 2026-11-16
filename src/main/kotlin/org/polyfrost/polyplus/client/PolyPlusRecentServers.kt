package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class RecentServer(
    val name: String,
    val ip: String,
    val lastPlayed: Long = 0L,
    val kind: String = PolyPlusRecentServers.KIND_SERVER,
    val realm: Long = 0L,
) {
    val isRealm: Boolean get() = kind == PolyPlusRecentServers.KIND_REALM
}

object PolyPlusRecentServers {
    private const val RECORDED_MAX = 32
    const val KIND_SERVER = "server"
    const val KIND_REALM = "realm"

    @Serializable
    private data class State(
        val recents: List<RecentServer> = emptyList(),
        val pinned: List<RecentServer> = emptyList(),
        val realmsCollapsed: Boolean = false,
    )

    const val PINNED_MAX = 4

    //? if > 1.8.9 {
    private val file: File by lazy { File(Minecraft.getInstance().gameDirectory, "polyplus/recent_servers.json") }
    //?} else {
    /*private val file: File by lazy { File(Minecraft.getInstance().gameDir, "polyplus/recent_servers.json") }
    *///?}
    private val recents = mutableListOf<RecentServer>()
    private val pins = mutableListOf<RecentServer>()
    private var realmsCollapsed = false
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) {
                val state = PolyPlusClient.JSON.decodeFromString(State.serializer(), file.readText())
                recents.clear()
                recents.addAll(state.recents)
                pins.clear()
                pins.addAll(state.pinned.distinctBy { it.ip.lowercase() }.take(PINNED_MAX))
                realmsCollapsed = state.realmsCollapsed
            }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val state = State(recents.toList(), pins.toList(), realmsCollapsed)
            file.writeText(PolyPlusClient.JSON.encodeToString(State.serializer(), state))
        }
    }

    fun realmKey(id: Long): String = "$KIND_REALM:$id"

    @Synchronized
    fun isRealmPinned(id: Long): Boolean {
        ensureLoaded()
        return pins.any { it.isRealm && it.realm == id }
    }

    @Synchronized
    fun setRealmPinned(id: Long, name: String?, pinned: Boolean) {
        ensureLoaded()
        if (pinned) {
            if (pins.size >= PINNED_MAX || pins.any { it.isRealm && it.realm == id }) return
            val label = name?.trim().takeUnless { it.isNullOrEmpty() } ?: "Realm"
            pins.add(RecentServer(label, realmKey(id), System.currentTimeMillis(), KIND_REALM, id))
        } else if (!pins.removeAll { it.isRealm && it.realm == id }) {
            return
        }
        persist()
    }

    @Synchronized
    fun renameRealmPin(id: Long, name: String) {
        ensureLoaded()
        val index = pins.indexOfFirst { it.isRealm && it.realm == id }
        if (index < 0 || name.isBlank() || pins[index].name == name) return
        pins[index] = pins[index].copy(name = name)
        persist()
    }

    @Synchronized
    fun realmsCollapsed(): Boolean {
        ensureLoaded()
        return realmsCollapsed
    }

    @Synchronized
    fun setRealmsCollapsed(collapsed: Boolean) {
        ensureLoaded()
        if (realmsCollapsed == collapsed) return
        realmsCollapsed = collapsed
        persist()
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

    @Synchronized
    fun pinned(): List<RecentServer> {
        ensureLoaded()
        return pins.toList()
    }

    @Synchronized
    fun isPinned(ip: String?): Boolean {
        ensureLoaded()
        return ip != null && pins.any { it.ip.equals(ip.trim(), ignoreCase = true) }
    }

    @Synchronized
    fun canPin(): Boolean {
        ensureLoaded()
        return pins.size < PINNED_MAX
    }

    @Synchronized
    fun setPinned(name: String?, ip: String?, pinned: Boolean) {
        val address = ip?.trim().orEmpty()
        if (address.isEmpty()) return
        ensureLoaded()
        if (pinned) {
            if (pins.size >= PINNED_MAX || pins.any { it.ip.equals(address, ignoreCase = true) }) return
            pins.add(RecentServer(name?.trim().takeUnless { it.isNullOrEmpty() } ?: address, address, System.currentTimeMillis()))
        } else if (!pins.removeAll { it.ip.equals(address, ignoreCase = true) }) {
            return
        }
        persist()
    }

    @Synchronized
    fun movePinned(from: Int, to: Int) {
        ensureLoaded()
        if (from == to || from !in pins.indices || to !in pins.indices) return
        pins.add(to, pins.removeAt(from))
        persist()
    }

    @Synchronized
    fun setPinnedOrder(ips: List<String>) {
        ensureLoaded()
        val keys = ips.map { it.trim().lowercase() }
        val ordered = pins.sortedBy { pin -> keys.indexOf(pin.ip.trim().lowercase()).let { if (it < 0) Int.MAX_VALUE else it } }
        if (ordered == pins) return
        pins.clear()
        pins.addAll(ordered)
        persist()
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
