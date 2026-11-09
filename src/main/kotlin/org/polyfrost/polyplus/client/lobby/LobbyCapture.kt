package org.polyfrost.polyplus.client.lobby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.PauseScreen
//?} else {
/*import net.minecraft.client.gui.screen.game.DeathScreen
import net.minecraft.client.gui.screen.menu.GameMenuScreen as PauseScreen
*///?}
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.Connection
import net.minecraft.tags.BlockTags
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
//? if > 1.8.9
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LightLayer
//? if > 1.8.9
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
//? if > 1.8.9
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
//? if > 1.8.9
import net.minecraft.world.level.storage.LevelResource
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
import org.polyfrost.polyplus.client.party.PartyScene
import org.polyfrost.polyplus.client.serverapi.OneClientServerApi
import org.polyfrost.polyplus.client.serverapi.ServerApiProtocol
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.math.hypot

enum class LobbySpotAction { CHECK, CAPTURE }

sealed interface LobbySpotState {
    data object Idle : LobbySpotState

    data class Blocked(val action: LobbySpotAction, val reason: String) : LobbySpotState

    data class Waiting(val action: LobbySpotAction) : LobbySpotState

    data class Surveying(val action: LobbySpotAction, val progress: Float) : LobbySpotState

    data class Saving(val progress: Float) : LobbySpotState

    data class Done(val action: LobbySpotAction, val report: LobbySceneScore.Report, val saved: Boolean, val message: String) : LobbySpotState
}

sealed interface LobbySpotAccess {
    data class Hidden(val reason: String) : LobbySpotAccess

    data class Blocked(val reason: String) : LobbySpotAccess

    data object Ready : LobbySpotAccess
}

object LobbyCapture {
    private class Source(
        val world: String,
        val label: String,
        val origin: String,
        val privacy: LobbyPrivacy,
        val connection: Connection?,
        val stack: Set<String>,
        val action: LobbySpotAction? = null,
        val address: String? = null,
    ) {
        @Volatile
        var handOver = false

        var left: ClientLevel? = null
    }

    private class Leaving(val level: ClientLevel, val survey: Surveying?, val copy: Copying?)

    private class Return(val world: String, val fallback: LobbySceneEntry?) {
        var entry: LobbySceneEntry? = null
        var pending = true
        var tried: String? = null
    }

    private class Timing {
        var ticks = 0
        var nanos = 0L
        var maxNanos = 0L

        fun add(elapsed: Long) {
            ticks++
            nanos += elapsed
            maxNanos = maxOf(maxNanos, elapsed)
        }

        fun describe(): String = "%d ticks, %.2fms total, %.0fus worst tick".format(Locale.ROOT, ticks, nanos / NANOS_PER_MILLI, maxNanos / NANOS_PER_MICRO)
    }

    private class Surveying(val source: Source, val origin: BlockPos, val survey: LobbySurvey?, val room: LobbyRoom? = null, val timing: Timing = Timing()) {
        var cursor = 0

        val cells: Int
            get() = if (room != null) LobbyRoom.CELLS else LobbySurvey.CELLS

        fun judge(): LobbySceneScore.Verdict = room?.let(LobbySceneScore::judgeRoom) ?: LobbySceneScore.judge(checkNotNull(survey))
    }

    private class Copying(
        val source: Source,
        val verdict: LobbySceneScore.Verdict,
        val anchor: BlockPos,
        val radius: Int,
        val surveyTiming: Timing,
        val minSection: Int,
        val keptTop: Int,
        val weaker: Boolean = false,
    ) {
        val timing = Timing()
        val chunks = ArrayList<LobbyChunkCopy>()
        val pending = ArrayDeque<ChunkPos>()
        var chunk: LevelChunk? = null
        var copy: LobbyChunkCopy? = null
        var nextSection = 0
        var blockEntities: Iterator<BlockEntity>? = null
        var topSection = minSection
        val entities = ListTag()
        //? if > 1.8.9
        var props: ArrayDeque<Entity>? = null
    }

    private class Outcome(
        val root: CompoundTag,
        val score: Double,
        val biome: String,
        val clock: Long,
        val anchor: BlockPos,
        val solveMillis: Long,
        val packs: List<LobbyScenePack>,
        val points: Int?,
        val profile: LobbySceneScore.Profile,
        val dimension: String,
    )

    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PolyPlus Lobby Capture").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000.0
    private const val NANOS_PER_MICRO = 1_000.0
    private const val STEP_NANOS = 300_000L
    private const val FIRST_DELAY_TICKS = 200
    private const val SETTLED_TICKS = 60
    private const val RETRY_TICKS = 400
    private const val CAPTURED_TICKS = 6000
    private const val MOVED_BLOCKS = 24.0
    private const val MIN_SKY_LIGHT = 10
    private const val VANILLA_NAMESPACE = "minecraft:"
    private const val STAGE_SCORE_WEIGHT = 0.2
    private const val BIOME_NAME_FALLBACK = "minecraft:plains"
    private const val COPY_SHARE = 0.9f
    private const val FAILED = "Something went wrong. Try again in a moment."
    private const val SERVER_BLOCKS = "This server blocks scene capture"
    private const val SERVER_TURNED_OFF = "You turned off scene capture for this server"
    private const val SERVER_PACK = "Scene capture is off while a server resource pack is in use"

    @Volatile
    var busy = false
        private set

    private var surveying: Surveying? = null
    private var copying: Copying? = null
    private var leaving: Leaving? = null
    private var trackedLevel: ClientLevel? = null
    private var wait = FIRST_DELAY_TICKS
    private var lastSpot: BlockPos? = null
    private var settled = 0
    private var policy = ServerApiProtocol.ScenePolicy.UNSET
    private val landed = ConcurrentLinkedQueue<Runnable>()
    private var inFlight: Source? = null
    private var recent: LobbySceneEntry? = null
    private var returning: Return? = null
    private var earlyScreen: Any? = null
    private var session = WeakReference<Connection>(null)
    private val sessionScenes = ArrayList<LobbySceneEntry>()

