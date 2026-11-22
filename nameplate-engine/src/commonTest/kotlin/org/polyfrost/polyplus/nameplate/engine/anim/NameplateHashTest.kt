package org.polyfrost.polyplus.nameplate.engine.anim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash

class NameplateHashTest {
    @Test
    fun textIsFnv1aOverTheChars() {
        assertEquals(-2128831035, NameplateHash.text(""))
        assertEquals(-468965076, NameplateHash.text("a"))
        assertEquals(-1091163882, NameplateHash.text("tail_moves"))
        assertEquals(-96342536, NameplateHash.text("pick"))
    }

    @Test
    fun mixAndFinishArePinned() {
        assertEquals(-430675100, NameplateHash.mix(0, 0))
        assertEquals(2001329867, NameplateHash.mix(1, 2))
        assertEquals(0, NameplateHash.finish(0, 0))
    }

    @Test
    fun hashIsPinnedForSeedIdAndWindow() {
        assertEquals(-650046389, NameplateHash.hash(0, 0, 0))
        assertEquals(-1771127214, NameplateHash.hash(1, NameplateHash.text("tail_moves"), 2))
        assertEquals(128808553, NameplateHash.hash(-7, 5, -3))
    }

    @Test
    fun deriveGivesIndependentDrawsFromOneHash() {
        assertEquals(51827383, NameplateHash.derive(5, 1))
        val hash = NameplateHash.hash(1, 2, 3)
        assertNotEquals(NameplateHash.derive(hash, 1), NameplateHash.derive(hash, 2))
        assertNotEquals(NameplateHash.derive(hash, 2), NameplateHash.derive(hash, 3))
    }

    @Test
    fun everyArgumentChangesTheHash() {
        val base = NameplateHash.hash(10, 20, 30)
        assertNotEquals(base, NameplateHash.hash(11, 20, 30))
        assertNotEquals(base, NameplateHash.hash(10, 21, 30))
        assertNotEquals(base, NameplateHash.hash(10, 20, 31))
    }
}
