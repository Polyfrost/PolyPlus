package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.MainMenuChoice
import org.polyfrost.polyplus.client.MainMenuFpsLimitMode
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.lobby.LobbyTime
import java.nio.file.Files

class ConfigSplitMigrationTest {

    private companion object {
        val LEGACY_CONFIG = """
            {
                "useVanillaMainMenu": true,
                "hideMainMenuSocial": true,
                "hideMainMenuQuickplay": true,
                "mainMenuFpsLimitMode": {
                    "class": "org.polyfrost.polyplus.client.MainMenuFpsLimitMode",
                    "value": "CUSTOM"
                },
                "lobbyTime": "not a time at all",
                "mainMenuFpsLimit": 120,
                "panoramaInAllMenus": false,
                "customPanorama": false,
                "hideHeadCosmeticsWithHelmet": true,
                "hideFeetCosmeticsWithBoots": false,
                "showChatEmoji": false
            }
        """.trimIndent()

        @JvmStatic
        @BeforeAll
        fun migrate() {
            val folder = ConfigManager.active().folder
            Files.createDirectories(folder)
            Files.write(folder.resolve("${PolyPlusConstants.ID}.json"), LEGACY_CONFIG.toByteArray())
            Files.deleteIfExists(folder.resolve("${PolyPlusConstants.ID}-mainmenu.json"))
            Files.deleteIfExists(folder.resolve("${PolyPlusConstants.ID}-cosmetics.json"))
            Files.deleteIfExists(folder.resolve("${PolyPlusConstants.ID}-lobby.json"))

            PolyPlusMainMenuConfig.preload()
            PolyPlusCosmeticsConfig.preload()
        }
    }

    @Test
    fun `main menu options are carried over`() {
        assertEquals(MainMenuChoice.VANILLA, PolyPlusMainMenuConfig.mainMenu)
        assertTrue(PolyPlusMainMenuConfig.hideMainMenuSocial)
        assertTrue(PolyPlusMainMenuConfig.hideMainMenuQuickplay)
        assertEquals(MainMenuFpsLimitMode.CUSTOM, PolyPlusMainMenuConfig.mainMenuFpsLimitMode)
        assertEquals(120, PolyPlusMainMenuConfig.mainMenuFpsLimit)
        assertFalse(PolyPlusMainMenuConfig.panoramaInAllMenus)
        assertFalse(PolyPlusMainMenuConfig.customPanorama)
    }

    @Test
    fun `cosmetics options are carried over`() {
        assertTrue(PolyPlusCosmeticsConfig.hideHeadCosmeticsWithHelmet)
        assertFalse(PolyPlusCosmeticsConfig.hideFeetCosmeticsWithBoots)
    }

    @Test
    fun `options missing from the legacy config keep their default`() {
        assertFalse(PolyPlusMainMenuConfig.hideMainMenuRealms)
        assertFalse(PolyPlusMainMenuConfig.hideMainMenuModButtons)
    }

    /** One value the split config cannot read must not cost the options copied after it. */
    @Test
    fun `an unreadable value does not abort the rest of the migration`() {
        assertEquals(LobbyTime.CAPTURED, PolyPlusLobbyConfig.lobbyTime)
        assertFalse(PolyPlusMainMenuConfig.panoramaInAllMenus)
    }

    @Test
    fun `the migration only runs once`() {
        assertTrue(PolyPlusMainMenuConfig.migratedFromLegacyConfig)
        assertTrue(PolyPlusCosmeticsConfig.migratedFromLegacyConfig)

        val folder = ConfigManager.active().folder
        assertTrue(Files.exists(folder.resolve("${PolyPlusConstants.ID}-mainmenu.json")))
        assertTrue(Files.exists(folder.resolve("${PolyPlusConstants.ID}-cosmetics.json")))
    }
}
