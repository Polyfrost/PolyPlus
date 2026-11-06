package org.polyfrost.polyplus.client.lobby

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
import net.minecraft.network.Connection
import net.minecraft.tags.BlockTags
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
//? if > 1.8.9
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
//? if > 1.8.9
import net.minecraft.world.level.storage.LevelResource
import org.apache.logging.log4j.LogManager
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

object LobbyCapture {
    private class Source(
        val world: String,
        val label: String,
        val origin: String,
        val privacy: LobbyPrivacy,
        val connection: Connection?,
        val stack: Set<String>,
    ) {
        @Volatile
        var handOver = false
    }

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

    private class Surveying(val source: Source, val survey: LobbySurvey) {
        val timing = Timing()
        var cursor = 0
    }

    private class Copying(
        val source: Source,
        val verdict: LobbySceneScore.Verdict,
        val anchor: BlockPos,
        val radius: Int,
        val surveyTiming: Timing,
        val minSection: Int,
        val keptTop: Int,
    ) {
        val timing = Timing()
        val chunks = ArrayList<LobbyChunkCopy>()
        val pending = ArrayDeque<ChunkPos>()
        var chunk: LevelChunk? = null
        var copy: LobbyChunkCopy? = null
        var nextSection = 0
        var blockEntities: Iterator<BlockEntity>? = null
        var topSection = minSection
    }

    private class Outcome(
        val root: CompoundTag,
        val score: Double,
        val biome: String,
        val clock: Long,
        val anchor: BlockPos,
        val solveMillis: Long,
        val packs: List<LobbyScenePack>,
    )

    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
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
    private const val BURIED_BLOCKS = 8
    private const val STAGE_SCORE_WEIGHT = 0.2
    private const val BIOME_NAME_FALLBACK = "minecraft:plains"

    @Volatile
    var busy = false
        private set

    private var surveying: Surveying? = null
    private var copying: Copying? = null
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
        if (!busy) rememberPolicy(mc)
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

    @JvmStatic
    fun onServerPack(connection: Connection) {
        packed = WeakReference(connection)
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
        LOGGER.debug("Scene {} read from memory", id)
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
        if (!back.pending || busy || copying != null || surveying != null) return
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
        inFlight?.handOver = true
        if (copying == null && surveying == null && !busy) {
            begin(mc, level, player.blockPosition(), player.onGround(), player.isVehicle || player.isPassenger)
        }
        surveying?.let { job ->
            job.source.handOver = true
            val start = Util.getNanos()
            val cursor = BlockPos.MutableBlockPos()
            guarded {
                while (job.cursor < LobbySurvey.CELLS) sample(level, job.survey, job.cursor++, cursor)
                val verdict = LobbySceneScore.judge(job.survey)
                job.timing.add(Util.getNanos() - start)
                surveying = null
                finishSurvey(job, verdict)
            }
        }
        copying?.let { job ->
            job.source.handOver = true
            guarded { while (copying === job) stepCopy(level, job) }
        }
        settle()
    }

    @JvmStatic
    fun drain() {
        while (true) {
            val task = landed.poll() ?: break
            guarded { task.run() }
        }
        settle()
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            LOGGER.warn("Capture step failed", error)
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
        surveying = Surveying(source, LobbySurvey(feet, level.minY))
        report(source, feet, "started")
    }

    private fun source(mc: Minecraft): Source? {
        if (!LobbySceneStore.captureEnabled || sessionPacked(mc)) return null
        val connection = session.get()
        val stack = LobbyPacks.activeIds().toSet()
        val server = mc.singleplayerServer
        //? if > 1.8.9 {
        if (mc.hasSingleplayerServer() && server != null) {
            val folder = server.getWorldPath(LevelResource.ROOT).normalize().fileName?.toString() ?: server.worldData.levelName
            return Source("world:$folder", server.worldData.levelName, LobbySceneEntry.ORIGIN_WORLD, LobbyPrivacy.OWN_WORLD, connection, stack)
        }
        //?} else {
        /*if (mc.isIntegratedServerRunning && server != null) {
            return Source("world:${server.worldSaveName}", server.worldName, LobbySceneEntry.ORIGIN_WORLD, LobbyPrivacy.OWN_WORLD, connection, stack)
        }
        *///?}
        val data = mc.currentServer ?: return null
        val live = if (OneClientServerApi.isConnected) OneClientServerApi.scenePolicy else policy
        if (!LobbySceneStore.serverAllows(data.ip, live)) return null
        return Source(LobbySceneStore.worldKey(data.ip), data.name, LobbySceneEntry.ORIGIN_SERVER, LobbyPrivacy.SERVER, connection, stack)
    }

