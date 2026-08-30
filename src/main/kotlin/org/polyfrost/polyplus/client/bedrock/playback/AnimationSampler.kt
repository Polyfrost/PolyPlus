package org.polyfrost.polyplus.client.bedrock.playback

import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.animation.BoneAnimation
import org.polyfrost.polyplus.client.bedrock.animation.EasingMode
import org.polyfrost.polyplus.client.bedrock.animation.MolangVectorInterpolator
import org.polyfrost.polyplus.client.bedrock.molang.MolangContext
import org.polyfrost.polyplus.client.bedrock.molang.MolangEvaluator
import org.polyfrost.polyplus.client.bedrock.molang.MolangStatement
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.joml.Vector3f
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
        if (easing == EasingMode.CATMULLROM) {
            val p0 = previous ?: Vector3f(from)
            val p1 = Vector3f(from)
            val p2 = Vector3f(to)
            val p3 = next ?: Vector3f(to)
            return catmullRom(p0, p1, p2, p3, alpha.coerceIn(0f, 1f))
        }

        val clamped = alpha.coerceIn(0f, 1f)

        val t = when (easing) {
            EasingMode.STEP -> if (clamped >= 1f) 1f else 0f
            EasingMode.EASE_IN -> clamped.pow(2)
            EasingMode.EASE_OUT -> 1f - (1f - clamped).pow(2)
            EasingMode.EASE_IN_OUT -> if (clamped < 0.5f) 2f * clamped.pow(2) else 1f - (-2f * clamped + 2f).pow(2) / 2f
            EasingMode.LINEAR -> clamped
        }

        return Vector3f(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t,
        )
    }

    private fun catmullRom(p0: Vector3f, p1: Vector3f, p2: Vector3f, p3: Vector3f, t: Float): Vector3f {
        val t2 = t * t
        val t3 = t2 * t
        return Vector3f(
            catmullRomComponent(p0.x, p1.x, p2.x, p3.x, t, t2, t3),
            catmullRomComponent(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
            catmullRomComponent(p0.z, p1.z, p2.z, p3.z, t, t2, t3),
        )
    }

    private fun catmullRomComponent(p0: Float, p1: Float, p2: Float, p3: Float, t: Float, t2: Float, t3: Float): Float {
        return 0.5f * (
            (2f * p1) +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }
}
