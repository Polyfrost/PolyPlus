package org.polyfrost.polyplus.client.lobby

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mojang.authlib.GameProfile
import kotlinx.coroutines.launch
//? if > 1.8.9
import net.minecraft.client.ClientRecipeBook
//? if >= 26.1
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
//? if > 1.8.9
import net.minecraft.client.multiplayer.CommonListenerCookie
//? if >= 1.21.10
import net.minecraft.client.multiplayer.LevelLoadTracker
import net.minecraft.client.multiplayer.MultiPlayerGameMode
//? if >= 1.21.10
import net.minecraft.client.particle.ParticleEngine
//? if > 1.8.9
import net.minecraft.client.particle.SimpleAnimatedParticle
//? if >= 26.3
import net.minecraft.client.player.ItemActivation
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.GameRenderer
//? if >= 1.21.10
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
//? if >= 1.21.8 {
import net.minecraft.client.renderer.fog.FogData
//?} elif > 1.8.9 {
/*import com.mojang.blaze3d.shaders.FogShape
import net.minecraft.client.renderer.FogRenderer
*///?}
//? if >= 1.21.4 && < 1.21.8
/*import net.minecraft.client.renderer.FogParameters*/
//? if < 1.21.4 && > 1.8.9
/*import com.mojang.blaze3d.systems.RenderSystem*/
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.mixin.client.access.LevelChunkInvoker
import net.minecraft.client.resource.skin.DefaultSkinUtils
import net.minecraft.client.world.chunk.ClientChunkCache
import net.minecraft.scoreboard.team.AbstractTeam
import net.minecraft.world.WorldSettings
import net.minecraft.world.gen.WorldGeneratorType
import org.polyfrost.oneconfig.internal.legacy.PlayerSkin
*///?}
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
//? if > 1.8.9
import net.minecraft.core.particles.ParticleTypes
//? if > 1.8.9
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
//? if > 1.8.9
import net.minecraft.nbt.NbtOps
import net.minecraft.network.Connection
//? if > 1.8.9
import net.minecraft.network.protocol.PacketFlow
//? if > 1.8.9
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData
import net.minecraft.resources.Identifier
//? if > 1.8.9
import net.minecraft.server.ServerLinks
//? if > 1.8.9
import net.minecraft.stats.StatsCounter
import net.minecraft.util.Mth
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import net.minecraft.world.Difficulty
//? if >= 26.1
import net.minecraft.world.clock.ClockNetworkState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
//? if > 1.8.9
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.DataLayer
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
//? if > 1.8.9
import net.minecraft.world.level.chunk.UpgradeData
//? if > 1.8.9
import net.minecraft.world.level.chunk.status.ChunkStatus
//? if > 1.8.9
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
//? if > 1.8.9
import net.minecraft.world.scores.Team
//? if > 1.8.9
import net.minecraft.world.ticks.LevelChunkTicks
import org.polyfrost.polyplus.api.lobby.Avatar
import org.polyfrost.polyplus.api.lobby.AvatarRole
import org.polyfrost.polyplus.api.lobby.LobbyApi
import org.polyfrost.polyplus.api.lobby.LobbyInputs
import org.polyfrost.polyplus.api.lobby.LobbyMode
import org.polyfrost.polyplus.api.lobby.LobbyPhase
import org.polyfrost.polyplus.api.lobby.LobbyState
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.party.PartyScene
import org.polyfrost.polyplus.client.pets.PetManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.privacy.PrivacyConsent
//? if >= 26.1
import org.polyfrost.polyplus.mixin.client.access.GameRendererAccessor
//? if >= 1.21.10 {
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
import org.polyfrost.polyplus.mixin.client.access.ParticleEngineAccessor
//?}
//? if > 1.8.9 {
import org.polyfrost.polyplus.mixin.client.access.OptionsAccessor
import org.polyfrost.polyplus.mixin.client.access.CameraAccessor
import net.minecraft.client.CameraType
//?}
//? if >= 26.3 {
import org.lwjgl.sdl.SDLMouse
//?} elif > 1.8.9 {
/*import org.lwjgl.glfw.GLFW
*///?} else {
/*import org.lwjgl.input.Mouse
import org.lwjgl.opengl.Display
*///?}
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.OptionalDouble
import java.util.UUID
import java.util.concurrent.CompletableFuture
//? if < 1.21.10 && > 1.8.9
/*import java.util.function.Supplier*/
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
//? if < 26.3 && > 1.8.9
/*import kotlin.math.roundToLong*/
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random
import org.apache.logging.log4j.LogManager

object LobbyScene {
    enum class Phase { IDLE, LOADING, READY, FAILED }

    data class Label(
        val name: String,
        val x: Float,
        val y: Float,
        val isLocal: Boolean,
        val invite: String? = null,
        val whereabouts: String? = null,
    )

    private class Live(
        val listener: ClientPacketListener,
        val level: ClientLevel,
        val gameMode: MultiPlayerGameMode,
        val rig: LocalPlayer,
        val stage: LobbyStage,
        val portrait: LobbyShot,
        val avatars: MutableList<LobbyAvatar>,
        var mood: LobbyMood,
        val captured: Long,
        val posts: MutableList<Pair<BlockPos, BlockState>>,
    ) {
        var nextAvatarId = FIRST_AVATAR_ID + avatars.size
        var friendTicks = 0
        var extracted = false
        var started = false
        var startNanos = 0L
        val builtNanos = Util.getNanos()
        var frames = 0L
        var frameTime = 0.0
        var frameLook: Vec3 = Vec3(0.0, 0.0, 0.0)
        val worn = HashMap<UUID, List<AttachedCosmetic>>()
        val requestedCosmetics = HashSet<Int>()
        var requestTicks = 0
        var simTick = 0L
        val joined = HashMap<UUID, Long>()
        //? if >= 1.21.10
        var particles: ParticleEngine? = null
        //? if > 1.8.9
        val props = ArrayList<Entity>()
    }

    private class Driven(
        val elapsed: Double,
        val clock: Long,
        val mouseX: Float,
        val mouseY: Float,
        val aspect: Double,
        val cast: LobbyCast,
        val reveal: LobbyReveal,
    )

    private class Saved(val scene: ClientLevel, val level: ClientLevel?, val player: LocalPlayer?, val gameMode: MultiPlayerGameMode?, val camera: Entity?, val reach: Int) {
        //? if = 1.8.9
        /*var fancy = true*/
        //? if >= 1.21.10
        var particles: ParticleEngine? = null
        //? if > 1.8.9 {
        var view: CameraType? = null
        //?} else {
        /*var view = 0
        *///?}
    }

    private class Binding(val previous: ClientLevel?)

    //? if > 1.8.9 {
    private class Loaded(val entry: LobbySceneEntry, val root: CompoundTag, val registries: LobbyRegistries)
    //?} else {
    /*private class Loaded(val entry: LobbySceneEntry, val root: CompoundTag)
    *///?}

    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    private const val VIEW_DISTANCE = 8
    private const val SHADOW_REACH = 0.08
    private const val TEAM = "lobby"
    private const val FRIEND_SYNC_TICKS = 40
    private const val COSMETIC_RETRY_TICKS = 100
    private const val CAMERA_FOV = LobbyStage.CAMERA_FOV.toFloat()
    private const val FIRST_AVATAR_ID = 0x504C415A
    private const val HEAD_LABEL_OFFSET = 0.42
    private const val SWAY_YAW_DEGREES = 1.4
    private const val SWAY_PITCH_DEGREES = 0.4
    private const val HEAD_TURN_RANGE = 30f
    private const val SPEAKER_TURN_RANGE = 75f
    private const val HOLOGRAM_TURN_RANGE = 12f
    private const val IDLE_BODY_SHARE = 0.18f
    private const val SPEAKER_BODY_SHARE = 0.2f
    private const val SPEAKER_PITCH_SHARE = 0.3f
    const val INTRO_SECONDS = 2.5
    private const val CRANE_SECONDS = 5.0
    private const val START_WAIT_NANOS = 4_000_000_000L
    private const val LABEL_HEAD_SHARE = 0.9
    private const val LABEL_BODY_SHARE = 0.5
    private const val LABEL_SIGHT_SLACK = 0.5
    private const val OPENING_SECONDS = CRANE_SECONDS
    private const val WAVE_LEAD_SECONDS = 2.0
    private const val FIRST_DISTANCE = 46f
    private const val SNOW_BOX_HEIGHT = 11.0
    private const val WAVE_SPEED = 28.0
    private const val SNOW_ATTEMPTS = 6
    private const val SNOW_CHANCE = 0.35
    private const val FOG_START_MARGIN = 18f
    private const val FOG_END_MARGIN = 64f
    private const val SNOW_BOX = 16.0
    private const val FOCUS_RATE = 5.5
    private const val FOCUS_RETURN_RATE = 4.2
    private const val FOCUS_DEPART_RATE = 9.0
    private const val MIN_LENS_DEPTH = 0.5
    private const val DEPART_SECONDS = 0.75
    private const val LOOP_TICK = 23000L
    private const val LOOP_RESUME = 3000L
    private const val OVERLAY_SECONDS = 4.2
    private const val RETURN_OVERLAY_SECONDS = 0.4
    private const val OVERLAY_FADE_SECONDS = 0.7
    private const val CAST_LIGHT = 0x00E000E0
    private const val PORTRAIT_LIGHT = 0x00F000F0
    private const val CLEAR_RADIUS = 7
    private const val CLEAR_DEPTH = 5.0
    private const val DAY_FOG_START = 34f
    private const val DAY_FOG_END = 82f
    private const val DEPART_REACH = 0.9
    private const val DEPART_FOV = 96f
    private const val PARALLAX_YAW_DEGREES = 1.6
    private const val PARALLAX_PITCH_DEGREES = 0.45
    private const val PARALLAX_RATE = 6.0
    private const val PARALLAX_REST_RATE = 2.5
    private const val PARALLAX_FOCUS_SHARE = 0.5
    private const val CULL_MARGIN_DEGREES = 8f
    private const val DRESS_FLAGS = 2 or 16
    private const val REQUEUE_RADIUS = 34
    private const val TICKS_PER_SECOND = 20.0
    private const val MAX_SIM_TICKS = 2400L
    private const val GAZE_WARM_TICKS = 20L
    private const val DAY_TICKS = 24000L
    private const val DEFAULT_CLOCK = 6000L
    private const val HOVER_LINGER_SECONDS = 1.4
    private const val TAG_HEIGHT = 0.045
    private const val TAG_CHAR = 0.013
    private const val TAG_PAD = 0.03
    private const val TAG_YOU = 0.04

    @Volatile
    var phase = Phase.IDLE
        private set

    var focused by mutableStateOf<String?>(null)
        private set

    @Volatile
    var labels: List<Label> = emptyList()
        private set

    @Volatile
    var overlayAlpha = 0f
        private set

    @Volatile
    var departProgress = 0f
        private set

    var currentId by mutableStateOf<String?>(null)
        private set

    private var live: Live? = null
    private var session: LobbySession? = null
    private var saved: Saved? = null
    private var binding: Binding? = null

    @Volatile
    private var sceneLevel: ClientLevel? = null

    @Volatile
    private var swappedIn = false
    //? if > 1.8.9
    private var cachedRegistries: LobbyRegistries? = null
    private var selected: LobbyAvatar? = null
    private val focusBlend = LobbyEase()
    private val focusAimX = LobbyEase()
    private val focusAimY = LobbyEase()
    private val focusAimZ = LobbyEase()
    private val focusReach = LobbyEase()
    private var focusSubject: Vec3? = null
    private var lensRange = 0.0
    private val parallaxX = LobbyEase()
    private val parallaxY = LobbyEase()
    private var departNanos = 0L
    private var restedPointer: Pair<Double, Double>? = null
    private var focusPointer: Pair<Double, Double>? = null
    private var releasing = false
    private var currentFov = CAMERA_FOV
    private var currentDistance = FIRST_DISTANCE
    private val attempts = ArrayDeque<LobbySceneEntry>()
    private var reloadAfterLoad = false
    private var switching = false
    private var quietSwitch = false
    private var lastPoseTime = 0.0
    private var departDone: (() -> Unit)? = null
    private var departed: (() -> Unit)? = null
    private var overlayDelay = OVERLAY_SECONDS
    private var skipPending = false

