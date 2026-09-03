package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.compat.ModConfigDefaults
import org.polyfrost.polyplus.compat.ModConfigDefaults.Default
import java.nio.file.Path
import kotlin.io.path.writeText

class ModConfigDefaultsTest {

    private companion object {
        val SHOW_TOASTS = Default("fastquit", "fastquit.toml", "showToasts", false)
        val NESTED = Default("controlify", "controlify.json", "global.out_of_focus_input", true)
        val BLOCK_OVERLAY_FACE =
            Default("simpleblockoverlay", "simpleblockoverlay.json", "disableOverlay", true, newInstallsOnly = true)
    }

    @Test
    fun `a missing flat key is appended`() {
        assertEquals(
            "saveOnQuit = true\nshowToasts = false\n",
            ModConfigDefaults.mergeFlat("saveOnQuit = true\n", listOf(SHOW_TOASTS), toml = false),
        )
    }

    @Test
    fun `an existing flat key is rewritten in place keeping its indent`() {
        assertEquals(
            "\tshowToasts = false\nsaveOnQuit = true\n",
            ModConfigDefaults.mergeFlat("\tshowToasts = true\nsaveOnQuit = true\n", listOf(SHOW_TOASTS), toml = false),
        )
    }

    @Test
    fun `a toml key lands above the first table so it stays top level`() {
        assertEquals(
            "saveOnQuit = true\nshowToasts = false\n[toasts]\ncolor = \"red\"\n",
            ModConfigDefaults.mergeFlat(
                "saveOnQuit = true\n[toasts]\ncolor = \"red\"\n",
                listOf(SHOW_TOASTS),
                toml = true,
            ),
        )
    }

    @Test
    fun `a json key is merged without dropping its siblings`() {
        val merged = ModConfigDefaults.mergeJson("""{"global":{"vibrate":false},"controllers":{}}""", listOf(NESTED))
        assertTrue(Regex(""""vibrate":\s*false""").containsMatchIn(merged), merged)
        assertTrue(Regex(""""out_of_focus_input":\s*true""").containsMatchIn(merged), merged)
        assertTrue(Regex(""""controllers":\s*\{""").containsMatchIn(merged), merged)
    }

    @Test
    fun `a json key builds the objects it needs on an empty config`() {
        val merged = ModConfigDefaults.mergeJson("", ModConfigDefaults.DEFAULTS.filter { it.file == "dynamic_fps.json" })
        assertTrue(Regex(""""frame_rate_target":\s*10""").containsMatchIn(merged), merged)
        assertTrue(Regex(""""master":\s*1\.0""").containsMatchIn(merged), merged)
        assertTrue(Regex(""""run_garbage_collector":\s*true""").containsMatchIn(merged), merged)
    }

    @Test
    fun `retuning a default gives it a new marker so it reaches installs that ran the old one`() {
        val quiet = Default("fastquit", "fastquit.toml", "showToasts", false)
        val loud = Default("fastquit", "fastquit.toml", "showToasts", true)
        assertNotEquals(quiet.id, loud.id)
        assertEquals(quiet.id, Default("fastquit", "fastquit.toml", "showToasts", false).id)
    }

    @Test
    fun `a wrapped array value is not mistaken for the start of a table`() {
        assertEquals(
            "whitelist = [\n  \"a\",\n]\nshowToasts = false\n[toasts]\n",
            ModConfigDefaults.mergeFlat(
                "whitelist = [\n  \"a\",\n]\n[toasts]\n",
                listOf(SHOW_TOASTS),
                toml = true,
            ),
        )
    }

    @Test
    fun `a new-installs-only default is skipped once the mod owns a config`(@TempDir configDir: Path) {
        assertTrue(ModConfigDefaults.wanted(BLOCK_OVERLAY_FACE, configDir))
        configDir.resolve(BLOCK_OVERLAY_FACE.file).writeText("{}")
        assertFalse(ModConfigDefaults.wanted(BLOCK_OVERLAY_FACE, configDir))
    }

    @Test
    fun `an ordinary default still applies over an existing config`(@TempDir configDir: Path) {
        configDir.resolve(SHOW_TOASTS.file).writeText("showToasts = true\n")
        assertTrue(ModConfigDefaults.wanted(SHOW_TOASTS, configDir))
    }

    @Test
    fun `the block overlay face is turned off for new installs only`() {
        val default = ModConfigDefaults.DEFAULTS.single { it.modId == "simpleblockoverlay" }
        assertEquals("disableOverlay", default.key)
        assertEquals(true, default.value)
        assertTrue(default.newInstallsOnly)
        assertTrue(
            Regex(""""disableOverlay":\s*true""").containsMatchIn(ModConfigDefaults.mergeJson("", listOf(default))),
        )
    }

    @Test
    fun `no early default lives in a json file`() {
        assertTrue(ModConfigDefaults.everyEarlyDefaultIsFlat())
    }

    @Test
    fun `every default renders in its own format`() {
        ModConfigDefaults.DEFAULTS.groupBy(Default::file).forEach { (file, defaults) ->
            val merged =
                if (file.endsWith(".json")) ModConfigDefaults.mergeJson("", defaults)
                else ModConfigDefaults.mergeFlat("", defaults, toml = file.endsWith(".toml"))
            assertTrue(merged.isNotBlank(), file)
        }
    }

}
