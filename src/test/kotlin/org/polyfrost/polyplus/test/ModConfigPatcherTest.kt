package org.polyfrost.polyplus.test

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.compat.ModConfigPatcher

class ModConfigPatcherTest {

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `flat keys are replaced and others kept`() {
        val current = parse("""{"isEnabled": false, "toggleSprint": false, "flyBoostAmount": 4.0}""")
        val patched = ModConfigPatcher.patched(
            current,
            mapOf("isEnabled" to JsonPrimitive(true), "toggleSprint" to JsonPrimitive(true)),
        )
        assertEquals(
            parse("""{"isEnabled": true, "toggleSprint": true, "flyBoostAmount": 4.0}"""),
            patched,
        )
    }

    @Test
    fun `dot paths reach into nested objects and create missing ones`() {
        val current = parse("""{"droppedItem": {"speed": 1.0, "scale": 2.0}}""")
        val patched = ModConfigPatcher.patched(
            current,
            mapOf("droppedItem.speed" to JsonPrimitive(3.0), "primary.lineWidth" to JsonPrimitive(4)),
        )
        assertEquals(
            parse("""{"droppedItem": {"speed": 3.0, "scale": 2.0}, "primary": {"lineWidth": 4}}"""),
            patched,
        )
    }

    @Test
    fun `oneconfig class wrappers keep their class`() {
        val current = parse(
            """{"mode": {"class": "a.b.Mode", "value": "OLD"}, "color": {"class": "a.b.PolyColor", "value": [0, 0, 0, 255]}}""",
        )
        val red = JsonArray(listOf(255, 0, 0, 255).map(::JsonPrimitive))
        val patched = ModConfigPatcher.patched(current, mapOf("mode" to JsonPrimitive("NEW"), "color" to red))
        assertEquals(
            parse(
                """{"mode": {"class": "a.b.Mode", "value": "NEW"}, "color": {"class": "a.b.PolyColor", "value": [255, 0, 0, 255]}}""",
            ),
            patched,
        )
    }

    @Test
    fun `a missing config is built from the patch alone`() {
        val patched = ModConfigPatcher.patched(JsonObject(emptyMap()), mapOf("isEnabled" to JsonPrimitive(true)))
        assertEquals(parse("""{"isEnabled": true}"""), patched)
    }
}
