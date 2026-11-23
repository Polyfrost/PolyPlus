package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticScene
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

class CosmeticSceneTest {
    @Test
    fun `a texture sheet animates a cosmetic and a single texture doesn't`() {
        assertTrue(scene(CUBE, imageHeight = 48).isAnimated, "three stacked 16x16 frames")
        assertFalse(scene(CUBE, imageHeight = 16).isAnimated)
    }

    @Test
    fun `geometry with no cubes is empty`() {
        assertTrue(scene(BONES_ONLY, imageHeight = 16).isEmpty)
        assertFalse(scene(CUBE, imageHeight = 16).isEmpty)
    }

    private fun scene(geometry: String, imageHeight: Int) =
        CosmeticScene(geometry, BodySlot.Hat, anchor = null, imageWidth = 16, imageHeight = imageHeight, scale = 1f)

    private companion object {
        fun geometry(bones: String) = """{"format_version":"1.12.0","minecraft:geometry":[{
            "description":{"identifier":"geometry.test","texture_width":16,"texture_height":16},
            "bones":[$bones]}]}"""

        val CUBE = geometry("""{"name":"brim","pivot":[0,24,0],"cubes":[{"origin":[-1,32,-1],"size":[2,2,2],"uv":[0,0]}]}""")
        val BONES_ONLY = geometry("""{"name":"brim","pivot":[0,24,0]}""")
    }
}
