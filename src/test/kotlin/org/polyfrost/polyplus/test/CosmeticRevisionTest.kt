package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision

class CosmeticRevisionTest {
    private val first = "a".repeat(64)
    private val second = "b".repeat(64)
    private val placeholder = CosmeticRevision.PLACEHOLDER_HASH

    @Test
    fun `each content hash is its own revision`() {
        assertNotEquals(CosmeticRevision.of(first, "https://cdn/a.zip"), CosmeticRevision.of(second, "https://cdn/a.zip"))
    }

    @Test
    fun `a content hash ignores where the bundle is hosted`() {
        assertEquals(CosmeticRevision.of(first, "https://cdn/a.zip"), CosmeticRevision.of(first.uppercase(), "https://cdn/b.zip"))
    }

    @Test
    fun `a placeholder hash takes its revision from the url`() {
        assertNotEquals(CosmeticRevision.of(placeholder, "https://cdn/a.zip"), CosmeticRevision.of(placeholder, "https://cdn/b.zip"))
        assertEquals(CosmeticRevision.of(placeholder, "https://cdn/a.zip?sig=1"), CosmeticRevision.of(placeholder, "https://cdn/a.zip?sig=2"))
    }

    @Test
    fun `each variant is its own revision`() {
        val red = CosmeticRevision.of(first, null, "red")
        val blue = CosmeticRevision.of(first, null, "blue")
        assertNotEquals(red, blue)
        assertNotEquals(red.tag, blue.tag)
        assertTrue(red.sameContent(blue))
    }

    @Test
    fun `a placeholder revision tag follows the url`() {
        assertNotEquals(CosmeticRevision.of(placeholder, "https://cdn/a.zip").tag, CosmeticRevision.of(placeholder, "https://cdn/b.zip").tag)
    }

    @Test
    fun `the same revision keeps the same tag`() {
        assertEquals(CosmeticRevision.of(first, null, "red").tag, CosmeticRevision.of(first.uppercase(), null, "red").tag)
    }

    @Test
    fun `revision tags are valid texture paths`() {
        val tag = CosmeticRevision.of("Mixed/Case+hash==", "https://cdn/x.zip", "Variant Key").tag
        assertTrue(tag.all { it in 'a'..'z' || it in '0'..'9' })
    }

    @Test
    fun `the placeholder is not a content hash`() {
        assertFalse(CosmeticRevision.isContentHash(placeholder))
        assertFalse(CosmeticRevision.isContentHash(placeholder.uppercase()))
        assertTrue(CosmeticRevision.isContentHash(first))
        assertFalse(CosmeticRevision.isContentHash("abc"))
    }

    @Test
    fun `a query that addresses the bundle is part of its revision`() {
        assertNotEquals(CosmeticRevision.of(placeholder, "https://cdn/a.zip?v=1"), CosmeticRevision.of(placeholder, "https://cdn/a.zip?v=2"))
        assertEquals(
            CosmeticRevision.of(placeholder, "https://cdn/a.zip?v=1&lang=en"),
            CosmeticRevision.of(placeholder, "https://cdn/a.zip?lang=en&v=1"),
        )
    }

    @Test
    fun `signing and expiry parameters are ignored`() {
        val signed = "https://cdn/a.zip?v=3&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=c&X-Amz-Date=d&X-Amz-Expires=60&X-Amz-Signature=s"
        assertEquals("https://cdn/a.zip?v=3", CosmeticRevision.stableUrl(signed))
        assertEquals("https://cdn/a.zip", CosmeticRevision.stableUrl("https://cdn/a.zip?Expires=1&Signature=x&Key-Pair-Id=k&Policy=p"))
        assertEquals("https://cdn/a.zip", CosmeticRevision.stableUrl("https://cdn/a.zip?X-Goog-Signature=x&X-Goog-Expires=9"))
        assertEquals(CosmeticRevision.of(placeholder, "https://cdn/a.zip"), CosmeticRevision.of(placeholder, "https://cdn/a.zip?Expires=1&Signature=y"))
    }

    @Test
    fun `urls without a query keep their key`() {
        assertEquals("https://cdn/a.zip", CosmeticRevision.stableUrl("https://cdn/a.zip"))
        assertEquals("https://cdn/a.zip", CosmeticRevision.stableUrl("https://cdn/a.zip?"))
    }

    @Test
    fun `a new etag at the same url is a new revision`() {
        val before = CosmeticRevision.of("9b2cf535f27731c974343645a3985328", "https://cdn/a.zip?sig=1")
        val after = CosmeticRevision.of("e3b0c44298fc1c149afbf4c8996fb924", "https://cdn/a.zip?sig=2")
        assertNotEquals(before, after)
        assertFalse(before.sameContent(after))
        assertNotEquals(before.tag, after.tag)
        assertEquals(before, CosmeticRevision.of("9b2cf535f27731c974343645a3985328", "https://cdn/a.zip?sig=3"))
    }

    @Test
    fun `an etag revision differs from the placeholder at the same url`() {
        assertNotEquals(CosmeticRevision.of(placeholder, "https://cdn/a.zip"), CosmeticRevision.of("9b2cf535f27731c974343645a3985328", "https://cdn/a.zip"))
        assertEquals(CosmeticRevision.of(placeholder, "https://cdn/a.zip"), CosmeticRevision.of("", "https://cdn/a.zip"))
    }
}
