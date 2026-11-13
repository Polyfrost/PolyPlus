package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.compat.ClientFeatureTranslator
import org.polyfrost.polyplus.compat.HitboxSettingsTranslator
import org.polyfrost.polyplus.compat.ModConfigPatcher

/** Checks how each client's hitbox settings become PolyHitbox settings, using each client's real format. */
class HitboxImportTest {

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    private fun rgba(vararg channels: Int) = JsonArray(channels.map(::JsonPrimitive))

    @Test
    fun `lunar player style enables players and self with the same color`() {
        // -11184641 is 0xFF5555FF, a light blue with full alpha.
        val values = HitboxSettingsTranslator.fromLunar(parse("""{"hitboxPlayerLineColor": {"value": -11184641}}"""))
        for (category in listOf("PLAYER", "SELF")) {
            assertEquals(JsonPrimitive(true), values["${category}_overwriteVisuals"])
            assertEquals(rgba(85, 85, 255, 255), values["${category}_outlineColor"])
            assertEquals(JsonPrimitive(true), values["${category}_overwriteLogic"])
            assertEquals(JsonPrimitive(0), values["${category}_showCondition"])
            assertEquals(JsonPrimitive(true), values["$category.${category}_override"])
            assertEquals(JsonPrimitive(0), values["$category.${category}_showCondition"])
            assertEquals(rgba(85, 85, 255, 255), values["$category.${category}_outlineColor"])
        }
    }

    @Test
    fun `lunar hitbox settings ride along with turning the mod on`() {
        val root = parse("""{"HITBOX": {"enabled": true, "options": {"hitboxPlayerLineColor": {"value": -11184641}}}}""")
        val patch = ClientFeatureTranslator.fromLunar(root).single()
        assertEquals("polyhitbox.json", patch.configId)
        assertEquals(JsonPrimitive(true), patch.values["enabled"])
        for (category in listOf("PLAYER", "SELF")) {
            assertEquals(JsonPrimitive(true), patch.values["${category}_overwriteVisuals"])
            assertEquals(JsonPrimitive(true), patch.values["$category.${category}_override"])
            assertEquals(rgba(85, 85, 255, 255), patch.values["$category.${category}_outlineColor"])
        }
    }

    @Test
    fun `lunar default player settings enable self even without a customized color`() {
        val patch = ClientFeatureTranslator.fromLunar(parse("""{"HITBOX": {"enabled": true}}""")).single()
        val flat = ModConfigPatcher.patched(parse("""{"SELF_overwriteLogic":true,"SELF_showCondition":3}"""), patch.values)
        val nested = ModConfigPatcher.patched(parse("""{"SELF":{"SELF_override":false,"SELF_showCondition":3}}"""), patch.values)
        assertEquals(JsonPrimitive(true), flat["SELF_overwriteLogic"])
        assertEquals(JsonPrimitive(0), flat["SELF_showCondition"])
        assertEquals(JsonPrimitive(true), nested["SELF"]?.jsonObject?.get("SELF_override"))
        assertEquals(JsonPrimitive(0), nested["SELF"]?.jsonObject?.get("SELF_showCondition"))
    }

    @Test
    fun `badlion main style goes to general and only differing entities get their own`() {
        val settings = parse(
            """
            {
              "enabled": true,
              "animalHitBoxesEnabled": {"value": false},
              "playerHitboxesEnabled": {"value": true},
              "mainHitboxColor": {"enabled": true, "color": {"red": -1, "green": -1, "blue": -1, "alpha": -1}},
              "playerHitboxColor": {"enabled": true, "color": {"red": -1, "green": 0, "blue": 0, "alpha": -1}},
              "monsterHitboxColor": {"enabled": true, "color": {"red": -1, "green": -1, "blue": -1, "alpha": -1}},
              "mainHitboxSize": {"value": 2.0},
              "witherSkullHitboxSize": {"value": 4.0},
              "monsterHitboxSize": {"value": 2.0}
            }
            """,
        )
        val values = HitboxSettingsTranslator.fromBadlion(settings)

        assertEquals(rgba(255, 255, 255, 255), values["DEFAULT_outlineColor"])
        assertEquals(rgba(255, 255, 255, 255), values["DEFAULT.DEFAULT_outlineColor"])
        assertEquals(JsonPrimitive(true), values["MOB.MOB_override"])
        assertEquals(JsonPrimitive(3), values["MOB.MOB_showCondition"])
        assertEquals(JsonPrimitive(1.0), values["DEFAULT_outlineThickness"])

        assertEquals(JsonPrimitive(true), values["MOB_overwriteLogic"])
        assertEquals(JsonPrimitive(3), values["MOB_showCondition"], "hidden animals show Never")

        assertEquals(JsonPrimitive(true), values["PLAYER_overwriteVisuals"])
        assertEquals(rgba(255, 0, 0, 255), values["PLAYER_outlineColor"])
        assertEquals(JsonPrimitive(0), values["PLAYER_showCondition"], "players stay visible")

        assertEquals(JsonPrimitive(2.0), values["WITHER_SKULL_outlineThickness"])
        assertEquals(JsonPrimitive(2.0), values["WITHER_SKULL.WITHER_SKULL_outlineThickness"])
        assertNull(values["MONSTER_overwriteVisuals"], "same as the main style, so general settings keep applying")
    }

    @Test
    fun `norisk groups map onto categories with their own visuals`() {
        val settings = parse(
            """
            {
              "PLAYER": {
                "visible": true, "lineWidth": 2.0, "showLookVector": true,
                "multiColor": {"color": {"red": 1.0, "green": 0.0, "blue": 0.0, "alpha": 1.0}},
                "facingColor": {"color": {"red": 0.0, "green": 0.0, "blue": 1.0, "alpha": 1.0}}
              },
              "MOBS": {"visible": false, "lineWidth": 1.0, "showLookVector": false},
              "OTHER": {"visible": false}
            }
            """,
        )
        val values = HitboxSettingsTranslator.fromNoRisk(settings)

        assertEquals(JsonPrimitive(true), values["PLAYER_overwriteVisuals"])
        assertEquals(rgba(255, 0, 0, 255), values["PLAYER_outlineColor"])
        assertEquals(rgba(0, 0, 255, 255), values["PLAYER_viewRayColor"])
        assertEquals(JsonPrimitive(true), values["PLAYER_showViewRay"])
        assertEquals(JsonPrimitive(true), values["PLAYER.PLAYER_override"])
        assertEquals(JsonPrimitive(0), values["PLAYER.PLAYER_showCondition"])
        assertEquals(rgba(0, 0, 255, 255), values["PLAYER.PLAYER_viewRayColor"])
        assertEquals(JsonPrimitive(3), values["MONSTER.MONSTER_showCondition"])
        assertEquals(JsonPrimitive(2.0), values["PLAYER_outlineThickness"])

        // NoRisk's single mob group covers both of PolyHitbox's mob categories.
        assertEquals(JsonPrimitive(3), values["MOB_showCondition"])
        assertEquals(JsonPrimitive(3), values["MONSTER_showCondition"])
        assertNull(values.keys.firstOrNull { it.startsWith("OTHER") }, "no PolyHitbox category for NoRisk's OTHER")
    }
}
