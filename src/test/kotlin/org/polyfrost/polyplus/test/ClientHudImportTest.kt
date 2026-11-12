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
    fun `oneconfig offsets point inward from right and bottom edges`() {
        // Lunar's Reach: 167px left of the right edge, 36px down.
        assertEquals(167.0 to 36.0, OneConfigHudInstances.oneConfigOffset("TopRight", -167.0, 36.0, 1280.0, 694.0))
        // Lunar's Media Player: 135px above the bottom edge.
        assertEquals(7.0 to 135.0, OneConfigHudInstances.oneConfigOffset("BottomLeft", 7.0, -135.0, 1280.0, 694.0))
        // Lunar's Memory: 45px in from the right edge, 60.75px above the middle; centered axes keep screen direction.
        assertEquals(45.0 to -60.75, OneConfigHudInstances.oneConfigOffset("CenterRight", -45.0, -60.75, 1280.0, 694.0))
        assertEquals(0.0 to -40.0, OneConfigHudInstances.oneConfigOffset("Center", 0.0, -40.0, 1280.0, 694.0))
    }

    @Test
    fun `oneconfig offsets are clamped onto the screen`() {
        // Pushed past the right edge: lands on the edge instead of off screen.
        assertEquals(0.0 to 0.0, OneConfigHudInstances.oneConfigOffset("TopRight", 0.5, -3.0, 1280.0, 694.0))
        // Far past the far edge: kept within the screen.
        val (x, y) = OneConfigHudInstances.oneConfigOffset("TopLeft", 5000.0, 5000.0, 1280.0, 694.0)
        assertEquals(1152.0, x, 0.01)
        assertEquals(624.6, y, 0.01)
        assertEquals(640.0 to -347.0, OneConfigHudInstances.oneConfigOffset("Center", 9000.0, -9000.0, 1280.0, 694.0))
    }

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
    fun `lunar maps saturation, playtime, tab and child huds`() {
        val root = Json.parseToJsonElement(
            """
            {
              "SATURATION": {"enabled": false, "SATURATION_HUD_CHILD": {"x": -10.0, "y": 70.0, "position": "topRight"}},
              "PLAYTIME": {"x": 90.0, "y": 46.0, "position": "topLeft"},
              "TAB": {"panelIndex": 25},
              "HYPIXEL_MOD": {"HYPIXEL_TPS": {"enabled": true, "options": {"background": false}, "x": 7.0, "y": 32.0, "position": "topLeft"}},
              "HYPIXEL_BEDWARS": {"HYPIXEL_BEDWARS_HEIGHT_LIMIT_CHILD": {"y": 140.0, "position": "topRight"}},
              "PING": {"enabled": true, "PING_HUD": {"options": {"background": false}, "x": 7.0, "y": 19.0, "position": "topLeft"}}
            }
            """,
        ).jsonObject
        val requests = LunarHudTranslator.requests(root, 1.0, 1.0)

        val saturation = requests.of("Lunar SATURATION")
        assertEquals(OneConfigHudInstances.SATURATION, saturation.target)
        assertEquals(false, saturation.enabled)
        assertEquals("TopRight", saturation.section)

        val playtime = requests.of("Lunar PLAYTIME")
        assertEquals(OneConfigHudInstances.PLAYTIME, playtime.target)
        assertEquals(true, playtime.enabled)
        assertNear(90.0, playtime.x)

        val tab = requests.of("Lunar TAB")
        assertEquals(OneConfigHudInstances.TAB_LIST, tab.target)
        assertNull(tab.enabled)

        val tps = requests.of("Lunar TPS")
        assertEquals(OneConfigHudInstances.TPS, tps.target)
        assertEquals(true, tps.enabled)
        assertEquals("TopLeft", tps.section)
        assertNear(32.0, tps.y)
        assertEquals(JsonPrimitive(false), tps.style["showBackground"])

        val heightLimit = requests.of("Lunar HEIGHT_LIMIT")
        assertEquals(OneConfigHudInstances.HEIGHT_LIMIT, heightLimit.target)
        assertNull(heightLimit.enabled, "neither the module nor the child says whether it is on")
        assertNear(140.0, heightLimit.y)

        // Options saved on the child that holds the placement are read too.
        assertEquals(JsonPrimitive(false), requests.of("Lunar PING").style["showBackground"])
    }

    @Test
    fun `item tracker maps from lunar and badlion`() {
        val lunar = Json.parseToJsonElement(
            """{"ITEM_TRACKER": {"enabled": true, "x": 10.0, "y": 75.0, "position": "middleLeft", "options": {"textShadow": true}}}""",
        ).jsonObject
        val fromLunar = LunarHudTranslator.requests(lunar, 1.0, 1.0).of("Lunar ITEM_TRACKER")
        assertEquals(OneConfigHudInstances.ITEM_TRACKER, fromLunar.target)
        assertEquals("CenterLeft", fromLunar.section)
        assertNear(75.0, fromLunar.y)
        assertEquals(JsonPrimitive(true), fromLunar.style["showShadow"])

        val badlion = Json.parseToJsonElement(
            """
            {
              "boxes": [{"identifier": "item tracker", "quadrant": "BOTTOM_RIGHT", "xPosition": 0.284375, "yPosition": 1.0}],
              "itemTracker": {"enabled": true}
            }
            """,
        ).jsonObject
        val fromBadlion = BadlionHudTranslator.requests(badlion, 1200.0, 600.0).of("Badlion item tracker")
        assertEquals(OneConfigHudInstances.ITEM_TRACKER, fromBadlion.target)
        assertEquals(true, fromBadlion.enabled)
        assertEquals("BottomRight", fromBadlion.section)
        assertNear(-286.25, fromBadlion.x)
        assertNear(0.0, fromBadlion.y)
    }

    @Test
    fun `lunar child huds stay off while their module is off`() {
        val root = Json.parseToJsonElement(
            """{"HYPIXEL_MOD": {"enabled": false, "HYPIXEL_TPS": {"enabled": true, "x": 7.0, "position": "topLeft"}}}""",
        ).jsonObject
        assertEquals(false, LunarHudTranslator.requests(root, 1.0, 1.0).of("Lunar TPS").enabled)
    }

    @Test
    fun `badlion mods without a box carry their on state only`() {
        val root = Json.parseToJsonElement(
            """
            {
              "boxes": [],
              "scoreboard": {"enabled": true},
              "showTps": {"enabled": true},
              "uptime": {"enabled": false},
              "playerList": {"enabled": true}
            }
            """,
        ).jsonObject
        val requests = BadlionHudTranslator.requests(root, 1200.0, 600.0)

        val tps = requests.of("Badlion tps")
        assertEquals(OneConfigHudInstances.TPS, tps.target)
        assertEquals(true, tps.enabled)
        assertNull(tps.section)
        assertEquals(true, OneConfigHudInstances.wouldCreate(tps))

        assertEquals(false, requests.of("Badlion uptime").enabled)
        assertEquals(OneConfigHudInstances.PLAYTIME, requests.of("Badlion uptime").target)
        assertFalse(requests.any { it.source == "Badlion server address" }, "absent from the profile")

        // The game's own HUDs are not created in the corner when the client saved no position for them.
        assertFalse(OneConfigHudInstances.wouldCreate(requests.of("Badlion scoreboard")))
        assertFalse(OneConfigHudInstances.wouldCreate(requests.of("Badlion tab list")))
    }

    @Test
    fun `norisk maps the mouse tracker and reads the 3d skin module`() {
        val modules = Json.parseToJsonElement(
            """
            [
              {"name": "MouseTracker", "values": [
                {"name": "isEnabled", "value": true},
                {"name": "anchorPosition", "value": {"anchor": "TOP_LEFT", "relativX": 0.1, "relativY": 0.2}},
                {"name": "hudScale", "value": 1.5}
              ]},
              {"name": "3D Skin", "values": [{"name": "isEnabled", "value": false}]}
            ]
            """,
        ) as JsonArray
        val mouse = NoRiskHudTranslator.requests(modules, 1000.0, 500.0).of("NoRisk MouseTracker")
        assertEquals(OneConfigHudInstances.MOUSE_STROKES, mouse.target)
        assertEquals(true, mouse.enabled)
        assertNear(100.0, mouse.x)
        assertNear(100.0, mouse.y)
        assertEquals(1.5, mouse.scale)

        assertEquals(false, NoRiskHudTranslator.skinLayersEnabled(modules))
        assertNull(NoRiskHudTranslator.skinLayersEnabled(JsonArray(emptyList())))
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
