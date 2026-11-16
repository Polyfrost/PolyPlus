package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.RecentServer
import org.polyfrost.polyplus.client.featured.FeaturedServer
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.featured.ServerFolders
import org.polyfrost.polyplus.client.featured.normalizeServerAddress
import org.polyfrost.polyplus.client.lobby.LOBBY_LOGGER
import org.polyfrost.polyplus.client.gui.pausescreen.PauseText
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.lobby.LobbyI18n*/
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.WeakHashMap

internal enum class ServerKind { SAVED, UNSAVED, LAN, FEATURED, SPONSORED }

internal class ServerEntry(
    val data: ServerData,
    val kind: ServerKind,
    val savedIndex: Int = -1,
    val featured: FeaturedServer? = null,
) {
    val key: String get() = "$kind:${System.identityHashCode(data)}:${data.ip}"
}

internal sealed interface ServerSlot {
    val key: String
}

internal class EntrySlot(val entry: ServerEntry, val nested: Boolean = false) : ServerSlot {
    override val key: String get() = entry.key
}

internal class FolderSlot(val folder: String, val count: Int, val collapsed: Boolean, val order: List<String>) : ServerSlot {
    override val key: String get() = "folder:$folder"
}

internal class SectionSlot(val label: String) : ServerSlot {
    override val key: String get() = "section:$label"
}

internal class GroupSlot(val label: String, val count: Int) : ServerSlot {
    override val key: String get() = "group:$label"
}

internal fun List<ServerSlot>.withGroupHeader(label: String): List<ServerSlot> =
    listOf(GroupSlot(label, takeWhile { it is EntrySlot }.size)) + this

internal class ServerLayout(val slots: List<ServerSlot>, val grouped: Boolean)

internal sealed interface FolderPrompt

internal class MoveFolderPrompt(val entry: ServerEntry) : FolderPrompt

internal class RenameFolderPrompt(val folder: String) : FolderPrompt

internal class DeleteFolderPrompt(val folder: String) : FolderPrompt

internal val ServerData.shownName: String get() = PolyPlusRecentServers.listLabel(name, ip)

internal class PlayerSample(val online: Int, val names: List<String>) {
    val more: Int get() = (online - names.size).coerceAtLeast(0)
}

private const val AND_MORE_KEY = "multiplayer.status.and_more"

internal fun playerSample(data: ServerData): PlayerSample? {
    if (data.isLan || LobbyServers.failed(data)) return null
    if (data.state() != ServerData.State.SUCCESSFUL && data.state() != ServerData.State.INCOMPATIBLE) return null
    val online = data.players?.online() ?: return null
    //? if > 1.8.9 {
    val names = data.playerList.orEmpty()
        .filterNot { (it.contents as? TranslatableContents)?.key == AND_MORE_KEY }
        .map { PauseText.plain(it.string).trim() }
        .filter { it.isNotEmpty() }
    //?} else {
    /*val lines = data.entry.onlinePlayers.orEmpty().split('\n').map { PauseText.plain(it).trim() }.filter { it.isNotEmpty() }
    val names = if (lines.isNotEmpty() && lines.last() == PauseText.plain(LobbyI18n.get(AND_MORE_KEY, online - (lines.size - 1))).trim()) lines.dropLast(1) else lines
    *///?}
    return PlayerSample(online, names)
}

internal object ServerRevisions {
    private val states = WeakHashMap<ServerData, MutableIntState>()

    fun of(data: ServerData): Int = state(data).intValue

    fun bump(data: ServerData) {
        state(data).intValue++
    }

    private fun state(data: ServerData): MutableIntState = states.getOrPut(data) { mutableIntStateOf(0) }
}

internal class LobbyServers(private val minecraft: Minecraft) {
    @Volatile
    private var pinger = ServerStatusPinger()
    private var pings: Job = SupervisorJob(PolyPlusClient.SCOPE.coroutineContext[Job])
    private val signatures = WeakHashMap<ServerData, Int>()
    private val watching = LinkedHashSet<ServerData>()

    @Volatile
    private var closed = true
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
    var pinned by mutableStateOf<List<ServerEntry>>(emptyList())
        private set
    var pins by mutableStateOf<List<RecentServer>>(emptyList())
        private set
    var folderRevision by mutableIntStateOf(ServerFolders.revision())
        private set
    var folderPrompt by mutableStateOf<FolderPrompt?>(null)

    val serverList: ServerList get() = list

