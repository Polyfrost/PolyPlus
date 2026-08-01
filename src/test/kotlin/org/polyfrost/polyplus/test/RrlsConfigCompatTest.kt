package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.compat.RrlsConfigCompat
import org.polyfrost.polyplus.compat.RrlsConfigCompat.Format

/**
 * Covers the translation between the two `config/rrls.toml` layouts Remove Reloading Screen uses.
 *
 * [LEGACY_CONFIG] is what Cloth Config's toml4j writer produces on 1.21.1 and older;
 * [MODERN_CONFIG] is what NightConfig produces on 1.21.4 and newer, `nan` and all.
 */
class RrlsConfigCompatTest {

    private companion object {
        val LEGACY_CONFIG = """
            hideOverlays = "LOADING"
            rgbProgress = true
            blockOverlay = false
            miniRender = false
            enableScissor = true
            type = "TEXT"
            reloadText = "Reloading # now"
            resetResources = false
            reInitScreen = true
            removeOverlayAtEnd = false
            earlyPackStatusSend = true
            doubleLoad = "LOAD"
            animationSpeed = 750.0
        """.trimIndent()

        val MODERN_CONFIG = """
            [global]
            	#Allowed Values: ALL, LOADING, RELOADING, NONE
            	hideOverlays = "LOADING"
            	blockOverlay = false
            	miniRender = false
            	enableScissor = true

            [splash]
            	#Allowed Values: PROGRESS, TEXT, TEXT_WITH_BACKGROUND, NONE
            	type = "TEXT"
            	rgbProgress = true
            	reloadText = "Reloading # now"
            	animationSpeed = 750.0

            [interpolation]
            	interpolateProgress = true
            	interpolateAtEnd = true
            	ease = "OUTBOUNCE"
            	easingArg = nan

            [other]
            	resetResources = false
            	reInitScreen = true
            	earlyPackStatusSend = true
            	#Allowed Values: FORCE_LOAD, LOAD, NONE
            	doubleLoad = "LOAD"

            [platform]
            	skipForgeOverlay = true
        """.trimIndent()
    }

    @Test
    fun `detects each layout`() {
        assertEquals(Format.LEGACY, RrlsConfigCompat.detectFormat(LEGACY_CONFIG))
        assertEquals(Format.MODERN, RrlsConfigCompat.detectFormat(MODERN_CONFIG))
        assertNull(RrlsConfigCompat.detectFormat(""))
        assertNull(RrlsConfigCompat.detectFormat("# nothing but a comment"))
    }

    @Test
    fun `each layout is only usable by its own build`() {
        assertTrue(RrlsConfigCompat.isUsableBy(LEGACY_CONFIG, Format.LEGACY))
        assertFalse(RrlsConfigCompat.isUsableBy(LEGACY_CONFIG, Format.MODERN))
        assertTrue(RrlsConfigCompat.isUsableBy(MODERN_CONFIG, Format.MODERN))
        assertFalse(RrlsConfigCompat.isUsableBy(MODERN_CONFIG, Format.LEGACY))
    }

    @Test
    fun `rejects literals the legacy parser chokes on`() {
        val poisoned = "$LEGACY_CONFIG\neasingArg = nan"
        assertFalse(RrlsConfigCompat.isUsableBy(poisoned, Format.LEGACY))
    }

