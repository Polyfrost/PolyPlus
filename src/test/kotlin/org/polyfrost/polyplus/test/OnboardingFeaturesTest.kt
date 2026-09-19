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
import org.polyfrost.polyplus.client.gui.gammaPreviewPaths
import org.polyfrost.polyplus.client.gui.modGuidePaths

class OnboardingFeaturesTest {
    private val everyCard = OnboardingFeatures.ModCard.entries.toList()

    @Test
    fun `a first run is offered every card its mods support`() {
        assertEquals(everyCard, OnboardingFeatures.newModCards(completedVersion = 0, available = everyCard))
    }

    @Test
    fun `a returning user only walks the cards added since they last finished`() {
        assertEquals(
            listOf(OnboardingFeatures.ModCard.GAMMA),
            OnboardingFeatures.newModCards(completedVersion = 1, available = everyCard),
        )
    }

    @Test
    fun `reviewed or unavailable mod settings do not reopen onboarding`() {
        assertTrue(
            OnboardingFeatures.newModCards(
                completedVersion = OnboardingFeatures.MOD_SETTINGS_VERSION,
                available = everyCard,
            ).isEmpty(),
        )
        assertTrue(OnboardingFeatures.newModCards(completedVersion = 0, available = emptyList()).isEmpty())
    }

    @Test
    fun `every card is tagged with a version that has actually shipped`() {
        everyCard.forEach { card ->
            assertTrue(
                card.introducedIn in 1..OnboardingFeatures.MOD_SETTINGS_VERSION,
                "${card.name} claims version ${card.introducedIn}",
            )
        }
    }

    @Test
    fun `a cardless mods page does not bury the cards of mods installed later`() {
        val completed = OnboardingFeatures.completedModSettingsVersion(completedVersion = 0, available = emptyList())

        assertEquals(0, completed)
        assertEquals(everyCard, OnboardingFeatures.newModCards(completed, available = everyCard))
        assertEquals(
            OnboardingFeatures.MOD_SETTINGS_VERSION,
            OnboardingFeatures.completedModSettingsVersion(completedVersion = 0, available = everyCard),
        )
    }

    @Test
    fun `a run that never offered the newest card still offers it later`() {
        val olderCards = everyCard.filter { it.introducedIn < OnboardingFeatures.MOD_SETTINGS_VERSION }

        val completed = OnboardingFeatures.completedModSettingsVersion(completedVersion = 0, available = olderCards)

        assertEquals(1, completed)
        assertEquals(
            listOf(OnboardingFeatures.ModCard.GAMMA),
            OnboardingFeatures.newModCards(completed, available = everyCard),
        )
    }

    @Test
    fun `a card that was available but unreadable still counts as walked`() {
        val completed = OnboardingFeatures.completedModSettingsVersion(completedVersion = 0, available = everyCard)

        assertEquals(OnboardingFeatures.MOD_SETTINGS_VERSION, completed)
        assertTrue(OnboardingFeatures.newModCards(completed, available = everyCard).isEmpty())
    }

    @Test
    fun `a newest card that is unavailable does not count as walked`() {
        val withoutGamma = everyCard - OnboardingFeatures.ModCard.GAMMA

        val completed = OnboardingFeatures.completedModSettingsVersion(completedVersion = 0, available = withoutGamma)

        assertEquals(1, completed)
        assertEquals(
            listOf(OnboardingFeatures.ModCard.GAMMA),
            OnboardingFeatures.newModCards(completed, available = everyCard),
        )
    }

    @Test
    fun `an offered card settles unless the user moved it`() {
        val card = OnboardingFeatures.ModCard.CAPES

        assertFalse(settledAfterRun(card, offered = everyCard, available = everyCard, touched = setOf(card)))
        assertTrue(settledAfterRun(card, offered = everyCard, available = everyCard, touched = emptySet()))
    }

    @Test
    fun `a card whose mod is absent was never asked`() {
        val card = OnboardingFeatures.ModCard.CAPES

        listOf(false, true).forEach { current ->
            assertTrue(
                settledAfterRun(card, offered = emptyList(), available = everyCard - card, current = current),
                "current=$current",
            )
        }
    }

    @Test
    fun `a present card this run did not offer keeps its outstanding choice`() {
        val card = OnboardingFeatures.ModCard.CAPES

        assertFalse(settledAfterRun(card, offered = everyCard - card, available = everyCard, current = false))
        assertTrue(settledAfterRun(card, offered = everyCard - card, available = everyCard, current = true))
    }

    @Test
    fun `a card too broken to render on the run that introduces it is not left armed`() {
        val card = OnboardingFeatures.ModCard.CAPES

        assertTrue(
            settledAfterRun(
                card,
                offered = everyCard - card,
                available = everyCard,
                current = false,
                completedVersion = card.introducedIn - 1,
            ),
        )
    }

    private fun settledAfterRun(
        card: OnboardingFeatures.ModCard,
        offered: List<OnboardingFeatures.ModCard>,
        available: List<OnboardingFeatures.ModCard>,
        touched: Set<OnboardingFeatures.ModCard> = emptySet(),
        current: Boolean = false,
        completedVersion: Int = OnboardingFeatures.MOD_SETTINGS_VERSION,
    ) = OnboardingFeatures.settledAfterRun(card, offered, available, touched, current, completedVersion)

    @Test
    fun `every better grass preview path resolves`() {
        OnboardingFeatures.BETTER_GRASS_MODES.forEach { mode ->
            val path = betterGrassPreviewPath(mode)
            assertNotNull(javaClass.classLoader.getResource(path), path)
        }
    }

    @Test
    fun `every mod guide screenshot resolves`() {
        assertEquals(5, modGuidePaths.size)
        modGuidePaths.forEach { path ->
            assertNotNull(javaClass.classLoader.getResource(path), path)
        }
    }

    @Test
    fun `a guide is owed once, for a mod that is off its default and already onboarded`() {
        val card = OnboardingFeatures.ModCard.MOUNT

        assertTrue(guideNeeded(card))
        assertFalse(guideNeeded(card, movedFromDefault = false), "nothing was changed")
        assertFalse(guideNeeded(card, shown = OnboardingFeatures.guideFlag(card)), "shown twice")
        assertFalse(guideNeeded(card, available = false), "the mod is gone")
        assertFalse(guideNeeded(card, completedVersion = 0), "the card is still ahead of them")
    }

    @Test
    fun `every guided card claims its own bit and no other`() {
        val flags = OnboardingFeatures.guidedCards.map(OnboardingFeatures::guideFlag)

        assertEquals(flags.distinct(), flags)
        assertTrue(flags.all { it != 0 && it and (it - 1) == 0 }, "flags are $flags")
        (OnboardingFeatures.ModCard.entries - OnboardingFeatures.guidedCards.toSet()).forEach { card ->
            assertEquals(0, OnboardingFeatures.guideFlag(card), card.name)
        }
    }

    private fun guideNeeded(
        card: OnboardingFeatures.ModCard,
        shown: Int = 0,
        completedVersion: Int = OnboardingFeatures.MOD_SETTINGS_VERSION,
        available: Boolean = true,
        movedFromDefault: Boolean = true,
    ) = OnboardingFeatures.guideNeeded(card, shown, completedVersion, available, movedFromDefault)

    @Test
    fun `both fullbright preview frames resolve`() {
        gammaPreviewPaths.forEach { path ->
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
