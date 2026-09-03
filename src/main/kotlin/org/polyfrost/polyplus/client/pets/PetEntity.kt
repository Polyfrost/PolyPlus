package org.polyfrost.polyplus.client.pets

import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.client.bedrock.controller.BedrockAnimationControllerRunner
import org.polyfrost.polyplus.client.cosmetics.PetArchetype
import org.polyfrost.polyplus.client.cosmetics.PetDefinition
import java.util.UUID
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
    override fun causeFallDamage(fallDistance: Double, multiplier: Float, source: net.minecraft.world.damagesource.DamageSource): Boolean = false
    //?} else {
    /*override fun causeFallDamage(fallDistance: Float, multiplier: Float, source: net.minecraft.world.damagesource.DamageSource): Boolean = false
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
                val moveYaw = Math.toDegrees(kotlin.math.atan2(-toDesired.x, toDesired.z)).toFloat()
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
