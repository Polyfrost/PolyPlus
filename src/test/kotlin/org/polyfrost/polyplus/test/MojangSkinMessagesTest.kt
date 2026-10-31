package org.polyfrost.polyplus.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.MojangSkinService
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitException
import org.polyfrost.polyplus.client.network.http.loggableHost
import org.polyfrost.polyplus.client.network.http.readLimited
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode
import org.polyfrost.polyplus.libs.ktor.utils.io.ByteReadChannel

class MojangSkinMessagesTest {
    @Test
    fun `only a 401 asks the player to sign in`() {
        assertTrue("Sign in" in MojangSkinService.rejectionMessage(HttpStatusCode.Unauthorized))
        assertFalse("Sign in" in MojangSkinService.rejectionMessage(HttpStatusCode.Forbidden))
    }

    @Test
    fun `a skin that fails to download keeps its reason`() {
        assertEquals("Couldn't download that image.", MojangSkinService.failureMessage(OutfitException("Couldn't download that image.")))
    }

    @Test
    fun `a body at the limit is read and one past it is refused`() = runBlocking {
        val body = ByteArray(16) { it.toByte() }
        assertArrayEquals(body, ByteReadChannel(body).readLimited(16))
        assertNull(ByteReadChannel(body).readLimited(15))
    }

    @Test
    fun `a logged link keeps only its host`() {
        assertEquals("cdn.example.com", loggableHost("https://user:secret@cdn.example.com/token/skin.png?sig=secret#secret"))
        assertEquals("an unparseable link", loggableHost("https://user:se cret@cdn.example.com/skin.png"))
        assertEquals("an unparseable link", loggableHost("https://user:secret@bad_host/skin.png"))
    }
}
