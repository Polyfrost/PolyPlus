package org.polyfrost.polyplus.test

import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticRows
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticScene
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
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

    @Test
    fun `a legacy cosmetic gets its own scene for each slot it is worn in`() {
        val head = scene(CUBE, imageHeight = 16)
        val back = head.forSlot(BodySlot.Backpack)
        assertNotSame(head, back)
        assertSame(back, head.forSlot(BodySlot.Backpack))
        assertSame(back, head.forSlot(BodySlot.Tail), "a tail hangs from the back like a backpack")
        assertSame(head, head.forSlot(BodySlot.Hat))
        assertSame(head, back.forSlot(BodySlot.Hat))
        assertSame(back, back.forSlot(BodySlot.Backpack))
    }

    @Test
    fun `moving an equipped legacy cosmetic to another slot moves its scene too`() {
        val head = scene(CUBE, imageHeight = 16)
        val id = Identifier.fromNamespaceAndPath("polyplus", "cosmetics/1/attached")
        val texture = Identifier.fromNamespaceAndPath("polyplus", "cosmetics/1/texture")
        val worn = AttachedCosmetic(id, 1, BodySlot.Hat, texture, head, -1)
        assertSame(head, worn.scene)
        val onBack = worn.copy(slot = BodySlot.Backpack)
        assertSame(head.forSlot(BodySlot.Backpack), onBack.scene)
        assertSame(head, onBack.copy(slot = BodySlot.Hat).scene)
    }

    @Test
    fun `a pre-collected row matches a drawn row whose events and detail differ`() {
        val stride = 6
        val input = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 1f, 2f, 3f, 0f, 1f, 6f)
        assertTrue(CosmeticRows.same(input, 0, stride, stride, eventsAt = 3, detailAt = 4))
        input[stride + 5] = 6.0001f
        assertFalse(CosmeticRows.same(input, 0, stride, stride, eventsAt = 3, detailAt = 4))
    }

    @Test
    fun `rows that differ only in the sign of zero don't match`() {
        val input = floatArrayOf(0f, -0f)
        assertFalse(CosmeticRows.same(input, 0, 1, 1, eventsAt = -1, detailAt = -1))
        input[1] = Float.NaN
        input[0] = Float.NaN
        assertTrue(CosmeticRows.same(input, 0, 1, 1, eventsAt = -1, detailAt = -1))
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
