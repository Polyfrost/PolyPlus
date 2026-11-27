package org.polyfrost.polyplus.test

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.NameplateBundles
import org.polyfrost.polyplus.client.nameplate.NameplateWearers
import java.nio.file.Path
import java.util.UUID

class NameplateWearersTest {
    private val wearer = UUID(11L, 22L)

    @AfterEach
    fun reset() {
        NameplateWearers.forgetAll()
        NameplateBundles.clear()
    }

    @Test
    fun `resolve follows a reinstalled equip`() {
        NameplateBundles.install(500, "red", "key", "hash", Path.of("a.pnp"))
        NameplateWearers.bind(wearer, 500)
        val first = NameplateWearers.resolve(wearer)
        assertSame(first, NameplateWearers.resolve(wearer))
        assertEquals("red", first?.variantKey)

        NameplateBundles.install(500, "blue", "key", "hash", Path.of("a.pnp"))
        val second = NameplateWearers.resolve(wearer)
        assertNotSame(first, second)
        assertEquals("blue", second?.variantKey)
    }

    @Test
    fun `retaining away an equip changes the revision`() {
        NameplateBundles.install(501, null, "key2", "hash", Path.of("b.pnp"))
        val before = NameplateBundles.revision()
        NameplateBundles.retain(emptySet())
        assertNotEquals(before, NameplateBundles.revision())
    }
}