    private fun early(level: ClientLevel, feet: BlockPos): LobbySceneScore.Rejection? {
        //? if > 1.8.9 {
        val dimension = level.dimensionType()
        if (dimension.hasCeiling() || !dimension.hasSkyLight() || dimension.hasFixedTime()) return LobbySceneScore.Rejection.DIMENSION
        //?} else {
        /*if (level.dimension.id != 0) return LobbySceneScore.Rejection.DIMENSION
        *///?}
        if (level.getBrightness(LightLayer.SKY, feet) >= MIN_SKY_LIGHT) return null
        val surface = groundHeight(level, feet.x, feet.z)
        return if (surface > feet.y + BURIED_BLOCKS) LobbySceneScore.Rejection.UNDERGROUND else LobbySceneScore.Rejection.ENCLOSED
    }

    private fun stepSurvey(level: ClientLevel, job: Surveying) {
        val start = Util.getNanos()
        val cursor = BlockPos.MutableBlockPos()
        val survey = job.survey
        while (job.cursor < LobbySurvey.CELLS && Util.getNanos() - start < STEP_NANOS) {
            sample(level, survey, job.cursor++, cursor)
        }
        job.timing.add(Util.getNanos() - start)
        if (job.cursor < LobbySurvey.CELLS) return
        surveying = null
        busy = true
        inFlight = job.source
        worker.execute {
            val verdict = try {
                LobbySceneScore.judge(survey)
            } catch (error: Throwable) {
                LOGGER.warn("Capture survey failed", error)
                landed.add(Runnable { busy = false })
                return@execute
            }
            landed.add(Runnable { finishSurvey(job, verdict) })
        }
    }

