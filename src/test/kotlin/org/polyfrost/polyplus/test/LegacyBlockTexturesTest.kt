package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.engine.LegacyBlockTextures

class LegacyBlockTexturesTest {
    @Test
    fun `renamed blocks map to their 1_8_9 texture names`() {
        assertEquals("prismarine_rough", LegacyBlockTextures.name("prismarine"))
        assertEquals("stonebrick", LegacyBlockTextures.name("stone_bricks"))
        assertEquals("grass_top", LegacyBlockTextures.name("grass_block_top"))
    }

    @Test
    fun `wood families use the old order and dark oak is big oak`() {
        assertEquals("planks_oak", LegacyBlockTextures.name("oak_planks"))
        assertEquals("log_big_oak", LegacyBlockTextures.name("dark_oak_log"))
        assertEquals("log_birch_top", LegacyBlockTextures.name("birch_log_top"))
        assertEquals("leaves_jungle", LegacyBlockTextures.name("jungle_leaves"))
    }

    @Test
    fun `color families use the old names and light gray is silver`() {
        assertEquals("wool_colored_light_blue", LegacyBlockTextures.name("light_blue_wool"))
        assertEquals("glass_silver", LegacyBlockTextures.name("light_gray_stained_glass"))
        assertEquals("hardened_clay_stained_red", LegacyBlockTextures.name("red_terracotta"))
    }

    @Test
    fun `names that kept their texture pass through`() {
        assertEquals("diamond_block", LegacyBlockTextures.name("diamond_block"))
        assertEquals("glass", LegacyBlockTextures.name("glass"))
    }
}