    fun open() {
        closed = false
        pings = SupervisorJob(PolyPlusClient.SCOPE.coroutineContext[Job])
        pinger = ServerStatusPinger()
        watching.clear()
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
        }.onFailure { LOBBY_LOGGER.warn("Unable to start LAN server detection", it) }
    }

    fun close() {
        closed = true
        pings.cancel()
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
        syncFolders()
        if (watching.isNotEmpty()) {
            watching.toList().forEach(::checkChanged)
            watching.removeAll { it.state() != ServerData.State.PINGING }
        }
    }

    fun ping(data: ServerData) {
        if (closed || data.state() != ServerData.State.INITIAL || data.isLan) return
        data.setState(ServerData.State.PINGING)
        data.motd = CommonComponents.EMPTY
        data.status = CommonComponents.EMPTY
        ServerRevisions.bump(data)
        watching += data
        val owner = pinger
        PolyPlusClient.SCOPE.launch(pings + PING_DISPATCHER) {
            if (closed || owner !== pinger) return@launch
            owner.pingInto(data, { ClientPlatform.runOnMain { if (!closed) persist(data) } }) { ServerRevisions.bump(data) }
            if (closed || owner !== pinger) owner.removeAll()
        }
    }

    fun rebuild() {
        saved = (0 until list.size()).map {
            SAVED_INDICES[list.get(it)] = it
            ServerEntry(list.get(it), ServerKind.SAVED, savedIndex = it)
        }
        recent = PolyPlusRecentServers.recent().map(::entryFor)
        syncPins()
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
        persistAndRebuild()
    }

    fun moveTo(entry: ServerEntry, target: Int) {
        var from = (0 until list.size()).firstOrNull { list.get(it) === entry.data } ?: return
        val to = target.coerceIn(0, list.size() - 1)
        if (from == to) return
        val moved = list.get(from)
        val step = if (to > from) 1 else -1
        while (from != to) {
            list.replace(from, list.get(from + step))
            from += step
        }
        list.replace(to, moved)
        persistAndRebuild()
    }

    fun moveInFolder(entry: ServerEntry, direction: Int) {
        val siblings = folderSiblings(entry)
        val target = siblings.getOrNull(siblings.indexOfFirst { it.data === entry.data } + direction) ?: return
        if (entry.savedIndex < 0 || target.savedIndex < 0) return
        list.swap(entry.savedIndex, target.savedIndex)
        persistAndRebuild()
    }

    fun drop(entry: ServerEntry, folder: String?, after: ServerEntry?, before: ServerEntry?, toEnd: Boolean) {
        if (ServerFolders.folderOf(entry.data.ip) != folder) ServerFolders.setFolder(entry.data.ip, folder.orEmpty())
        val anchor = if (toEnd) {
            saved.lastOrNull { it.data !== entry.data && ServerFolders.folderOf(it.data.ip) == ServerFolders.folderOf(entry.data.ip) }
        } else {
            after ?: before
        }
        val from = savedIndexOf(entry.data)
        val at = anchor?.let { savedIndexOf(it.data) } ?: -1
        if (from >= 0 && at >= 0) {
            val to = if (toEnd || after != null) (if (from < at) at else at + 1) else (if (from < at) at - 1 else at)
            if (to != from) moveTo(entry, to)
        }
        syncFolders()
    }

    private fun savedIndexOf(data: ServerData): Int = (0 until list.size()).firstOrNull { list.get(it) === data } ?: -1

    fun folderSiblings(entry: ServerEntry): List<ServerEntry> {
        val folder = ServerFolders.folderOf(entry.data.ip)
        return saved.filter { ServerFolders.folderOf(it.data.ip) == folder }
    }

    fun layout(entries: List<ServerEntry>): ServerLayout {
        val savedEntries = entries.filter { it.kind == ServerKind.SAVED }
        val groups = ServerFolders.layout(savedEntries, { it.data.ip }, { it.data.shownName })
        val sorted = ServerFolders.sort() != ServerFolders.Sort.CUSTOM
        if (groups.none { it.folder != null }) {
            if (!sorted) return ServerLayout(entries.map(::EntrySlot), false)
            val ordered = groups.firstOrNull()?.items.orEmpty()
            return ServerLayout((entries.filter { it.kind == ServerKind.FEATURED } + ordered + entries.filter { it.kind != ServerKind.FEATURED && it.kind != ServerKind.SAVED }).map(::EntrySlot), true)
        }
        val order = groups.mapNotNull { it.folder }
        val slots = ArrayList<ServerSlot>()
        entries.filter { it.kind == ServerKind.FEATURED }.mapTo(slots, ::EntrySlot)
        groups.firstOrNull { it.folder == null }?.items?.mapTo(slots, ::EntrySlot)
        val rest = entries.filter { it.kind != ServerKind.FEATURED && it.kind != ServerKind.SAVED }
        for (group in groups) {
            val folder = group.folder ?: continue
            val collapsed = ServerFolders.isCollapsed(folder)
            slots += FolderSlot(folder, group.items.size, collapsed, order)
            if (!collapsed) group.items.mapTo(slots) { EntrySlot(it, nested = true) }
        }
        if (rest.isNotEmpty()) {
            slots += SectionSlot(MORE_SERVERS)
            rest.mapTo(slots, ::EntrySlot)
        }
        return ServerLayout(slots, true)
    }

    fun setFolder(entry: ServerEntry, folder: String) {
        ServerFolders.setFolder(entry.data.ip, folder)
        syncFolders()
    }

    fun toggleFolder(folder: String) {
        ServerFolders.toggle(folder)
        syncFolders()
    }

    fun renameFolder(folder: String, to: String) {
        ServerFolders.renameFolder(folder, to)
        syncFolders()
    }

    fun deleteFolder(folder: String) {
        ServerFolders.deleteFolder(folder)
        syncFolders()
    }

    fun moveFolder(folder: String, by: Int, order: List<String>) {
        ServerFolders.moveFolder(folder, by, order)
        syncFolders()
    }

    fun setSort(sort: ServerFolders.Sort) {
        ServerFolders.setSort(sort)
        syncFolders()
    }

    fun syncFolders() {
        val current = ServerFolders.revision()
        if (current != folderRevision) folderRevision = current
    }

    fun saveUnsaved(entry: ServerEntry) {
        if (list.unhide(entry.data.ip) == null) list.add(entry.data, false)
        persistAndRebuild()
    }

    fun isPinned(data: ServerData): Boolean = pinned.any { it.data.ip.equals(data.ip, ignoreCase = true) }

    fun canPin(): Boolean = pins.size < PolyPlusRecentServers.PINNED_MAX

    fun setPinned(data: ServerData, pin: Boolean) {
        PolyPlusRecentServers.setPinned(data.name, data.ip, pin)
        syncPins()
    }

    fun isRealmPinned(id: Long): Boolean = pins.any { it.isRealm && it.realm == id }

    fun setRealmPinned(realm: RealmEntry, pin: Boolean) {
        PolyPlusRecentServers.setRealmPinned(realm.id, realm.name, pin)
        syncPins()
    }

    fun movePinned(from: Int, to: Int) {
        PolyPlusRecentServers.movePinned(from, to)
        syncPins()
    }

    fun setPinnedOrder(ips: List<String>) {
        PolyPlusRecentServers.setPinnedOrder(ips)
        syncPins()
    }

    private fun syncPins() {
        pins = PolyPlusRecentServers.pinned()
        pinned = pins.filterNot { it.isRealm }.map(::entryFor)
    }

    fun quickRows(pinned: ServerData?): List<ServerEntry> {
        val rows = (recent + saved).distinctBy { it.data.ip.lowercase() }.take(QUICK_ROWS)
        if (pinned == null || rows.any { it.data.ip.equals(pinned.ip, ignoreCase = true) }) return rows
        return (listOf(ServerEntry(pinned, ServerKind.UNSAVED)) + rows).take(QUICK_ROWS)
    }

    fun resolve(old: ServerData): ServerData {
        if (old.isLan) return old
        SAVED_INDICES[old]?.takeIf { it in 0 until list.size() }?.let { list.get(it) }?.takeIf { it.sameAs(old) }?.let { return it }
        val known = saved.firstOrNull { it.data.sameAs(old) }
            ?: (saved + recent + featured + sponsored).firstOrNull { it.data.ip.equals(old.ip, ignoreCase = true) }
        return known?.data ?: unsavedData(old.name, old.ip)
    }

    private fun ServerData.sameAs(other: ServerData) = name == other.name && ip.equals(other.ip, ignoreCase = true)

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

    private fun persist(data: ServerData) {
        list.save()
        ServerRevisions.bump(data)
    }

    private fun checkChanged(data: ServerData) {
        val signature = signature(data)
        if (signatures.put(data, signature) != signature) ServerRevisions.bump(data)
    }

    private fun signature(data: ServerData): Int {
        var hash = data.state().ordinal
        hash = hash * 31 + System.identityHashCode(data.motd)
        hash = hash * 31 + System.identityHashCode(data.status)
        hash = hash * 31 + data.ping.hashCode()
        hash = hash * 31 + (data.players?.online() ?: -1)
        hash = hash * 31 + (data.iconBytes?.contentHashCode() ?: 0)
        return hash
    }

    companion object {
        const val QUICK_ROWS = 3
        private const val MORE_SERVERS = "More servers"
        private val PING_DISPATCHER = Dispatchers.IO.limitedParallelism(5)
        private val SAVED_INDICES = WeakHashMap<ServerData, Int>()

        fun failed(data: ServerData): Boolean {
            if (data.state() == ServerData.State.UNREACHABLE) return true
            val key = (data.motd?.contents as? TranslatableContents)?.key
            return key == CANT_CONNECT_KEY || key == CANT_RESOLVE_KEY
        }
    }
}