    @Volatile
    private var packed = WeakReference<Connection>(null)

    @Volatile
    private var handover: Pair<LobbySceneEntry, CompoundTag>? = null

    @Volatile
    private var saved = false

    private var queued: LobbySpotAction? = null

    var spot by mutableStateOf<LobbySpotState>(LobbySpotState.Idle)
        private set

    @JvmStatic
    fun onClientTick() {
        drain()
        steer()
        watchPacks()
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level !== trackedLevel) {
            trackedLevel = level
            surveying = null
            copying = null
            lastSpot = null
            wait = FIRST_DELAY_TICKS
            settled = 0
            policy = ServerApiProtocol.ScenePolicy.UNSET
            recent = null
            queued = null
            spot = LobbySpotState.Idle
            if (level != null) returning = null
        }
        val justSaved = saved
        saved = false
        if (level == null || player == null || LobbyScene.terrainActive()) return
        if (sessionPacked(mc)) {
            abandonSession()
            return
        }
        settled++
        if (OneClientServerApi.isConnected) policy = OneClientServerApi.scenePolicy
        rememberPolicy(mc)
        val screen = ClientPlatform.currentScreen()
        val pausing = screen is PauseScreen || screen is OneClientPauseScreen
        if (pausing || screen is DeathScreen) {
            (copying?.source ?: surveying?.source)?.handOver = true
        } else {
            earlyScreen = null
            if (handover != null) dropHandover()
        }
        copying?.let {
            guarded { stepCopy(level, it) }
            return
        }
        surveying?.let {
            guarded { stepSurvey(level, it) }
            return
        }
        if (busy) return
        queued?.let {
            queued = null
            startManual(mc, level, player, it)
            return
        }
        if (local(mc)) {
            if (justSaved && settled >= SETTLED_TICKS) start(mc, level, player, pausing)
            return
        }
        if (pausing && screen !== earlyScreen) {
            earlyScreen = screen
            start(mc, level, player, true)
            return
        }
        if (--wait > 0) return
        start(mc, level, player, false)
    }

    @JvmStatic
    fun onWorldSaved() {
        saved = true
    }

    private fun sessionPacked(mc: Minecraft): Boolean {
        val connection = LobbyPacks.connectionOf(mc)
        if (connection != null && connection !== session.get()) {
            session = WeakReference(connection)
            sessionScenes.clear()
        }
        val current = session.get() ?: return false
        if (LobbyPacks.serverPackApplied(mc)) packed = WeakReference(current)
        return packed.get() === current
    }

    private fun abandonSession() {
        progressAction()?.let { spot = LobbySpotState.Blocked(it, SERVER_PACK) }
        queued = null
        surveying = null
        copying = null
        recent = null
        returning = null
        dropHandover()
        sessionScenes.forEach(LobbySceneStore::delete)
        sessionScenes.clear()
    }

    private fun watchPacks() {
        if (!LobbyPacks.changed()) return
        val id = LobbyScene.currentId ?: return
        val own = LobbySceneStore.scenes.firstOrNull { it.id == id }
        if (id.startsWith(LobbySceneEntry.PARTY_PREFIX) || own?.let(LobbySceneStore::problem) != null) LobbyScene.reload()
    }

    private fun start(mc: Minecraft, level: ClientLevel, player: LocalPlayer, handOver: Boolean) {
        begin(mc, level, player.blockPosition(), player.onGround(), player.isVehicle || player.isPassenger)
        if (handOver) surveying?.source?.handOver = true
    }

    private fun local(mc: Minecraft): Boolean {
        //? if > 1.8.9 {
        return mc.hasSingleplayerServer()
        //?} else {
        /*return mc.isIntegratedServerRunning
        *///?}
    }

    fun returnEntry(): LobbySceneEntry? = returning?.entry

    @Synchronized
    fun takeRoot(id: String): CompoundTag? {
        val (entry, root) = handover ?: return null
        if (entry.id != id) return null
        handover = null
        LOBBY_LOGGER.debug("Scene {} read from memory", id)
        return root
    }

    @Synchronized
    private fun dropHandover() {
        handover = null
    }

    @Synchronized
    private fun keepHandover(entry: LobbySceneEntry, root: CompoundTag) {
        handover = entry to root
    }

    private fun steer() {
        val back = returning ?: return
        val entry = back.entry
        when {
            entry == null -> if (!back.pending) returning = null
            LobbySceneStore.pinned != null -> returning = null
            PartyScene.followed() != null -> returning = null
            LobbyScene.currentId == entry.id -> if (!back.pending) returning = null
            back.tried != entry.id && LobbyScene.phase == LobbyScene.Phase.READY -> if (LobbyScene.switchTo(entry)) back.tried = entry.id
        }
    }

    private fun settle() {
        val back = returning ?: return
        if (!back.pending || busy || copying != null || surveying != null || leaving != null) return
        back.pending = false
        if (back.entry == null) back.entry = back.fallback
    }

    @JvmStatic
    fun onLeave() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (level !== trackedLevel || settled < SETTLED_TICKS || LobbyScene.terrainActive()) return
        settled = 0
        val source = source(mc)
        if (source == null) {
            returning = null
            return
        }
        returning = Return(source.world, recent?.takeIf { it.world == source.world })
        inFlight?.let {
            it.handOver = true
            it.left = level
        }
        if (copying == null && surveying == null && !busy) {
            begin(mc, level, player.blockPosition(), player.onGround(), player.isVehicle || player.isPassenger)
        }
        val survey = surveying
        val copy = copying
        val owner = copy?.source ?: survey?.source
        if (owner != null) {
            surveying = null
            copying = null
            owner.handOver = true
            owner.left = level
            busy = true
            inFlight = owner
            leaving = Leaving(level, survey, copy)
        }
        settle()
    }

    @JvmStatic
    fun drain() {
        while (true) {
            val task = landed.poll() ?: break
            guarded { task.run() }
        }
        leaving?.let {
            if (Minecraft.getInstance().level !== it.level) {
                leaving = null
                finishDetached(it)
            }
        }
        if (!busy) inFlight = null
        settle()
    }

    private fun finishDetached(left: Leaving) {
        val source = left.copy?.source ?: left.survey?.source ?: return
        worker.execute {
            try {
                left.copy?.let {
                    copyAll(left.level, it)
                    encoder(left.level, it).run()
                }
                left.survey?.let { landed.add(surveyAll(left.level, it)) }
            } catch (error: Throwable) {
                LOBBY_LOGGER.warn("Capture after leaving failed", error)
                landed.add(
                    Runnable {
                        busy = false
                        fail(source)
                    },
                )
            }
        }
    }

    private fun surveyAll(level: ClientLevel, job: Surveying): Runnable {
        val start = Util.getNanos()
        val cursor = BlockPos.MutableBlockPos()
        while (job.cursor < job.cells) sample(level, job, job.cursor++, cursor)
        var verdict = job.judge()
        val room = indoorRetry(level, job, verdict)
        if (room != null) {
            while (room.cursor < room.cells) sample(level, room, room.cursor++, cursor)
            verdict = room.judge()
        }
        job.timing.add(Util.getNanos() - start)
        val done = room ?: job
        return Runnable { finishSurvey(done, verdict, level) }
    }

    private fun copyAll(level: ClientLevel, job: Copying) {
        val start = Util.getNanos()
        while (true) {
            val chunk = job.chunk
            if (chunk == null) {
                if (!nextChunk(level, job) && !nextProp(level, job)) break
                continue
            }
            copyStep(level, chunk, job)
        }
        job.timing.add(Util.getNanos() - start)
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            LOBBY_LOGGER.warn("Capture step failed", error)
            progressAction()?.let { spot = LobbySpotState.Blocked(it, FAILED) }
            surveying = null
            copying = null
            busy = false
        }
    }

    private fun rememberPolicy(mc: Minecraft) {
        val address = mc.currentServer?.ip ?: return
        if (OneClientServerApi.isConnected) LobbySceneStore.rememberServerPolicy(address, OneClientServerApi.scenePolicy)
    }

    private fun begin(mc: Minecraft, level: ClientLevel, feet: BlockPos, grounded: Boolean, riding: Boolean) {
        wait = RETRY_TICKS
        val source = source(mc) ?: return
        val moved = lastSpot?.let { hypot((it.x - feet.x).toDouble(), (it.z - feet.z).toDouble()) } ?: Double.MAX_VALUE
        if (moved < MOVED_BLOCKS || !grounded || riding) return
        lastSpot = feet
        early(level, feet)?.let {
            report(source, feet, "rejected ${it.name}")
            return
        }
        surveying = surveyAt(level, source, feet)
        report(source, feet, "started")
    }

    private fun source(mc: Minecraft, action: LobbySpotAction? = null): Source? {
        if (!LobbySceneStore.captureEnabled || sessionPacked(mc)) return null
        val connection = session.get()
        val stack = LobbyPacks.activeIds().toSet()
        val server = mc.singleplayerServer
        //? if > 1.8.9 {
        if (mc.hasSingleplayerServer() && server != null) {
            val folder = server.getWorldPath(LevelResource.ROOT).normalize().fileName?.toString() ?: server.worldData.levelName
            return Source("world:$folder", server.worldData.levelName, LobbySceneEntry.ORIGIN_WORLD, LobbyPrivacy.OWN_WORLD, connection, stack, action)
        }
        //?} else {
        /*if (mc.isIntegratedServerRunning && server != null) {
            return Source("world:${server.worldSaveName}", server.worldName, LobbySceneEntry.ORIGIN_WORLD, LobbyPrivacy.OWN_WORLD, connection, stack, action)
        }
        *///?}
        val data = mc.currentServer ?: return null
        val live = if (OneClientServerApi.isConnected) OneClientServerApi.scenePolicy else policy
        if (!LobbySceneStore.serverAllows(data.ip, live)) return null
        return Source(LobbySceneStore.worldKey(data.ip), data.name, LobbySceneEntry.ORIGIN_SERVER, LobbyPrivacy.SERVER, connection, stack, action, data.ip)
    }

    fun spotAccess(): LobbySpotAccess {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return LobbySpotAccess.Hidden("Join a world first")
        if (LobbyScene.terrainActive()) return LobbySpotAccess.Hidden("Scene capture is not available here")
        if (!LobbySceneStore.captureEnabled) return LobbySpotAccess.Hidden("Scene capture is turned off")
        if (local(mc)) return LobbySpotAccess.Ready
        val data = mc.currentServer ?: return LobbySpotAccess.Hidden("Scene capture is not available here")
        val live = if (OneClientServerApi.isConnected) OneClientServerApi.scenePolicy else policy
        if (live == ServerApiProtocol.ScenePolicy.DENY) return LobbySpotAccess.Blocked(SERVER_BLOCKS)
        if (LobbySceneStore.serverChoice(data.ip) == false) return LobbySpotAccess.Blocked(SERVER_TURNED_OFF)
        if (!LobbySceneStore.serverAllows(data.ip, live)) return LobbySpotAccess.Blocked(SERVER_BLOCKS)
        val connection = LobbyPacks.connectionOf(mc)
        if (LobbyPacks.serverPackApplied(mc) || (connection != null && packed.get() === connection)) return LobbySpotAccess.Blocked(SERVER_PACK)
        return LobbySpotAccess.Ready
    }

    fun checkSpot() = request(LobbySpotAction.CHECK)

    fun captureSpot() = request(LobbySpotAction.CAPTURE)

    private fun progressAction(): LobbySpotAction? = when (val current = spot) {
        is LobbySpotState.Waiting -> current.action
        is LobbySpotState.Surveying -> current.action
        is LobbySpotState.Saving -> LobbySpotAction.CAPTURE
        else -> null
    }

    private fun request(action: LobbySpotAction) {
        if (progressAction() != null) return
        when (val access = spotAccess()) {
            is LobbySpotAccess.Hidden -> spot = LobbySpotState.Blocked(action, access.reason)
            is LobbySpotAccess.Blocked -> spot = LobbySpotState.Blocked(action, access.reason)
            LobbySpotAccess.Ready -> {
                val mc = Minecraft.getInstance()
                val level = mc.level ?: return
                val player = mc.player ?: return
                if (surveying?.source?.action == null) surveying = null
                if (copying != null || busy || surveying != null) {
                    queued = action
                    spot = LobbySpotState.Waiting(action)
                } else {
                    startManual(mc, level, player, action)
                }
            }
        }
    }

    private fun startManual(mc: Minecraft, level: ClientLevel, player: LocalPlayer, action: LobbySpotAction) {
        val source = source(mc, action)
        if (source == null) {
            val reason = when (val access = spotAccess()) {
                is LobbySpotAccess.Blocked -> access.reason
                is LobbySpotAccess.Hidden -> access.reason
                LobbySpotAccess.Ready -> SERVER_BLOCKS
            }
            spot = LobbySpotState.Blocked(action, reason)
            return
        }
        if (player.isVehicle || player.isPassenger) {
            spot = LobbySpotState.Blocked(action, "Get off what you are riding first")
            return
        }
        if (!player.onGround()) {
            spot = LobbySpotState.Blocked(action, "Stand on the ground first")
            return
        }
        val feet = player.blockPosition()
        lastSpot = feet
        early(level, feet)?.let {
            report(source, feet, "rejected ${it.name}")
            val result = if (it == LobbySceneScore.Rejection.ROOF) {
                LobbySceneScore.report(null, it, false, null, false, profile = LobbySceneScore.Profile.CAVE, realm = LobbyRealm.NETHER)
            } else {
                LobbySceneScore.report(null, it, false, null, false)
            }
            spot = LobbySpotState.Done(action, result, false, it.reason)
            return
        }
        surveying = surveyAt(level, source, feet)
        spot = LobbySpotState.Surveying(action, 0f)
        report(source, feet, "started by hand")
    }

    private fun fail(source: Source, message: String = FAILED) {
        source.action?.let { spot = LobbySpotState.Blocked(it, message) }
    }

    private fun dimensionId(level: ClientLevel): String {
        //? if >= 1.21.11 {
        return level.dimension().identifier().toString()
        //?} elif > 1.8.9 {
        /*return level.dimension().location().toString()
        *///?} else {
        /*return level.dimension.id.toString()
        *///?}
    }

    private fun realmOf(level: ClientLevel): LobbyRealm = LobbyRealm.of(dimensionId(level))

    private fun early(level: ClientLevel, feet: BlockPos): LobbySceneScore.Rejection? {
        val id = dimensionId(level)
        val realm = LobbyRealm.of(id)
        //? if > 1.8.9 {
        if (realm == LobbyRealm.OVERWORLD) {
            val dimension = level.dimensionType()
            if (dimension.hasCeiling() || !dimension.hasSkyLight() || dimension.hasFixedTime()) return LobbySceneScore.Rejection.DIMENSION
        } else if (!id.startsWith(VANILLA_NAMESPACE)) {
            return LobbySceneScore.Rejection.DIMENSION
        }
        //?} else {
        /*if (realm == LobbyRealm.OVERWORLD && level.dimension.id != 0) return LobbySceneScore.Rejection.DIMENSION
        *///?}
        if (realm == LobbyRealm.NETHER && feet.y > LobbySceneScore.NETHER_ROOF) return LobbySceneScore.Rejection.ROOF
        return null
    }

    private fun surveyAt(level: ClientLevel, source: Source, feet: BlockPos): Surveying {
        val realm = realmOf(level)
        return when {
            realm == LobbyRealm.NETHER -> roomSurvey(level, source, feet)
            realm == LobbyRealm.END || level.getBrightness(LightLayer.SKY, feet) >= MIN_SKY_LIGHT -> Surveying(source, feet, LobbySurvey(feet, level.minY, realm))
            else -> roomSurvey(level, source, feet)
        }
    }

    private fun roomSurvey(level: ClientLevel, source: Source, feet: BlockPos, timing: Timing = Timing()): Surveying =
        Surveying(source, feet, null, LobbyRoom(feet, biomeAt(level, feet), realmOf(level)), timing)

    private fun indoorRetry(level: ClientLevel, job: Surveying, verdict: LobbySceneScore.Verdict): Surveying? {
        if (job.room != null || verdict.rejection != LobbySceneScore.Rejection.CEILING) return null
        return roomSurvey(level, job.source, job.origin, job.timing)
    }

    private fun biomeAt(level: ClientLevel, pos: BlockPos): String {
        //? if >= 1.21.11 {
        return level.getBiome(pos).unwrapKey().map { it.identifier().toString() }.orElse(BIOME_NAME_FALLBACK)
        //?} elif > 1.8.9 {
        /*return level.getBiome(pos).unwrapKey().map { it.location().toString() }.orElse(BIOME_NAME_FALLBACK)
        *///?} else {
        /*return level.getBiome(pos).name
        *///?}
    }

    private fun stepSurvey(level: ClientLevel, job: Surveying) {
        val start = Util.getNanos()
        val cursor = BlockPos.MutableBlockPos()
        while (job.cursor < job.cells && Util.getNanos() - start < STEP_NANOS) {
            sample(level, job, job.cursor++, cursor)
        }
        job.timing.add(Util.getNanos() - start)
        job.source.action?.let { spot = LobbySpotState.Surveying(it, job.cursor.toFloat() / job.cells) }
        if (job.cursor < job.cells) return
        surveying = null
        busy = true
        inFlight = job.source
        worker.execute {
            val verdict = try {
                job.judge()
            } catch (error: Throwable) {
                LOBBY_LOGGER.warn("Capture survey failed", error)
                landed.add(
                    Runnable {
                        busy = false
                        fail(job.source)
                    },
                )
                return@execute
            }
            landed.add(Runnable { finishSurvey(job, verdict, level) })
        }
    }

    private fun sample(level: ClientLevel, job: Surveying, index: Int, cursor: BlockPos.MutableBlockPos) {
        val room = job.room
        if (room != null) sampleRoom(level, room, index, cursor) else sampleGround(level, checkNotNull(job.survey), index, cursor)
    }

    private fun passable(level: ClientLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.getCollisionShape(level, pos).isEmpty && state.fluidState.isEmpty
    }

    private fun sampleRoom(level: ClientLevel, room: LobbyRoom, index: Int, cursor: BlockPos.MutableBlockPos) {
        val cellX = index / LobbyRoom.SIDE - LobbyRoom.RADIUS
        val cellZ = index % LobbyRoom.SIDE - LobbyRoom.RADIUS
        val x = room.origin.x + cellX
        val z = room.origin.z + cellZ
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) return
        var y = room.origin.y
        if (passable(level, cursor.set(x, y, z))) {
            var drop = 0
            while (drop < LobbyRoom.DROP && y - 1 > level.minY && passable(level, cursor.set(x, y - 1, z))) {
                y--
                drop++
            }
            if (passable(level, cursor.set(x, y - 1, z))) {
                room.kind[index] = LobbySurvey.WALL
                return
            }
        } else {
            val wall = level.getBlockState(cursor.set(x, y, z))
            var rise = 0
            while (rise < LobbyRoom.RISE && !passable(level, cursor.set(x, y, z))) {
                y++
                rise++
            }
            if (!passable(level, cursor.set(x, y, z))) {
                room.kind[index] = LobbySurvey.WALL
                room.block[index] = System.identityHashCode(wall.block)
                room.crafted[index] = crafted(wall)
                room.lit[index] = wall.lightEmission > 0
                room.beauty[index] = caveLook(wall, room.realm)
                return
            }
        }
        val floor = level.getBlockState(cursor.set(x, y - 1, z))
        var beauty = caveLook(floor, room.realm)
        var lit = floor.lightEmission > 0
        var head = 0
        while (head < LobbyRoom.MAX_HEADROOM && passable(level, cursor.set(x, y + head, z))) {
            val inside = level.getBlockState(cursor)
            beauty += caveLook(inside, room.realm)
            if (inside.lightEmission > 0) lit = true
            head++
        }
        val ceiling = if (head < LobbyRoom.MAX_HEADROOM) level.getBlockState(cursor.set(x, y + head, z)) else null
        if (ceiling != null) {
            beauty += caveLook(ceiling, room.realm)
            if (ceiling.lightEmission > 0) lit = true
        }
        room.floor[index] = y
        room.headroom[index] = head
        room.block[index] = System.identityHashCode(floor.block)
        room.crown[index] = ceiling?.let { System.identityHashCode(it.block) } ?: 0
        room.light[index] = (lightAt(level, cursor.set(x, y, z)) + lightAt(level, cursor.set(x, y + maxOf(head - 1, 0), z))) / 2f
        room.lit[index] = lit
        room.beauty[index] = beauty
        room.crafted[index] = crafted(floor) || (ceiling != null && crafted(ceiling))
        room.kind[index] = when {
            !floor.fluidState.isEmpty && floor.`is`(Blocks.LAVA) -> LobbySurvey.LAVA
            !floor.fluidState.isEmpty -> LobbySurvey.WATER
            room.crafted[index] -> LobbySurvey.BUILT
            else -> LobbySurvey.NATURAL
        }
    }

    private fun lightAt(level: ClientLevel, pos: BlockPos): Float =
        maxOf(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos)).toFloat()

    //? if > 1.8.9 {
    private val caveBlocks: Map<Block, Int> by lazy {
        mapOf(
            Blocks.MOSS_BLOCK to 2,
            Blocks.MOSS_CARPET to 2,
            Blocks.AZALEA to 4,
            Blocks.FLOWERING_AZALEA to 5,
            Blocks.AZALEA_LEAVES to 2,
            Blocks.FLOWERING_AZALEA_LEAVES to 3,
            Blocks.CAVE_VINES to 4,
            Blocks.CAVE_VINES_PLANT to 3,
            Blocks.SPORE_BLOSSOM to 8,
            Blocks.GLOW_LICHEN to 3,
            Blocks.SMALL_DRIPLEAF to 3,
            Blocks.BIG_DRIPLEAF to 3,
            Blocks.BIG_DRIPLEAF_STEM to 2,
            Blocks.HANGING_ROOTS to 2,
            Blocks.ROOTED_DIRT to 1,
            Blocks.POINTED_DRIPSTONE to 3,
            Blocks.DRIPSTONE_BLOCK to 1,
            Blocks.AMETHYST_BLOCK to 3,
            Blocks.BUDDING_AMETHYST to 3,
            Blocks.AMETHYST_CLUSTER to 5,
            Blocks.LARGE_AMETHYST_BUD to 4,
            Blocks.MEDIUM_AMETHYST_BUD to 3,
            Blocks.SMALL_AMETHYST_BUD to 2,
        )
    }

    private val netherBlocks: Map<Block, Int> by lazy {
        mapOf(
            Blocks.GLOWSTONE to 3,
            Blocks.SHROOMLIGHT to 3,
            Blocks.CRIMSON_FUNGUS to 3,
            Blocks.WARPED_FUNGUS to 3,
            Blocks.CRIMSON_ROOTS to 2,
            Blocks.WARPED_ROOTS to 2,
            Blocks.NETHER_SPROUTS to 1,
            Blocks.WEEPING_VINES to 3,
            Blocks.WEEPING_VINES_PLANT to 2,
            Blocks.TWISTING_VINES to 3,
            Blocks.TWISTING_VINES_PLANT to 2,
            Blocks.SOUL_FIRE to 3,
            Blocks.CRYING_OBSIDIAN to 2,
        )
    }

    private fun caveLook(state: BlockState, realm: LobbyRealm): Int = (if (realm == LobbyRealm.NETHER) netherBlocks else caveBlocks)[state.block] ?: 0
    //?} else {
    /*private fun caveLook(state: BlockState, realm: LobbyRealm): Int = 0
    *///?}

    private fun sampleGround(level: ClientLevel, survey: LobbySurvey, index: Int, cursor: BlockPos.MutableBlockPos) {
        val cellX = index / LobbySurvey.SIDE - LobbySurvey.RADIUS
        val cellZ = index % LobbySurvey.SIDE - LobbySurvey.RADIUS
        val x = survey.origin.x + cellX * LobbySurvey.STEP
        val z = survey.origin.z + cellZ * LobbySurvey.STEP
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) return
        val ground = groundHeight(level, x, z) - 1
        val top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1
        survey.ground[index] = ground
        survey.top[index] = top
        if (ground < survey.minY) {
            survey.kind[index] = LobbySurvey.VOID
            return
        }
        val state = level.getBlockState(cursor.set(x, ground, z))
        val crown = level.getBlockState(cursor.set(x, top, z))
        survey.block[index] = System.identityHashCode(state.block)
        survey.lit[index] = state.lightEmission > 0 || crown.lightEmission > 0
        survey.kind[index] = when {
            state.fluidState.isEmpty.not() && state.`is`(Blocks.LAVA) -> LobbySurvey.LAVA
            state.fluidState.isEmpty.not() -> LobbySurvey.WATER
            crafted(state) || crafted(crown) -> LobbySurvey.BUILT
            else -> LobbySurvey.NATURAL
        }
    }

    //? if >= 1.21.5 {
    private fun groundHeight(level: ClientLevel, x: Int, z: Int): Int = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
    //?} elif = 1.8.9 {
    /*private fun groundHeight(level: ClientLevel, x: Int, z: Int): Int {
        var y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        while (y > 0 && level.getBlockState(BlockPos(x, y - 1, z)).`is`(BlockTags.LEAVES)) y--
        return y
    }
    *///?} else {
    /*private fun groundHeight(level: ClientLevel, x: Int, z: Int): Int {
        val cursor = BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z)
        while (cursor.y > level.minY && level.getBlockState(cursor.setY(cursor.y - 1)).`is`(BlockTags.LEAVES)) continue
        return cursor.y + 1
    }
    *///?}

    private fun crafted(state: BlockState): Boolean = state.`is`(BlockTags.PLANKS) ||
        state.`is`(BlockTags.STAIRS) ||
        state.`is`(BlockTags.SLABS) ||
        state.`is`(BlockTags.WALLS) ||
        state.`is`(BlockTags.FENCES) ||
        state.`is`(BlockTags.DOORS) ||
        state.`is`(BlockTags.TRAPDOORS) ||
        state.`is`(BlockTags.WOOL) ||
        state.`is`(BlockTags.BEDS) ||
        state.`is`(Blocks.BRICKS) ||
        state.`is`(Blocks.GLASS) ||
        state.`is`(Blocks.GLASS_PANE) ||
        state.`is`(Blocks.STONE_BRICKS) ||
        state.`is`(Blocks.COBBLESTONE) ||
        state.`is`(Blocks.LANTERN) ||
        state.`is`(Blocks.TORCH)

    private fun finishSurvey(job: Surveying, verdict: LobbySceneScore.Verdict, surveyed: ClientLevel) {
        busy = false
        val mc = Minecraft.getInstance()
        val left = job.source.left
        val level = left ?: mc.level
        val anchor = verdict.anchor
        val rejection = verdict.rejection
        val action = job.source.action
        if (level == null || level !== surveyed || (left == null && level !== trackedLevel)) {
            if (action != null) spot = LobbySpotState.Idle
            return
        }
        indoorRetry(level, job, verdict)?.let {
            report(job.source, job.origin, "outdoor ${rejection?.name}, trying indoors")
            if (left != null) {
                busy = true
                inFlight = job.source
                leaving = Leaving(left, it, null)
            } else {
                surveying = it
            }
            if (action != null) spot = LobbySpotState.Surveying(action, 0f)
            return
        }
        if (rejection != null || anchor == null) {
            report(job.source, job.origin, "rejected ${rejection?.name} (survey ${job.timing.describe()})")
            val reason = rejection ?: if (verdict.profile.interior) LobbySceneScore.Rejection.ROOM else LobbySceneScore.Rejection.SMALL
            if (action != null) {
                val result = LobbySceneScore.report(verdict.breakdown, reason, false, null, false, profile = verdict.profile, realm = verdict.realm)
                spot = LobbySpotState.Done(action, result, false, reason.reason)
            }
            return
        }
        val weaker = !LobbySceneStore.wouldKeep(job.source.world, anchor.x, anchor.z, verdict.score, job.source.stack, verdict.realm)
        if (action == null && weaker) {
            report(job.source, anchor, "rejected ${LobbySceneScore.Rejection.WEAKER.name} (score ${verdict.score})")
            return
        }
        val radius = minOf(LobbySnapshot.DEFAULT_RADIUS, mc.options.effectiveRenderDistance)
        if (radius < LobbySnapshot.MIN_RADIUS) {
            report(job.source, anchor, "rejected ${LobbySceneScore.Rejection.UNLOADED.name} (render distance)")
            if (action != null) {
                val reason = "Raise your render distance to at least ${LobbySnapshot.MIN_RADIUS} chunks"
                spot = LobbySpotState.Done(action, LobbySceneScore.report(verdict.breakdown, LobbySceneScore.Rejection.UNLOADED, true, weaker, false, reason, realm = verdict.realm), false, reason)
            }
            return
        }
        if (action == LobbySpotAction.CHECK) {
            val message = if (weaker) "Good spot. Capturing it adds an extra scene." else "Good spot. You can capture it."
            spot = LobbySpotState.Done(action, LobbySceneScore.report(verdict.breakdown, null, true, weaker, false, realm = verdict.realm), false, message)
            return
        }
        val minSection = maxOf(level.minSectionY, SectionPos.blockToSectionCoord(anchor.y - LobbySnapshot.BELOW_ANCHOR))
        val keptTop = minOf(level.maxSectionY, SectionPos.blockToSectionCoord(anchor.y + LobbySnapshot.ABOVE_ANCHOR))
        val copy = Copying(job.source, verdict, anchor, radius, job.timing, minSection, keptTop, weaker)
        if (action != null) spot = LobbySpotState.Saving(0f)
        val center = LobbyCompat.chunkOf(anchor)
        for (x in center.x - radius..center.x + radius) {
            for (z in center.z - radius..center.z + radius) copy.pending += ChunkPos(x, z)
        }
        if (left != null) {
            busy = true
            inFlight = copy.source
            leaving = Leaving(left, null, copy)
        } else {
            copying = copy
        }
    }

    private fun stepCopy(level: ClientLevel, job: Copying) {
        val start = Util.getNanos()
        while (Util.getNanos() - start < STEP_NANOS) {
            val chunk = job.chunk
            if (chunk == null) {
                if (!nextChunk(level, job) && !nextProp(level, job)) break
                continue
            }
            copyStep(level, chunk, job)
        }
        job.timing.add(Util.getNanos() - start)
        if (job.source.action != null) {
            val side = job.radius * 2 + 1
            spot = LobbySpotState.Saving(COPY_SHARE * (side * side - job.pending.size) / (side * side))
        }
        if (job.pending.isEmpty() && job.chunk == null && propsDone(job)) {
            copying = null
            finishCopy(level, job)
        }
    }

    private fun nextChunk(level: ClientLevel, job: Copying): Boolean {
        val pos = job.pending.removeFirstOrNull() ?: return false
        //? if > 1.8.9 {
        val chunk = level.chunkSource.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) ?: return true
        val copy = LobbyChunkCopy(pos.x, pos.z)
        //?} else {
        /*if (!level.hasChunk(pos.x, pos.z)) return true
        val chunk = level.chunkSource.getChunk(pos.x, pos.z)
        val copy = LobbyChunkCopy(pos.x, pos.z)
        copy.biomes = chunk.biomes.clone()
        *///?}
        job.chunk = chunk
        job.copy = copy
        job.nextSection = job.minSection
        job.blockEntities = null
        job.chunks += copy
        return true
    }

    //? if > 1.8.9 {
    private fun nextProp(level: ClientLevel, job: Copying): Boolean {
        val props = job.props
        if (props == null) {
            job.props = LobbyEntities.gather(level, job.anchor, job.minSection, job.topSection, job.source.privacy)
            return true
        }
        val entity = props.removeFirstOrNull() ?: return false
        LobbyEntities.save(level, entity, job.source.privacy)?.let { job.entities.add(it) }
        return true
    }

    private fun propsDone(job: Copying): Boolean = job.props?.isEmpty() == true
    //?} else {
    /*private fun nextProp(level: ClientLevel, job: Copying): Boolean = false

    private fun propsDone(job: Copying): Boolean = true
    *///?}

    private fun copyStep(level: ClientLevel, chunk: LevelChunk, job: Copying) {
        val copy = job.copy ?: return
        val entities = job.blockEntities
        if (entities != null) {
            if (entities.hasNext()) {
                val blockEntity = entities.next()
                //? if > 1.8.9 {
                val section = SectionPos.blockToSectionCoord(blockEntity.blockPos.y)
                //?} else {
                /*val section = SectionPos.blockToSectionCoord(blockEntity.pos.y)
                *///?}
                if (section in job.minSection..job.topSection) {
                    //? if > 1.8.9 {
                    copy.blockEntities.add(LobbySnapshot.clean(blockEntity.saveWithFullMetadata(level.registryAccess()), job.source.privacy))
                    //?} else {
                    /*copy.blockEntities.add(LobbySnapshot.clean(CompoundTag().also(blockEntity::writeNbt), job.source.privacy))
                    *///?}
                }
            } else {
                job.chunk = null
                job.copy = null
                job.blockEntities = null
            }
            return
        }
        val sectionY = job.nextSection
        if (sectionY > level.maxSectionY) {
            //? if > 1.8.9 {
            job.blockEntities = chunk.blockEntities.values.toList().iterator()
            //?} else {
            /*job.blockEntities = chunk.blockEntities.values.toList().iterator()
            *///?}
            return
        }
        job.nextSection++
        //? if > 1.8.9 {
        val section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY))
        if (sectionY > job.keptTop && section.hasOnlyAir()) return
        //?} else {
        /*val section = chunk.sections[sectionY] ?: return
        if (sectionY > job.keptTop && section.isEmpty) return
        *///?}
        copy.sections += LobbySnapshot.copySection(level, chunk.pos, sectionY, section)
        job.topSection = maxOf(job.topSection, sectionY)
    }

    private fun finishCopy(level: ClientLevel, job: Copying) {
        val encode = encoder(level, job)
        busy = true
        inFlight = job.source
        worker.execute(encode)
    }

    private fun encoder(level: ClientLevel, job: Copying): Runnable {
        val anchor = job.anchor
        val clock = LobbyCompat.clockTime(level)
        //? if >= 1.21.11 {
        val biome = level.getBiome(anchor).unwrapKey().map { it.identifier().toString() }.orElse(BIOME_NAME_FALLBACK)
        //?} elif > 1.8.9 {
        /*val biome = level.getBiome(anchor).unwrapKey().map { it.location().toString() }.orElse(BIOME_NAME_FALLBACK)
        *///?} else {
        /*val biome = level.getBiome(anchor).name
        *///?}
        val dimension = dimensionId(level)
        //? if > 1.8.9
        val factory = LobbyCompat.containers(level)
        val minY = level.minY
        val height = level.height
        return Runnable {
            try {
                val solveStart = Util.getNanos()
                val stage = LobbyStage.build(LobbyCopyView(job.chunks, minY, height), anchor, job.verdict.profile.interior)
                val solveMillis = (Util.getNanos() - solveStart) / 1_000_000L
                if (stage == null) {
                    landed.add(
                        Runnable {
                            busy = false
                            report(job.source, anchor, "rejected ${LobbySceneScore.Rejection.NO_STAGE.name} (solve ${solveMillis}ms)")
                            job.source.action?.let {
                                val none = LobbySceneScore.Rejection.NO_STAGE
                                spot = LobbySpotState.Done(it, LobbySceneScore.report(job.verdict.breakdown, none, true, job.weaker, false, realm = job.verdict.realm), false, none.reason)
                            }
                        },
                    )
                    return@Runnable
                }
                val encodeStart = Util.getNanos()
                val packs = LobbyPacks.record(job.source.stack)
                val meta = LobbySnapshot.Meta(anchor, clock, job.radius, job.minSection, job.topSection, biome, job.source.privacy, stage.toTag(), packs, job.entities)
                //? if > 1.8.9 {
                val root = LobbySnapshot.encode(job.chunks, meta, factory)
                //?} else {
                /*val root = LobbySnapshot.encode(job.chunks, meta)
                *///?}
                val score = job.verdict.score + stage.quality * STAGE_SCORE_WEIGHT
                val outcome = Outcome(root, score, biome, clock, anchor, solveMillis, packs, job.verdict.breakdown?.total, job.verdict.profile, dimension)
                val encoded = Util.getNanos()
                if (job.source.handOver) {
                    //? if > 1.8.9 {
                    val handed = root.copy()
                    //?} else {
                    /*val handed = root.copy() as CompoundTag
                    *///?}
                    val copyMillis = (Util.getNanos() - encoded) / 1_000_000L
                    val candidate = entry(job.source, outcome, 0L)
                    landed.add(Runnable { offer(job.source, candidate, handed, copyMillis) })
                }
                val compressStart = Util.getNanos()
                val data = LobbySnapshot.compress(root)
                val compressMillis = (Util.getNanos() - compressStart) / 1_000_000L
                val encodeMillis = (encoded - encodeStart) / 1_000_000L
                landed.add(Runnable { store(job, outcome, data, encodeMillis, compressMillis) })
            } catch (error: Throwable) {
                LOBBY_LOGGER.warn("Capture copy failed", error)
                landed.add(
                    Runnable {
                        busy = false
                        fail(job.source)
                    },
                )
            }
        }
    }

    private fun entry(source: Source, outcome: Outcome, bytes: Long) = LobbySceneEntry(
        "",
        "",
        source.world,
        source.label,
        outcome.biome,
        outcome.clock,
        outcome.score,
        System.currentTimeMillis(),
        bytes,
        outcome.anchor.x,
        outcome.anchor.z,
        LobbySnapshot.dataVersion,
        source.origin,
        outcome.packs,
        PartyScene.mcVersion,
        outcome.points,
        source.action == LobbySpotAction.CAPTURE,
        outcome.profile.key,
        y = outcome.anchor.y,
        dimension = outcome.dimension,
    )

    private fun offer(source: Source, candidate: LobbySceneEntry, root: CompoundTag, copyMillis: Long) {
        if (blocked(source) != null) return
        if (!candidate.manual && !LobbySceneStore.wouldKeep(source.world, candidate.x, candidate.z, candidate.score, source.stack, candidate.realm)) return
        val entry = LobbySceneStore.named(candidate)
        keepHandover(entry, root)
        returning?.takeIf { it.world == source.world }?.entry = entry
        report(source, BlockPos(entry.x, 0, entry.z), "handed over in memory as ${entry.id} (copy ${copyMillis}ms)")
    }

    private fun store(job: Copying, outcome: Outcome, data: ByteArray, encodeMillis: Long, compressMillis: Long) {
        busy = false
        val source = job.source
        blocked(source)?.let {
            report(source, outcome.anchor, "rejected ($it)")
            fail(source, it)
            return
        }
        val entry = entry(source, outcome, data.size.toLong())
        val kept = LobbySceneStore.admit(entry, data)
        if (kept) {
            wait = CAPTURED_TICKS
            val stored = LobbySceneStore.named(entry)
            recent = stored
            if (source.connection != null && source.connection === session.get()) sessionScenes += stored
            returning?.takeIf { it.world == source.world }?.entry = stored
        }
        source.action?.let {
            val report = LobbySceneScore.report(job.verdict.breakdown, null, true, job.weaker, false, realm = job.verdict.realm)
            spot = LobbySpotState.Done(it, report, kept, if (kept) "Saved to your lobby scenes" else "Could not save this scene")
        }
        report(
            source,
            outcome.anchor,
            "${if (kept) "kept" else "rejected WEAKER"} score=%.1f biome=%s size=%dKB chunks=%d entities=%d survey[%s] copy[%s] solve=%dms encode=%dms compress=%dms".format(
                Locale.ROOT,
                outcome.score,
                outcome.biome,
                data.size / 1024,
                job.chunks.size,
                job.entities.size,
                job.surveyTiming.describe(),
                job.timing.describe(),
                outcome.solveMillis,
                encodeMillis,
                compressMillis,
            ),
        )
    }

    private fun blocked(source: Source): String? {
        val mc = Minecraft.getInstance()
        val connection = source.connection
        val live = connection != null && connection === LobbyPacks.connectionOf(mc)
        if (connection != null && (connection === packed.get() || (live && LobbyPacks.serverPackApplied(mc)))) return SERVER_PACK
        val address = source.address ?: return null
        if (LobbySceneStore.serverChoice(address) == false) return SERVER_TURNED_OFF
        val rule = when {
            live && OneClientServerApi.isConnected -> OneClientServerApi.scenePolicy
            live -> policy
            else -> LobbySceneStore.serverPolicy(address)
        }
        return if (LobbySceneStore.serverAllows(address, rule)) null else SERVER_BLOCKS
    }

    private fun report(source: Source, pos: BlockPos, result: String) {
        LOBBY_LOGGER.debug("Capture {} at {}: {}", source.label, pos.toShortString(), result)
    }
}
