package org.polyfrost.polyplus.client.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
//? if > 1.8.9
import net.minecraft.world.level.storage.LevelResource
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.lobby.LobbyScene
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.lobby.currentServer
import org.polyfrost.polyplus.client.lobby.level
import org.polyfrost.polyplus.client.lobby.singleplayerServer
*///?}
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@Serializable
internal data class PlaySpot(
    val name: String,
    val server: Boolean,
    val millis: Long = 0L,
    val sessions: Int = 0,
    val lastPlayed: Long = 0L,
)

@Serializable
internal data class PlayLog(
    val spots: Map<String, PlaySpot> = emptyMap(),
    val days: Map<Long, Long> = emptyMap(),
)

internal object PlaySessions {
    @Serializable
    private data class State(val players: Map<String, PlayLog> = emptyMap())

    private class Place(val key: String, val name: String, val server: Boolean)

    private class Session(val player: UUID, val place: Place) {
        var lastNanos = System.nanoTime()
        var elapsed = 0L
        var pending = 0L
        var savedAt = 0L
        var counted = false
    }

    const val WORLD_PREFIX = "world:"
    const val SERVER_PREFIX = "server:"

    private val LOGGER = LogManager.getLogger("PolyPlus/Stats")
    private const val MIN_SESSION_MILLIS = 10_000L
    private const val SAVE_EVERY_MILLIS = 30_000L
    private const val LONGEST_TICK_MILLIS = 5_000L
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val KEPT_DAYS = 14L
    private const val FRIEND_WORLD = "Friend's world"

    private val lock = Any()
    private var state: State? = null
    private var session: Session? = null

    private val file: File by lazy { File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/play_sessions.json") }

    fun initialize() {
        eventHandler<TickEvent.End> { tick() }
    }

    fun log(player: UUID): PlayLog = synchronized(lock) { loaded().players[player.toString()] ?: PlayLog() }

    private fun tick() {
        val mc = Minecraft.getInstance()
        val place = currentPlace(mc)
        val open = session
        if (open != null && open.place.key != place?.key) {
            advance(open, mc)
            flush(open)
            session = null
        }
        if (place != null && session == null) session = Session(ClientPlatform.localPlayerUuid(), place)
        session?.let {
            advance(it, mc)
            if (it.elapsed - it.savedAt >= SAVE_EVERY_MILLIS) flush(it)
        }
    }

    private fun advance(open: Session, mc: Minecraft) {
        val now = System.nanoTime()
        val delta = ((now - open.lastNanos) / NANOS_PER_MILLI).coerceAtMost(LONGEST_TICK_MILLIS)
        open.lastNanos = now
        if (mc.isPaused) return
        open.elapsed += delta
        open.pending += delta
    }

    private fun flush(open: Session) {
        if (open.elapsed < MIN_SESSION_MILLIS) return
        val millis = open.pending
        val started = !open.counted
        open.pending = 0L
        open.savedAt = open.elapsed
        open.counted = true
        record(open.player, open.place, millis, started)
    }

    private fun record(player: UUID, place: Place, millis: Long, started: Boolean) {
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            synchronized(lock) {
                val current = loaded()
                val id = player.toString()
                val log = current.players[id] ?: PlayLog()
                val next = current.copy(players = current.players + (id to logged(log, place, millis, started)))
                state = next
                runCatching { write(next) }.onFailure { LOGGER.warn("Could not save play sessions", it) }
            }
        }
    }

    private fun logged(log: PlayLog, place: Place, millis: Long, started: Boolean): PlayLog {
        val spot = log.spots[place.key] ?: PlaySpot(place.name, place.server)
        val today = LocalDate.now().toEpochDay()
        val updated = spot.copy(
            name = place.name,
            millis = spot.millis + millis,
            sessions = spot.sessions + if (started) 1 else 0,
            lastPlayed = System.currentTimeMillis(),
        )
        return PlayLog(
            log.spots + (place.key to updated),
            (log.days + (today to (log.days[today] ?: 0L) + millis)).filterKeys { today - it < KEPT_DAYS },
        )
    }

    private fun loaded(): State {
        state?.let { return it }
        val read = runCatching {
            if (file.isFile) PolyPlusClient.JSON.decodeFromString(State.serializer(), file.readText()) else State()
        }.onFailure { LOGGER.warn("Could not read play sessions", it) }.getOrDefault(State())
        state = read
        return read
    }

    private fun write(next: State) {
        val path = file.toPath()
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.createDirectories(path.parent)
        Files.write(temporary, PolyPlusClient.JSON.encodeToString(State.serializer(), next).toByteArray(Charsets.UTF_8))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun currentPlace(mc: Minecraft): Place? {
        if (mc.level == null || mc.player == null || LobbyScene.terrainActive()) return null
        val integrated = mc.singleplayerServer
        //? if > 1.8.9 {
        if (mc.hasSingleplayerServer() && integrated != null) {
            val folder = integrated.getWorldPath(LevelResource.ROOT).normalize().fileName?.toString() ?: integrated.worldData.levelName
            return Place(WORLD_PREFIX + folder, integrated.worldData.levelName, false)
        }
        //?} else {
        /*if (mc.isIntegratedServerRunning && integrated != null) {
            return Place(WORLD_PREFIX + integrated.worldSaveName, integrated.worldName, false)
        }
        *///?}
        val data = mc.currentServer ?: return null
        val name = if (data.ip == P2PSessionManager.P2P_PLACEHOLDER_IP) FRIEND_WORLD else data.name
        return Place(SERVER_PREFIX + data.ip.trim().lowercase(Locale.ROOT), name, true)
    }
}
