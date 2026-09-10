package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.eos.EosP2PSocketId
import org.polyfrost.polyplus.client.network.eos.EosProductUserId
import org.polyfrost.polyplus.client.network.p2p.EosP2PAddress
import org.polyfrost.polyplus.client.network.p2p.P2PConnectionContext
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager

class P2PConnectionArmTest {
    private val host = EosProductUserId("0002eac")
    private val socket = EosP2PSocketId("polyplus-session")
    private val target = P2PSessionManager.JoinTarget(host, socket)

    @Test
    fun `an armed join redirects the connection it was armed for`() {
        P2PConnectionContext.setPendingJoin(target)
        assertTrue(P2PConnectionContext.hasPendingJoin(), "the channel swap must see the arm")
        assertEquals(
            EosP2PAddress(host, socket),
            P2PConnectionContext.consumeAddressOverride(),
            "the connect must get the P2P address",
        )
        assertFalse(P2PConnectionContext.hasPendingJoin(), "consuming must disarm")
    }

    @Test
    fun `a join the connect never picked up stops hijacking later servers`() {
        P2PConnectionContext.setPendingJoin(target)
        P2PConnectionContext.clearPendingJoin()

        assertFalse(P2PConnectionContext.hasPendingJoin(), "the ordinary connect must not get an EOS channel")
        assertNull(P2PConnectionContext.consumeAddressOverride(), "an ordinary join must not be sent over EOS")
    }
}
