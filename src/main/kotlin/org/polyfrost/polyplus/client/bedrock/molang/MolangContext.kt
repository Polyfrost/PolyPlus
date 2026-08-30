package org.polyfrost.polyplus.client.bedrock.molang

import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.joml.Vector3f
import kotlin.math.PI

class MolangContext(
    val animTimeSeconds: Float,
    val lifeTimeSeconds: Float = 0f,
    val deltaTimeSeconds: Float = 1f / BedrockConstants.TICKS_PER_SECOND,
    private val variables: MutableMap<String, Float> = mutableMapOf(),
    private val renderContext: PlayerRenderContext? = null,
) {

    fun setVariable(name: String, value: Float) {
        variables[name] = value
    }

    fun getVariable(name: String): Float = variables[name] ?: 0f

    fun query(name: String): Float {
        val context = renderContext
        return when (name) {
            "anim_time" -> animTimeSeconds
            "life_time" -> lifeTimeSeconds
            "delta_time" -> deltaTimeSeconds
            "time_of_day" -> if (context != null) (context.ageInTicks / BedrockConstants.TICKS_PER_SECOND) % 24000f else 0f
            "is_moving" -> if (context != null) if (context.walkAnimationSpeed > 0.01f) 1f else 0f else 0f
            "ground_speed" -> context?.walkAnimationSpeed ?: 0f
            "vertical_speed" -> 0f
            "is_on_ground" -> if (context != null) if (context.isOnGround) 1f else 0f else 1f
            "is_in_water" -> if (context != null) if (context.isInWater || context.swimAmount > 0.01f) 1f else 0f else 0f
            "is_jumping" -> 0f
            "is_sneaking" -> if (context != null) if (context.isCrouching) 1f else 0f else 0f
            "is_sprinting" -> if (context != null) if (context.walkAnimationSpeed > 0.12f) 1f else 0f else 0f
            "yaw_speed" -> 0f
            else -> 0f
        }
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

data class MolangVector3(
    val x: MolangExpr,
    val y: MolangExpr,
    val z: MolangExpr,
    val isRotation: Boolean = false,
) {
    fun eval(context: MolangContext): Vector3f {
        val scale = if (isRotation) (PI / 180.0).toFloat() else 1f
        return Vector3f(
            (MolangEvaluator.eval(x, context) * scale).toFloat(),
            (MolangEvaluator.eval(y, context) * scale).toFloat(),
            (MolangEvaluator.eval(z, context) * scale).toFloat(),
        )
    }

    companion object {
        fun constant(x: Float, y: Float, z: Float, isRotation: Boolean = false): MolangVector3 {
            return MolangVector3(
                x = MolangExpr.Number(x.toDouble()),
                y = MolangExpr.Number(y.toDouble()),
                z = MolangExpr.Number(z.toDouble()),
                isRotation = isRotation,
            )
        }
    }
}