    @Test
    fun `rejects enum constants the legacy build does not have`() {
        val unknown = LEGACY_CONFIG.replace("""type = "TEXT"""", """type = "NONE"""")
        assertFalse(RrlsConfigCompat.isUsableBy(unknown, Format.LEGACY))
    }

    @Test
    fun `converts the modern layout to a legacy one the old build accepts`() {
        val converted = RrlsConfigCompat.translate(MODERN_CONFIG, Format.LEGACY, "")

        assertEquals(
            """
                hideOverlays = "LOADING"
                blockOverlay = false
                miniRender = false
                enableScissor = true
                type = "TEXT"
                rgbProgress = true
                reloadText = "Reloading # now"
                animationSpeed = 750.0
                resetResources = false
                reInitScreen = true
                earlyPackStatusSend = true
                doubleLoad = "LOAD"
            """.trimIndent() + "\n",
            converted,
        )
        assertTrue(RrlsConfigCompat.isUsableBy(converted, Format.LEGACY))
    }

    @Test
    fun `converts the legacy layout to a modern one`() {
        val converted = RrlsConfigCompat.translate(LEGACY_CONFIG, Format.MODERN, "")

        assertEquals(
            """
                [global]
                	hideOverlays = "LOADING"
                	blockOverlay = false
                	miniRender = false
                	enableScissor = true

                [splash]
                	type = "TEXT"
                	rgbProgress = true
                	reloadText = "Reloading # now"
                	animationSpeed = 750.0

                [other]
                	resetResources = false
                	reInitScreen = true
                	earlyPackStatusSend = true
                	doubleLoad = "LOAD"
            """.trimIndent() + "\n",
            converted,
        )
        assertTrue(RrlsConfigCompat.isUsableBy(converted, Format.MODERN))
    }

    @Test
    fun `restores settings the legacy layout cannot hold`() {
        val converted = RrlsConfigCompat.translate(LEGACY_CONFIG, Format.MODERN, MODERN_CONFIG)

        assertTrue(converted.contains("""ease = "OUTBOUNCE""""))
        assertTrue(converted.contains("easingArg = nan"))
        assertTrue(converted.contains("skipForgeOverlay = true"))
        assertTrue(RrlsConfigCompat.isUsableBy(converted, Format.MODERN))
    }

    @Test
    fun `restores settings the modern layout cannot hold`() {
        val converted = RrlsConfigCompat.translate(MODERN_CONFIG, Format.LEGACY, LEGACY_CONFIG)

        assertTrue(converted.contains("removeOverlayAtEnd = false"))
        assertTrue(RrlsConfigCompat.isUsableBy(converted, Format.LEGACY))
    }

    @Test
    fun `carries changed settings over the preserved backup`() {
        val edited = MODERN_CONFIG.replace("""hideOverlays = "LOADING"""", """hideOverlays = "NONE"""")
        val converted = RrlsConfigCompat.translate(edited, Format.LEGACY, LEGACY_CONFIG)

        assertTrue(converted.contains("""hideOverlays = "NONE""""))
    }

    @Test
    fun `drops values the target build would reject`() {
        val edited = MODERN_CONFIG
            .replace("""type = "TEXT"""", """type = "NONE"""")
            .replace("animationSpeed = 750.0", "animationSpeed = nan")
        val converted = RrlsConfigCompat.translate(edited, Format.LEGACY, "")

        assertFalse(converted.contains("type"))
        assertFalse(converted.contains("animationSpeed"))
        assertTrue(RrlsConfigCompat.isUsableBy(converted, Format.LEGACY))
    }

    @Test
    fun `writes nothing when there is nothing to write`() {
        assertEquals("", RrlsConfigCompat.translate("", Format.LEGACY, ""))
        assertEquals("", RrlsConfigCompat.translate("# only a comment", Format.MODERN, ""))
    }

    @Test
    fun `round trips without drift`() {
        val legacy = RrlsConfigCompat.translate(MODERN_CONFIG, Format.LEGACY, "")
        val modern = RrlsConfigCompat.translate(legacy, Format.MODERN, MODERN_CONFIG)

        assertEquals(MODERN_CONFIG.lines().filterNot { it.trim().startsWith('#') }.joinToString("\n") { it.trim() },
            modern.lines().joinToString("\n") { it.trim() }.trimEnd())
    }

    @Test
    fun `keeps escapes and comment markers inside strings`() {
        val edited = LEGACY_CONFIG.replace(
            """reloadText = "Reloading # now"""",
            """reloadText = "a\tb # \"c\" é"""",
        )
        val converted = RrlsConfigCompat.translate(edited, Format.MODERN, "")

        assertTrue(converted.contains("""reloadText = "a\tb # \"c\" é""""))
    }
}
