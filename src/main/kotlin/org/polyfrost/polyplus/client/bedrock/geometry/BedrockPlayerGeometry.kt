package org.polyfrost.polyplus.client.bedrock.geometry

import org.polyfrost.polyplus.client.cosmetics.assets.BedrockPlayerGeometryCache

object BedrockPlayerGeometry {
    fun get(): BedrockGeometry = BedrockPlayerGeometryCache.getOrThrow()
}
