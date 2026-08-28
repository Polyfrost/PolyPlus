package org.polyfrost.polyplus.client.bedrock.animation

import org.polyfrost.polyplus.client.bedrock.molang.MolangContext
import org.polyfrost.polyplus.client.bedrock.molang.MolangStatement
import org.polyfrost.polyplus.client.bedrock.molang.MolangVector3
import org.joml.Vector3f

data class BedrockAnimationFile(
    val animations: Map<String, BedrockAnimation>,
)

data class BedrockAnimation(
    val name: String,
    val lengthTicks: Float,
    val loop: LoopMode,
    val initialize: List<MolangStatement> = emptyList(),
    val preAnimation: List<MolangStatement> = emptyList(),
    val boneAnimations: Map<String, BoneAnimation>,
    val bonePivots: Map<String, Vector3f>,
    val boneParents: Map<String, String>,
)

enum class LoopMode {
    ONCE, LOOP, HOLD_LAST,
}

data class BoneAnimation(
    val rotation: VectorChannel = VectorChannel.empty(),
    val position: VectorChannel = VectorChannel.empty(),
    val scale: VectorChannel = VectorChannel.empty(),
)

class VectorChannel private constructor(
    private val procedural: MolangVector3?,
    private val keyframes: MolangKeyframeTrack?,
) {
    val isEmpty: Boolean
        get() = procedural == null && (keyframes == null || keyframes.isEmpty)

    val maxTimeTicks: Float
        get() = keyframes?.maxTimeTicks ?: 0f

    fun sample(
        timeTicks: Float,
        context: MolangContext,
        default: Vector3f,
        interpolator: MolangVectorInterpolator,
    ): Vector3f {
        procedural?.let { return it.eval(context) }
        return keyframes?.sample(timeTicks, context, default, interpolator) ?: default
    }

    companion object {
        fun empty(): VectorChannel = VectorChannel(null, null)

        fun procedural(value: MolangVector3): VectorChannel = VectorChannel(value, null)

        fun keyframed(track: MolangKeyframeTrack): VectorChannel = VectorChannel(null, track)
    }
}

data class Keyframe<T>(
    val timeTicks: Float,
    val valuePre: T,
    val valuePost: T,
    val easing: EasingMode = EasingMode.LINEAR,
) {
    constructor(timeTicks: Float, value: T, easing: EasingMode = EasingMode.LINEAR)
        : this(timeTicks, value, value, easing)
}

enum class EasingMode {
    LINEAR, STEP, CATMULLROM, EASE_IN, EASE_OUT, EASE_IN_OUT;

    companion object {
        fun of(name: String?): EasingMode {
            return when (name?.lowercase()) {
                "step" -> STEP
                "catmullrom", "catmull_rom" -> CATMULLROM
                "ease_in" -> EASE_IN
                "ease_out" -> EASE_OUT
                "ease_in_out" -> EASE_IN_OUT
                else -> LINEAR
            }
        }
    }
}

fun interface MolangVectorInterpolator {
    fun interpolate(
        previous: Vector3f?,
        from: Vector3f,
        to: Vector3f,
        next: Vector3f?,
        alpha: Float,
        easing: EasingMode,
    ): Vector3f
}

class MolangKeyframeTrack private constructor(
    val keyframes: List<Keyframe<MolangVector3>>,
) {
    val isEmpty: Boolean get() = keyframes.isEmpty()

    val maxTimeTicks: Float
        get() = keyframes.maxOfOrNull { it.timeTicks } ?: 0f

    fun sample(
        timeTicks: Float,
        context: MolangContext,
        default: Vector3f,
        interpolator: MolangVectorInterpolator,
    ): Vector3f {
        if (keyframes.isEmpty()) return default
        if (timeTicks <= keyframes.first().timeTicks) return keyframes.first().valuePost.eval(context)
        if (timeTicks >= keyframes.last().timeTicks) return keyframes.last().valuePost.eval(context)

        for (index in 1 until keyframes.size) {
            val fromKeyframe = keyframes[index - 1]
            val toKeyframe = keyframes[index]

            if (timeTicks <= toKeyframe.timeTicks) {
                val span = toKeyframe.timeTicks - fromKeyframe.timeTicks
                val alpha = if (span <= 0f) 1f else (timeTicks - fromKeyframe.timeTicks) / span

                return interpolator.interpolate(
                    keyframes.getOrNull(index - 2)?.valuePost?.eval(context),
                    fromKeyframe.valuePost.eval(context),
                    toKeyframe.valuePre.eval(context),
                    keyframes.getOrNull(index + 1)?.valuePre?.eval(context),
                    alpha,
                    fromKeyframe.easing,
                )
            }

        }
        return keyframes.last().valuePost.eval(context)
    }

    companion object {
        fun of(keyframes: List<Keyframe<MolangVector3>>): MolangKeyframeTrack {
            if (keyframes.isEmpty())
                return MolangKeyframeTrack(emptyList())

            return MolangKeyframeTrack(keyframes.sortedBy { it.timeTicks })
        }
    }
}
