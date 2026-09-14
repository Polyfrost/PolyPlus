package org.polyfrost.polyplus.client.emotes.playback

import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.emotes.Emote

data class EmotePlaybackSnapshot(
    val emote: Emote,
    val sample: Map<String, BoneTransform>,
    val playerWeight: Float,
    val effectPoseWeight: Float = playerWeight,
)
