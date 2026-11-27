package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision
import org.polyfrost.polyplus.client.cosmetics.RetiredTextures
import org.polyfrost.polyplus.client.pets.PetAssets

class PetRevisionTest {
    private val hash = "0123456789abcdef" + "a".repeat(48)
    private val placeholder = CosmeticRevision.PLACEHOLDER_HASH
    private val bundle = "first".toByteArray()
    private val moved = "second".toByteArray()

    @Test
    fun `placeholder pets moved to a new url get a new engine identity`() {
        val old = CosmeticRevision.of(placeholder, "https://cdn/a.zip")
        val new = CosmeticRevision.of(placeholder, "https://cdn/b.zip")
        assertNotEquals(PetAssets.identity(old, bundle), PetAssets.identity(new, moved))
    }

    @Test
    fun `content hashed pets take their identity from the hash`() {
        val revision = CosmeticRevision.of(hash, "https://cdn/a.zip")
        assertEquals(0x0123456789abcdefL, PetAssets.identity(revision, bundle))
        assertEquals(PetAssets.identity(revision, bundle), PetAssets.identity(CosmeticRevision.of(hash.uppercase(), "https://cdn/b.zip"), moved))
    }

    @Test
    fun `placeholder pets moved to a new url get new texture ids`() {
        val old = PetAssets.texturePrefix(7, CosmeticRevision.of(placeholder, "https://cdn/a.zip?sig=1"))
        val same = PetAssets.texturePrefix(7, CosmeticRevision.of(placeholder, "https://cdn/a.zip?sig=2"))
        val new = PetAssets.texturePrefix(7, CosmeticRevision.of(placeholder, "https://cdn/b.zip"))
        assertEquals(old, same)
        assertNotEquals(old, new)
    }

    @Test
    fun `each pet variant gets its own texture ids`() {
        assertNotEquals(
            PetAssets.texturePrefix(7, CosmeticRevision.of(hash, null, "red")),
            PetAssets.texturePrefix(7, CosmeticRevision.of(hash, null, "blue")),
        )
    }

    @Test
    fun `pet texture ids are valid paths`() {
        val prefix = PetAssets.texturePrefix(7, CosmeticRevision.of(placeholder, "https://cdn/Some Pet.zip", "Variant Key"))
        assertTrue(prefix.all { it in 'a'..'z' || it in '0'..'9' || it == '/' })
    }

    @Test
    fun `a live update gives the new pet scene its own effect texture ids`() {
        val old = CosmeticRevision.of(hash, "https://cdn/a.zip")
        val new = CosmeticRevision.of("f".repeat(64), "https://cdn/b.zip")
        val oldScene = PetAssets.nextScene()
        val newScene = PetAssets.nextScene()
        assertNotEquals(PetAssets.effectPrefix(7, old, oldScene), PetAssets.effectPrefix(7, new, newScene))
        assertNotEquals(PetAssets.effectPrefix(7, old, oldScene), PetAssets.effectPrefix(7, old, newScene))
        assertTrue(PetAssets.effectPrefix(7, old, oldScene).all { it in 'a'..'z' || it in '0'..'9' || it == '/' || it == '_' })
    }

    @Test
    fun `a replaced pet stays loaded while a pet still draws it`() {
        val retired = RetiredTextures<String>()
        val released = ArrayList<String>()
        retired.retire(listOf("old"))
        retired.sweep(setOf("new"), setOf("old"), released::add)
        assertTrue(released.isEmpty())
        retired.sweep(setOf("new"), emptySet(), released::add)
        assertEquals(listOf("old"), released)
        assertTrue(retired.isEmpty())
    }
}
