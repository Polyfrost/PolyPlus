package org.polyfrost.polyplus.client.emotes.playback

import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.animation.LoopMode
import org.polyfrost.polyplus.client.bedrock.playback.AnimationSampler
import org.polyfrost.polyplus.client.bedrock.playback.BedrockAnimationPlayback
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.conditions.EmoteConditions
import org.polyfrost.polyplus.client.render.PlayerRenderContext

private const val BLEND_MS = 140f
class EmoteController {
    private var phase: Phase = Phase.Inactive

    val isActive: Boolean
        get() = phase !is Phase.Inactive

    fun playbackSnapshot(): EmotePlaybackSnapshot? {
        return when (val current = phase) {
            is Phase.BlendingIn -> {
                val weight = blendWeight(current.startTimeMs)
                EmotePlaybackSnapshot(current.emote, current.lastSample, weight, weight)
            }
            is Phase.Playing -> EmotePlaybackSnapshot(current.emote, current.lastSample, 1f, 1f)
            is Phase.BlendingOut -> {
                val playerWeight = 1f - blendWeight(current.startTimeMs)
                EmotePlaybackSnapshot(current.emote, current.lastSample, playerWeight, 1f)
            }
            Phase.Inactive -> null
        }
    }

    private fun blendWeight(startTimeMs: Long): Float {
        return ((Util.getMillis() - startTimeMs) / BLEND_MS).coerceIn(0f, 1f)
    }

    fun play(emote: Emote) {
        val molangVariables = mutableMapOf<String, Float>()
        AnimationSampler.runInitialize(emote.animation, molangVariables)
        phase = Phase.BlendingIn(
            emote = emote,
            startTimeMs = Util.getMillis(),
            molangVariables = molangVariables,
        )
    }

    fun stop() {
        phase = Phase.Inactive
    }

    fun tick(player: AbstractClientPlayer) {
        when (val current = phase) {
            is Phase.BlendingIn -> if (!EmoteConditions.allows(player, current.emote.rules)) {
                beginBlendOut(current.emote, current.lastSample)
            }
            is Phase.Playing -> if (!EmoteConditions.allows(player, current.emote.rules)) {
                beginBlendOut(current.emote, current.lastSample)
            }
            else -> Unit
        }
    }

    fun applyToModel(model: PlayerModel, renderContext: PlayerRenderContext): Map<String, BoneTransform> {
        return when (val current = phase) {
            is Phase.Inactive -> emptyMap()
            is Phase.BlendingIn -> applyBlendingIn(model, renderContext, current)
            is Phase.Playing -> applyPlaying(model, renderContext, current)
            is Phase.BlendingOut -> applyBlendingOut(model, current)
        }
    }

    private fun applyBlendingIn(model: PlayerModel, renderContext: PlayerRenderContext, blending: Phase.BlendingIn): Map<String, BoneTransform> {
        val elapsedMs = Util.getMillis() - blending.startTimeMs
        val progress = (elapsedMs / BLEND_MS).coerceIn(0f, 1f)

        val sample = sampleAnimation(blending.emote.animation, resolvePlayingTime(blending.emote.animation, blending.startTimeMs), renderContext, blending.molangVariables)
        blending.lastSample = sample
        applyWeighted(model, blending.emote, sample, progress)

        if (progress >= 1f) {
            phase = Phase.Playing(
                emote = blending.emote,
                startTimeMs = blending.startTimeMs,
                molangVariables = blending.molangVariables,
                lastSample = sample,
            )
        }
        return sample
    }

    private fun applyPlaying(model: PlayerModel, renderContext: PlayerRenderContext, playing: Phase.Playing): Map<String, BoneTransform> {
        val animation = playing.emote.animation
        val timeTicks = resolvePlayingTime(animation, playing.startTimeMs)

        if (animation.loop == LoopMode.ONCE && timeTicks >= animation.lengthTicks) {
            val lastSample = sampleAnimation(animation, timeTicks, renderContext, playing.molangVariables)
            beginBlendOut(playing.emote, lastSample)
            applyWeighted(model, playing.emote, lastSample, 1f)
            return lastSample
        }

        val sample = sampleAnimation(animation, timeTicks, renderContext, playing.molangVariables)
        playing.lastSample = sample
        applyWeighted(model, playing.emote, sample, 1f)
        return sample
    }

    private fun applyBlendingOut(model: PlayerModel, blending: Phase.BlendingOut): Map<String, BoneTransform> {
        val elapsedMs = Util.getMillis() - blending.startTimeMs
        val progress = (elapsedMs / BLEND_MS).coerceIn(0f, 1f)
        val playerWeight = 1f - progress

        applyWeighted(model, blending.emote, blending.lastSample, playerWeight)

        if (progress >= 1f) {
            stop()
        }
        return blending.lastSample
    }

    private fun sampleAnimation(
        animation: BedrockAnimation,
        timeTicks: Float,
        renderContext: PlayerRenderContext,
        molangVariables: MutableMap<String, Float>,
    ): Map<String, BoneTransform> {
        return AnimationSampler.sample(
            animation = animation,
            timeTicks = timeTicks,
            renderContext = renderContext,
            molangVariables = molangVariables,
        )
    }

    private fun beginBlendOut(emote: Emote, lastSample: Map<String, BoneTransform>) {
        phase = Phase.BlendingOut(
            emote = emote,
            lastSample = lastSample,
            startTimeMs = Util.getMillis(),
        )
    }

    private fun applyWeighted(model: PlayerModel, emote: Emote, sample: Map<String, BoneTransform>, weight: Float) {
        if (!emote.rules.allowVanillaPose) {
            ModelPoseApplicator.resetAnimatedPlayerBones(model, sample.keys)
        }
        ModelPoseApplicator.apply(model, sample, weight)
    }

    private fun resolvePlayingTime(animation: BedrockAnimation, startTimeMs: Long): Float {
        return BedrockAnimationPlayback.resolveTimeTicks(
            animation = animation,
            elapsedTicks = BedrockAnimationPlayback.elapsedTicksSince(startTimeMs),
        )
    }

    private sealed class Phase {
        data object Inactive : Phase()

        data class BlendingIn(
            val emote: Emote,
            val startTimeMs: Long,
            val molangVariables: MutableMap<String, Float>,
            var lastSample: Map<String, BoneTransform> = emptyMap(),
        ) : Phase()

        data class Playing(
            val emote: Emote,
            val startTimeMs: Long,
            val molangVariables: MutableMap<String, Float>,
            var lastSample: Map<String, BoneTransform> = emptyMap(),
        ) : Phase()

        data class BlendingOut(
            val emote: Emote,
            val lastSample: Map<String, BoneTransform>,
            val startTimeMs: Long,
        ) : Phase()
    }
}
