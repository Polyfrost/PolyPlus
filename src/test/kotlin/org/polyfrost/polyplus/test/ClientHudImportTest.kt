package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.compat.BadlionHudTranslator
import org.polyfrost.polyplus.compat.FeatherHudTranslator
import org.polyfrost.polyplus.compat.LunarHudTranslator
import org.polyfrost.polyplus.compat.NoRiskHudTranslator
import org.polyfrost.polyplus.compat.OneConfigHudInstances
import org.polyfrost.polyplus.compat.OneConfigHudInstances.Request

/**
 * Locks in how each client's saved HUD layout becomes OneConfig HUD placements.
 * The samples are trimmed from real client files; the positional rules were checked against in-game screenshots.
 */
class ClientHudImportTest {

    private fun resource(name: String): JsonObject {
        val text = requireNotNull(javaClass.classLoader.getResource("client-import/$name")) { name }.readText()
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun List<Request>.of(source: String): Request =
        firstOrNull { it.source == source } ?: fail("no request for $source")

    private fun assertNear(expected: Double, actual: Double?) =
        assertEquals(expected, actual ?: fail("expected $expected but the value was missing"), 0.01)

    @Test
    fun `lunar offsets are scaled from its gui pixels`() {
        val requests = LunarHudTranslator.requests(resource("lunar-mods.json"), factorX = 1.5, factorY = 2.0)

        val fps = requests.of("Lunar FPS")
        assertEquals(OneConfigHudInstances.FPS, fps.target)
        assertEquals("TopLeft", fps.section)
        assertNear(4.5, fps.x)
        assertNear(44.0, fps.y)
        assertEquals(JsonPrimitive(false), fps.style["showBackground"])

        val memory = requests.of("Lunar MEMORY")
        assertEquals("CenterRight", memory.section)
        assertNear(-12.0, memory.x)
        assertEquals(1.5, memory.scale)

        val heightLimit = requests.of("Lunar HEIGHT_LIMIT")
        assertEquals(OneConfigHudInstances.HEIGHT_LIMIT, heightLimit.target)
        assertEquals("TopRight", heightLimit.section)
        assertNear(-12.0, heightLimit.x)
        assertNear(188.0, heightLimit.y)

        // Lunar omits the on flag for HUDs it places by default.
        assertEquals(true, requests.of("Lunar COORDINATES").enabled)
        assertEquals(false, requests.of("Lunar DIRECTION_HUD").enabled)
    }

    @Test
    fun `lunar reads child placements and style options`() {
        val requests = LunarHudTranslator.requests(resource("lunar-mods.json"), 1.0, 1.0)

        val ping = requests.of("Lunar PING")
        assertEquals("TopLeft", ping.section)
        assertNear(66.0, ping.y)

        val coordinates = requests.of("Lunar COORDINATES").style
        assertEquals(JsonPrimitive(1543767044), coordinates["bgColor"])
        assertEquals(JsonPrimitive(true), coordinates["bgChroma"])
        assertEquals(JsonPrimitive(true), coordinates["showDirection"])
        assertEquals(JsonPrimitive(true), coordinates["showAxis"])

        // Non-HUD modules are not HUD requests.
        assertFalse(requests.any { it.source == "Lunar MOTION_BLUR" })
    }

    @Test
    fun `norisk fractions are measured from the anchor corner`() {
        val modules = resource("norisk-profile.json")["modules"] as JsonArray
        val requests = NoRiskHudTranslator.requests(modules, refW = 1000.0, refH = 500.0)

        val fps = requests.of("NoRisk FPS")
        assertEquals("TopRight", fps.section)
        assertNear(-100.0, fps.x)
        assertNear(50.0, fps.y)
        assertEquals(1.25, fps.scale)
        assertEquals(JsonPrimitive(false), fps.style["showBackground"])
        assertEquals(JsonPrimitive(0xFFFF0000.toInt()), fps.style["textColor"])
        assertEquals(JsonPrimitive(true), fps.style["textChroma"])

        val potions = requests.of("NoRisk Potion Status")
        assertEquals(OneConfigHudInstances.POTIONS, potions.target)
        assertEquals("CenterLeft", potions.section)
        assertNear(0.0, potions.y)

        assertEquals(false, requests.of("NoRisk Clock").enabled)
        // Out-of-range fractions are dropped instead of pushing the HUD off screen.
        assertNull(requests.of("NoRisk Speedometer").section)
        assertFalse(requests.any { it.source == "NoRisk Not A Hud" })
    }

    @Test
    fun `feather offsets use each module's own anchor`() {
        val requests = FeatherHudTranslator.requests(resource("feather-profile.json"), factorX = 2.0, factorY = 2.0)

        val fps = requests.of("Feather fps")
        assertEquals("TopLeft", fps.section)
        assertNear(406.0, fps.x)
        assertNear(280.0, fps.y)
        assertEquals(true, fps.enabled, "FPS is on by default in Feather")

        val keystrokes = requests.of("Feather keystrokes")
        assertEquals("BottomCenter", keystrokes.section)
        assertNear(404.0, keystrokes.x)
        assertNear(-2.0, keystrokes.y)

        assertEquals("TopRight", requests.of("Feather ping").section)
        assertEquals(true, requests.of("Feather ping").enabled, "Ping is on by default in Feather")

        val armor = requests.of("Feather armorStatus")
        assertEquals(false, armor.enabled, "Armor Status is off by default, so a missing flag means off")
        assertEquals("BottomRight", armor.section)

        assertEquals("TopLeft", requests.of("Feather comboDisplay").section)
        assertEquals("TopRight", requests.of("Feather packdisplay").section)

        val coordinates = requests.of("Feather coordinates")
        assertEquals(true, coordinates.enabled)
        assertNull(coordinates.section, "never moved, so Feather saved no position")
    }

    @Test
    fun `badlion positions are fractions inside a 3x3 grid cell`() {
        val requests = BadlionHudTranslator.requests(resource("badlion-data.json"), refW = 1200.0, refH = 600.0)

        val fps = requests.of("Badlion fps")
        assertEquals("TopRight", fps.section)
        assertNear(-1.875, fps.x)
        assertNear(93.89, fps.y)
        assertEquals(true, fps.enabled)
        assertEquals(JsonPrimitive(false), fps.style["showBackground"])
        assertEquals(JsonPrimitive(0x80000000.toInt()), fps.style["bgColor"])
        assertEquals(JsonPrimitive(0xFFFF0000.toInt()), fps.style["textColor"])
        assertNull(fps.style["showShadow"], "unchanged options are not carried over")

        val reach = requests.of("Badlion reach display")
        assertEquals("CenterRight", reach.section)
        assertNear(-97.22, reach.y)
        assertEquals(JsonPrimitive(false), reach.style["showShadow"])
        assertNull(reach.style["textColor"])

        val itemCounter = requests.of("Badlion item counter")
        assertEquals("BottomCenter", itemCounter.section)
        assertNear(163.75, itemCounter.x)
        assertNear(-1.11, itemCounter.y)
        assertEquals(false, itemCounter.enabled)

        assertFalse(requests.any { it.source == "Badlion minimap" }, "no evergreenhud equivalent")
    }
}
