package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.compat.ClientConfigSync
import org.polyfrost.polyplus.compat.GameOptionsTranslator
import java.nio.file.Files
import java.nio.file.Path

class GameOptionsImportTest {
    private fun convert(key: String, value: String, legacy: Boolean) = GameOptionsTranslator.entry(key, value, legacy)?.second

    @Test
    fun `keyboard and mouse bindings convert in both directions`() {
        val bindings = mapOf(
            "0" to "key.keyboard.unknown", "17" to "key.keyboard.w", "33" to "key.keyboard.f",
            "42" to "key.keyboard.left.shift", "29" to "key.keyboard.left.control",
            "56" to "key.keyboard.left.alt", "87" to "key.keyboard.f11", "88" to "key.keyboard.f12",
            "79" to "key.keyboard.keypad.1", "156" to "key.keyboard.keypad.enter",
            "200" to "key.keyboard.up", "211" to "key.keyboard.delete",
            "-100" to "key.mouse.left", "-99" to "key.mouse.right", "-98" to "key.mouse.middle", "-97" to "key.mouse.4",
        )
        for ((code, name) in bindings) {
            assertEquals(name, convert("key_key.attack", code, legacy = false))
            assertEquals(code, convert("key_key.attack", name, legacy = true))
            assertEquals(code, convert("key_key.attack", code, legacy = true))
            assertEquals(name, convert("key_key.attack", name, legacy = false))
        }
    }

    @Test
    fun `SDL uses the physical keypad period and application names`() {
        assertEquals("key.keyboard.keypad.period", GameOptionsTranslator.entry("key_key.attack", "83", legacy = false, sdlKeys = true)?.second)
        assertEquals("key.keyboard.application", GameOptionsTranslator.entry("key_key.attack", "221", legacy = false, sdlKeys = true)?.second)
        assertEquals("key.keyboard.keypad.decimal", convert("key_key.attack", "83", legacy = false))
        assertEquals("83", convert("key_key.attack", "key.keyboard.keypad.period", legacy = true))
        assertEquals("221", convert("key_key.attack", "key.keyboard.application", legacy = true))
    }
    @Test
    fun `unrepresentable keys are skipped rather than replacing working bindings`() {
        assertNull(convert("key_key.attack", "999", legacy = false))
        assertNull(convert("key_key.attack", "179", legacy = false))
        assertNull(convert("key_key.attack", "key.keyboard.f25", legacy = true))
        assertNull(convert("key_key.attack", "scancode.42", legacy = true))
        assertEquals("scancode.42", convert("key_key.attack", "scancode.42", legacy = false))
    }

    @Test
    fun `language and ambient occlusion use the destinations format`() {
        assertEquals("en_us", convert("lang", "en_US", legacy = false))
        assertEquals("pt_BR", convert("lang", "pt_br", legacy = true))
        assertEquals("false", convert("ao", "0", legacy = false))
        assertEquals("true", convert("ao", "1", legacy = false))
        assertEquals("true", convert("ao", "2", legacy = false))
        assertEquals("0", convert("ao", "false", legacy = true))
        assertEquals("2", convert("ao", "true", legacy = true))
    }

    @Test
    fun `modern cloud enum values remain JSON strings even when their names look boolean`() {
        for (mode in listOf("true", "false", "fast")) {
            assertEquals("\"$mode\"", convert("renderClouds", mode, legacy = false))
            assertEquals("\"$mode\"", convert("renderClouds", "\"$mode\"", legacy = false))
            assertEquals(mode, convert("renderClouds", "\"$mode\"", legacy = true))
        }
        assertNull(convert("renderClouds", "invalid", legacy = false))
    }

    @Test
    fun `ordinary options keep their values`() {
        assertEquals("0.45", convert("mouseSensitivity", "0.45", legacy = true))
        assertEquals("0.45", convert("mouseSensitivity", "0.45", legacy = false))
        assertEquals("0.0", convert("soundCategory_music", "0.0", legacy = false))
    }

    private fun translate(vararg entries: Pair<String, String>, legacy: Boolean = false, sdlKeys: Boolean = false) =
        GameOptionsTranslator.translate(entries.toList(), legacy, sdlKeys).toMap()

    @Test
    fun `graphics quality reaches the option each generation reads`() {
        val fromLegacy = translate("fancyGraphics" to "false")
        assertEquals("0", fromLegacy["graphicsMode"])
        assertEquals("\"custom\"", fromLegacy["graphicsPreset"])
        assertEquals("false", fromLegacy["cutoutLeaves"])
        assertEquals("5", fromLegacy["weatherRadius"])
        assertEquals("false", fromLegacy["vignette"])
        assertEquals("false", fromLegacy["improvedTransparency"])

        val fromFabulous = translate("graphicsMode" to "2")
        assertEquals("true", fromFabulous["cutoutLeaves"])
        assertEquals("10", fromFabulous["weatherRadius"])
        assertEquals("true", fromFabulous["improvedTransparency"])
        assertEquals("true", translate("graphicsMode" to "2", legacy = true)["fancyGraphics"])
        assertEquals("false", translate("graphicsMode" to "0", legacy = true)["fancyGraphics"])

        // A source that already has presets keeps them, and older destinations get the closest mode.
        val fromPreset = translate("graphicsPreset" to "\"fast\"", "cutoutLeaves" to "true")
        assertEquals("\"fast\"", fromPreset["graphicsPreset"])
        assertEquals("true", fromPreset["cutoutLeaves"])
        assertEquals("0", fromPreset["graphicsMode"])
        assertEquals("2", translate("graphicsPreset" to "\"custom\"", "improvedTransparency" to "true")["graphicsMode"])
        assertEquals("false", translate("graphicsPreset" to "\"custom\"", "cutoutLeaves" to "false", legacy = true)["fancyGraphics"])
        assertNull(translate("mouseSensitivity" to "0.5")["graphicsMode"])
    }

