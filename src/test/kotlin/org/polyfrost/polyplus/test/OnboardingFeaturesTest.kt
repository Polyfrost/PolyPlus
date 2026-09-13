package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.betterGrassPreviewPath

class OnboardingFeaturesTest {
    @Test
    fun `existing users see newly added mod settings`() {
        assertTrue(OnboardingFeatures.shouldShowModSettings(completedVersion = 0, availableCards = 1))
    }

    @Test
    fun `reviewed or unavailable mod settings do not reopen onboarding`() {
        assertFalse(
            OnboardingFeatures.shouldShowModSettings(
                completedVersion = OnboardingFeatures.MOD_SETTINGS_VERSION,
                availableCards = 1,
            ),
        )
        assertFalse(OnboardingFeatures.shouldShowModSettings(completedVersion = 0, availableCards = 0))
    }

    @Test
    fun `a cardless mods page does not bury the cards of mods installed later`() {
        val completed = OnboardingFeatures.completedModSettingsVersion(completedVersion = 0, availableCards = 0)

        assertEquals(0, completed)
        assertTrue(OnboardingFeatures.shouldShowModSettings(completed, availableCards = 1))
        assertEquals(
            OnboardingFeatures.MOD_SETTINGS_VERSION,
            OnboardingFeatures.completedModSettingsVersion(completedVersion = 0, availableCards = 1),
        )
    }

    @Test
    fun `every better grass preview path resolves`() {
        OnboardingFeatures.BETTER_GRASS_MODES.forEach { mode ->
            val path = betterGrassPreviewPath(mode)
            assertNotNull(javaClass.classLoader.getResource(path), path)
        }
    }

    @Test
    fun `an option is only offered when it is there and writable`() {
        val config = ModernOverlayConfig::class.java.name
        assertTrue(OnboardingFeatures.hasFloatingField(config, "fireOverlayHeight"))
        assertFalse(OnboardingFeatures.hasFloatingField(config, "saved"))
        assertFalse(OnboardingFeatures.hasFloatingField(config, "noSuchOption"))
        assertFalse(OnboardingFeatures.hasFloatingField("no.such.Config", "fireOverlayHeight"))
    }

    @Test
    fun `the wavey capes toggle only writes what on and off actually mean`() {
        assertEquals("VANILLA", OnboardingFeatures.waveyCapeMovementWrite(false, "DUNGEONS"))
        assertEquals("VANILLA", OnboardingFeatures.waveyCapeMovementWrite(false, "VANILLA"))

        assertEquals("BASIC_SIMULATION_3D", OnboardingFeatures.waveyCapeMovementWrite(true, "VANILLA"))
        assertNull(OnboardingFeatures.waveyCapeMovementWrite(true, "DUNGEONS"))
        assertNull(OnboardingFeatures.waveyCapeMovementWrite(true, "BASIC_SIMULATION"))
    }

    @Test
    fun `overlay tweaks legacy config handler remains supported`() {
        LegacyOverlayHandler.saved = false

        val access = OnboardingFeatures.overlayTweaksAccess(LegacyOverlayConfig::class.java)

        assertSame(LegacyOverlayHandler.instance, access.instance)
        access.save()
        assertTrue(LegacyOverlayHandler.saved)
    }

    @Test
    fun `overlay tweaks modern static instance and float fields are supported`() {
        ModernOverlayConfig.saved = false
        ModernOverlayConfig.fireOverlayHeight = 0f

        val access = OnboardingFeatures.overlayTweaksAccess(ModernOverlayConfig::class.java)
        OnboardingFeatures.writeFloatingField(access.instance, "fireOverlayHeight", -0.25)

        assertSame(ModernOverlayConfig.INSTANCE, access.instance)
        assertEquals(-0.25, OnboardingFeatures.readFloatingField(access.instance, "fireOverlayHeight"), 0.0001)
        access.save()
        assertTrue(ModernOverlayConfig.saved)
    }
}

class LegacyOverlayConfig {
    companion object {
        @JvmField
        val CONFIG = LegacyOverlayHandler
    }
}

object LegacyOverlayHandler {
    @JvmField
    val instance = LegacyOverlayConfig()

    @JvmField
    var saved = false

    @JvmStatic
    fun instance(): LegacyOverlayConfig = instance

    @JvmStatic
    fun save() {
        saved = true
    }
}

class ModernOverlayConfig {
    fun save() {
        saved = true
    }

    companion object {
        @JvmField
        val INSTANCE = ModernOverlayConfig()

        @JvmField
        var fireOverlayHeight = 0f

        @JvmField
        var saved = false
    }
}
