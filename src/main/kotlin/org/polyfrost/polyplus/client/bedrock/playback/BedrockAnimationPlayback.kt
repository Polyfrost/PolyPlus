package org.polyfrost.polyplus.client.bedrock.playback

//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.animation.LoopMode
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import kotlin.math.min

object BedrockAnimationPlayback {
    fun resolveTimeTicks(
        animation: BedrockAnimation,
        elapsedTicks: Float,
        clampOnce: Boolean = false,
    ): Float {
        return when (animation.loop) {
            LoopMode.LOOP -> elapsedTicks % animation.lengthTicks
            LoopMode.HOLD_LAST -> min(elapsedTicks, animation.lengthTicks)
            LoopMode.ONCE -> if (clampOnce) min(elapsedTicks, animation.lengthTicks) else elapsedTicks
        }
    }

    fun elapsedTicksSince(startTimeMs: Long): Float {
        return (Util.getMillis() - startTimeMs) / BedrockConstants.MS_PER_TICK
    }

    fun sampleTimed(
        animation: BedrockAnimation,
        startTimeMs: Long,
        renderContext: PlayerRenderContext?,
        clampOnce: Boolean = false,
        molangVariables: MutableMap<String, Float> = mutableMapOf(),
    ): Map<String, BoneTransform> {
        AnimationSampler.runInitialize(animation, molangVariables)
        val timeTicks = resolveTimeTicks(
            animation = animation,
            elapsedTicks = elapsedTicksSince(startTimeMs),
            clampOnce = clampOnce,
        )
        return AnimationSampler.sample(
            animation = animation,
            timeTicks = timeTicks,
            renderContext = renderContext,
            molangVariables = molangVariables,
        )
    }
}
