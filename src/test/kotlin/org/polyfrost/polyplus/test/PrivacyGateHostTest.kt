package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.privacy.isPolyfrostHost

class PrivacyGateHostTest {
    private val production = "plus.polyfrost.org"

    @Test
    fun `polyfrost endpoints are covered`() {
        assertTrue(isPolyfrostHost("plus.polyfrost.org", production))
        assertTrue(isPolyfrostHost("data-v2.polyfrost.org", production))
        assertTrue(isPolyfrostHost("polyfrost.org", production))
        assertTrue(isPolyfrostHost("PLUS.Polyfrost.ORG", production))
    }

    @Test
    fun `third party services are not covered`() {
        assertFalse(isPolyfrostHost("login.microsoftonline.com", production))
        assertFalse(isPolyfrostHost("user.auth.xboxlive.com", production))
        assertFalse(isPolyfrostHost("xsts.auth.xboxlive.com", production))
        assertFalse(isPolyfrostHost("api.minecraftservices.com", production))
        assertFalse(isPolyfrostHost("sessionserver.mojang.com", production))
        assertFalse(isPolyfrostHost("cdn.modrinth.com", production))
    }
}