    private fun sample(level: ClientLevel, survey: LobbySurvey, index: Int, cursor: BlockPos.MutableBlockPos) {
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

    private fun finishSurvey(job: Surveying, verdict: LobbySceneScore.Verdict) {
        busy = false
        val mc = Minecraft.getInstance()
        val level = mc.level
        val anchor = verdict.anchor
        val rejection = verdict.rejection
        if (level == null || level !== trackedLevel) return
        if (rejection != null || anchor == null) {
            report(job.source, job.survey.origin, "rejected ${rejection?.name} (survey ${job.timing.describe()})")
            return
        }
        if (!LobbySceneStore.wouldKeep(job.source.world, anchor.x, anchor.z, verdict.score, job.source.stack)) {
            report(job.source, anchor, "rejected ${LobbySceneScore.Rejection.WEAKER.name} (score ${verdict.score})")
            return
        }
        val radius = minOf(LobbySnapshot.DEFAULT_RADIUS, mc.options.effectiveRenderDistance)
        if (radius < LobbySnapshot.MIN_RADIUS) {
            report(job.source, anchor, "rejected ${LobbySceneScore.Rejection.UNLOADED.name} (render distance)")
            return
        }
        val minSection = maxOf(level.minSectionY, SectionPos.blockToSectionCoord(anchor.y - LobbySnapshot.BELOW_ANCHOR))
        val keptTop = minOf(level.maxSectionY, SectionPos.blockToSectionCoord(anchor.y + LobbySnapshot.ABOVE_ANCHOR))
        val copy = Copying(job.source, verdict, anchor, radius, job.timing, minSection, keptTop)
        val center = LobbyCompat.chunkOf(anchor)
        for (x in center.x - radius..center.x + radius) {
            for (z in center.z - radius..center.z + radius) copy.pending += ChunkPos(x, z)
        }
        copying = copy
    }

    private fun stepCopy(level: ClientLevel, job: Copying) {
        val start = Util.getNanos()
        while (Util.getNanos() - start < STEP_NANOS) {
            val chunk = job.chunk
            if (chunk == null) {
                if (!nextChunk(level, job)) break
                continue
            }
            copyStep(level, chunk, job)
        }
        job.timing.add(Util.getNanos() - start)
        if (job.pending.isEmpty() && job.chunk == null) {
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
        val anchor = job.anchor
        val clock = LobbyCompat.clockTime(level)
        //? if >= 1.21.11 {
        val biome = level.getBiome(anchor).unwrapKey().map { it.identifier().toString() }.orElse(BIOME_NAME_FALLBACK)
        //?} elif > 1.8.9 {
        /*val biome = level.getBiome(anchor).unwrapKey().map { it.location().toString() }.orElse(BIOME_NAME_FALLBACK)
        *///?} else {
        /*val biome = level.getBiome(anchor).name
        *///?}
        //? if > 1.8.9
        val factory = LobbyCompat.containers(level)
        val minY = level.minY
        val height = level.height
        busy = true
        inFlight = job.source
        worker.execute {
            try {
                val solveStart = Util.getNanos()
                val stage = LobbyStage.build(LobbyCopyView(job.chunks, minY, height), anchor)
                val solveMillis = (Util.getNanos() - solveStart) / 1_000_000L
                if (stage == null) {
                    landed.add(
                        Runnable {
                            busy = false
                            report(job.source, anchor, "rejected ${LobbySceneScore.Rejection.NO_STAGE.name} (solve ${solveMillis}ms)")
                        },
                    )
                    return@execute
                }
                val encodeStart = Util.getNanos()
                val packs = LobbyPacks.record(job.source.stack)
                val meta = LobbySnapshot.Meta(anchor, clock, job.radius, job.minSection, job.topSection, biome, job.source.privacy, stage.toTag(), packs)
                //? if > 1.8.9 {
                val root = LobbySnapshot.encode(job.chunks, meta, factory)
                //?} else {
                /*val root = LobbySnapshot.encode(job.chunks, meta)
                *///?}
                val score = job.verdict.score + stage.quality * STAGE_SCORE_WEIGHT
                val outcome = Outcome(root, score, biome, clock, anchor, solveMillis, packs)
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
                LOGGER.warn("Capture copy failed", error)
                landed.add(Runnable { busy = false })
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
    )

    private fun offer(source: Source, candidate: LobbySceneEntry, root: CompoundTag, copyMillis: Long) {
        if (!allowed(source) || !LobbySceneStore.wouldKeep(source.world, candidate.x, candidate.z, candidate.score, source.stack)) return
        val entry = LobbySceneStore.named(candidate)
        keepHandover(entry, root)
        returning?.takeIf { it.world == source.world }?.entry = entry
        report(source, BlockPos(entry.x, 0, entry.z), "handed over in memory as ${entry.id} (copy ${copyMillis}ms)")
    }

    private fun store(job: Copying, outcome: Outcome, data: ByteArray, encodeMillis: Long, compressMillis: Long) {
        busy = false
        val source = job.source
        if (!allowed(source)) {
            report(source, outcome.anchor, "rejected SERVER_PACK")
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
        report(
            source,
            outcome.anchor,
            "${if (kept) "kept" else "rejected WEAKER"} score=%.1f biome=%s size=%dKB chunks=%d survey[%s] copy[%s] solve=%dms encode=%dms compress=%dms".format(
                Locale.ROOT,
                outcome.score,
                outcome.biome,
                data.size / 1024,
                job.chunks.size,
                job.surveyTiming.describe(),
                job.timing.describe(),
                outcome.solveMillis,
                encodeMillis,
                compressMillis,
            ),
        )
    }

    private fun allowed(source: Source): Boolean = source.connection == null || source.connection !== packed.get()

    private fun report(source: Source, pos: BlockPos, result: String) {
        LOGGER.debug("Capture {} at {}: {}", source.label, pos.toShortString(), result)
    }
}
