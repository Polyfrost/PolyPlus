package org.polyfrost.polyplus.client.cosmetics.playback

import org.polyfrost.polyplus.client.bedrock.playback.BedrockAnimationPlayback
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.render.PlayerRenderContext

object CosmeticPlayback {
    fun sample(
        cosmetic: AttachedCosmetic,
        startTimeMs: Long,
        renderContext: PlayerRenderContext,
    ): Map<String, BoneTransform> {
        val animation = cosmetic.animation ?: return emptyMap()

        return BedrockAnimationPlayback.sampleTimed(
            animation = animation,
            startTimeMs = startTimeMs,
            renderContext = renderContext,
            clampOnce = true,
        )
    }
}
