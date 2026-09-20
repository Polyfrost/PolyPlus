package org.polyfrost.polyplus.client.bedrock.playback

import net.minecraft.util.Mth
import org.joml.Vector3f
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.animation.BoneAnimation
import org.polyfrost.polyplus.client.bedrock.animation.EasingMode
import org.polyfrost.polyplus.client.bedrock.animation.MolangVectorInterpolator
import org.polyfrost.polyplus.client.bedrock.molang.MolangContext
import org.polyfrost.polyplus.client.bedrock.molang.MolangEvaluator
import org.polyfrost.polyplus.client.bedrock.molang.MolangStatement
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import kotlin.math.pow

object AnimationSampler {
    private val DEFAULT_POSITION = Vector3f()
    private val DEFAULT_ROTATION = Vector3f()
    private val DEFAULT_SCALE = Vector3f(1f, 1f, 1f)

    private val vectorInterpolator = MolangVectorInterpolator { previous, from, to, next, alpha, easing ->
        interpolateVector(previous, from, to, next, alpha, easing)
    }

    fun sample(
        animation: BedrockAnimation,
        timeTicks: Float,
        renderContext: PlayerRenderContext? = null,
        molangVariables: MutableMap<String, Float> = mutableMapOf(),
    ): Map<String, BoneTransform> {
        val context = buildContext(animation, timeTicks, renderContext, molangVariables)
        val result = LinkedHashMap<String, BoneTransform>()

        for ((boneName, boneAnimation) in animation.boneAnimations) {
            result[boneName] = sampleBone(boneAnimation, timeTicks, context)
        }

        return result
    }

    fun runInitialize(
        animation: BedrockAnimation,
        molangVariables: MutableMap<String, Float>,
    ) {
        val context = MolangContext.forAnimation(
            animTimeSeconds = 0f,
            variables = molangVariables,
        )
        executeStatements(animation.initialize, context)
    }

    private fun buildContext(
        animation: BedrockAnimation,
        timeTicks: Float,
        renderContext: PlayerRenderContext?,
        molangVariables: MutableMap<String, Float>,
    ): MolangContext {
        val animTimeSeconds = timeTicks / BedrockConstants.TICKS_PER_SECOND
        val lifeTimeSeconds = renderContext?.ageInTicks?.div(BedrockConstants.TICKS_PER_SECOND) ?: 0f

        val context = MolangContext.forAnimation(
            animTimeSeconds = animTimeSeconds,
            lifeTimeSeconds = lifeTimeSeconds,
            renderContext = renderContext,
            variables = molangVariables,
        )

        executeStatements(animation.preAnimation, context)
        return context
    }

    private fun executeStatements(statements: List<MolangStatement>, context: MolangContext) {
        if (statements.isNotEmpty())
            MolangEvaluator.execute(statements, context)
    }

    private fun sampleBone(
        bone: BoneAnimation,
        timeTicks: Float,
        context: MolangContext,
    ): BoneTransform = BoneTransform(
        position = bone.position.sample(timeTicks, context, DEFAULT_POSITION, vectorInterpolator),
        rotation = bone.rotation.sample(timeTicks, context, DEFAULT_ROTATION, vectorInterpolator),
        scale = bone.scale.sample(timeTicks, context, DEFAULT_SCALE, vectorInterpolator),
    )

    private fun interpolateVector(
        previous: Vector3f?,
        from: Vector3f,
        to: Vector3f,
        next: Vector3f?,
        alpha: Float,
        easing: EasingMode,
    ): Vector3f {
        val clamped = alpha.coerceIn(0f, 1f)

        if (easing == EasingMode.CATMULLROM) {
            val p0 = previous ?: from
            val p3 = next ?: to
            return Vector3f(
                Mth.catmullrom(clamped, p0.x, from.x, to.x, p3.x),
                Mth.catmullrom(clamped, p0.y, from.y, to.y, p3.y),
                Mth.catmullrom(clamped, p0.z, from.z, to.z, p3.z),
            )
        }

        val t = when (easing) {
            EasingMode.STEP -> if (clamped >= 1f) 1f else 0f
            EasingMode.EASE_IN -> clamped.pow(2)
            EasingMode.EASE_OUT -> 1f - (1f - clamped).pow(2)
            EasingMode.EASE_IN_OUT -> if (clamped < 0.5f) 2f * clamped.pow(2) else 1f - (-2f * clamped + 2f).pow(2) / 2f
            EasingMode.LINEAR -> clamped
        }

        return Vector3f(from).lerp(to, t)
    }
}
