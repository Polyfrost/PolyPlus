package org.polyfrost.polyplus.client.bedrock.molang

import org.joml.Vector3f
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import kotlin.math.PI

class MolangContext(
    val animTimeSeconds: Float,
    val lifeTimeSeconds: Float = 0f,
    val deltaTimeSeconds: Float = 1f / BedrockConstants.TICKS_PER_SECOND,
    internal val variables: MutableMap<String, Float> = mutableMapOf(),
    private val renderContext: PlayerRenderContext? = null,
) {

    fun query(query: BedrockQuery): Double {
        val context = renderContext
        val value = when (query) {
            BedrockQuery.ANIM_TIME -> animTimeSeconds
            BedrockQuery.LIFE_TIME -> lifeTimeSeconds
            BedrockQuery.DELTA_TIME -> deltaTimeSeconds
            BedrockQuery.TIME_OF_DAY -> if (context != null) (context.ageInTicks / BedrockConstants.TICKS_PER_SECOND) % 24000f else 0f
            BedrockQuery.IS_MOVING -> if (context != null) if (context.walkAnimationSpeed > 0.01f) 1f else 0f else 0f
            BedrockQuery.GROUND_SPEED -> context?.walkAnimationSpeed ?: 0f
            BedrockQuery.VERTICAL_SPEED -> 0f
            BedrockQuery.IS_ON_GROUND -> if (context != null) if (context.isOnGround) 1f else 0f else 1f
            BedrockQuery.IS_IN_WATER -> if (context != null) if (context.isInWater || context.swimAmount > 0.01f) 1f else 0f else 0f
            BedrockQuery.IS_JUMPING -> 0f
            BedrockQuery.IS_SNEAKING -> if (context != null) if (context.isCrouching) 1f else 0f else 0f
            BedrockQuery.IS_SPRINTING -> if (context != null) if (context.walkAnimationSpeed > 0.12f) 1f else 0f else 0f
            BedrockQuery.YAW_SPEED -> 0f
        }
        return value.toDouble()
    }

    companion object {
        fun forAnimation(
            animTimeSeconds: Float,
            lifeTimeSeconds: Float = 0f,
            renderContext: PlayerRenderContext? = null,
            variables: MutableMap<String, Float> = mutableMapOf(),
        ): MolangContext {
            return MolangContext(
                animTimeSeconds = animTimeSeconds,
                lifeTimeSeconds = lifeTimeSeconds,
                renderContext = renderContext,
                variables = variables,
            )
        }
    }
}

class MolangVector3(
    val x: MolangScalar,
    val y: MolangScalar,
    val z: MolangScalar,
    val isRotation: Boolean = false,
) {
    fun eval(context: MolangContext): Vector3f {
        val scale = if (isRotation) (PI / 180.0).toFloat() else 1f
        return Vector3f(
            (x.evaluate(context) * scale).toFloat(),
            (y.evaluate(context) * scale).toFloat(),
            (z.evaluate(context) * scale).toFloat(),
        )
    }

    companion object {
        fun constant(x: Float, y: Float, z: Float, isRotation: Boolean = false): MolangVector3 {
            return MolangVector3(
                x = MolangScalar.Constant(x.toDouble()),
                y = MolangScalar.Constant(y.toDouble()),
                z = MolangScalar.Constant(z.toDouble()),
                isRotation = isRotation,
            )
        }
    }
}
