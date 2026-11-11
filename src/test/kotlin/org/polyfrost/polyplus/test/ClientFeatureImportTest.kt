package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.compat.ClientFeatureTranslator
import org.polyfrost.polyplus.compat.ModConfigPatcher.Patch

/** Checks which client features turn on which OneConfig mods, using trimmed real client files. */
class ClientFeatureImportTest {

    private fun resource(name: String): JsonObject {
        val text = requireNotNull(javaClass.classLoader.getResource("client-import/$name")) { name }.readText()
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun List<Patch>.configs(): Set<String> = map { it.configId }.toSet()

    private fun List<Patch>.of(configId: String): Patch =
        firstOrNull { it.configId == configId } ?: fail("no change for $configId")

    @Test
    fun `lunar turns on mods for features it has on`() {
        val patches = ClientFeatureTranslator.fromLunar(resource("lunar-mods.json"))

        assertEquals(JsonPrimitive(true), patches.of("polyblur.json").values["isEnabled"])

        val sprint = patches.of("polysprint.json").values
        assertEquals(JsonPrimitive(true), sprint["toggleSprint"])
        assertEquals(JsonPrimitive(6.0), sprint["flyBoostAmount"], "fly boost shares PolySprint's scale")

        // Off, or never changed, means leave the mod alone.
        assertTrue("polyhitbox.json" !in patches.configs())
        assertTrue("oitemphysic.json" !in patches.configs())
        assertTrue(patches.all { it.onlyIfExists })
    }

    @Test
    fun `features with a version specific config name try each name`() {
        val patches = ClientFeatureTranslator.fromLunar(resource("lunar-mods.json"))
        val animations = patches.filter { it.modId == "overflowanimations" }.map { it.configId }
        assertEquals(listOf("overflowanimations-1.8.9.json", "overflowanimations.json"), animations)
    }

    @Test
    fun `feather only counts features saved as on`() {
        val patches = ClientFeatureTranslator.fromFeather(resource("feather-profile.json"))

        assertEquals(setOf("polyblur.json", "oitemphysic.json"), patches.configs())
        assertEquals(
            JsonPrimitive(true),
            patches.of("oitemphysic.json").values["options.ItemPhysicConfig.Toggle mod"],
        )
    }

    @Test
    fun `badlion ignores the off state it saves for untouched mods`() {
        val patches = ClientFeatureTranslator.fromBadlion(resource("badlion-data.json"))

        assertEquals(setOf("damagetint.json", "fullbright.json"), patches.configs())
        assertEquals(JsonPrimitive(true), patches.of("fullbright.json").values["enable"])
    }

    @Test
    fun `norisk names are matched regardless of spacing and case`() {
        val modules = resource("norisk-profile.json")["modules"] as JsonArray
        val patches = ClientFeatureTranslator.fromNoRisk(modules)

        assertEquals(setOf("fullbright.json", "countdown.json"), patches.configs())
        assertEquals("NoRisk TNT_TIMER · TNT timer", patches.of("countdown.json").source)
    }
}