    @Test
    fun `renamed and reformatted modern options are upgraded and downgraded`() {
        assertEquals("\"never\"", translate("showNowPlayingToast" to "false")["musicToast"])
        assertEquals("\"pause_and_toast\"", translate("showNowPlayingToast" to "true")["musicToast"])
        assertEquals("false", translate("musicToast" to "\"pause\"")["showNowPlayingToast"])
        assertEquals("true", translate("musicToast" to "\"pause_and_toast\"")["showNowPlayingToast"])
        assertEquals("5", convert("menuBackgroundBlurriness", "0.5", legacy = false))
        assertEquals("7", convert("menuBackgroundBlurriness", "7", legacy = false))
        assertEquals("key.keyboard.f", translate("key_key.swapHands" to "key.keyboard.f")["key_key.swapOffhand"])
        assertEquals("1.0", convert("gamma", "1000.0", legacy = false))
        assertEquals("0.5", convert("gamma", "0.5", legacy = false))
        assertEquals("1000.0", convert("gamma", "1000.0", legacy = true))
    }

    @Test
    fun `key names follow the input backend of the source and destination`() {
        val key = "key_key.attack"
        // GLFW-era names into 26.3, which gave the same physical keys new names.
        assertEquals("key.keyboard.keypad.period", translate("version" to "3955", key to "key.keyboard.keypad.decimal", sdlKeys = true)[key])
        assertEquals("key.keyboard.application", translate(key to "key.keyboard.menu", sdlKeys = true)[key])
        assertNull(translate(key to "scancode.86", sdlKeys = true)[key])
        assertNull(translate(key to "key.keyboard.f25", sdlKeys = true)[key])
        // 26.3 names stay as they are on 26.3, and go back to the old names elsewhere.
        assertEquals("key.keyboard.menu", translate("version" to "5023", key to "key.keyboard.menu", sdlKeys = true)[key])
        assertEquals("key.keyboard.keypad.decimal", translate("version" to "5023", key to "key.keyboard.keypad.period")[key])
        assertEquals("221", translate("version" to "5023", key to "key.keyboard.application", legacy = true)[key])
        // Names older versions do not know would stop them loading the rest of the file.
        assertNull(translate("version" to "5023", key to "key.keyboard.lang1")[key])
        assertNull(translate("version" to "5023", key to "key.keyboard.menu")[key])
        assertNull(translate("version" to "5023", key to "key.keyboard.menu", legacy = true)[key])
        assertNull(translate(key to "key.keyboard.w:CONTROL")[key])
        assertNull(translate(key to "key.keyboard.w:CONTROL", sdlKeys = true)[key])
        assertEquals("key.mouse.9", translate(key to "key.mouse.9")[key])
    }

    @Test
    fun `options files tell a legacy profile from a modern one`() {
        val legacy = (1..30).map { "option$it:1" }
        assertEquals(ClientConfigSync.Generation.LEGACY, ClientConfigSync.optionsGeneration(legacy))
        assertEquals(ClientConfigSync.Generation.MODERN, ClientConfigSync.optionsGeneration(listOf("version:4903") + legacy))
        // A launcher's four-line stub says nothing about the game version.
        assertNull(ClientConfigSync.optionsGeneration(listOf("lang:en_us", "guiScale:2", "fov:0.0", "gamma:0.5")))
    }

    @Test
    fun `a client with both generations only offers the running one`() {
        val legacy = ClientConfigSync.Generation.LEGACY
        val modern = ClientConfigSync.Generation.MODERN
        val profiles = mapOf(
            ("NoRisk Client" to "legacy") to legacy,
            ("NoRisk Client" to "new") to modern,
            ("NoRisk Client" to "shared") to null,
            // Only one kind, so it stays and its options are converted on import.
            ("Modrinth" to "Fabulously Optimized") to modern,
            ("Lunar Client" to "Default") to null,
        )
        assertEquals(setOf("NoRisk Client" to "new"), ClientConfigSync.otherGenerationProfiles(profiles, legacy))
        assertEquals(setOf("NoRisk Client" to "legacy"), ClientConfigSync.otherGenerationProfiles(profiles, modern))
    }

    @Test
    fun `merging Lunar JSON converts keys without copying its data version or installation state`(@TempDir directory: Path) {
        val lunar = directory.resolve("optionsLC.txt")
        Files.writeString(lunar, """{"version": 1, "key_key.forward": 17, "renderClouds": false, "ao": 0, "mouseSensitivity": 0.4, "resourcePacks": ["missing.zip"]}""")
        val target = directory.resolve("options.txt")
        Files.writeString(target, "version:5023\nkey_key.forward:0\nresourcePacks:[]\n")
        ClientConfigSync.mergeOptions(listOf(lunar), target)
        val result = Files.readAllLines(target)
        assertTrue("version:5023" in result)
        assertTrue("resourcePacks:[]" in result)
        assertTrue("mouseSensitivity:0.4" in result)
        //? if = 1.8.9 {
        /*assertTrue("key_key.forward:17" in result)
        assertTrue("renderClouds:false" in result)
        assertTrue("ao:0" in result)
        *///?} else {
        assertTrue("key_key.forward:key.keyboard.w" in result)
        assertTrue("renderClouds:\"false\"" in result)
        assertTrue("ao:false" in result)
        //?}
    }
}