    @Volatile
    private var driven: Driven? = null
    private var simulating = false
    private var inputsFailed = false
    private var hoveredTag: String? = null
    private var leftTag: String? = null
    private var leftAt = 0.0

    @JvmStatic
    fun showing(): Boolean = shown() != null

    fun revealStarted(): Boolean = live?.started == true

    fun startupReveal(seconds: Double) {
        overlayDelay = seconds
    }

    @JvmStatic
    fun ownsFrame(): Boolean = !swappedIn && embedded && live != null && session?.visible == true

    @JvmStatic
    fun cameraFov(original: Float): Float = if (swappedIn) currentFov else original

    @JvmStatic
    fun cullingFov(original: Float): Float = if (swappedIn) minOf(original, currentFov + CULL_MARGIN_DEGREES) else original

    @JvmStatic
    fun lightFor(entity: Entity, original: Int): Int = when {
        !swappedIn || entity !is LobbyAvatar -> original
        LobbyWardrobe.open -> PORTRAIT_LIGHT
        else -> CAST_LIGHT
    }

    @JvmStatic
    fun terrainActive(): Boolean = swappedIn

    @JvmStatic
    fun hasRealPlayer(): Boolean = if (swappedIn) saved?.player != null else Minecraft.getInstance().player != null

    @JvmStatic
    fun waveClock(): Float = (waveTicks() / 20.0).toFloat()

    @JvmStatic
    fun waveAim(): Float = currentDistance

    @JvmStatic
    fun shadowDistance(distance: Double): Double = if (terrainActive()) distance * SHADOW_REACH else distance

