package org.polyfrost.polyplus.client.gui.onboarding.live

//? if = 1.8.9 {
/*import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.platform.NativeImage
import java.awt.image.BufferedImage
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.network.PlayerInfo
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.render.model.PlayerModelPart
import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.platform.Lighting
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resource.skin.DefaultSkinUtils
import net.minecraft.core.BlockPos
import net.minecraft.entity.living.LivingEntity
import net.minecraft.entity.living.attribute.EntityAttributes
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity
import net.minecraft.resources.Identifier
import net.minecraft.scoreboard.team.AbstractTeam
import net.minecraft.stat.PlayerStats
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.LocalAccountSkin
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.lobby.id
import org.polyfrost.polyplus.client.lobby.isPassenger
import org.polyfrost.polyplus.client.lobby.setOldPosAndRot
import org.polyfrost.polyplus.client.lobby.setPos
import org.polyfrost.polyplus.client.lobby.xRot
import org.polyfrost.polyplus.client.lobby.xRotO
import org.polyfrost.polyplus.client.lobby.yBodyRot
import org.polyfrost.polyplus.client.lobby.yBodyRotO
import org.polyfrost.polyplus.client.lobby.yHeadRot
import org.polyfrost.polyplus.client.lobby.yHeadRotO
import org.polyfrost.polyplus.client.lobby.yRot
import org.polyfrost.polyplus.client.lobby.yRotO
import org.slf4j.LoggerFactory
import sun.misc.Unsafe

internal class LivePlayerSkin(val texture: Identifier, val slim: Boolean, val cape: Identifier?)

internal class LivePlayerAvatar(level: ClientLevel, profile: GameProfile) : RemotePlayer(level, profile) {
    var shownSkin: LivePlayerSkin? = null
    var driving = false
    var wantSprint = false

    init {
        isSilent = true
        noClip = false
        stepHeight = STEP_HEIGHT
    }

    override fun getSkinTextureLocation(): Identifier = shownSkin?.texture ?: DefaultSkinUtils.getDefaultSkin(uuid)

    override fun getCapeTextureLocation(): Identifier? = shownSkin?.cape

    override fun getModelType(): String = if (shownSkin?.slim == true) SLIM_MODEL else WIDE_MODEL

    override fun getInfo(): PlayerInfo? = null

    override fun hasInfo(): Boolean = true

    override fun hasSkinTexture(): Boolean = true

    override fun isSpectator(): Boolean = false

    override fun getScoreboardTeam(): AbstractTeam? = null

    override fun isModelPartVisible(part: PlayerModelPart): Boolean = true

    override fun isLocallyControlled(): Boolean = true

    override fun pushAwayCollidingEntities() = Unit

    override fun playStepSound(pos: BlockPos, block: Block) = Unit

    override fun mobTick() {
        super.mobTick()
        if (!driving || isPassenger) return
        if (isSprinting != wantSprint) isSprinting = wantSprint
        speed = getAttribute(EntityAttributes.MOVEMENT_SPEED).get().toFloat()
        val walkSpeedO = lastWalkAnimationSpeed
        val walkSpeed = walkAnimationSpeed
        val walkProgress = walkAnimationProgress
        moveRelative(sidewaysSpeed, forwardSpeed)
        lastWalkAnimationSpeed = walkSpeedO
        walkAnimationSpeed = walkSpeed
        walkAnimationProgress = walkProgress
    }

    private companion object {
        const val STEP_HEIGHT = 0.6f
        const val SLIM_MODEL = "slim"
        const val WIDE_MODEL = "default"
    }
}

internal class LivePlayerHorse(level: ClientLevel) : HorseBaseEntity(level) {
    init {
        isSilent = true
    }

    override fun pushAwayCollidingEntities() = Unit

    override fun playStepSound(pos: BlockPos, block: Block) = Unit
}

private class LiveNameGuard(
    mc: Minecraft,
    level: Level,
    connection: ClientPacketListener,
    stats: PlayerStats,
) : LocalPlayer(mc, level, connection, stats) {
    override fun getScoreboardTeam(): AbstractTeam? = null
}

internal object LivePlayerAvatars {
    private val LOG = LoggerFactory.getLogger("polyplus/live-player")
    private val warned = HashSet<String>()
    private var guard: LocalPlayer? = null

    fun lobbyLevel(): ClientLevel? = LiveSceneWorld.level()

    fun level(): ClientLevel? = lobbyLevel()

    private var nextId = FIRST_ID

    fun player(level: ClientLevel): LivePlayerAvatar? =
        runCatching { LivePlayerAvatar(level, LocalAccountSkin.profile()).also { it.id = nextId++ } }.onFailure { warn("player", it) }.getOrNull()

    fun horse(level: ClientLevel): LivePlayerHorse? =
        runCatching { LivePlayerHorse(level).also { it.id = nextId++ } }.onFailure { warn("horse", it) }.getOrNull()

    fun nameGuard(): LocalPlayer? {
        guard?.let { return it }
        return runCatching { unsafe().allocateInstance(LiveNameGuard::class.java) as LocalPlayer }
            .onFailure { warn("name guard", it) }
            .getOrNull()
            .also { guard = it }
    }

    fun warn(key: String, error: Throwable) {
        if (warned.add(key)) LOG.warn("[live-player] {} preview failed", key, error)
    }

    fun release() = Unit

    private fun unsafe(): Unsafe {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return field.get(null) as Unsafe
    }

    private const val FIRST_ID = Int.MIN_VALUE + 4096
}

internal object LivePlayerSkins {
    private var fallbackCape: Identifier? = null
    private var showcase: LivePlayerSkin? = null
    private var showcaseMissing = false

    fun base(): LivePlayerSkin {
        val profile = LocalAccountSkin.profile()
        val textures = runCatching { LocalAccountSkin.textures() }.getOrNull()
        val texture = textures?.texture
        if (texture != null) return LivePlayerSkin(texture, textures.slim == true, textures.cape)
        return LivePlayerSkin(DefaultSkinUtils.getDefaultSkin(profile.id), DefaultSkinUtils.getDefaultModelType(profile.id) == "slim", null)
    }

    fun layered(skin: LivePlayerSkin): LivePlayerSkin {
        if (LivePlayerMods.hasOuterLayer(skin) != false) return skin
        return showcaseSkin() ?: skin
    }

    private fun showcaseSkin(): LivePlayerSkin? {
        showcase?.let { return it }
        if (showcaseMissing) return null
        val loaded = runCatching {
            val image = readImage(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, SHOWCASE_FILE)) ?: return@runCatching null
            if (image.width != SKIN_SIZE || image.height != SKIN_SIZE) return@runCatching null
            val id = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, SHOWCASE_TEXTURE)
            Minecraft.getInstance().textureManager.register(id, DynamicTexture(image))
            LivePlayerSkin(id, false, null)
        }.getOrNull()
        if (loaded == null) showcaseMissing = true
        showcase = loaded
        return loaded
    }

    fun withCape(skin: LivePlayerSkin): LivePlayerSkin {
        val cape = localCape() ?: skin.cape ?: fallback() ?: return skin
        return LivePlayerSkin(skin.texture, skin.slim, cape)
    }

    private fun localCape(): Identifier? = runCatching {
        CosmeticCatalog.localEquipped().cape?.let { CosmeticAssetCache.getCapeResource(it) }
            ?: MojangCapeService.localCapeTexture()
    }.getOrNull()

    private fun fallback(): Identifier? {
        fallbackCape?.let { return it }
        return runCatching {
            val id = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "onboarding/live_cape")
            val image = BufferedImage(CAPE_WIDTH, CAPE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
            val cloth = readImage(Identifier.fromNamespaceAndPath(VANILLA, CLOTH_TEXTURE))
            val trim = readImage(Identifier.fromNamespaceAndPath(VANILLA, TRIM_TEXTURE))
            for (y in 0 until CAPE_HEIGHT) {
                for (x in 0 until CAPE_WIDTH) {
                    val face = y in 1..CAPE_FACE_HEIGHT && (x in 1..CAPE_FACE_WIDTH || x in CAPE_BACK_U until CAPE_BACK_U + CAPE_FACE_WIDTH)
                    val u = if (x <= CAPE_FACE_WIDTH) x else x - CAPE_BACK_U + 1
                    val border = face && (y == 1 || y == CAPE_FACE_HEIGHT || u == 1 || u == CAPE_FACE_WIDTH)
                    val source = if (face && !border) cloth else trim
                    val fallbackColor = if (face && !border) CLOTH_COLOR else TRIM_COLOR
                    val color = source?.getRGB(x % source.width, y % source.height) ?: fallbackColor
                    image.setRGB(x, y, color or OPAQUE)
                }
            }
            Minecraft.getInstance().textureManager.register(id, DynamicTexture(image))
            id
        }.getOrNull().also { fallbackCape = it }
    }

    private fun readImage(id: Identifier): BufferedImage? = runCatching {
        Minecraft.getInstance().resourceManager.getResource(id).asStream().use { ImageIO.read(it) }
    }.getOrNull()

    private const val VANILLA = "minecraft"
    private const val SHOWCASE_FILE = "onboarding/showcase-skin.png"
    private const val SHOWCASE_TEXTURE = "onboarding/showcase_skin"
    private const val SKIN_SIZE = 64
    private const val CAPE_WIDTH = 64
    private const val CAPE_HEIGHT = 32
    private const val CAPE_BACK_U = 12
    private const val CAPE_FACE_WIDTH = 10
    private const val CAPE_FACE_HEIGHT = 16
    private const val CLOTH_TEXTURE = "textures/blocks/wool_colored_red.png"
    private const val TRIM_TEXTURE = "textures/blocks/wool_colored_white.png"
    private const val CLOTH_COLOR = 0xFFA12722.toInt()
    private const val TRIM_COLOR = 0xFFE9ECEC.toInt()
    private const val OPAQUE = 0xFF000000.toInt()
}

internal object LivePlayerMods {
    private val LOG = LoggerFactory.getLogger("polyplus/live-player")
    private val warned = HashSet<String>()

    private fun warnOnce(key: String, error: Throwable) {
        if (warned.add(key)) LOG.warn("[live-player] {} compat failed", key, error)
    }

    private fun config(name: String): Any? = runCatching {
        Class.forName(name, false, LivePlayerMods::class.java.classLoader).getField("INSTANCE").get(null)
    }.getOrNull()

    private val waveyMovement: Any? by lazy {
        runCatching { config(WAVEY_CONFIG)?.let { it.javaClass.getMethod("getCapeMovement").invoke(it) } }
            .onFailure { warnOnce("wavey-capes", it) }
            .getOrNull()
    }

    private val skinLayerOptions: List<Any>? by lazy {
        runCatching {
            val config = config(SKIN_LAYERS_CONFIG) ?: return@runCatching null
            SKIN_LAYER_OPTIONS.map { config.javaClass.getMethod("get" + it.replaceFirstChar(Char::uppercaseChar)).invoke(config) }
        }.onFailure { warnOnce("skin-layers", it) }.getOrNull()
    }

    val waveyInstalled: Boolean get() = waveyMovement != null

    val skinLayersInstalled: Boolean get() = skinLayerOptions != null

    fun <T> withWaveyCapes(simulated: Boolean, block: () -> T): T {
        val option = waveyMovement ?: return block()
        val saved = runCatching { (option.javaClass.getMethod("get").invoke(option) as Enum<*>).name }.getOrNull() ?: return block()
        val wanted = if (!simulated) MOVEMENT_VANILLA else if (saved == MOVEMENT_VANILLA) MOVEMENT_WAVEY else saved
        val setter = runCatching { option.javaClass.getMethod("setByName", String::class.java) }.getOrNull() ?: return block()
        runCatching { setter.invoke(option, wanted) }.onFailure { warnOnce("wavey-capes-set", it) }
        try {
            return if (simulated) withModEnabled(WAVEY_ID, block) else block()
        } finally {
            runCatching { setter.invoke(option, saved) }
        }
    }

    fun <T> withSkinLayers(enabled: Boolean, block: () -> T): T {
        val options = skinLayerOptions ?: return block()
        val saved = runCatching { options.map { it.javaClass.getMethod("get").invoke(it) } }.getOrNull() ?: return block()
        runCatching { options.forEach { it.javaClass.getMethod("set", Any::class.java).invoke(it, enabled) } }
            .onFailure { warnOnce("skin-layers-set", it) }
        try {
            return if (enabled) withModEnabled(SKIN_LAYERS_ID, block) else block()
        } finally {
            runCatching { options.forEachIndexed { i, it -> it.javaClass.getMethod("set", Any::class.java).invoke(it, saved[i]) } }
        }
    }

    private val toggleLookup: Pair<Method, Any?>? by lazy {
        runCatching {
            val toggles = Class.forName(MOD_TOGGLES)
            val method = runCatching { toggles.getMethod("handle", String::class.java) }.getOrNull()
                ?: toggles.getMethod("toggleFor", String::class.java)
            val owner = if (Modifier.isStatic(method.modifiers)) null else toggles.getField("INSTANCE").get(null)
            method to owner
        }.getOrNull()
    }

    private fun enabledField(toggle: Any): Field? {
        var type: Class<*>? = toggle.javaClass
        while (type != null) {
            val field = runCatching { type.getDeclaredField("enabled") }.getOrNull()
            if (field != null && field.type == Boolean::class.javaPrimitiveType) {
                field.isAccessible = true
                return field
            }
            type = type.superclass
        }
        return null
    }

    private fun <T> withModEnabled(modId: String, block: () -> T): T {
        val (method, owner) = toggleLookup ?: return block()
        val toggle = runCatching { method.invoke(owner, modId) }.getOrNull() ?: return block()
        val field = enabledField(toggle) ?: return block()
        val saved = field.getBoolean(toggle)
        if (saved) return block()
        field.setBoolean(toggle, true)
        try {
            return block()
        } finally {
            field.setBoolean(toggle, saved)
        }
    }

    private val outerCoverage = HashMap<Identifier, Boolean>()

    fun hasOuterLayer(skin: LivePlayerSkin): Boolean? {
        if (skinLayerOptions == null) return null
        outerCoverage[skin.texture]?.let { return it }
        val texture = Minecraft.getInstance().textureManager.get(skin.texture) ?: return null
        val image = runCatching {
            GlStateManager.bindTexture(texture.glId)
            val width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
            val height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
            if (width != SKIN_SIZE || height != SKIN_SIZE) return@runCatching null
            NativeImage(SKIN_SIZE, SKIN_SIZE, false).also { it.downloadTexture(0, false) }
        }.onFailure { warnOnce("skin-readback", it) }.getOrNull() ?: return null
        try {
            val hat = opaquePixels(image, HAT_REGION)
            val body = BODY_LAYER_REGIONS.sumOf { opaquePixels(image, it) }
            val layered = hat >= HAT_FACE_PIXELS * MIN_COVERAGE || body >= BODY_FACE_PIXELS * MIN_COVERAGE
            outerCoverage[skin.texture] = layered
            return layered
        } finally {
            image.close()
        }
    }

    private fun opaquePixels(image: NativeImage, region: IntArray): Int {
        var count = 0
        for (y in region[1] until region[3]) for (x in region[0] until region[2]) {
            if ((image.getPixelRGBA(x, y) ushr ALPHA_SHIFT) != 0) count++
        }
        return count
    }

    private const val ALPHA_SHIFT = 24
    private const val SKIN_SIZE = 64
    private val HAT_REGION = intArrayOf(32, 0, 64, 16)
    private val BODY_LAYER_REGIONS = listOf(
        intArrayOf(16, 32, 40, 48),
        intArrayOf(40, 32, 56, 48),
        intArrayOf(48, 48, 64, 64),
    )
    private const val HAT_FACE_PIXELS = 384
    private const val BODY_FACE_PIXELS = 752
    private const val MIN_COVERAGE = 0.15f

    private const val MOD_TOGGLES = "org.polyfrost.oneconfig.api.ui.v1.ModToggles"
    private const val WAVEY_CONFIG = "org.polyfrost.waveycapes.config.WaveyCapesConfig"
    private const val WAVEY_ID = "legacy-waveycapes"
    private const val MOVEMENT_VANILLA = "VANILLA"
    private const val MOVEMENT_WAVEY = "BASIC_SIMULATION_3D"
    private const val SKIN_LAYERS_CONFIG = "org.polyfrost.skinlayers.config.SkinLayersConfig"
    private const val SKIN_LAYERS_ID = "legacy-skin-layers"
    private val SKIN_LAYER_OPTIONS = listOf("enableHat", "enableJacket", "enableLeftSleeve", "enableRightSleeve", "enableLeftPants", "enableRightPants")
}

internal class LivePlayerActor(val tweak: LiveTweak, private val ours: Boolean) {
    @Volatile
    var place: LiveSpotPlace? = null

    var player: LivePlayerAvatar? = null
        private set
    var horse: LivePlayerHorse? = null
        private set
    var keyDown = false
        private set

    private var level: ClientLevel? = null
    private var runTicks = 0
    private var cyclePhase = 0
    private var placedAt: Vec3? = null
    private val cycle: Int = when (tweak) {
        LiveTweak.SPRINT -> SPRINT_CYCLE
        LiveTweak.CAPES -> CAPES_CYCLE
        LiveTweak.MOUNT -> MOUNT_CYCLE
        else -> 0
    }
    private var horseVelocity = 0.0
    private var fov = 1f
    private var fovO = 1f

    var keyDownState by mutableStateOf(false)
        private set
    var sprintingState by mutableStateOf(false)
        private set

    fun tick() {
        runCatching { step() }.onFailure { LivePlayerAvatars.warn("tick", it) }
    }

    private fun step() {
        val place = place ?: return
        val wanted = LivePlayerAvatars.lobbyLevel() ?: return
        if (wanted !== level || player == null) spawn(wanted, place)
        val p = player ?: return
        if (!samePlace(placedAt, place.feet)) restart(place)
        if (cycle > 0) {
            val next = cycleTick()
            val wrapped = next < cyclePhase
            cyclePhase = next
            if (wrapped) restart(place)
        }
        runTicks++
        drive(p)
        if (tweak != LiveTweak.SKIN_LAYERS && progress(place) > runLength(place)) p.forwardSpeed = 0f
        runCatching { tickPhysics(p, place) }.onFailure { LivePlayerAvatars.warn("physics", it) }
        if (p.y < place.feet.y - FALL_LIMIT) restart(place)
        fovO = fov
        fov += (runCatching { p.fovModifier }.getOrDefault(1f) - fov) * FOV_EASE
        keyDownState = keyDown
        sprintingState = p.isSprinting
    }

    private fun samePlace(a: Vec3?, b: Vec3): Boolean = a != null && a.x == b.x && a.y == b.y && a.z == b.z

    private fun cycleTick(): Int = ((LivePlayerScene.ticks - LivePlayerScene.groupStart(tweak) + cycleOffset()) % cycle).toInt()

    private fun cycleOffset(): Long = if (tweak == LiveTweak.SPRINT) SPRINT_OFFSET else 0L

    fun release() {
        player?.let { if (it.vehicle != null) it.startRiding(null) }
        player = null
        horse = null
        level = null
    }

    fun fadeAlpha(partial: Float): Float {
        if (cycle <= 0) return 0f
        val f = cyclePhase + partial
        return when {
            f >= cycle - FADE_IN -> ((f - (cycle - FADE_IN)) / FADE_IN).coerceIn(0f, 1f)
            f < FADE_OUT -> (1f - f / FADE_OUT).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    fun fovScale(partial: Float): Float = Mth.lerp(partial, fovO, fov)

    fun position(entity: LivingEntity, partial: Float): Vec3 = Vec3(
        Mth.lerp(partial.toDouble(), entity.prevX, entity.x),
        Mth.lerp(partial.toDouble(), entity.prevY, entity.y),
        Mth.lerp(partial.toDouble(), entity.prevZ, entity.z),
    )

    private fun spawn(target: ClientLevel, place: LiveSpotPlace) {
        release()
        level = target
        player = LivePlayerAvatars.player(target)
        if (tweak == LiveTweak.MOUNT) horse = LivePlayerAvatars.horse(target)
        cyclePhase = if (cycle > 0) cycleTick() else 0
        horse?.let { h ->
            runCatching {
                h.setType(0)
                h.setVariant(CHESTNUT_VARIANT)
                h.setTame(true)
                h.setSaddled(true)
            }.onFailure { LivePlayerAvatars.warn("saddle", it) }
        }
        restart(place)
    }

    private fun restart(place: LiveSpotPlace) {
        val p = player ?: return
        val start = if (tweak == LiveTweak.SKIN_LAYERS) place.feet else place.ahead(PATH_MARGIN)
        placedAt = place.feet
        runTicks = if (cycle > 0) cyclePhase else 0
        horseVelocity = 0.0
        p.isSprinting = false
        p.forwardSpeed = 0f
        p.sidewaysSpeed = 0f
        val h = horse
        if (h != null) {
            if (p.vehicle !== h) runCatching { p.startRiding(h) }.onFailure { LivePlayerAvatars.warn("riding", it) }
            place(h, start, place.yaw)
        }
        place(p, if (h != null) start.add(0.0, RIDER_OFFSET, 0.0) else start, place.yaw)
        p.xRot = if (h != null) RIDE_PITCH else 0f
        p.xRotO = p.xRot
    }

    private fun place(entity: LivingEntity, pos: Vec3, yaw: Float) {
        entity.setPositionAndAngles(pos.x, pos.y, pos.z, yaw, entity.xRot)
        entity.setOldPosAndRot()
        entity.velocityX = 0.0
        entity.velocityY = 0.0
        entity.velocityZ = 0.0
        entity.yBodyRot = yaw
        entity.yBodyRotO = yaw
        entity.yHeadRot = yaw
        entity.yHeadRotO = yaw
        entity.lastWalkAnimationSpeed = 0f
        entity.walkAnimationSpeed = 0f
        entity.walkAnimationProgress = 0f
    }

    private fun drive(p: LivePlayerAvatar) {
        val forward = tweak != LiveTweak.SKIN_LAYERS
        p.driving = forward && horse == null
        p.forwardSpeed = if (!forward) 0f else if (horse != null) RIDE_INPUT else FORWARD_INPUT
        p.sidewaysSpeed = 0f
        place?.let {
            p.yRot = it.yaw
            p.yHeadRot = it.yaw
        }
        keyDown = false
        if (tweak != LiveTweak.SPRINT) return
        val pressed = runTicks >= SPRINT_PRESS
        val release = if (ours) SPRINT_PRESS + TAP_TICKS else SPRINT_PRESS + HOLD_TICKS
        keyDown = pressed && runTicks < release
        p.wantSprint = if (ours) pressed else keyDown
        p.isSprinting = p.wantSprint
    }

    private fun tickPhysics(p: LivePlayerAvatar, place: LiveSpotPlace) {
        val h = horse
        if (h != null) {
            stepHorse(h, p, place)
            p.setOldPosAndRot()
            p.ticks++
            p.rideTick()
            return
        }
        p.setOldPosAndRot()
        p.ticks++
        if (tweak == LiveTweak.CAPES) LivePlayerMods.withWaveyCapes(true) { p.tick() } else p.tick()
    }

    private fun stepHorse(h: LivePlayerHorse, p: LivePlayerAvatar, place: LiveSpotPlace) {
        val rad = Math.toRadians(place.yaw.toDouble())
        h.setOldPosAndRot()
        h.ticks++
        horseVelocity = (horseVelocity + HORSE_SPEED * p.forwardSpeed / FORWARD_INPUT * FORWARD_INPUT) * GROUND_DRAG
        h.setPos(h.x - sin(rad) * horseVelocity, place.feet.y, h.z + cos(rad) * horseVelocity)
        h.yRot = place.yaw
        h.yBodyRot = place.yaw
        h.yHeadRot = place.yaw
        h.lastWalkAnimationSpeed = h.walkAnimationSpeed
        val stride = (horseVelocity * WALK_STRIDE).toFloat().coerceAtMost(1f)
        h.walkAnimationSpeed += (stride - h.walkAnimationSpeed) * WALK_EASE
        h.walkAnimationProgress += h.walkAnimationSpeed
    }

    private fun progress(place: LiveSpotPlace): Double {
        val p: LivingEntity = horse ?: player ?: return 0.0
        val rad = Math.toRadians(place.yaw.toDouble())
        val start = place.ahead(PATH_MARGIN)
        return (p.x - start.x) * -sin(rad) + (p.z - start.z) * cos(rad)
    }

    private fun runLength(place: LiveSpotPlace): Double = (place.length - PATH_MARGIN * 2).coerceAtLeast(1.0)

    private companion object {
        const val FADE_IN = 4
        const val FADE_OUT = 6
        const val SPRINT_CYCLE = 85
        const val CAPES_CYCLE = 100
        const val MOUNT_CYCLE = 180
        const val PATH_MARGIN = 3.0
        const val FALL_LIMIT = 4.0
        const val FORWARD_INPUT = 0.98f
        const val RIDE_INPUT = 0.26f
        const val RIDER_OFFSET = 0.875
        const val RIDE_PITCH = 0f
        const val SPRINT_PRESS = 20
        const val SPRINT_OFFSET = 45L
        const val TAP_TICKS = 4
        const val HOLD_TICKS = 40
        const val FOV_EASE = 0.5f
        const val HORSE_SPEED = 0.225
        const val GROUND_DRAG = 0.546
        const val WALK_STRIDE = 4.0
        const val WALK_EASE = 0.4f
        const val CHESTNUT_VARIANT = 2
    }
}

internal class LivePlayerStage(private val tweak: LiveTweak, private val ours: Boolean) : RenderBlocksContent {
    var horseOpacity = LiveOurs.HORSE_OPACITY
    val actor = LivePlayerActor(tweak, ours)

    private val scene = LiveScene(if (tweak == LiveTweak.SKIN_LAYERS) LiveSpot.STANDING else LiveSpot.RIDING_PATH)
    private val startNanos = System.nanoTime()

    override fun render(renderer: RenderBlocksRenderer, target: RenderTarget, width: Int, height: Int, aspect: Float, nowNanos: Long): Boolean {
        val place = scene.place()
        actor.place = place
        val partial = LivePlayerScene.partial()
        val player = actor.player
        val base = LivePlayerSkins.base()
        if (player == null) return scene.render(target, aspect, LiveCamera.orbit(place, 0f, IDLE_DISTANCE, IDLE_HEIGHT)) { }
        player.shownSkin = when (tweak) {
            LiveTweak.CAPES -> LivePlayerSkins.withCape(base)
            LiveTweak.SKIN_LAYERS -> LivePlayerSkins.layered(base)
            else -> base
        }
        val pos = actor.position(player, partial)
        val time = ((nowNanos - startNanos) / NANOS_PER_SECOND).toFloat()
        return when (tweak) {
            LiveTweak.CAPES -> renderCapes(target, aspect, player, pos, partial)
            LiveTweak.SKIN_LAYERS -> renderSkinLayers(target, aspect, place, player, pos, partial, time)
            LiveTweak.SPRINT -> renderSprint(target, aspect, place, player, pos, partial)
            LiveTweak.MOUNT -> renderMount(target, aspect, player, pos, partial)
            else -> false
        }
    }

    override fun release(): List<AutoCloseable> = listOf(scene)

    private fun renderCapes(target: RenderTarget, aspect: Float, player: LivePlayerAvatar, pos: Vec3, partial: Float): Boolean {
        val yaw = rotLerp(partial, player.yRotO, player.yRot)
        val camera = follow(pos, yaw + CAPE_ORBIT, CAPE_DISTANCE, CAPE_HEIGHT, CAPE_TARGET, LiveCamera.DEFAULT_FOV)
        return LivePlayerMods.withSkinLayers(false) {
            LivePlayerMods.withWaveyCapes(ours) {
                scene.render(target, aspect, camera) { frame -> draw(frame, player, pos, partial, camera.position) }
            }
        }
    }

    private fun renderSkinLayers(
        target: RenderTarget,
        aspect: Float,
        place: LiveSpotPlace,
        player: LivePlayerAvatar,
        pos: Vec3,
        partial: Float,
        time: Float,
    ): Boolean {
        val angle = SKIN_ORBIT + SKIN_SWAY * sin(time * TAU / SKIN_PERIOD)
        val camera = LiveCamera.orbit(place, angle, SKIN_DISTANCE, SKIN_HEIGHT, SKIN_TARGET, SKIN_FOV)
        return LivePlayerMods.withSkinLayers(ours) {
            scene.render(target, aspect, camera) { frame -> draw(frame, player, pos, partial, camera.position) }
        }
    }

    private fun renderSprint(target: RenderTarget, aspect: Float, place: LiveSpotPlace, player: LivePlayerAvatar, pos: Vec3, partial: Float): Boolean {
        val camera = follow(pos, place.yaw + SIDE_ANGLE, SPRINT_DISTANCE, SPRINT_HEIGHT, SPRINT_TARGET, LiveCamera.DEFAULT_FOV * actor.fovScale(partial))
        return LivePlayerMods.withSkinLayers(false) {
            scene.render(target, aspect, camera) { frame -> draw(frame, player, pos, partial, camera.position) }
        }
    }

    private fun renderMount(target: RenderTarget, aspect: Float, player: LivePlayerAvatar, pos: Vec3, partial: Float): Boolean {
        val eye = pos.add(0.0, player.eyeHeight.toDouble(), 0.0)
        val camera = LiveCamera(eye, rotLerp(partial, player.yRotO, player.yRot), Mth.lerp(partial, player.xRotO, player.xRot))
        val horse = actor.horse ?: return scene.render(target, aspect, camera) { }
        val horsePos = actor.position(horse, partial)
        return scene.render(target, aspect, camera) { frame -> draw(frame, horse, horsePos, partial, camera.position) }
    }

    private fun follow(pos: Vec3, angle: Float, distance: Double, height: Double, targetHeight: Double, fov: Float): LiveCamera {
        val rad = Math.toRadians(angle.toDouble())
        val eye = pos.add(-sin(rad) * distance, height, cos(rad) * distance)
        return LiveCamera.lookingAt(eye, pos.add(0.0, targetHeight, 0.0), fov)
    }

    private fun rotLerp(partial: Float, from: Float, to: Float): Float = from + Mth.wrapDegrees(to - from) * partial

    private fun draw(frame: LiveSceneFrame, entity: LivingEntity, pos: Vec3, partial: Float, eye: Vec3) {
        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        val packed = entity.getLightLevel(partial)
        val block = maxOf((packed shr BLOCK_SHIFT) and LIGHT_MASK, MIN_LIGHT)
        val sky = maxOf((packed shr SKY_SHIFT) and LIGHT_MASK, MIN_LIGHT)
        val light = (block shl BLOCK_SHIFT) or (sky shl SKY_SHIFT)
        val rel = frame.relative(pos)
        val yaw = rotLerp(partial, entity.yRotO, entity.yRot)
        val camera = dispatcher.camera
        val world = dispatcher.world
        val cameraX = dispatcher.cameraX
        val cameraY = dispatcher.cameraY
        val cameraZ = dispatcher.cameraZ
        val localPlayer = mc.player
        val arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)
        val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS)
        try {
            dispatcher.camera = entity
            dispatcher.world = entity.world
            dispatcher.cameraX = eye.x
            dispatcher.cameraY = eye.y
            dispatcher.cameraZ = eye.z
            if (localPlayer == null) mc.player = LivePlayerAvatars.nameGuard()
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, (light % LIGHTMAP_SPLIT).toFloat(), (light / LIGHTMAP_SPLIT).toFloat())
            GlStateManager.color4f(1f, 1f, 1f, 1f)
            Lighting.turnOn()
            mc.gameRenderer.enableLightMap()
            runCatching { dispatcher.render(entity, rel.x, rel.y, rel.z, yaw, partial) }
                .onFailure { LivePlayerAvatars.warn("draw", it) }
        } finally {
            mc.gameRenderer.disableLightMap()
            Lighting.turnOff()
            GL11.glPopClientAttrib()
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer)
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer)
            GL20.glUseProgram(program)
            GlStateManager.activeTexture(GLX.GL_TEXTURE0)
            GlStateManager.color4f(1f, 1f, 1f, 1f)
            mc.player = localPlayer
            dispatcher.camera = camera
            dispatcher.world = world
            dispatcher.cameraX = cameraX
            dispatcher.cameraY = cameraY
            dispatcher.cameraZ = cameraZ
        }
    }

    private companion object {
        const val TAU = (PI * 2.0).toFloat()
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MIN_LIGHT = 14
        const val BLOCK_SHIFT = 4
        const val SKY_SHIFT = 20
        const val LIGHT_MASK = 0xF
        const val LIGHTMAP_SPLIT = 65536
        const val IDLE_DISTANCE = 3.5
        const val IDLE_HEIGHT = 1.4
        const val CAPE_ORBIT = 150f
        const val CAPE_DISTANCE = 3.4
        const val CAPE_HEIGHT = 1.7
        const val CAPE_TARGET = 1.1
        const val SKIN_ORBIT = -60f
        const val SKIN_SWAY = 18f
        const val SKIN_PERIOD = 12f
        const val SKIN_DISTANCE = 2.0
        const val SKIN_HEIGHT = 1.6
        const val SKIN_TARGET = 1.4
        const val SKIN_FOV = 50f
        const val SIDE_ANGLE = 90f
        const val SPRINT_DISTANCE = 4.2
        const val SPRINT_HEIGHT = 1.3
        const val SPRINT_TARGET = 0.95
    }
}
*///?}
