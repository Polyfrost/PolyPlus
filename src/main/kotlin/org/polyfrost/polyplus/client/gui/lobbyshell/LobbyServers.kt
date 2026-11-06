package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.gui.serveredit.CANT_CONNECT_KEY
import org.polyfrost.polyplus.client.gui.serveredit.CANT_RESOLVE_KEY
import org.polyfrost.polyplus.client.gui.serveredit.pingInto
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.server.LanServerDetection
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.contents.TranslatableContents
//? if >= 1.21.11
import net.minecraft.server.network.EventLoopGroupHolder
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.RecentServer
import org.polyfrost.polyplus.client.featured.FeaturedServer
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.featured.normalizeServerAddress
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal enum class ServerKind { SAVED, UNSAVED, LAN, FEATURED, SPONSORED }

internal class ServerEntry(
    val data: ServerData,
    val kind: ServerKind,
    val savedIndex: Int = -1,
    val featured: FeaturedServer? = null,
) {
    val key: String get() = "$kind:$savedIndex:${data.ip}"
}

internal class LobbyServers(private val minecraft: Minecraft) {
    private val pinger = ServerStatusPinger()
    private val list = ServerList(minecraft)
    private val unsaved = HashMap<String, ServerData>()
    private var lanList: LanServerDetection.LanServerList? = null
    private var lanDetector: LanServerDetection.LanServerDetector? = null
    private var featuredRevision = Long.MIN_VALUE

    var revision by mutableIntStateOf(0)
        private set
    var saved by mutableStateOf<List<ServerEntry>>(emptyList())
        private set
    var recent by mutableStateOf<List<ServerEntry>>(emptyList())
        private set
    var lan by mutableStateOf<List<ServerEntry>>(emptyList())
        private set
    var featured by mutableStateOf<List<ServerEntry>>(emptyList())
        private set
    var sponsored by mutableStateOf<List<ServerEntry>>(emptyList())
        private set
    var hero by mutableStateOf<ServerEntry?>(null)
        private set

    val serverList: ServerList get() = list

    fun open() {
        list.load()
        unsaved.clear()
        lan = emptyList()
        rebuild()
        FeaturedServers.warmUp()
        rebuildFeatured()
        val detected = LanServerDetection.LanServerList()
        lanList = detected
        runCatching {
            lanDetector = LanServerDetection.LanServerDetector(detected).also { it.start() }
        }.onFailure { LOGGER.warn("Unable to start LAN server detection", it) }
    }

    fun close() {
        lanDetector?.interrupt()
        lanDetector = null
        pinger.removeAll()
    }

    fun refresh() {
        close()
        open()
    }

    fun tick() {
        lanList?.takeDirtyServers()?.let { servers ->
            lan = servers.map { ServerEntry(ServerData(it.motd, it.address, ServerData.Type.LAN), ServerKind.LAN) }
        }
        pinger.tick()
        if (FeaturedServers.revision() != featuredRevision) rebuildFeatured()
        if (anyPinging()) revision++
    }

    fun ping(data: ServerData) {
        if (data.state() != ServerData.State.INITIAL || data.isLan) return
        data.setState(ServerData.State.PINGING)
        data.motd = CommonComponents.EMPTY
        data.status = CommonComponents.EMPTY
        PolyPlusClient.SCOPE.launch(PING_DISPATCHER) {
            pinger.pingInto(data, { ClientPlatform.runOnMain { persist() } }) { revision++ }
        }
    }

    fun rebuild() {
        saved = (0 until list.size()).map { ServerEntry(list.get(it), ServerKind.SAVED, savedIndex = it) }
        recent = PolyPlusRecentServers.recent().map(::entryFor)
        hero = recent.firstOrNull() ?: saved.firstOrNull()
        rebuildFeatured()
        revision++
    }

    fun reload() {
        list.load()
        rebuild()
    }

    fun persistAndRebuild() {
        list.save()
        rebuild()
    }

    fun move(entry: ServerEntry, direction: Int) {
        val target = entry.savedIndex + direction
        if (entry.savedIndex < 0 || target !in 0 until list.size()) return
        list.swap(entry.savedIndex, target)
        rebuild()
    }

    fun saveUnsaved(entry: ServerEntry) {
        if (list.unhide(entry.data.ip) == null) list.add(entry.data, false)
        persistAndRebuild()
    }

    fun quickRows(pinned: ServerData?): List<ServerEntry> {
        val rows = (recent + saved).distinctBy { it.data.ip.lowercase() }.take(QUICK_ROWS)
        if (pinned == null || rows.any { it.data.ip.equals(pinned.ip, ignoreCase = true) }) return rows
        return (listOf(ServerEntry(pinned, ServerKind.UNSAVED)) + rows).take(QUICK_ROWS)
    }

    fun resolve(old: ServerData): ServerData {
        if (old.isLan) return old
        val known = (saved + recent + featured + sponsored).firstOrNull { it.data.ip.equals(old.ip, ignoreCase = true) }
        return known?.data ?: unsavedData(old.name, old.ip)
    }

    fun dataFor(server: FeaturedServer): ServerData = unsavedData(server.name, server.address)

    private fun entryFor(server: RecentServer): ServerEntry {
        val known = list.get(server.ip)
        if (known != null) {
            val index = (0 until list.size()).firstOrNull { list.get(it) === known } ?: -1
            return ServerEntry(known, if (index >= 0) ServerKind.SAVED else ServerKind.UNSAVED, savedIndex = index)
        }
        return ServerEntry(unsavedData(server.name, server.ip), ServerKind.UNSAVED)
    }

    private fun unsavedData(name: String, ip: String): ServerData =
        unsaved.getOrPut(ip.lowercase()) { ServerData(name, ip, ServerData.Type.OTHER) }

    private fun rebuildFeatured() {
        featuredRevision = FeaturedServers.revision()
        val snapshot = FeaturedServers.snapshot()
        val now = System.currentTimeMillis()
        val savedAddresses = saved.mapTo(HashSet()) { normalizeServerAddress(it.data.ip) }
        fun rows(servers: List<FeaturedServer>, kind: ServerKind) = servers
            .filter { normalizeServerAddress(it.address) !in savedAddresses }
            .map { ServerEntry(unsavedData(it.name, it.address), kind, featured = it) }
        featured = rows(snapshot.featuredServers(now), ServerKind.FEATURED)
        sponsored = rows(snapshot.sponsoredServers(now), ServerKind.SPONSORED)
    }

    private fun persist() {
        list.save()
        revision++
    }

    private fun anyPinging(): Boolean =
        saved.any(::pinging) || recent.any(::pinging) || featured.any(::pinging) || sponsored.any(::pinging)

    private fun pinging(entry: ServerEntry) = entry.data.state() == ServerData.State.PINGING

    companion object {
        const val QUICK_ROWS = 3
        private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
        private val PING_DISPATCHER = Dispatchers.IO.limitedParallelism(5)

        fun failed(data: ServerData): Boolean {
            if (data.state() == ServerData.State.UNREACHABLE) return true
            val key = (data.motd?.contents as? TranslatableContents)?.key
            return key == CANT_CONNECT_KEY || key == CANT_RESOLVE_KEY
        }
    }
}