    fun wornTextures(): Set<Identifier> = live?.avatars.orEmpty().flatMapTo(HashSet()) { avatar ->
        (avatar as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equipped().map { it.cosmetic.texture }
    }

    fun tick(): Long {
        val scene = live
        return if (scene != null && driven != null) scene.simTick else (elapsed() * 20.0).toLong()
    }

    fun motionSeconds(): Double = if (simulating) tick() / TICKS_PER_SECOND else elapsed()

    fun drivenReveal(): LobbyReveal? = driven?.reveal

    fun state(): LobbyState? {
        val scene = shown() ?: return null
        val local = scene.avatars.firstOrNull { it.isLocal } ?: return null
        val others = scene.avatars.filterNot { it.isLocal }.map { avatar ->
            Avatar(avatar.uuid, avatar.displayName, if (scene.stage.slots.any { it === avatar.rest }) AvatarRole.PARTY else AvatarRole.BACKGROUND)
        }
        return LobbyState(
            LobbyApi.API_VERSION,
            currentId.orEmpty(),
            LobbyReveal.active.id,
            elapsed(),
            scene.mood.clock,
            Avatar(local.uuid, local.displayName, AvatarRole.SELF),
            Collections.unmodifiableList(others),
        )
    }

    fun waveSeconds(): Double = if (live?.started == true && !LobbyReveal.active.waves) LOOP_RESUME / 20.0 else seconds()

    fun seconds(): Double {
        val time = elapsed()
        val rush = (time / INTRO_SECONDS).coerceIn(0.0, 1.0)
        return time + WAVE_LEAD_SECONDS * rush * (2.0 - rush)
    }

    fun introProgress(): Float = (elapsed() / INTRO_SECONDS).toFloat().coerceIn(0f, 1f)

    private fun elapsed(): Double {
        val scene = live ?: return 0.0
        driven?.let { return it.elapsed }
        return if (scene.started) (Util.getNanos() - scene.startNanos) / 1_000_000_000.0 else 0.0
    }

    private fun waveTicks(): Double {
        if (live?.started == true && !LobbyReveal.active.waves) return LOOP_RESUME.toDouble()
        val ticks = seconds() * 20.0
        if (ticks < LOOP_TICK) return ticks
        return LOOP_RESUME + (ticks - LOOP_RESUME) % (LOOP_TICK - LOOP_RESUME)
    }

    fun introPlaying(): Boolean = !skipPending && when (phase) {
        Phase.LOADING -> true
        Phase.READY -> live?.let { !it.started || elapsed() < OPENING_SECONDS } == true
        else -> false
    }

    fun skipIntro(): Boolean {
        if (driven != null || !introPlaying() || overlayAlpha >= 1f) return false
        val scene = live
        if (scene == null || !scene.started) skipPending = true else jumpOpening(scene)
        return true
    }

    private fun jumpOpening(scene: Live) {
        skipPending = false
        scene.startNanos = minOf(scene.startNanos, Util.getNanos() - (OPENING_SECONDS * 1_000_000_000.0).toLong())
        LOGGER.debug("Intro skipped")
        for (avatar in scene.avatars) {
            (avatar as PlayerEmotesAccess).`polyplus$emoteController`().stop()
            avatar.pose = Pose.STANDING
            avatar.arriveNow()
        }
        if (swappedIn) tickSceneEntities(scene.level) else swapped(scene) { tickSceneEntities(scene.level) }
    }

    private fun tickSceneEntities(level: ClientLevel) {
        //? if > 1.8.9 {
        LobbyEntities.tick(level)
        //?} else {
        /*level.tickEntities()
        *///?}
    }

    private fun pointer(mc: Minecraft): Pair<Double, Double> =
        driven?.let { it.mouseX.toDouble() to it.mouseY.toDouble() } ?: (LobbyCompat.mouseX(mc) to LobbyCompat.mouseY(mc))

    fun focus(name: String?) {
        val pointer = pointer(Minecraft.getInstance())
        if (name == null) {
            if (selected != null && pointer == focusPointer) {
                releasing = true
                return
            }
            restedPointer = pointer
        } else if (pointer == restedPointer) {
            return
        }
        select(name?.let { wanted -> live?.avatars?.firstOrNull { it.displayName == wanted } }, pointer)
    }

    fun clearFocus() {
        val pointer = pointer(Minecraft.getInstance())
        restedPointer = pointer
        select(null, pointer)
    }

    private fun select(next: LobbyAvatar?, pointer: Pair<Double, Double>) {
        if (next !== selected) LOGGER.debug("Focus {}", next?.displayName)
        selected = next
        focused = next?.displayName
        focusPointer = pointer
        releasing = false
    }

    fun depart(done: () -> Unit) {
        if (live == null) {
            done()
            return
        }
        if (departNanos != 0L) return
        LOGGER.debug("Depart")
        departNanos = Util.getNanos()
        focus(live?.avatars?.firstOrNull { it.isLocal }?.displayName)
        departDone = done
    }

    fun arrive() {
        departNanos = 0L
        departProgress = 0f
        departDone = null
        departed = null
    }

    @JvmStatic
    fun onFrameStart() {
        StartupOverlay.onFrame()
        if (saved != null) {
            LOGGER.warn("Lobby swap was left open, restoring the client")
            end()
        }
        val done = departed ?: return
        departed = null
        done()
    }

    @Volatile
    var building = false
        private set

    private val embedded: Boolean get() = session?.embedded == true

    fun start() {
        if (phase != Phase.IDLE) return
        switching = false
        reloadAfterLoad = false
        //? if = 1.8.9
        /*if (LobbyCompat.joiningWorld(Minecraft.getInstance())) return*/
        session = LobbySession(LobbyMode.STANDALONE, null)
        attempts.clear()
        attempts += LobbySceneStore.queue()
        phase = Phase.LOADING
        LobbyFriends.refresh()
        tryNext()
    }

    fun available(): Boolean = runCatching { LobbySceneStore.bundledScenes.isNotEmpty() }.getOrDefault(false)

    fun cannotRun(): Boolean {
        if (phase == Phase.FAILED) return true
        //? if = 1.8.9 {
        /*return LobbyLegacyRender.failed
        *///?} else {
        return !LobbyPipelines.compiled()
        //?}
    }

    fun apiPhase(): LobbyPhase = when (phase) {
        Phase.IDLE -> LobbyPhase.IDLE
        Phase.LOADING -> LobbyPhase.LOADING
        Phase.READY -> LobbyPhase.READY
        Phase.FAILED -> LobbyPhase.FAILED
    }

    fun isSceneLevel(level: Level?): Boolean = level != null && level === sceneLevel

    fun findScene(id: String): LobbySceneEntry? {
        val known = listOfNotNull(LobbyCapture.returnEntry()) + LobbySceneStore.usableScenes + LobbySceneStore.bundledScenes
        val entry = known.firstOrNull { it.id == id } ?: return null
        val file = if (entry.origin == LobbySceneEntry.ORIGIN_PARTY) Path.of(entry.file) else LobbySceneStore.fileOf(entry)
        return entry.takeIf { file == null || Files.exists(file) }
    }

    fun startSession(sceneId: String, inputs: LobbyInputs, mode: LobbyMode): LobbySession? {
        val entry = findScene(sceneId) ?: return null
        session?.let(::stopSession)
        if (live != null) stop()
        val owner = LobbySession(mode, inputs)
        session = owner
        switching = false
        reloadAfterLoad = false
        attempts.clear()
        attempts += entry
        phase = Phase.LOADING
        LobbyFriends.refresh()
        tryNext()
        return owner
    }

    fun stopSession(owner: LobbySession) {
        if (owner !== session) return
        if (live != null) {
            stop()
            return
        }
        session = null
        attempts.clear()
        phase = Phase.IDLE
        owner.finish(LobbyPhase.IDLE)
    }

    fun levelOf(owner: LobbySession): ClientLevel? = live?.level?.takeIf { owner === session }

    fun fovOf(owner: LobbySession): OptionalDouble =
        if (owner === session && live != null && owner.visible) OptionalDouble.of(currentFov.toDouble()) else OptionalDouble.empty()

    fun bind(owner: LobbySession): AutoCloseable {
        val scene = live
        if (owner !== session || scene == null || binding != null) return AutoCloseable {}
        val mc = Minecraft.getInstance()
        val bound = Binding(mc.level)
        binding = bound
        try {
            attach(mc, scene.level, scene)
        } catch (error: Throwable) {
            binding = null
            runCatching { attach(mc, bound.previous, null) }
            throw error
        }
        return AutoCloseable { unbind(bound) }
    }

    private fun unbind(bound: Binding) {
        if (binding !== bound) return
        binding = null
        attach(Minecraft.getInstance(), bound.previous, null)
    }

    private fun attach(mc: Minecraft, level: ClientLevel?, scene: Live?) {
        val reach = reach(mc)
        if (scene != null) setReach(mc, VIEW_DISTANCE)
        try {
            //? if > 1.8.9 {
            LobbyCompat.levelSink(mc).setLevel(level)
            //?} else {
            /*val fancy = mc.options.fancyGraphics
            if (scene != null) mc.options.fancyGraphics = true
            LobbyCompat.levelSink(mc).setWorld(level)
            mc.options.fancyGraphics = fancy
            *///?}
        } finally {
            setReach(mc, reach)
        }
        //? if >= 1.21.10 {
        val shared = live?.particles == null
        //?} else {
        /*val shared = true
        *///?}
        //? if > 1.8.9 {
        if (shared) mc.particleEngine.setLevel(level)
        //?} else {
        /*if (shared) mc.particleEngine.setWorld(level)
        *///?}
        //? if >= 1.21.8
        mc.gameRenderer.setLevel(level)
        //? if > 1.8.9 {
        if (level == null) return
        val center = if (scene != null) LobbyCompat.chunkOf(LobbyCompat.blockAt(scene.stage.anchor)) else (mc.cameraEntity ?: mc.player)?.let { LobbyCompat.chunkOf(it.blockPosition()) }
        LobbyCompat.requeueChunks(mc, level, center?.x ?: 0, center?.z ?: 0, REQUEUE_RADIUS)
        //?}
    }

    //? if > 1.8.9 {
    private fun reach(mc: Minecraft): Int = (mc.options as OptionsAccessor).`polyplus$serverRenderDistance`()

    private fun setReach(mc: Minecraft, reach: Int) = mc.options.setServerRenderDistance(reach)
    //?} else {
    /*private fun reach(mc: Minecraft): Int = mc.options.viewDistance

    private fun setReach(mc: Minecraft, reach: Int) = setRenderReach(mc, reach)
    *///?}

    fun particlesAllowed(): Boolean {
        if (!embedded) return true
        //? if >= 1.21.10 {
        return live?.particles != null
        //?} else {
        /*return false
        *///?}
    }

    fun reload() {
        if (embedded || session?.inputs != null) return
        if (phase == Phase.LOADING) {
            reloadAfterLoad = true
        } else if (phase == Phase.READY || phase == Phase.FAILED) {
            stop()
        }
    }

    fun switchTo(vararg entries: LobbySceneEntry, quiet: Boolean = false): Boolean {
        if (phase != Phase.READY || switching || departNanos != 0L || LobbyWardrobe.open || embedded || session?.inputs != null) return false
        switching = true
        quietSwitch = quiet
        attempts.clear()
        attempts += entries
        tryNext()
        return true
    }

    private fun tryNext() {
        val entry = attempts.removeFirstOrNull()
        if (entry == null) {
            if (switching) {
                switching = false
            } else {
                phase = Phase.FAILED
                session?.finish(LobbyPhase.FAILED)
            }
            return
        }
        val mc = Minecraft.getInstance()
        val owner = session
        CompletableFuture.supplyAsync({
            val root = LobbySceneStore.read(entry)
            check(LobbySnapshot.compatible(root, entry.bundled)) { "Scene ${entry.id} was saved by another game version" }
            //? if > 1.8.9 {
            val registries = cachedRegistries ?: LobbyRegistries.load()
            Loaded(entry, root, registries)
            //?} else {
            /*Loaded(entry, root)
            *///?}
        }, Util.ioPool()).whenCompleteAsync({ loaded, error ->
            if (owner !== session) {
                LOGGER.debug("Scene {} load abandoned", entry.id)
            } else if (error != null) {
                fail(entry, error)
            } else {
                runCatching { enter(mc, loaded) }.onFailure {
                    sceneLevel = live?.level
                    detach(mc)
                    fail(entry, it)
                }
            }
        }, LobbyCompat.mainExecutor(mc))
    }

    private fun fail(entry: LobbySceneEntry, error: Throwable) {
        LOGGER.warn("Scene {} did not load: {}", entry.id, error.message)
        tryNext()
    }

    private fun enter(mc: Minecraft, loaded: Loaded) {
        if (switching) {
            switching = false
            if (phase != Phase.READY || mc.level != null) return
            stop()
            overlayDelay = -OVERLAY_FADE_SECONDS
            skipPending = quietSwitch
        }
        if (mc.level != null && !embedded) {
            reloadAfterLoad = false
            phase = Phase.IDLE
            session?.finish(LobbyPhase.IDLE)
            session = null
            return
        }
        val buildStart = Util.getNanos()
        //? if > 1.8.9 {
        cachedRegistries = loaded.registries
        loaded.registries.bindStaticTags()
        //?}
        building = true
        val created = try {
            build(mc, loaded)
        } finally {
            building = false
        }
        currentId = loaded.entry.id
        LobbyReveal.next()
        //? if >= 1.21.10
        if (embedded) created.particles = ParticleEngine(created.level, (mc.particleEngine as ParticleEngineAccessor).`polyplus$resources`())
        if (driven != null) created.started = true
        live = created
        sceneLevel = created.level
        phase = Phase.READY
        session?.markBuilt()
        LobbyEvents.built(created.level)
        if (reloadAfterLoad) {
            reloadAfterLoad = false
            stop()
            return
        }
    }

    //? if = 1.8.9 {
    /*private fun build(mc: Minecraft, loaded: Loaded): Live {
        val listener = ClientPacketListener(mc, null, LobbyCompat.offlineConnection(), mc.gameProfile)
        val settings = WorldSettings(0L, WorldSettings.GameMode.SPECTATOR, false, false, WorldGeneratorType.DEFAULT)
        val level = ClientLevel(listener, settings, 0, Difficulty.PEACEFUL, mc.profiler)
        sceneLevel = level
        level.gameRules.set("doDaylightCycle", "false")
        if (!embedded) {
            val fancy = mc.options.fancyGraphics
            mc.options.fancyGraphics = true
            LobbyCompat.levelSink(mc).setWorld(level)
            mc.options.fancyGraphics = fancy
            mc.particleEngine.setWorld(level)
        }
        val root = loaded.root
        val radius = root.getIntOr("radius", LobbySnapshot.DEFAULT_RADIUS)
        val anchorData = checkNotNull(root.optIntArray("anchor"))
        val anchor = BlockPos(anchorData[0], anchorData[1], anchorData[2])
        populate(level, root, LobbyCompat.chunkOf(anchor), radius)
        val gameMode = MultiPlayerGameMode(mc, listener)
        val outerLevel = mc.level
        val outerPlayer = mc.player
        try {
            mc.level = level
            val rig = LobbyCompat.createRig(mc, level, listener)
            mc.player = rig
            rig.noClip = true
            rig.id = FIRST_AVATAR_ID - 1
            return finishBuild(mc, loaded, listener, level, gameMode, rig, anchor)
        } finally {
            mc.level = outerLevel
            mc.player = outerPlayer
        }
    }

    private fun populate(level: ClientLevel, root: CompoundTag, center: ChunkPos, radius: Int) {
        val cache = level.chunkSource as ClientChunkCache
        root.getListOrEmpty("chunks").compounds().forEach { tag ->
            val chunk = cache.loadChunk(tag.getIntOr("x", 0), tag.getIntOr("z", 0))
            tag.optByteArray("biomes")?.takeIf { it.size == chunk.biomes.size }?.let(chunk::setBiomes)
            tag.getListOrEmpty("sections").compounds().forEach { section ->
                val sectionY = section.getIntOr("y", 0)
                val data = LevelChunkSection(sectionY shl SectionPos.SECTION_BITS, true)
                section.optByteArray("blocks")?.let { data.blockStates = LobbySnapshot.unpackBlocks(it) }
                LobbySnapshot.readLight(section, "block_light")?.let { data.blockLightStorage = it }
                LobbySnapshot.readLight(section, "sky_light")?.let { data.skyLightStorage = it }
                data.validateBlockCounters()
                chunk.sections[sectionY] = data
            }
            (chunk as LevelChunkInvoker).`polyplus$populateHeightMapOnly`()
            chunk.isTerrainPopulated = true
            chunk.isLightPopulated = true
            tag.getListOrEmpty("block_entities").compounds().forEach { blockEntity ->
                BlockEntity.fromNbt(blockEntity)?.let(chunk::addBlockEntity)
            }
            chunk.load()
        }
        for (x in center.x - radius - 1..center.x + radius + 1) {
            for (z in center.z - radius - 1..center.z + radius + 1) {
                if (!cache.hasChunk(x, z)) cache.loadChunk(x, z)
            }
        }
    }

    private fun setRenderReach(mc: Minecraft, reach: Int) {
        mc.options.viewDistance = reach
    }
    *///?} else {
    private fun build(mc: Minecraft, loaded: Loaded): Live {
        val registries = loaded.registries.access
        //? if < 1.21.4 {
        /*val cookie = CommonListenerCookie(
            mc.gameProfile,
            mc.telemetryManager.createWorldSessionManager(false, null, null),
            registries,
            FeatureFlags.DEFAULT_FLAGS,
            "polyplus-lobby",
            null,
            null,
            emptyMap(),
            null,
            false,
            emptyMap(),
            ServerLinks.EMPTY,
        )
        *///?} else {
        val cookie = CommonListenerCookie(
            //? if >= 1.21.10
            LevelLoadTracker(),
            mc.gameProfile,
            //? if >= 26.2 {
            mc.telemetryManager.createWorldSessionManager(false, null, null, UUID.randomUUID()),
            //?} else {
            /*mc.telemetryManager.createWorldSessionManager(false, null, null),
            *///?}
            registries,
            FeatureFlags.DEFAULT_FLAGS,
            "polyplus-lobby",
            null,
            null,
            emptyMap(),
            null,
            emptyMap(),
            ServerLinks.EMPTY,
            //? if >= 1.21.10 {
            emptyMap(),
            false,
            //?}
        )
        //?}
        val listener = ClientPacketListener(mc, Connection(PacketFlow.CLIENTBOUND), cookie)
        val dimension = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD)
        val root = loaded.root
        val radius = root.getIntOr("radius", LobbySnapshot.DEFAULT_RADIUS)
        val level = ClientLevel(
            listener,
            ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false),
            Level.OVERWORLD,
            dimension,
            radius + 2,
            2,
            //? if < 1.21.4
            /*mc::getProfiler,*/
            LobbyCompat.levelSink(mc),
            false,
            0L,
            //? if >= 1.21.4
            63,
        )
        sceneLevel = level
        if (!embedded) {
            setReach(mc, VIEW_DISTANCE)
            LobbyCompat.levelSink(mc).setLevel(level)
            mc.particleEngine.setLevel(level)
            //? if >= 1.21.8
            mc.gameRenderer.setLevel(level)
            setReach(mc, 0)
        }
        val anchorData = checkNotNull(root.optIntArray("anchor"))
        val anchor = BlockPos(anchorData[0], anchorData[1], anchorData[2])
        val center = LobbyCompat.chunkOf(anchor)
        populate(level, root, center, radius)
        val gameMode = MultiPlayerGameMode(mc, listener)
        //? if >= 26.3 {
        val rig = gameMode.createPlayer(level, StatsCounter(), ClientRecipeBook(), ItemActivation())
        //?} else {
        /*val rig = gameMode.createPlayer(level, StatsCounter(), ClientRecipeBook())
        *///?}
        rig.noPhysics = true
        rig.id = FIRST_AVATAR_ID - 1
        return finishBuild(mc, loaded, listener, level, gameMode, rig, anchor)
    }
    //?}

    private fun finishBuild(mc: Minecraft, loaded: Loaded, listener: ClientPacketListener, level: ClientLevel, gameMode: MultiPlayerGameMode, rig: LocalPlayer, anchor: BlockPos): Live {
        sample(mc)
        val root = loaded.root
        val stage = root.optCompound("stage")?.let { LobbyStage.restore(level, it) }
            ?: LobbyStage.build(level, anchor)
            ?: error("Scene ${loaded.entry.id} has no place for the party")
        val captured = root.getLongOr("time", 0L)
        val mood = LobbyMood.of(level, LobbyCompat.blockAt(stage.aim), sceneClock(captured))
        applyTime(level, mood)
        LobbySky.mood = mood
        val posts = dress(level, stage, mood)
        val avatars = spawnAvatars(mc, level, listener, stage, driven?.cast ?: LobbyFriends.cast())
        LobbyEntrance.plan(level, avatars.filterNot { it.spectral }, avatars) { restingView(mc, stage, it) }
        val built = Live(listener, level, gameMode, rig, stage, stage.portraitShot(level), avatars, mood, captured, posts)
        //? if > 1.8.9
        built.props += runCatching { LobbyEntities.load(level, root, stage) }.getOrElse { emptyList() }
        runCatching { LobbyHerobrine.onSceneShown(level, stage) }
        return built
    }

    private fun sceneClock(captured: Long): Long = driven?.clock ?: LobbyTime.clock(captured)

    private fun syncTime(scene: Live) {
        val clock = sceneClock(scene.captured)
        if (clock == scene.mood.clock) return
        val level = scene.level
        val next = LobbyMood.of(level, LobbyCompat.blockAt(scene.stage.aim), clock)
        val mood = if (next.dark == scene.mood.dark) next.copy(lanterns = scene.mood.lanterns) else next
        if (next.dark != scene.mood.dark) {
            for ((pos, state) in scene.posts.asReversed()) level.setBlock(pos, state, DRESS_FLAGS)
            scene.posts.clear()
            scene.posts += hang(level, scene.stage, mood.lanterns)
            clearForeground(level, scene.stage)
            relight(level)
        }
        applyTime(level, mood)
        LobbySky.mood = mood
        scene.mood = mood
    }

    private fun applyTime(level: ClientLevel, mood: LobbyMood) {
        //? if >= 26.1 {
        val clock = level.dimensionType().defaultClock().orElse(null) ?: return
        level.setTimeFromServer(0L)
        level.clockManager().handleUpdates(0L, mapOf(clock to ClockNetworkState(mood.clock, 0f, 0f)))
        //?} else {
        /*LobbyCompat.setDayTime(level, mood.clock)
        *///?}
    }

    private fun dress(level: ClientLevel, stage: LobbyStage, mood: LobbyMood): MutableList<Pair<BlockPos, BlockState>> {
        val posts = hang(level, stage, mood.lanterns)
        clearForeground(level, stage)
        relight(level)
        return posts
    }

    private fun hang(level: ClientLevel, stage: LobbyStage, count: Int): MutableList<Pair<BlockPos, BlockState>> {
        val posts = ArrayList<Pair<BlockPos, BlockState>>()
        val post = Blocks.SPRUCE_FENCE.defaultBlockState()
        for (spot in stage.lanterns.take(count)) {
            place(level, spot, post, posts)
            place(level, spot.above(), post, posts)
            place(level, spot.above(2), Blocks.LANTERN.defaultBlockState(), posts)
        }
        return posts
    }

    private fun relight(level: ClientLevel) {
        //? if > 1.8.9 {
        val lightEngine = level.lightEngine
        while (lightEngine.hasLightWork()) lightEngine.runLightUpdates()
        //?}
    }

    private fun clearForeground(level: ClientLevel, stage: LobbyStage) {
        val center = LobbyCompat.blockAt(stage.anchor)
        val forward = stage.forward()
        val right = stage.right()
        for (pos in LobbyCompat.between(center.offset(-CLEAR_RADIUS, -3, -CLEAR_RADIUS), center.offset(CLEAR_RADIUS, 3, CLEAR_RADIUS))) {
            val offset = Vec3(pos.x + 0.5 - stage.anchor.x, 0.0, pos.z + 0.5 - stage.anchor.z)
            if (offset.dot(forward) < CLEAR_DEPTH && abs(offset.dot(right)) < CLEAR_RADIUS) clearPlant(level, pos)
        }
        for (slot in stage.slots + stage.background) {
            val feet = LobbyCompat.blockAt(slot.pos)
            LobbyCompat.between(feet.offset(-1, 0, -1), feet.offset(1, 1, 1)).forEach { clearPlant(level, it) }
        }
    }

    private fun clearPlant(level: ClientLevel, pos: BlockPos) {
        val state = level.getBlockState(pos)
        if (state.isAir || !state.canBeReplaced() || !state.fluidState.isEmpty || state.`is`(Blocks.SNOW)) return
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), DRESS_FLAGS)
    }

    private fun place(level: ClientLevel, pos: BlockPos, state: BlockState, posts: MutableList<Pair<BlockPos, BlockState>>) {
        val previous = level.getBlockState(pos)
        if (!previous.canBeReplaced()) return
        posts += pos to previous
        level.setBlock(pos, state, DRESS_FLAGS)
    }

    //? if > 1.8.9 {
    private fun populate(level: ClientLevel, root: CompoundTag, center: ChunkPos, radius: Int) {
        val cache = level.chunkSource
        val lightEngine = level.lightEngine
        val factory = LobbyCompat.containers(level)
        val biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME)
        val blockLookup = level.registryAccess().lookupOrThrow(Registries.BLOCK)
        val native = LobbySnapshot.native(root)
        val filled = HashMap<ChunkPos, Int>()
        cache.updateViewCenter(center.x, center.z)
        root.getListOrEmpty("chunks").compounds().forEach { tag ->
            val pos = ChunkPos(tag.getIntOr("x", 0), tag.getIntOr("z", 0))
            val sections = Array(level.sectionsCount) { factory.emptySection() }
            val light = ArrayList<Triple<Int, DataLayer?, DataLayer?>>()
            tag.getListOrEmpty("sections").compounds().forEach { section ->
                val sectionY = section.getIntOr("y", 0)
                val states = section.optCompound("blocks")?.let {
                    if (native) factory.blockCodec.parse(NbtOps.INSTANCE, it).getOrThrow() else LobbySnapshot.foreignBlocks(it, factory.emptyBlocks(), blockLookup)
                } ?: factory.emptyBlocks()
                val biomeTag = LobbySnapshot.knownBiomes(section.getCompoundOrEmpty("biomes"), biomeLookup)
                val biomes = factory.biomeCodec.parse(NbtOps.INSTANCE, biomeTag).getOrThrow()
                sections[level.getSectionIndexFromSectionY(sectionY)] = LevelChunkSection(states, biomes)
                light += Triple(sectionY, LobbySnapshot.readLight(section, "block_light"), LobbySnapshot.readLight(section, "sky_light"))
            }
            val chunk = load(level, pos, sections) ?: return@forEach
            filled[pos] = light.maxOfOrNull { it.first } ?: (level.minSectionY - 1)
            for ((sectionY, block, sky) in light) {
                val lightPos = SectionPos.of(pos, sectionY)
                block?.let { lightEngine.queueSectionData(LightLayer.BLOCK, lightPos, it) }
                sky?.let { lightEngine.queueSectionData(LightLayer.SKY, lightPos, it) }
            }
            tag.getListOrEmpty("block_entities").compounds().forEach { blockEntity ->
                val blockPos = LobbyCompat.blockEntityPos(pos, blockEntity)
                val state = chunk.getBlockState(blockPos)
                BlockEntity.loadStatic(blockPos, state, blockEntity, level.registryAccess())?.let(chunk::addAndRegisterBlockEntity)
            }
        }
        for (x in center.x - radius - 1..center.x + radius + 1) {
            for (z in center.z - radius - 1..center.z + radius + 1) {
                val pos = ChunkPos(x, z)
                if (pos !in filled) load(level, pos, Array(level.sectionsCount) { factory.emptySection() })
            }
        }
        for (x in center.x - radius - 1..center.x + radius + 1) {
            for (z in center.z - radius - 1..center.z + radius + 1) {
                val pos = ChunkPos(x, z)
                val chunk = cache.getChunk(x, z, ChunkStatus.FULL, false) ?: continue
                val top = filled[pos] ?: (level.minSectionY - 1)
                for (sectionY in top + 1 until lightEngine.maxLightSection) {
                    lightEngine.queueSectionData(LightLayer.SKY, SectionPos.of(pos, sectionY), DataLayer(15))
                }
                lightEngine.setLightEnabled(pos, true)
                LobbyCompat.lightReady(level, x, z)
                chunk.sections.forEachIndexed { index, section ->
                    lightEngine.updateSectionStatus(SectionPos.of(pos, level.getSectionYFromSectionIndex(index)), section.hasOnlyAir())
                }
            }
        }
        while (lightEngine.hasLightWork()) lightEngine.runLightUpdates()
    }

    private fun load(level: ClientLevel, pos: ChunkPos, sections: Array<LevelChunkSection>): LevelChunk? {
        val stub = LevelChunk(level, pos, UpgradeData.EMPTY, LevelChunkTicks(), LevelChunkTicks(), 0L, sections, null, null)
        Heightmap.primeHeightmaps(stub, ChunkStatus.FULL.heightmapsAfter())
        val data = ClientboundLevelChunkPacketData(stub)
        //? if >= 26.3 {
        return level.chunkSource.replaceWithPacketData(pos.x, pos.z, data)
        //?} else {
        /*return level.chunkSource.replaceWithPacketData(pos.x, pos.z, data.readBuffer, data.heightmaps, data.getBlockEntitiesTagsConsumer(pos.x, pos.z))
        *///?}
    }
    //?}

    private fun spawnAvatars(mc: Minecraft, level: ClientLevel, listener: ClientPacketListener, stage: LobbyStage, cast: LobbyCast): MutableList<LobbyAvatar> {
        //? if > 1.8.9 {
        val team = listener.scoreboard().addPlayerTeam(TEAM)
        team.nameTagVisibility = Team.Visibility.NEVER
        //?} else {
        /*val team = level.scoreboard.addTeam(TEAM)
        team.nameTagVisibility = AbstractTeam.Visibility.NEVER
        *///?}
        return castAvatars(mc, level, listener, stage, cast)
    }

    private fun castAvatars(mc: Minecraft, level: ClientLevel, listener: ClientPacketListener, stage: LobbyStage, cast: LobbyCast): MutableList<LobbyAvatar> {
        //? if > 1.8.9 {
        val localProfile = mc.gameProfile
        //?} else {
        /*val localProfile = GameProfile(ClientPlatform.localPlayerUuid(), mc.gameProfile.name)
        *///?}
        //? if >= 1.21.10 {
        val localSkin = mc.skinManager.createLookup(localProfile, false)
        //?} elif > 1.8.9 {
        /*val localSkin = Supplier { mc.skinManager.getInsecureSkin(localProfile) }
        *///?} else {
        /*val localSkin = mc.skinManager.getOrLoad(localProfile)
        *///?}
        val slot = stage.slots.first()
        //? if > 1.8.9 {
        val avatar = LobbyAvatar(level, localProfile, localProfile.name, true, slot) { localSkin.get() }
        //?} else {
        /*val avatar = LobbyAvatar(level, localProfile, localProfile.name, true, slot) { localSkin.getNow(null) ?: PlayerSkin(DefaultSkinUtils.getDefaultSkin(localProfile.id)) }
        *///?}
        enter(level, listener, avatar, FIRST_AVATAR_ID)
        val avatars = mutableListOf(avatar)
        (cast.party.zip(stage.slots.drop(1)) + cast.background.zip(stage.background)).forEach { (friend, friendSlot) ->
            avatars += friendAvatar(level, friend, friendSlot).also {
                enter(level, listener, it, FIRST_AVATAR_ID + avatars.size)
                friend.whereabouts?.let(it::conjure)
            }
        }
        subscribe(avatars)
        return avatars
    }

    private fun subscribe(avatars: List<LobbyAvatar>) {
        if (driven == null || !PrivacyConsent.allowsOnlineServices()) return
        val ids = avatars.filterNot { it.isLocal }.map { it.uuid.toString() }
        if (ids.isNotEmpty()) CosmeticSync.subscribeFriends(ids)
    }

    private fun drive(avatar: LobbyAvatar) {
        avatar.gaze.driven = true
        (avatar as PlayerEmotesAccess).`polyplus$emoteController`().clock = { (motionSeconds() * 1000.0).toLong() }
    }

    private fun friendAvatar(level: ClientLevel, friend: LobbyFriend, slot: LobbySlot) =
        LobbyAvatar(level, GameProfile(friend.uuid, friend.name), friend.name, false, slot) { LobbyFriends.skin(friend) }

    private fun enter(level: ClientLevel, listener: ClientPacketListener, avatar: LobbyAvatar, id: Int) {
        val slot = avatar.rest
        avatar.id = id
        if (driven != null) drive(avatar)
        LobbyCompat.snapEntity(avatar, slot.pos, slot.yaw)
        avatar.yHeadRot = slot.yaw
        avatar.yBodyRot = slot.yaw
        //? if > 1.8.9 {
        level.addEntity(avatar)
        listener.scoreboard().getPlayerTeam(TEAM)?.let { listener.scoreboard().addPlayerToTeam(avatar.scoreboardName, it) }
        //?} else {
        /*level.forceEntity(avatar.id, avatar)
        level.scoreboard.addMemberToTeam(avatar.name, TEAM)
        *///?}
    }

    private fun syncFriends(mc: Minecraft, scene: Live) {
        if (driven != null) return
        LobbyFriends.tick()
        if (!scene.started || ++scene.friendTicks < FRIEND_SYNC_TICKS) return
        scene.friendTicks = 0
        recast(mc, scene, LobbyFriends.cast())
    }

    private fun recast(mc: Minecraft, scene: Live, cast: LobbyCast) {
        for (friend in cast.party + cast.background) {
            val avatar = scene.avatars.firstOrNull { it.uuid == friend.uuid } ?: continue
            if (avatar.project(friend.whereabouts)) (avatar as PlayerEmotesAccess).`polyplus$emoteController`().stop()
        }
        val party = cast.party.mapTo(HashSet()) { it.uuid }
        val background = cast.background.mapTo(HashSet()) { it.uuid }
        for (avatar in scene.avatars.filterNot { it.isLocal }) {
            val wanted = when (avatar.uuid) {
                in party -> scene.stage.slots
                in background -> scene.stage.background
                else -> null
            }
            if (wanted != null && wanted.any { it === avatar.rest }) continue
            val slot = wanted?.firstOrNull { free -> scene.avatars.none { it.rest === free } }
            if (slot == null) {
                if (avatar.projection > 0.0) avatar.dismiss() else leave(scene, avatar)
            } else {
                LobbyEntrance.walk(scene.level, avatar, slot, scene.avatars, tick()) { restingView(mc, scene.stage, it) }
            }
        }
        val arrivals = arrive(scene, cast.party, scene.stage.slots) + arrive(scene, cast.background, scene.stage.background)
        if (arrivals.isEmpty()) return
        LobbyEntrance.plan(scene.level, arrivals.filterNot { it.spectral }, scene.avatars, tick()) { restingView(mc, scene.stage, it) }
        subscribe(arrivals)
    }

    private fun sample(mc: Minecraft) {
        val inputs = session?.inputs
        if (inputs == null) {
            driven = null
            return
        }
        driven = runCatching { read(mc, inputs) }.getOrElse { error ->
            if (!inputsFailed) LOGGER.warn("Lobby inputs failed", error)
            inputsFailed = true
            driven ?: Driven(0.0, DEFAULT_CLOCK, 0.5f, 0.5f, windowAspect(mc), LobbyCast(emptyList(), emptyList()), LobbyReveal.WAVE)
        }
    }

    private fun read(mc: Minecraft, inputs: LobbyInputs): Driven {
        val others = inputs.others()
        val aspect = inputs.aspect().toDouble()
        return Driven(
            inputs.elapsedSeconds().takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
            Math.floorMod(inputs.clock(), DAY_TICKS),
            unit(inputs.mouseX()),
            unit(inputs.mouseY()),
            aspect.takeIf { it.isFinite() && it > 0.0 } ?: windowAspect(mc),
            LobbyCast(castOf(others, AvatarRole.PARTY), castOf(others, AvatarRole.BACKGROUND)),
            LobbyReveal.byId(inputs.reveal()) ?: LobbyReveal.WAVE,
        )
    }

    private fun unit(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f

    private fun castOf(others: List<Avatar>, role: AvatarRole): List<LobbyFriend> =
        others.filter { it.role == role }.distinctBy { it.id }.map { LobbyFriend(it.id, it.name) }

    private fun windowAspect(mc: Minecraft): Double = mc.window.width.toDouble() / mc.window.height.coerceAtLeast(1)

    private fun viewAspect(mc: Minecraft): Double = driven?.aspect ?: windowAspect(mc)

    private fun pointerX(mc: Minecraft): Double =
        driven?.mouseX?.toDouble() ?: (LobbyCompat.mouseX(mc) / mc.window.screenWidth.coerceAtLeast(1)).coerceIn(0.0, 1.0)

    private fun pointerY(mc: Minecraft): Double =
        driven?.mouseY?.toDouble() ?: (LobbyCompat.mouseY(mc) / mc.window.screenHeight.coerceAtLeast(1)).coerceIn(0.0, 1.0)

    private fun pointerInside(mc: Minecraft): Boolean {
        if (driven != null) return true
        //? if >= 26.3 {
        return mc.isWindowActive && SDLMouse.SDL_GetMouseFocus() == mc.window.handle()
        //?} elif >= 1.21.10 {
        /*return mc.isWindowActive && GLFW.glfwGetWindowAttrib(mc.window.handle(), GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE
        *///?} elif > 1.8.9 {
        /*return mc.isWindowActive && GLFW.glfwGetWindowAttrib(mc.window.window, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE
        *///?} else {
        /*return Display.isActive() && Mouse.isInsideWindow()
        *///?}
    }

    private fun soften(offset: Double): Double = offset * (1.5 - 0.5 * offset * offset)

    private fun drivenPartial(): Float? = driven?.let {
        val ticks = it.elapsed * TICKS_PER_SECOND
        (ticks - floor(ticks)).toFloat()
    }

    @JvmStatic
    fun framePartial(): Float? = if (swappedIn) drivenPartial() else null

    private fun advanceDriven(mc: Minecraft, scene: Live) {
        val frame = driven ?: return
        val target = (frame.elapsed * TICKS_PER_SECOND).toLong()
        if (target < scene.simTick) rewind(mc, scene, frame, target)
        val everyone = frame.cast.party + frame.cast.background
        for (friend in everyone) scene.joined.putIfAbsent(friend.uuid, scene.simTick)
        recast(mc, scene, due(frame.cast, scene.joined, scene.simTick, target))
        val steps = (target - scene.simTick).coerceAtMost(MAX_SIM_TICKS)
        val late = everyone.any { (scene.joined[it.uuid] ?: 0L) in scene.simTick + 1..scene.simTick + steps }
        simulating = true
        try {
            for (step in 0L until steps) {
                if (late) recast(mc, scene, due(frame.cast, scene.joined, scene.simTick, target))
                scene.simTick++
                tickSceneEntities(scene.level)
                for (avatar in scene.avatars) {
                    if (avatar.consumeLanded()) land(avatar)
                }
                if (steps > 2L && steps - step <= GAZE_WARM_TICKS) warmGaze(scene)
            }
        } finally {
            simulating = false
        }
        if (steps > 2L) lastPoseTime = scene.simTick / TICKS_PER_SECOND
        hover(frame)
    }

    private fun due(cast: LobbyCast, joined: Map<UUID, Long>, tick: Long, target: Long): LobbyCast {
        val present = { friend: LobbyFriend -> (joined[friend.uuid] ?: 0L).let { it <= tick || it > target } }
        return LobbyCast(cast.party.filter(present), cast.background.filter(present))
    }

    private fun warmGaze(scene: Live) {
        val time = scene.simTick / TICKS_PER_SECOND
        scene.avatars.forEach { idle(it, null, time, 1.0 / TICKS_PER_SECOND) }
    }

    private fun rewind(mc: Minecraft, scene: Live, frame: Driven, target: Long) {
        val focusName = selected?.displayName
        for (avatar in scene.avatars) LobbyCompat.removeEntity(scene.level, avatar)
        if (!embedded) scene.avatars.firstOrNull { it.isLocal }?.let { PetManager.despawn(it.uuid) }
        scene.avatars.clear()
        scene.worn.clear()
        scene.avatars += castAvatars(mc, scene.level, scene.listener, scene.stage, due(frame.cast, scene.joined, 0L, target))
        scene.nextAvatarId = FIRST_AVATAR_ID + scene.avatars.size
        scene.simTick = 0L
        LobbyEntrance.plan(scene.level, scene.avatars.filterNot { it.spectral }, scene.avatars) { restingView(mc, scene.stage, it) }
        selected = focusName?.let { name -> scene.avatars.firstOrNull { it.displayName == name } }
        LOGGER.debug("Lobby rewound to {}s", frame.elapsed)
    }

    private fun hover(frame: Driven) {
        val time = frame.elapsed
        val tag = labels.firstOrNull { under(it, frame) }?.name
        val previous = hoveredTag
        if (tag != previous) {
            hoveredTag = tag
            if (previous != null) {
                leftTag = previous
                leftAt = time
            }
            if (tag != null) {
                if (tag == leftTag) leftTag = null
                focus(tag)
            }
        }
        val left = leftTag ?: return
        if (time - leftAt < HOVER_LINGER_SECONDS) return
        leftTag = null
        if (focused == left) focus(null)
    }

    private fun under(label: Label, frame: Driven): Boolean {
        val x = (label.x + 1.0) * 0.5
        val y = (1.0 - label.y) * 0.5
        val width = TAG_PAD + label.name.length * TAG_CHAR + if (label.isLocal) TAG_YOU else 0.0
        return abs(frame.mouseX - x) * frame.aspect <= width / 2.0 && frame.mouseY.toDouble() in (y - TAG_HEIGHT)..y
    }

    private fun arrive(scene: Live, friends: List<LobbyFriend>, slots: List<LobbySlot>): List<LobbyAvatar> {
        val free = slots.filter { slot -> scene.avatars.none { it.rest === slot } }
        return friends.filter { friend -> scene.avatars.none { it.uuid == friend.uuid } }.zip(free).map { (friend, slot) ->
            friendAvatar(scene.level, friend, slot).also {
                enter(scene.level, scene.listener, it, scene.nextAvatarId++)
                scene.avatars += it
                friend.whereabouts?.let(it::conjure)
            }
        }
    }

    private fun leave(scene: Live, avatar: LobbyAvatar) {
        if (selected === avatar) clearFocus()
        scene.avatars -= avatar
        scene.worn -= avatar.uuid
        LobbyCompat.removeEntity(scene.level, avatar)
    }

    private fun shown(): Live? {
        val scene = live ?: return null
        val mc = Minecraft.getInstance()
        if (swappedIn) return scene
        val owner = session
        if (owner != null && !owner.visible) return null
        if (owner?.embedded == true) return scene
        if (mc.level != null) return null
        return scene.takeIf { ClientPlatform.currentScreen().let { it is PolyPlusLobbyScreen || it is OneConfigUIScreen } }
    }

    private inline fun <T> swapped(scene: Live, block: () -> T): T {
        val began = begin(scene)
        try {
            return block()
        } finally {
            if (began) end()
        }
    }

    private fun begin(scene: Live): Boolean {
        if (saved != null) return false
        val mc = Minecraft.getInstance()
        LobbyEvents.swapIn(scene.level)
        val previous = Saved(scene.level, mc.level, mc.player, mc.gameMode, mc.cameraEntity, reach(mc))
        //? if = 1.8.9
        /*previous.fancy = mc.options.fancyGraphics*/
        saved = previous
        try {
            //? if >= 1.21.10 {
            scene.particles?.let {
                previous.particles = mc.particleEngine
                (mc as MinecraftAccessor).`polyplus$setParticleEngine`(it)
            }
            //?}
            mc.level = scene.level
            mc.player = scene.rig
            mc.gameMode = scene.gameMode
            setReach(mc, VIEW_DISTANCE)
            //? if = 1.8.9
            /*mc.options.fancyGraphics = true*/
            mc.setCameraEntity(scene.rig)
            //? if > 1.8.9 {
            previous.view = mc.options.cameraType
            mc.options.setCameraType(CameraType.FIRST_PERSON)
            if (!embedded) {
                val camera = LobbyCompat.camera(mc.gameRenderer) as CameraAccessor
                camera.`polyplus$setEyeHeight`(scene.rig.eyeHeight)
                camera.`polyplus$setEyeHeightOld`(scene.rig.eyeHeight)
            }
            //?} else {
            /*previous.view = mc.options.perspective
            mc.options.perspective = 0
            *///?}
            swappedIn = true
        } catch (error: Throwable) {
            end()
            throw error
        }
        return true
    }

    private fun end() {
        val previous = saved ?: return
        saved = null
        val mc = Minecraft.getInstance()
        try {
            mc.level = previous.level
            mc.player = previous.player
            mc.gameMode = previous.gameMode
            try {
                setReach(mc, previous.reach)
                //? if = 1.8.9
                /*mc.options.fancyGraphics = previous.fancy*/
                //? if >= 1.21.10
                previous.particles?.let { (mc as MinecraftAccessor).`polyplus$setParticleEngine`(it) }
            } finally {
                //? if > 1.8.9 {
                previous.view?.let(mc.options::setCameraType)
                //?} else {
                /*mc.options.perspective = previous.view
                *///?}
                mc.setCameraEntity(previous.camera)
            }
        } finally {
            swappedIn = false
            LobbyEvents.swapOut(previous.scene)
        }
    }

    private fun lerp(from: Double, to: Double, progress: Double) = from + (to - from) * progress

    private fun restingView(mc: Minecraft, stage: LobbyStage, time: Double): LobbyEntrance.View {
        val crane = 1.0 - (time / CRANE_SECONDS).coerceIn(0.0, 1.0)
        val eased = 1.0 - crane * crane * crane
        val distance = lerp(stage.craneDistance, stage.distance, eased)
        val pitch = lerp(stage.cranePitch, stage.pitch.toDouble(), eased)
        val yaw = stage.yaw + lerp(stage.craneOrbit, 0.0, eased)
        val aspect = viewAspect(mc)
        val look = LobbyEntrance.view(Vec3(0.0, 0.0, 0.0), yaw, pitch, CAMERA_FOV, aspect).look
        return LobbyEntrance.view(stage.aim.subtract(look.scale(distance)), yaw, pitch, CAMERA_FOV, aspect)
    }

    private fun poseRig(scene: Live, time: Double): Vec3 {
        val stage = scene.stage
        val rig = scene.rig
        val mc = Minecraft.getInstance()
        val crane = 1.0 - (time / CRANE_SECONDS).coerceIn(0.0, 1.0)
        val eased = 1.0 - crane * crane * crane
        val elapsed = (time - lastPoseTime).coerceIn(0.0, 0.1)
        lastPoseTime = time
        LobbyWardrobe.advance(elapsed)
        settlePointer(mc, scene, elapsed)
        settleFocus(mc, scene, elapsed)
        val parallaxShare = 1.0 - (1.0 - PARALLAX_FOCUS_SHARE) * focusBlend.value
        var distance = lerp(stage.craneDistance, stage.distance, eased) + focusReach.value
        var pitch = lerp(stage.cranePitch, stage.pitch.toDouble(), eased) + sin(time * 0.23 + 2.0) * SWAY_PITCH_DEGREES + parallaxY.value * PARALLAX_PITCH_DEGREES * parallaxShare
        var yaw = stage.yaw + lerp(stage.craneOrbit, 0.0, eased) + sin(time * 0.17) * SWAY_YAW_DEGREES + parallaxX.value * PARALLAX_YAW_DEGREES * parallaxShare
        var aim = stage.aim.add(Vec3(focusAimX.value, focusAimY.value, focusAimZ.value))
        val framed = LobbyFraming.frame(time, elapsed, eased, OPENING_SECONDS)
        scene.avatars.firstOrNull { it.isLocal }?.takeIf { framed > 0.0 }?.let { star ->
            pitch += (LobbyFraming.PITCH - scene.stage.pitch) * framed
            distance += (LobbyFraming.DISTANCE - distance) * framed
            aim = aim.lerp(LobbyFraming.aim(star.rest.pos, yaw, pitch, CAMERA_FOV.toDouble(), viewAspect(mc)), framed)
        }
        val speaker = if (driven != null) null else PartyScene.speaking().let { ids -> scene.avatars.firstOrNull { it.uuid in ids } }
        scene.avatars.forEach { idle(it, speaker, time, elapsed) }
        val portrait = LobbyWardrobe.amount
        val local = scene.avatars.firstOrNull { it.isLocal }
        scene.avatars.filterNot { it.isLocal }.forEach { it.fade(LobbyWardrobe.castFade) }
        if (time >= INTRO_SECONDS) {
            scene.avatars.toList().forEach { avatar ->
                avatar.settleProjection(elapsed)
                if (avatar.vanished) leave(scene, avatar)
            }
        }
        if (portrait > 0.0 && local != null) {
            val worn = (local as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equipped().map { it.cosmetic }
            val pose = LobbyWardrobe.pose(scene.portrait, local.rest.pos, worn, CAMERA_FOV, elapsed, time)
            aim = aim.lerp(pose.aim, portrait)
            distance = lerp(distance, pose.distance, portrait)
            pitch = lerp(pitch, pose.pitch, portrait)
            yaw += Mth.wrapDegrees(pose.yaw - yaw) * portrait
            LobbyWardrobe.poseAvatar(local, scene.portrait, time)
        }
        currentFov = CAMERA_FOV

        if (departNanos != 0L) {
            val leaving = ((Util.getNanos() - departNanos) / 1_000_000_000.0 / DEPART_SECONDS).coerceIn(0.0, 1.0)
            departProgress = leaving.toFloat()
            val push = leaving * leaving
            distance = lerp(distance, DEPART_REACH, push)
            currentFov = (CAMERA_FOV + (DEPART_FOV - CAMERA_FOV) * push).toFloat()
            if (leaving >= 1.0) {
                departed = departDone
                departDone = null
            }
        }

        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        val look = Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
        val camera = aim.subtract(look.scale(distance))
        currentDistance = distance.toFloat()
        settleLens(camera, look, stage)
        LobbyWaveState.view(look, currentFov)
        LobbyFraming.viewed(camera, look, currentFov.toDouble(), viewAspect(mc))
        rig.setPos(camera.x, camera.y - rig.eyeHeight, camera.z)
        rig.setYRot(yaw.toFloat())
        rig.setXRot(pitch.toFloat())
        rig.setOldPosAndRot()
        return look
    }

    private fun settlePointer(mc: Minecraft, scene: Live, elapsed: Double) {
        val inside = pointerInside(mc)
        val x = if (inside) soften(pointerX(mc) * 2.0 - 1.0) else 0.0
        val y = if (inside) soften(pointerY(mc) * 2.0 - 1.0) else 0.0
        if (scene.started) {
            val rate = if (inside) PARALLAX_RATE else PARALLAX_REST_RATE
            parallaxX.step(x, rate, elapsed)
            parallaxY.step(y, rate, elapsed)
        } else {
            parallaxX.snap(x)
            parallaxY.snap(y)
        }
        if (releasing && (!inside || pointer(mc) != focusPointer)) clearFocus()
    }

    private fun settleFocus(mc: Minecraft, scene: Live, elapsed: Double) {
        val stage = scene.stage
        val avatar = selected
        val leaving = departNanos != 0L
        val shot = avatar?.let { LobbyFraming.shot(restingView(mc, stage, CRANE_SECONDS), it.position(), it.bbHeight.toDouble(), leaving) }
        val rate = when {
            leaving -> FOCUS_DEPART_RATE
            avatar != null -> FOCUS_RATE
            else -> FOCUS_RETURN_RATE
        }
        focusBlend.step(if (avatar != null) 1.0 else 0.0, rate, elapsed)
        focusAimX.step(shot?.let { it.aim.x - stage.aim.x } ?: 0.0, rate, elapsed)
        focusAimY.step(shot?.let { it.aim.y - stage.aim.y } ?: 0.0, rate, elapsed)
        focusAimZ.step(shot?.let { it.aim.z - stage.aim.z } ?: 0.0, rate, elapsed)
        focusReach.step(shot?.let { it.distance - stage.distance } ?: 0.0, rate, elapsed)
        shot?.let { focusSubject = it.subject }
    }

    private fun settleLens(camera: Vec3, look: Vec3, stage: LobbyStage) {
        val rest = viewDepth(camera, look, stage.anchor)
        val subject = focusSubject?.let { viewDepth(camera, look, it) } ?: rest
        lensRange = lerp(rest, subject, focusBlend.value)
    }

    private fun viewDepth(camera: Vec3, look: Vec3, point: Vec3): Double {
        val depth = point.subtract(camera).dot(look)
        return if (depth.isFinite()) depth.coerceAtLeast(MIN_LENS_DEPTH) else MIN_LENS_DEPTH
    }

    //? if >= 26.1 {
    @JvmStatic
    fun onExtractEnd(renderer: GameRenderer, deltaTracker: DeltaTracker) {
        val scene = shown() ?: return
        val mc = Minecraft.getInstance()
        sample(mc)
        swapped(scene) {
            advanceDriven(mc, scene)
            val time = elapsed()
            val look = poseRig(scene, time)
            scene.level.update()
            val camera = LobbyCompat.camera(renderer)
            camera.update(deltaTracker)
            //? if < 26.2
            /*mc.levelRenderer.update(camera)*/
            val partialTicks = drivenPartial() ?: deltaTracker.getGameTimeDeltaPartialTick(false)
            val state = LobbyCompat.renderState(renderer)
            val access = renderer as GameRendererAccessor
            //? if >= 26.3
            state.shouldRenderLevel = true
            access.`polyplus$lightmapExtractor`().extract(state.lightmapRenderState, 1f)
            //? if >= 26.3 {
            access.`polyplus$extractCamera`(deltaTracker, partialTicks)
            //?} else {
            /*access.`polyplus$extractCamera`(deltaTracker, partialTicks, camera.getCameraEntityPartialTicks(deltaTracker))
            *///?}
            try {
                LobbyCompat.extractLevel(mc, deltaTracker, camera, partialTicks)
            } catch (error: Throwable) {
                if (!LobbyEntities.purge(scene.level, partialTicks)) throw error
                LobbyCompat.extractLevel(mc, deltaTracker, camera, partialTicks)
            }
            //? if >= 26.2
            state.levelRenderState.render3dCrosshair = false
            if (scene.started) {
                val wave = waveTicks()
                //? if >= 26.3 {
                state.levelRenderState.gameTime = wave.toLong()
                state.levelRenderState.worldPartialTicks = (wave - wave.toLong()).toFloat()
                //?} else {
                /*state.levelRenderState.gameTime = (wave - partialTicks).roundToLong()
                *///?}
            }
            //? if >= 26.3 {
            state.levelRenderState.playerRenderState.hasPlayer = false
            state.requestedPostEffects.add(LobbySky.POST_EFFECT)
            //?}
            shapeFog(scene, state.levelRenderState.cameraRenderState.fogData)
            hideUnrevealedBlockEntities(state.levelRenderState.blockEntityRenderStates)
            publishFrame(mc, renderer, scene, time, look)
            LobbyHologram.extract(scene.avatars, partialTicks)
        }
    }

    @JvmStatic
    fun onRenderHead() {
        val scene = live ?: return
        if (scene.extracted) begin(scene)
    }
    //?} else {
    /*@JvmStatic
    fun onRenderHead() {
        val scene = shown() ?: return
        val mc = Minecraft.getInstance()
        //? if > 1.8.9
        if (mc.noRender) return
        sample(mc)
        begin(scene)
        advanceDriven(mc, scene)
        //? if > 1.8.9
        syncWave(scene, mc)
        mc.gameRenderer.setRenderBlockOutline(false)
        scene.frameTime = elapsed()
        scene.frameLook = poseRig(scene, scene.frameTime)
        scene.extracted = true
    }

    @JvmStatic
    fun onLevelDrawn(renderer: GameRenderer) {
        val scene = live ?: return
        if (scene.extracted && swappedIn) publishFrame(Minecraft.getInstance(), renderer, scene, scene.frameTime, scene.frameLook)
    }

    *///?}

    //? if < 26.1 && > 1.8.9 {
    /*private fun syncWave(scene: Live, mc: Minecraft) {
        if (!scene.started) return
        //? if >= 1.21.4 {
        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        //?} else {
        /^val partialTicks = mc.timer.getGameTimeDeltaPartialTick(false)
        ^///?}
        LobbyCompat.setGameTime(scene.level, (waveTicks() - partialTicks).roundToLong())
    }
    *///?}

    //? if < 26.1 && >= 1.21.8 {
    /*@JvmStatic
    fun onFogBuilt(fog: FogData) {
        val scene = live ?: return
        if (swappedIn) shapeFog(scene, fog)
    }
    *///?}

    //? if >= 1.21.4 && < 1.21.8 {
    /*@JvmStatic
    fun shapeFog(fog: FogParameters, mode: FogRenderer.FogMode): FogParameters {
        val range = terrainFogRange(mode) ?: return fog
        return FogParameters(range.first, range.second, FogShape.CYLINDER, fog.red(), fog.green(), fog.blue(), fog.alpha())
    }
    *///?} elif < 1.21.4 && > 1.8.9 {
    /*@JvmStatic
    fun shapeFog(mode: FogRenderer.FogMode) {
        val range = terrainFogRange(mode) ?: return
        RenderSystem.setShaderFogStart(range.first)
        RenderSystem.setShaderFogEnd(range.second)
        RenderSystem.setShaderFogShape(FogShape.CYLINDER)
    }
    *///?}

    //? if = 1.8.9 {
    /*@JvmStatic
    fun terrainFogStart(): Float {
        val scene = live ?: return 0f
        return currentDistance + Mth.lerp(scene.mood.night, DAY_FOG_START, FOG_START_MARGIN)
    }

    @JvmStatic
    fun terrainFogEnd(): Float {
        val scene = live ?: return 0f
        return currentDistance + Mth.lerp(scene.mood.night, DAY_FOG_END, FOG_END_MARGIN)
    }

    @JvmStatic
    fun aimDistance(): Float = currentDistance
    *///?}

    //? if >= 1.21.8 {
    private fun shapeFog(scene: Live, fog: FogData) {
        fog.environmentalStart = Float.MAX_VALUE
        fog.environmentalEnd = currentDistance
        fog.renderDistanceStart = currentDistance + Mth.lerp(scene.mood.night, DAY_FOG_START, FOG_START_MARGIN)
        fog.renderDistanceEnd = currentDistance + Mth.lerp(scene.mood.night, DAY_FOG_END, FOG_END_MARGIN)
    }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun aimDistance(): Float = currentDistance

    private fun terrainFogRange(mode: FogRenderer.FogMode): Pair<Float, Float>? {
        val scene = live ?: return null
        if (!swappedIn || mode != FogRenderer.FogMode.FOG_TERRAIN) return null
        return (currentDistance + Mth.lerp(scene.mood.night, DAY_FOG_START, FOG_START_MARGIN)) to (currentDistance + Mth.lerp(scene.mood.night, DAY_FOG_END, FOG_END_MARGIN))
    }
    *///?}

    private fun sectionsSettled(mc: Minecraft): Boolean = LobbyCompat.sectionsDone(mc)

    private fun publishFrame(mc: Minecraft, renderer: GameRenderer, scene: Live, time: Double, look: Vec3) {
        val yawRad = Math.toRadians(scene.rig.yRot.toDouble())
        val right = Vec3(-cos(yawRad), 0.0, -sin(yawRad))
        //? if = 1.8.9
        /*LobbyCompat.pose(scene.rig, look, right, currentFov)*/
        val focusDistance = lerp(lensRange, currentDistance.toDouble(), LobbyWardrobe.amount).toFloat()
        LobbySky.upload(mc, currentFov, look, right, right.cross(look), time.toFloat(), focusDistance, scene.mood)
        val eye = scene.rig.position().add(0.0, scene.rig.eyeHeight.toDouble(), 0.0)
        labels = scene.avatars.filter { it.arrival == LobbyAvatar.Arrival.LANDED && !it.dismissed && inSight(scene.level, eye, look, it) }.map { avatar ->
            val head = avatar.position().add(0.0, avatar.bbHeight + HEAD_LABEL_OFFSET, 0.0)
            val screen = LobbyCompat.project(renderer, head, currentFov)
            Label(avatar.displayName, screen.x.toFloat(), screen.y.toFloat(), avatar.isLocal, avatar.uuid.toString().takeIf { scene.stage.background.any { it === avatar.rest } }, avatar.whereabouts)
        }
        overlayAlpha = (((time - overlayDelay) / OVERLAY_FADE_SECONDS).toFloat()).coerceIn(0f, 1f) * (1f - departProgress * 4f).coerceIn(0f, 1f)
        scene.extracted = true
        scene.frames++
        if (!scene.started && scene.frames > 30 && (sectionsSettled(mc) || Util.getNanos() - scene.builtNanos > START_WAIT_NANOS)) {
            scene.started = true
            scene.startNanos = Util.getNanos()
            LobbyCompat.setGameTime(scene.level, 0L)
            if (skipPending) jumpOpening(scene)
        }
    }

    private fun inSight(level: ClientLevel, eye: Vec3, look: Vec3, avatar: LobbyAvatar): Boolean {
        val base = avatar.position()
        val head = base.add(0.0, avatar.bbHeight * LABEL_HEAD_SHARE, 0.0)
        if (head.subtract(eye).dot(look) <= 0.0) return false
        return listOf(head, base.add(0.0, avatar.bbHeight * LABEL_BODY_SHARE, 0.0)).any { point ->
            LobbyStage.reach(level, eye, point) >= eye.distanceTo(point) - LABEL_SIGHT_SLACK
        }
    }

    @JvmStatic
    fun unrevealed(x: Int, z: Int): Boolean {
        val scene = live ?: return false
        val front = seconds() * WAVE_SPEED
        if (!swappedIn || front > 200.0) return false
        if (scene.started && !LobbyReveal.active.waves) return false
        val rig = scene.rig
        val camera = rig.position().add(0.0, rig.eyeHeight.toDouble(), 0.0)
        return LobbyWaveFront.distance(x + 0.5, z + 0.5, camera, scene.stage.aim, rig.yRot.toDouble(), rig.xRot.toDouble(), currentFov.toDouble()) > front - 6.0
    }

    //? if >= 1.21.10 {
    @JvmStatic
    fun hideUnrevealedBlockEntities(states: MutableList<BlockEntityRenderState>) {
        states.removeIf { unrevealed(it.blockPos.x, it.blockPos.z) }
    }
    //?}

    //? if >= 26.1 {
    @JvmStatic
    fun drawHolograms(renderer: GameRenderer) {
        if (swappedIn) LobbyHologram.draw(renderer)
    }
    //?} elif >= 1.21.10 {
    /*@JvmStatic
    fun drawHolograms(renderer: GameRenderer) {
        val scene = live ?: return
        if (!swappedIn) return
        LobbyHologram.extract(scene.avatars, drivenPartial() ?: Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false))
        LobbyHologram.draw(renderer)
    }
    *///?} elif >= 1.21.4 {
    /*@JvmStatic
    fun drawHolograms(renderer: GameRenderer) {
        val scene = live ?: return
        if (swappedIn) LobbyHologram.draw(renderer, scene.avatars, drivenPartial() ?: Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false))
    }
    *///?} elif > 1.8.9 {
    /*@JvmStatic
    fun drawHolograms(renderer: GameRenderer) {
        val scene = live ?: return
        if (swappedIn) LobbyHologram.draw(renderer, scene.avatars, drivenPartial() ?: Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false))
    }
    *///?} else {
    /*@JvmStatic
    fun drawHolograms(tickDelta: Float) {
        val scene = live ?: return
        if (swappedIn) LobbyHologram.draw(scene.avatars, drivenPartial() ?: tickDelta, waveSeconds().toFloat())
    }
    *///?}

    @JvmStatic
    fun onLevelRendered() {
        val scene = live ?: return
        if (scene.extracted && swappedIn) {
            //? if < 26.1
            /*Minecraft.getInstance().gameRenderer.setRenderBlockOutline(true)*/
            end()
            scene.extracted = false
        }
    }

    @JvmStatic
    fun onClientTick() {
        val scene = shown() ?: return
        val mc = Minecraft.getInstance()
        val inside = embedded
        swapped(scene) {
            if (!inside) mc.gameRenderer.tick()
            //? if > 1.8.9
            stageProps(scene)
            if (driven == null) tickSceneEntities(scene.level)
            LobbyHerobrine.tick(scene.level, scene.rig, driven == null && scene.started && !introPlaying() && selected == null && departNanos == 0L && !LobbyWardrobe.open)
            //? if >= 1.21.10
            scene.level.tickBlockEntities()
            //? if > 1.8.9 {
            scene.level.tick { true }
            //?} else {
            /*scene.level.tick()
            if (!inside) mc.worldRenderer.tick()
            *///?}
            syncTime(scene)
            LobbyCompat.setGameTime(scene.level, if (scene.started) waveTicks().toLong() else 0L)
            ambient(scene)
            syncLocalCosmetics(scene)
            syncFriends(mc, scene)
            scene.avatars.filterNot { it.isLocal }.forEach { syncFriendCosmetics(scene, it) }
            if (++scene.requestTicks >= COSMETIC_RETRY_TICKS) {
                scene.requestTicks = 0
                scene.requestedCosmetics.clear()
            }
            //? if >= 1.21.10 {
            if (!inside || scene.particles != null) mc.particleEngine.tick()
            //?} else {
            /*if (!inside) mc.particleEngine.tick()
            *///?}
        }
    }

    //? if > 1.8.9 {
    private fun stageProps(scene: Live) {
        val props = scene.props
        var index = props.size - 1
        while (index >= 0) {
            val prop = props[index]
            if (!unrevealed(prop.blockX, prop.blockZ)) {
                props.removeAt(index)
                LobbyEntities.enter(scene.level, prop)
            }
            index--
        }
    }
    //?}

    private fun syncLocalCosmetics(scene: Live) {
        val ids = CosmeticCatalog.localEquipped().ids()
        requestCosmetics(scene, ids)
        val avatar = scene.avatars.firstOrNull { it.isLocal } ?: return
        if (!embedded) syncLocalPet(avatar)
        val resolved = LobbyWardrobe.preview?.equipment?.equipped()?.map { it.cosmetic }
            ?: ids.mapNotNull(CosmeticAssetCache::getAttachedCosmetic)
        wear(scene, avatar, resolved)
    }

    private fun syncFriendCosmetics(scene: Live, avatar: LobbyAvatar) {
        val ids = CosmeticCatalog.getRemoteEquipped(avatar.uuid)?.values.orEmpty()
        requestCosmetics(scene, ids)
        wear(scene, avatar, ids.mapNotNull(CosmeticAssetCache::getAttachedCosmetic))
    }

    private fun requestCosmetics(scene: Live, ids: Collection<Int>) {
        ids.filter { CosmeticAssetCache.getAttachedCosmetic(it) == null && scene.requestedCosmetics.add(it) }.forEach { id ->
            PolyPlusClient.SCOPE.launch { runSuspendCatching { CosmeticAssetCache.ensureCosmeticLoaded(id) } }
        }
    }

    private fun wear(scene: Live, avatar: LobbyAvatar, cosmetics: List<AttachedCosmetic>) {
        if (cosmetics == scene.worn[avatar.uuid].orEmpty()) return
        val equipment = (avatar as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`()
        equipment.equipped().map { it.cosmetic.slot }.forEach(equipment::unequip)
        cosmetics.forEach(equipment::equip)
        scene.worn[avatar.uuid] = cosmetics
    }

    private fun syncLocalPet(avatar: LobbyAvatar) {
        val pet = CosmeticCatalog.localEquipped().equipped[BodySlot.Pet]?.takeIf { CosmeticAssetCache.getPetDefinition(it) != null }
        if (pet != null) {
            PetManager.ensurePet(avatar.uuid, pet)
        } else if (PetManager.currentPetCosmeticId(avatar.uuid) != null) {
            PetManager.despawn(avatar.uuid)
        }
    }

    private fun ambient(scene: Live) {
        if (!scene.started) return
        val level = scene.level
        val aim = scene.stage.anchor
        if (scene.mood.snow && particlesAllowed()) {
            repeat(SNOW_ATTEMPTS) {
                if (Random.nextDouble() < SNOW_CHANCE) {
                    val x = aim.x + Random.nextDouble(-SNOW_BOX, SNOW_BOX)
                    val z = aim.z + Random.nextDouble(-SNOW_BOX, SNOW_BOX)
                    val y = aim.y + Random.nextDouble(0.0, SNOW_BOX_HEIGHT)
                    //? if > 1.8.9 {
                    level.addAlwaysVisibleParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.015, -0.035, 0.01)
                    //?} else {
                    /*LobbyCompat.snowflake(Minecraft.getInstance().particleEngine, x, y, z)
                    *///?}
                }
            }
        }
        if (driven != null) return
        for (avatar in scene.avatars) {
            if (avatar.consumeLanded()) land(avatar)
        }
    }

    private fun land(avatar: LobbyAvatar) {
        if (avatar.pose == Pose.STANDING && !avatar.spectral) LobbyEntrance.wave(avatar)
    }

    private fun idle(avatar: LobbyAvatar, speaker: LobbyAvatar?, time: Double, elapsed: Double) {
        if (avatar.arrival != LobbyAvatar.Arrival.LANDED) return
        val gaze = avatar.gaze
        val spectral = avatar.spectral
        gaze.wander(time, if (spectral) HOLOGRAM_TURN_RANGE else HEAD_TURN_RANGE, avatar.landedTick.coerceAtLeast(0L) / TICKS_PER_SECOND)
        val rest = avatar.rest.yaw
        val steered = avatar.isLocal && LobbyFraming.steer(avatar, time)
        if (!steered && !spectral && speaker != null && speaker !== avatar) {
            val from = avatar.position()
            val to = speaker.position()
            val toward = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z)).toFloat()
            val turn = Mth.wrapDegrees(toward - rest).coerceIn(-SPEAKER_TURN_RANGE, SPEAKER_TURN_RANGE)
            gaze.aim(rest + turn, gaze.wanderPitch * SPEAKER_PITCH_SHARE, rest + turn * SPEAKER_BODY_SHARE)
        } else if (!steered) {
            gaze.aim(rest + gaze.wanderYaw, gaze.wanderPitch, rest + gaze.wanderYaw * IDLE_BODY_SHARE)
        }
        gaze.greet(if (spectral) 0f else gaze.greeting(time))
        gaze.drive(avatar, elapsed)
    }

    private fun detach(mc: Minecraft) {
        if (embedded) return
        mc.setCameraEntity(null)
        //? if > 1.8.9 {
        LobbyCompat.levelSink(mc).setLevel(null)
        mc.particleEngine.setLevel(null)
        //?} else {
        /*LobbyCompat.levelSink(mc).setWorld(null)
        mc.particleEngine.setWorld(null)
        *///?}
        //? if >= 1.21.8
        mc.gameRenderer.setLevel(null)
    }

    @JvmStatic
    fun stop() {
        switching = false
        reloadAfterLoad = false
        val scene = live ?: run {
            if (phase != Phase.LOADING) {
                phase = Phase.IDLE
                session?.finish(LobbyPhase.IDLE)
                session = null
            } else {
                session?.takeIf { it.embedded }?.let(::stopSession)
            }
            return
        }
        val mc = Minecraft.getInstance()
        end()
        binding?.let(::unbind)
        val owner = session
        val inside = owner?.embedded == true
        live = null
        currentId = null
        LobbySky.mood = LobbyMood.NEUTRAL
        overlayDelay = RETURN_OVERLAY_SECONDS
        skipPending = false
        labels = emptyList()
        overlayAlpha = 0f
        departProgress = 0f
        departNanos = 0L
        departDone = null
        selected = null
        currentFov = CAMERA_FOV
        for (ease in listOf(focusBlend, focusAimX, focusAimY, focusAimZ, focusReach, parallaxX, parallaxY)) ease.snap(0.0)
        focusSubject = null
        focusPointer = null
        releasing = false
        phase = Phase.IDLE
        focused = null
        driven = null
        inputsFailed = false
        hoveredTag = null
        leftTag = null
        LobbyWardrobe.reset()
        LobbyHerobrine.stop()
        if (!inside) scene.avatars.firstOrNull { it.isLocal }?.let { PetManager.despawn(it.uuid) }
        detach(mc)
        session = null
        sceneLevel = null
        //? if > 1.8.9 {
        runCatching { scene.level.close() }.onFailure { LOGGER.warn("Level close failed", it) }
        //?}
        LOGGER.info("Scene released")
        owner?.finish(LobbyPhase.IDLE)
        LobbyEvents.ended()
    }
}

object LobbyFraming {
    enum class Reaction { NOD, GLANCE, WAVE }

    const val PITCH = 9.0
    const val DISTANCE = 6.2
    const val SCREEN_X = 0.58
    private const val SCREEN_Y = -0.06
    private const val CHEST = 1.05
    private const val EYE = 1.62
    private const val RATE = 4.0
    private const val FOCUS_ZOOM = 0.42
    private const val FOCUS_CENTER_PULL = 0.2
    private const val FOCUS_SUBJECT_SHARE = 0.6
    private const val FOCUS_BODY_HALF = 0.45
    private const val FOCUS_TAG_LIFT = 0.42
    private const val FOCUS_TAG_HEIGHT = 34.0
    private const val FOCUS_TAG_HALF = 80.0
    private const val CARD_DEPTH = 0.4
    private const val BODY_SHARE = 0.3
    private const val NOD_SECONDS = 0.55
    private const val NOD_DEGREES = 13.0
    private const val NOD_DIP = 0.32
    private const val NOD_REBOUND = 0.7
    private const val NOD_REBOUND_SHARE = 0.16
    private const val GLANCE_SECONDS = 1.3
    private const val GLANCE_IN = 0.22
    private const val GLANCE_OUT = 0.7
    private const val SWAY_SHARE = 0.25
    private const val WAVE_REST_SECONDS = 4.0

    class Shot(val aim: Vec3, val distance: Double, val subject: Vec3)

    @Volatile
    var onboarding = false

    private val ease = LobbyEase()
    private var amount = 0.0
    private var cardX = -0.45
    private var cardY = 0.0
    private var camera = Vec3(0.0, 0.0, 0.0)
    private var look = Vec3(0.0, 0.0, 1.0)
    private var tanV = 0.0
    private var aspect = 1.0
    private var reaction: Reaction? = null
    private var reactedAt = -1.0
    private var waving = false

    fun card(x: Double, y: Double) {
        cardX = x
        cardY = y
    }

    fun react(next: Reaction) {
        if (next == Reaction.WAVE) {
            waving = true
        } else {
            reaction = next
            reactedAt = -1.0
        }
    }

    fun frame(time: Double, elapsed: Double, eased: Double, opening: Double): Double {
        val target = if (onboarding) 1.0 else 0.0
        if (time < opening) ease.snap(target) else ease.step(target, RATE, elapsed)
        amount = ease.value.coerceIn(0.0, 1.0)
        return amount * eased
    }

    fun shot(view: LobbyEntrance.View, feet: Vec3, height: Double, diving: Boolean): Shot {
        val raise = height * FOCUS_SUBJECT_SHARE
        val subject = feet.add(0.0, raise, 0.0)
        val offset = subject.subtract(view.camera)
        val depth = offset.dot(view.look)
        val reach = depth * (1.0 - FOCUS_ZOOM)
        if (diving) return Shot(subject, reach, subject)
        val span = reach * view.tanHalf
        val unitX = 2.0 * minOf(1.0 / LobbyLayout.DESIGN_WIDTH, 1.0 / (LobbyLayout.DESIGN_HEIGHT * view.aspect))
        val unitY = 2.0 * minOf(view.aspect / LobbyLayout.DESIGN_WIDTH, 1.0 / LobbyLayout.DESIGN_HEIGHT)
        val halfWidth = maxOf(FOCUS_BODY_HALF / (span * view.aspect), FOCUS_TAG_HALF * unitX)
        val side = 1.0 - LobbyLayout.BarEnd.value * unitX - halfWidth
        val above = (height - raise + FOCUS_TAG_LIFT) * view.up.y / span + FOCUS_TAG_HEIGHT * unitY
        val below = raise * view.up.y / span
        val top = 1.0 - LobbyLayout.Top.value * unitY - above
        val bottom = LobbyLayout.PageBottom.value * unitY + below - 1.0
        val x = fit(offset.dot(view.right) / (depth * view.tanHalf * view.aspect) * (1.0 - FOCUS_CENTER_PULL), -side, side)
        val y = fit(offset.dot(view.up) / (depth * view.tanHalf) * (1.0 - FOCUS_CENTER_PULL), bottom, top)
        return Shot(subject.subtract(view.right.scale(x * span * view.aspect)).subtract(view.up.scale(y * span)), reach, subject)
    }

    private fun fit(value: Double, low: Double, high: Double): Double = if (low > high) (low + high) / 2.0 else value.coerceIn(low, high)

    fun aim(rest: Vec3, yaw: Double, pitch: Double, fov: Double, aspect: Double): Vec3 {
        val (right, up) = basis(lookOf(yaw, pitch))
        val half = DISTANCE * tan(Math.toRadians(fov / 2.0))
        return rest.add(0.0, CHEST, 0.0).subtract(right.scale(SCREEN_X * half * aspect)).subtract(up.scale(SCREEN_Y * half))
    }

    fun viewed(camera: Vec3, look: Vec3, fov: Double, aspect: Double) {
        this.camera = camera
        this.look = look
        this.tanV = tan(Math.toRadians(fov / 2.0))
        this.aspect = aspect
    }

    fun steer(avatar: LobbyAvatar, time: Double): Boolean {
        if (waving && avatar.pose == Pose.STANDING) {
            waving = false
            if (avatar.gaze.wavedWithin(WAVE_REST_SECONDS)) {
                reaction = Reaction.NOD
                reactedAt = -1.0
            } else {
                LobbyEntrance.wave(avatar)
            }
        }
        val kind = reaction
        if (kind != null && reactedAt < 0.0) reactedAt = time
        val since = time - reactedAt
        val reacting = kind != null && since < if (kind == Reaction.NOD) NOD_SECONDS else GLANCE_SECONDS
        if (!reacting) reaction = null
        if (amount <= 0.001 && !reacting) return false
        val gaze = avatar.gaze
        val idleYaw = avatar.rest.yaw + gaze.wanderYaw
        val idlePitch = gaze.wanderPitch
        val eye = avatar.position().add(0.0, EYE, 0.0)
        val (right, up) = basis(look)
        val depth = currentDepth(eye) * CARD_DEPTH
        val card = camera.add(look.scale(depth)).add(right.scale(cardX * depth * tanV * aspect)).add(up.scale(cardY * depth * tanV))
        var yaw = Mth.lerp(amount.toFloat(), idleYaw, facing(eye, card) + (idleYaw - avatar.rest.yaw) * SWAY_SHARE.toFloat())
        var pitch = Mth.lerp(amount.toFloat(), idlePitch, tilt(eye, card) + idlePitch * SWAY_SHARE.toFloat())
        if (reacting && kind == Reaction.GLANCE) {
            val weight = glanceWeight(since / GLANCE_SECONDS)
            yaw += Mth.wrapDegrees(facing(eye, camera) - yaw) * weight
            pitch += (tilt(eye, camera) - pitch) * weight
        }
        val nod = if (reacting && kind == Reaction.NOD) (nodCurve(since / NOD_SECONDS) * NOD_DEGREES).toFloat() else 0f
        val body = avatar.rest.yaw + Mth.wrapDegrees(facing(eye, card) - avatar.rest.yaw) * (BODY_SHARE * amount).toFloat()
        gaze.aim(yaw, pitch, body, reacting, nod)
        return true
    }

    private fun glanceWeight(progress: Double): Float {
        val rise = LobbyGaze.smooth((progress / GLANCE_IN).toFloat())
        val fall = LobbyGaze.smooth(((progress - GLANCE_OUT) / (1.0 - GLANCE_OUT)).toFloat())
        return rise * (1f - fall)
    }

    private fun nodCurve(progress: Double): Double = when {
        progress < NOD_DIP -> LobbyGaze.smooth((progress / NOD_DIP).toFloat()).toDouble()
        progress < NOD_REBOUND -> 1.0 - (1.0 + NOD_REBOUND_SHARE) * LobbyGaze.smooth(((progress - NOD_DIP) / (NOD_REBOUND - NOD_DIP)).toFloat())
        else -> -NOD_REBOUND_SHARE * (1.0 - LobbyGaze.smooth(((progress - NOD_REBOUND) / (1.0 - NOD_REBOUND)).toFloat()))
    }

    private fun currentDepth(eye: Vec3): Double = eye.subtract(camera).dot(look).coerceAtLeast(1.0)

    private fun lookOf(yaw: Double, pitch: Double): Vec3 {
        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        return Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
    }

    private fun basis(look: Vec3): Pair<Vec3, Vec3> {
        val right = look.cross(Vec3(0.0, 1.0, 0.0)).normalize()
        return right to right.cross(look)
    }

    private fun facing(from: Vec3, to: Vec3): Float = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z)).toFloat()

    private fun tilt(from: Vec3, to: Vec3): Float =
        (-Math.toDegrees(atan2(to.y - from.y, hypot(to.x - from.x, to.z - from.z)))).toFloat()
}
