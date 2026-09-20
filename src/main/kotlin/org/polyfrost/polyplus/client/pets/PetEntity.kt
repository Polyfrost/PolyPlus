package org.polyfrost.polyplus.client.pets

//? if > 1.8.9 {
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.client.bedrock.controller.BedrockAnimationControllerRunner
import org.polyfrost.polyplus.client.cosmetics.PetArchetype
import org.polyfrost.polyplus.client.cosmetics.PetDefinition
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PetEntity(
    entityType: EntityType<out PetEntity>,
    level: Level,
) : PathfinderMob(entityType, level) {

    var definition: PetDefinition? = null
        private set

    var ownerUuid: UUID? = null
        private set

    var animationState: String = "idle"
        private set(value) {
            if (field != value) {
                previousAnimationState = field
                animationBlendTicks = 0
            }
            field = value
        }

    var previousAnimationState: String = "idle"
        private set

    var animationBlendTicks: Int = ANIMATION_BLEND_TICKS
        private set

    var controllerRunner: BedrockAnimationControllerRunner? = null
        private set

    private val hoverPhase = Random.nextDouble() * Math.PI * 2

    fun initialize(definition: PetDefinition, ownerUuid: UUID) {
        this.definition = definition
        this.ownerUuid = ownerUuid
        this.controllerRunner = definition.controller?.let { controller ->
            BedrockAnimationControllerRunner(controller, definition.animations)
        }

        if (definition.archetype == PetArchetype.Walking) {
            setNoGravity(false)
            noPhysics = false
        } else {
            setNoGravity(true)
            noPhysics = true
        }
    }

    //? if >= 1.21.5 {
    override fun causeFallDamage(fallDistance: Double, multiplier: Float, source: DamageSource): Boolean = false
    //?} else {
    /*override fun causeFallDamage(fallDistance: Float, multiplier: Float, source: DamageSource): Boolean = false
    *///?}

    override fun tick() {
        super.tick()
        if (level().isClientSide) {
            if (animationBlendTicks < ANIMATION_BLEND_TICKS) {
                animationBlendTicks++
            }
            follow()
        }
    }

    private fun follow() {
        val def = definition ?: return
        val owner = ownerEntity() ?: return
        val ownerPos = owner.position()
        val leashRadius = def.leashRadius.toDouble()

        val desired = desiredPosition(ownerPos, def, owner.yRot)

        val distanceFromOwner = position().distanceTo(ownerPos)
        if (distanceFromOwner > leashRadius * 6) {
            //? if >= 1.21.5 {
            snapTo(desired.x, desired.y, desired.z, owner.yRot, owner.xRot)
            //?} else {
            /*moveTo(desired.x, desired.y, desired.z, owner.yRot, owner.xRot)
            *///?}
            animationState = "idle"
            return
        }

        val toDesired = desired.subtract(position())
        val distance = if (def.archetype == PetArchetype.Walking) {
            Vec3(toDesired.x, 0.0, toDesired.z).length()
        } else {
            toDesired.length()
        }

        if (def.archetype == PetArchetype.Walking) {
            if (distance > 0.1) {
                val moveYaw = Math.toDegrees(atan2(-toDesired.x, toDesired.z)).toFloat()
                yRot = Mth.rotLerp(FOLLOW_EASE.toFloat(), yRot, moveYaw)
            } else {
                yRot = Mth.rotLerp(FOLLOW_EASE.toFloat(), yRot, owner.yRot)
            }
            xRot = 0f
        } else {
            yRot = Mth.rotLerp(FOLLOW_EASE.toFloat(), yRot, owner.yRot)
            xRot = Mth.rotLerp(FOLLOW_EASE.toFloat(), xRot, owner.xRot)
        }

        if (distance < 0.02) {
            animationState = "idle"
            return
        }

        val step = toDesired.scale(FOLLOW_EASE)
        if (def.archetype == PetArchetype.Walking) {
            move(MoverType.SELF, Vec3(step.x, 0.0, step.z))
        } else {
            val newPos = position().add(step)
            setPos(newPos.x, newPos.y, newPos.z)
        }
        animationState = if (distance > 0.3) "move" else "idle"
    }

    private fun leftVector(ownerYRot: Float): Vec3 {
        val yawRad = Math.toRadians(ownerYRot.toDouble())
        return Vec3(cos(yawRad), 0.0, sin(yawRad))
    }

    private fun desiredPosition(ownerPos: Vec3, def: PetDefinition, ownerYRot: Float): Vec3 {
        val left = leftVector(ownerYRot)
        return if (def.archetype == PetArchetype.Flying) {
            val bob = sin((tickCount + hoverPhase) / 12.0) * 0.15
            ownerPos.add(left.x * 1.3, 1.4 + bob, left.z * 1.3)
        } else {
            val yawRad = Math.toRadians(ownerYRot.toDouble())
            ownerPos.add(sin(yawRad) * 1.5 + left.x * 0.6, 0.0, -cos(yawRad) * 1.5 + left.z * 0.6)
        }
    }

    fun ownerEntity(): Player? {
        val uuid = ownerUuid ?: return null
        return level().players().firstOrNull { it.uuid == uuid }
    }

    //? if >= 1.21.5 {
    override fun isLocalClientAuthoritative(): Boolean = true
    //?} else {
    /*override fun isEffectiveAi(): Boolean = true
    *///?}

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult = InteractionResult.PASS

    override fun isInvisible(): Boolean = super.isInvisible() || ownerEntity()?.isInvisible == true

    override fun isPickable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun isAttackable(): Boolean = false

    override fun canBeLeashed(): Boolean = false

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    override fun isPersistenceRequired(): Boolean = true

    companion object {
        private const val FOLLOW_EASE = 0.15

        const val ANIMATION_BLEND_TICKS = 6

        fun createAttributes(): AttributeSupplier.Builder =
            Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 16.0)
    }
}
//?} else {
/*import net.minecraft.entity.living.LivingEntity
import net.minecraft.entity.living.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import org.polyfrost.polyplus.client.bedrock.controller.BedrockAnimationControllerRunner
import org.polyfrost.polyplus.client.cosmetics.PetArchetype
import org.polyfrost.polyplus.client.cosmetics.PetDefinition
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class PetEntity(world: World) : LivingEntity(world) {

    var definition: PetDefinition? = null
        private set

    var ownerUuid: UUID? = null
        private set

    var animationState: String = "idle"
        private set(value) {
            if (field != value) {
                previousAnimationState = field
                animationBlendTicks = 0
            }
            field = value
        }

    var previousAnimationState: String = "idle"
        private set

    var animationBlendTicks: Int = ANIMATION_BLEND_TICKS
        private set

    var controllerRunner: BedrockAnimationControllerRunner? = null
        private set

    val tickCount: Int get() = ticks

    val id: Int get() = networkId

    private val hoverPhase = Random.nextDouble() * Math.PI * 2

    init {
        setSize(0.8f, 0.8f)
    }

    fun initialize(definition: PetDefinition, ownerUuid: UUID) {
        this.definition = definition
        this.ownerUuid = ownerUuid
        this.controllerRunner = definition.controller?.let { controller ->
            BedrockAnimationControllerRunner(controller, definition.animations)
        }
        noClip = definition.archetype != PetArchetype.Walking
    }

    override fun takeFallDamage(distance: Float, damageMultiplier: Float) = Unit

    override fun tick() {
        super.tick()
        if (world.isClient) {
            if (animationBlendTicks < ANIMATION_BLEND_TICKS) {
                animationBlendTicks++
            }
            follow()
        }
    }

    override fun moveRelative(sideways: Float, forwards: Float) {
        if (definition?.archetype == PetArchetype.Walking) super.moveRelative(sideways, forwards)
    }

    private fun follow() {
        val def = definition ?: return
        val owner = ownerEntity() ?: return
        val leashRadius = def.leashRadius.toDouble()

        val desired = desiredPosition(owner, def)

        if (distanceTo(owner.x, owner.y, owner.z) > leashRadius * 6) {
            setPositionAndAngles(desired[0], desired[1], desired[2], owner.yaw, owner.pitch)
            lastYaw = yaw
            lastPitch = pitch
            animationState = "idle"
            return
        }

        val toX = desired[0] - x
        val toY = desired[1] - y
        val toZ = desired[2] - z
        val walking = def.archetype == PetArchetype.Walking
        val distance = if (walking) sqrt(toX * toX + toZ * toZ) else sqrt(toX * toX + toY * toY + toZ * toZ)

        if (walking) {
            if (distance > 0.1) {
                val moveYaw = Math.toDegrees(atan2(-toX, toZ)).toFloat()
                yaw = rotLerp(yaw, moveYaw)
            } else {
                yaw = rotLerp(yaw, owner.yaw)
            }
            pitch = 0f
        } else {
            yaw = rotLerp(yaw, owner.yaw)
            pitch = rotLerp(pitch, owner.pitch)
        }

        if (distance < 0.02) {
            animationState = "idle"
            return
        }

        if (walking) {
            move(toX * FOLLOW_EASE, 0.0, toZ * FOLLOW_EASE)
        } else {
            setPosition(x + toX * FOLLOW_EASE, y + toY * FOLLOW_EASE, z + toZ * FOLLOW_EASE)
        }
        animationState = if (distance > 0.3) "move" else "idle"
    }

    private fun rotLerp(start: Float, end: Float): Float =
        start + FOLLOW_EASE.toFloat() * MathHelper.wrapDegrees(end - start)

    private fun desiredPosition(owner: PlayerEntity, def: PetDefinition): DoubleArray {
        val yawRad = Math.toRadians(owner.yaw.toDouble())
        val leftX = cos(yawRad)
        val leftZ = sin(yawRad)
        return if (def.archetype == PetArchetype.Flying) {
            val bob = sin((ticks + hoverPhase) / 12.0) * 0.15
            doubleArrayOf(owner.x + leftX * 1.3, owner.y + 1.4 + bob, owner.z + leftZ * 1.3)
        } else {
            doubleArrayOf(owner.x + sin(yawRad) * 1.5 + leftX * 0.6, owner.y, owner.z - cos(yawRad) * 1.5 + leftZ * 0.6)
        }
    }

    fun ownerEntity(): PlayerEntity? {
        val uuid = ownerUuid ?: return null
        return world.getPlayer(uuid)
    }

    override fun isLocallyControlled(): Boolean = true

    override fun isInvisible(): Boolean = super.isInvisible() || ownerEntity()?.isInvisible == true

    override fun hasCollision(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun isSilent(): Boolean = true

    override fun makesSteps(): Boolean = false

    override fun getDisplayItemInHand(): ItemStack? = null

    override fun getEquipment(slot: Int): ItemStack? = null

    override fun getArmor(slot: Int): ItemStack? = null

    override fun setEquipment(slot: Int, item: ItemStack?) = Unit

    override fun getEquipment(): Array<ItemStack?> = NO_EQUIPMENT

    companion object {
        private const val FOLLOW_EASE = 0.15

        const val ANIMATION_BLEND_TICKS = 6

        private val NO_EQUIPMENT = arrayOfNulls<ItemStack>(5)
    }
}
*///?}
