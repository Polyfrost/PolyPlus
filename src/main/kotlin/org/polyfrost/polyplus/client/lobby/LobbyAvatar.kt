package org.polyfrost.polyplus.client.lobby

import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.util.Mth
//? if > 1.8.9 {
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.PlayerModelPart
//?} else {
/*import net.minecraft.client.render.model.PlayerModelPart
import net.minecraft.resource.Identifier
*///?}
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?} else {
/*import org.polyfrost.oneconfig.internal.legacy.PlayerSkin
*///?}
import net.minecraft.world.phys.Vec3
//? if = 1.8.9
/*import net.minecraft.entity.Entity*/
import org.polyfrost.polyplus.client.cosmetics.CapeResolver
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache*/
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.cosmetics.MojangCapeService*/
import org.polyfrost.polyplus.client.cosmetics.LocalSkinOverride
import kotlin.math.hypot

class LobbyAvatar(
    level: ClientLevel,
    profile: GameProfile,
    //? if = 1.8.9
    /*@get:JvmName("lobbyDisplayName")*/
    val displayName: String,
    //? if = 1.8.9
    /*@get:JvmName("lobbyLocal")*/
    val isLocal: Boolean,
    rest: LobbySlot,
    private val skinSource: () -> PlayerSkin,
) : RemotePlayer(level, profile) {
    enum class Arrival { WAITING, ARRIVING, LANDED }

    var rest = rest
        private set
    var arrival = Arrival.WAITING
        private set
    var landedTick = -1L
        private set

    var whereabouts: String? = null
        private set
    var projection = 0.0
        private set
    var dismissed = false
        private set

    private var hidden = false
    private var phantom = false

    val spectral: Boolean get() = whereabouts != null
    val projecting: Boolean get() = !hidden && projection > 0.0
    val vanished: Boolean get() = dismissed && projection <= 0.0

    private var motion: LobbyMotion? = null
    private var landedThisTick = false

    val gaze by lazy { LobbyGaze(uuid.hashCode()) }

    //? if = 1.8.9 {
    /*init {
        stepHeight = LEGACY_STEP
        noClip = false
    }
    *///?}

    //? if > 1.8.9 {
    override fun getSkin(): PlayerSkin {
        val skin = CapeResolver.applyCape(uuid, gameProfile, skinSource())
        if (!isLocal) return skin
        val chosen = LobbyWardrobe.preview?.skin?.let { LocalSkinOverride.withSkin(skin, it) } ?: LocalSkinOverride.apply(uuid, skin)
        return LobbyWardrobe.withPreviewCape(chosen)
    }
    //?} else {
    /*override fun getLightLevel(tickDelta: Float): Int = LobbyScene.lightFor(this, super.getLightLevel(tickDelta))

    private val account by lazy { LobbyAccountSkins.of(uuid, gameProfile.name, isLocal) }

    private fun chosenSkin() = (LobbyWardrobe.preview?.skin ?: LocalSkinOverride.forPlayer(uuid)).takeIf { isLocal }

    override fun getSkinTextureLocation(): Identifier = chosenSkin()?.texture ?: account.texture ?: skinSource().texture()

    override fun getModelType(): String {
        val slim = chosenSkin()?.slim ?: account.slim ?: return super.getModelType()
        return if (slim) "slim" else "default"
    }

    override fun hasSkinTexture(): Boolean = true

    override fun hasInfo(): Boolean = true

    override fun getCapeTextureLocation(): Identifier? {
        if (isLocal) LobbyWardrobe.previewCape()?.let { return it }
        val game = super.getCapeTextureLocation()
        if (game != null && CapeResolver.keepsModCape(game.namespace)) return game
        CosmeticAssetCache.getCapeTexture(uuid)?.let { return it }
        if (isLocal && MojangCapeService.overridesLocalCape(uuid)) {
            MojangCapeService.localCapeTexture()?.let { return it }
            if (MojangCapeService.localCapeHidden()) return null
        }
        return account.cape ?: game
    }

    var scale = 1f
        private set
    *///?}

    override fun shouldRender(x: Double, y: Double, z: Double): Boolean =
        !hidden && !phantom && projection < SOLID_SWAP && arrival != Arrival.WAITING && super.shouldRender(x, y, z)

    //? if > 1.8.9 {
    override fun isModelPartShown(part: PlayerModelPart): Boolean = true

    override fun distanceToSqr(pos: Vec3): Double = 0.0
    //?} else {
    /*override fun isModelPartVisible(part: PlayerModelPart): Boolean = true

    override fun squaredDistanceTo(entity: Entity): Double = 0.0
    *///?}

    //? if > 1.8.9 {
    override fun tick() {
        val body = yBodyRot
        val head = yHeadRot
        val waiting = arrival == Arrival.WAITING
        val moving = stepArrival()
        super.tick()
        if (moving) {
            yBodyRot = yRot
            yHeadRot = yRot
            if (!waiting) {
                yBodyRotO = yBodyRot + Mth.wrapDegrees(body - yBodyRot)
                yHeadRotO = yHeadRot + Mth.wrapDegrees(head - yHeadRot)
            }
        }
    }
    //?} else {
    /*private var moving = false

    override fun mobTick() {
        moving = stepArrival()
        super.mobTick()
    }

    override fun tick() {
        super.tick()
        if (moving) {
            yBodyRot = yRot
            yHeadRot = yRot
        }
    }
    *///?}

    private fun stepArrival(): Boolean {
        val current = motion ?: return false
        if (arrival == Arrival.LANDED) {
            current.settle(this)
            return false
        }
        val elapsed = LobbyScene.tick() - current.startTick
        if (elapsed < 0L) return false
        if (arrival == Arrival.WAITING) {
            arrival = Arrival.ARRIVING
            current.begin(this)
            setOldPosAndRot()
        }
        if (current.advance(this, elapsed)) {
            arrival = Arrival.LANDED
            landedThisTick = true
            if (landedTick < 0L) landedTick = LobbyScene.tick()
        }
        return true
    }

    fun consumeLanded(): Boolean = landedThisTick.also { landedThisTick = false }

    fun stride(step: Vec3?) {
        isSprinting = step != null && hypot(step.x, step.z) > SPRINT_STEP
        //? if > 1.8.9 {
        deltaMovement = step ?: Vec3.ZERO
        //?} else {
        /*velocityX = step?.x ?: 0.0
        velocityZ = step?.z ?: 0.0
        *///?}
    }

    //? if > 1.8.9 {
    val climb: Double get() = maxUpStep().toDouble()

    val fallRate: Double get() = gravity

    val bumped: Boolean get() = horizontalCollision

    val bumpedVertically: Boolean get() = verticalCollision

    fun walk(delta: Vec3) = move(MoverType.SELF, delta)

    fun leap(): Double {
        jumpFromGround()
        return deltaMovement.y
    }
    //?} else {
    /*val climb: Double get() = stepHeight.toDouble()

    val fallRate: Double get() = LEGACY_GRAVITY

    val bumped: Boolean get() = collidingHorizontally

    val bumpedVertically: Boolean get() = collidingVertically

    fun walk(delta: Vec3) = move(delta.x, delta.y, delta.z)

    fun leap(): Double {
        jump()
        return velocityY
    }
    *///?}

    fun arriveNow() {
        motion = null
        landedThisTick = false
        stride(null)
        //? if >= 1.21.4 {
        walkAnimation.stop()
        //?} elif > 1.8.9 {
        /*walkAnimation.setSpeed(0f)
        *///?} else {
        /*lastWalkAnimationSpeed = 0f
        walkAnimationSpeed = 0f
        walkAnimationProgress = 0f
        *///?}
        if (arrival == Arrival.LANDED) return
        arrival = Arrival.LANDED
        if (landedTick < 0L) landedTick = LobbyScene.tick()
        place(rest.pos, rest.yaw)
        face(rest.yaw, 0f)
        setOldPosAndRot()
    }

    fun begin(next: LobbyMotion) {
        motion = next
    }

    fun moveTo(slot: LobbySlot) {
        rest = slot
    }

    fun travel(next: LobbyMotion) {
        motion = next
        landedThisTick = false
        arrival = Arrival.ARRIVING
        next.begin(this)
    }

    fun project(place: String?): Boolean {
        val changed = (place != null) != spectral
        whereabouts = place
        dismissed = false
        if (place == null) phantom = false
        return changed
    }

    fun conjure(place: String) {
        whereabouts = place
        phantom = true
        arriveNow()
    }

    fun dismiss() {
        dismissed = true
        phantom = true
    }

    fun settleProjection(elapsed: Double) {
        if (arrival != Arrival.LANDED) return
        val target = if (spectral && !dismissed) 1.0 else 0.0
        val step = elapsed / PROJECTION_SECONDS
        projection = if (projection < target) minOf(target, projection + step) else maxOf(target, projection - step)
    }

    fun fade(amount: Double) {
        hidden = amount >= 1.0
        //? if > 1.8.9 {
        getAttribute(Attributes.SCALE)?.baseValue = (1.0 - amount * amount).coerceAtLeast(MIN_SCALE)
        //?} else {
        /*scale = (1.0 - amount * amount).coerceAtLeast(MIN_SCALE).toFloat()
        *///?}
    }

    fun face(yaw: Float, headTurn: Float) {
        yRot = yaw
        yRotO = yaw
        yBodyRot = yaw
        yBodyRotO = yaw
        yHeadRot = yaw + headTurn
        yHeadRotO = yaw + headTurn
    }

    fun place(pos: Vec3, yaw: Float) {
        setPos(pos)
        yRot = yaw
        yBodyRot = yaw
        yHeadRot = yaw
    }

    companion object {
        private const val MIN_SCALE = 0.07
        private const val SOLID_SWAP = 0.5
        private const val PROJECTION_SECONDS = 1.5
        private const val SPRINT_STEP = 0.12
        //? if = 1.8.9
        /*private const val LEGACY_GRAVITY = 0.08*/
        //? if = 1.8.9
        /*private const val LEGACY_STEP = 0.13f*/
    }
}
