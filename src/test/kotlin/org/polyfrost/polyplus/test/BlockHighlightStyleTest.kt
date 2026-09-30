package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.BlockHighlightDraft
import org.polyfrost.polyplus.client.features.BlockHighlightStyle
import org.polyfrost.polyplus.client.features.BlockHighlightStyle.Depth

class BlockHighlightStyleTest {
    private fun line(enabled: Boolean, width: Float, color: Int = -16777216, alpha: Int = 255) = """
        {"enabled": $enabled, "lineWidth": $width, "lineDepthTest": "NORMAL", "outlineType": "ALL",
         "lineExpandBlocks": -0.0625, "lineExpandPercentage": 1.0,
         "color": {"col1": $color, "col2": $color, "alpha": $alpha,
                   "rainbowSettings": {"enabled": false, "delay": 250, "saturation": 1.0, "brightness": 1.0, "speed": 5.0}}}
    """

    private fun config(mod: Boolean = true, vanilla: Boolean = false, fill: Boolean = false, primary: Boolean = true) = """
        {"enableModRendering": $mod, "drawVanillaOutline": $vanilla, "fillEnabled": $fill,
         "fillDepthTest": "ALWAYS_PASS", "fillType": "LOOKAT",
         "fillCol": {"col1": -65536, "col2": -65536, "alpha": 39, "rainbowSettings": {"enabled": true, "speed": 5.0}},
         "primary": ${line(primary, 2.5f, alpha = 103)},
         "secondary": ${line(true, 7.5f, color = -1)},
         "tertiary": ${line(false, 12.5f)}}
    """

    @Test
    fun `layers draw widest first and the primary last`() {
        val style = BlockHighlightStyle.parse(config())

        assertEquals(listOf(7.5f, 2.5f), style.layers.map { it.width })
        assertEquals(103, style.layers.last().colors.alpha)
        assertEquals(0xFFFFFF, style.layers.first().colors.first)
        assertEquals(Depth.NORMAL, style.layers.last().depth)
        assertEquals(0.875f, style.layers.last().scale, 1e-6f)
    }

    @Test
    fun `a disabled primary hides every layer, as in CBH`() {
        assertTrue(BlockHighlightStyle.parse(config(primary = false)).layers.isEmpty())
    }

    @Test
    fun `turning mod rendering off leaves only the vanilla outline`() {
        assertTrue(BlockHighlightStyle.parse(config(mod = false, fill = true)).layers.isEmpty())
        assertNull(BlockHighlightStyle.parse(config(mod = false, fill = true)).fill)

        val vanilla = BlockHighlightStyle.parse(config(mod = false, vanilla = true)).layers.single()
        assertEquals(102, vanilla.colors.alpha)
        assertEquals(Depth.NORMAL, vanilla.depth)
    }

    @Test
    fun `the fill keeps its faces, depth and rainbow`() {
        val fill = BlockHighlightStyle.parse(config(fill = true)).fill
        assertNotNull(fill)
        assertEquals(BlockHighlightStyle.Faces.LOOKAT, fill!!.faces)
        assertEquals(Depth.ALWAYS_PASS, fill.depth)
        assertNotNull(fill.colors.rainbow)
        assertNull(BlockHighlightStyle.parse(config(fill = false)).fill)
    }

    @Test
    fun `the editor writes colours opaque and keeps everything it does not expose`() {
        val draft = BlockHighlightDraft(config())
        draft.outlineStart = 0x5BCEFA
        draft.outlineOpacity = 0.5f

        val style = BlockHighlightStyle.parse(draft.toJson())
        assertEquals(0x5BCEFA, style.layers.last().colors.first)
        assertEquals(128, style.layers.last().colors.alpha)
        assertEquals(7.5f, style.layers.first().width, "the secondary layer rides along")
        assertTrue(draft.toJson().contains("-10760454"), "stored as opaque ARGB like java.awt.Color#getRGB")
    }

    @Test
    fun `see-through moves every layer and the fill together`() {
        val draft = BlockHighlightDraft(config(fill = true))
        draft.seeThrough = true

        val style = BlockHighlightStyle.parse(draft.toJson())
        assertTrue(style.layers.all { it.depth == Depth.ALWAYS_PASS })
        assertEquals(Depth.ALWAYS_PASS, style.fill!!.depth)
        assertTrue(draft.seeThrough)
    }

    @Test
    fun `switching the fill on never leaves it hidden behind the block`() {
        val draft = BlockHighlightDraft(config().replace("\"fillDepthTest\": \"ALWAYS_PASS\"", "\"fillDepthTest\": \"HIDDEN_ONLY\""))
        draft.fill = true

        assertEquals(Depth.NORMAL, BlockHighlightStyle.parse(draft.toJson()).fill!!.depth)
    }

    @Test
    fun `an untouched draft still matches its preset whatever the formatting`() {
        val json = config()
        assertTrue(BlockHighlightDraft(json).matches(BlockHighlightDraft(json).toJson()))
        assertFalse(BlockHighlightDraft(json).apply { animations = true }.matches(json))
    }
}
