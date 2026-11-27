package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.gui.wardrobe.TryAction
import org.polyfrost.polyplus.client.gui.wardrobe.TryInfo
import org.polyfrost.polyplus.client.network.http.responses.BonusRef

class LobbyTryOnScopeTest {
    @Test
    fun `bonuses and overrides apply only inside the lobby scope`() {
        val tryOn = PreviewTryOn()
        tryOn.bonuses = listOf(BonusRef(7))
        tryOn.toggle(PlayerSignals.IS_IN_COMBAT)
        assertFalse(PreviewTryOn.inLobbyScope())
        assertNull(PreviewTryOn.lobbyBonuses())
        assertEquals(0, PreviewTryOn.lobbyOverrideMask())
        PreviewTryOn.withLobby(tryOn) {
            assertTrue(PreviewTryOn.inLobbyScope())
            assertEquals(listOf(BonusRef(7)), PreviewTryOn.lobbyBonuses())
            assertEquals(1, PreviewTryOn.lobbyOverrideMask())
        }
        assertFalse(PreviewTryOn.inLobbyScope())
        assertNull(PreviewTryOn.lobbyBonuses())
        assertEquals(0, PreviewTryOn.lobbyOverrideMask())
    }

    @Test
    fun `the scope is not seen from another thread`() {
        val tryOn = PreviewTryOn()
        var seenElsewhere = true
        PreviewTryOn.withLobby(tryOn) {
            val other = Thread { seenElsewhere = PreviewTryOn.inLobbyScope() }
            other.start()
            other.join()
        }
        assertFalse(seenElsewhere)
    }

    @Test
    fun `wear again plays once for the tried pieces drawn in one frame`() {
        val tryOn = PreviewTryOn()
        assertFalse(tryOn.lobbyEquip(3, 1))
        tryOn.wearAgain(setOf(1, 2))
        assertTrue(tryOn.lobbyEquip(4, 1))
        assertTrue(tryOn.lobbyEquip(4, 2))
        assertFalse(tryOn.lobbyEquip(4, 9))
        assertFalse(tryOn.lobbyEquip(5, 1))
        tryOn.wearAgain(setOf(1))
        tryOn.reset()
        assertFalse(tryOn.lobbyEquip(6, 1))
    }

    @Test
    fun `wear again in the wardrobe preview reaches only the tried pieces`() {
        val tryOn = PreviewTryOn()
        val previous = PreviewTryOn.current
        PreviewTryOn.current = tryOn
        try {
            tryOn.wearAgain(setOf(1))
            tryOn.beginFrame()
            assertNotEquals(0, PreviewTryOn.equipEvents(1))
            assertEquals(0, PreviewTryOn.equipEvents(2))
            tryOn.beginFrame()
            assertEquals(0, PreviewTryOn.equipEvents(1))
        } finally {
            PreviewTryOn.current = previous
        }
    }

    @Test
    fun `wear again in the lobby keeps the equip control`() {
        val info = TryInfo(listOf(TryAction.WearAgain, TryAction.Jump, TryAction.Effect("join", "Join")), emptyList())
        assertEquals(listOf("equip", "jump"), info.withoutEffects().actions.map { it.key })
    }

    @Test
    fun `no try-on leaves the caller unscoped`() {
        val result = PreviewTryOn.withLobby(null) { PreviewTryOn.inLobbyScope() }
        assertFalse(result)
    }
}
