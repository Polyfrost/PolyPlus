package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.launcher.LauncherAccountStore
import java.time.Duration
import java.time.Instant

class AccountExpiryTest {
    @Test
    fun `an unreadable expiry counts as expired`() {
        assertTrue(LauncherAccountStore.isExpired(""))
        assertTrue(LauncherAccountStore.isExpired("2026-09-11T12:00:00"))
        assertTrue(LauncherAccountStore.isExpired("1757606400"))
    }

    @Test
    fun `a real expiry still decides`() {
        assertTrue(LauncherAccountStore.isExpired(Instant.now().minus(Duration.ofMinutes(1)).toString()))
        assertTrue(LauncherAccountStore.isExpired(Instant.now().plusSeconds(30).toString()))
        assertFalse(LauncherAccountStore.isExpired(Instant.now().plus(Duration.ofHours(1)).toString()))
    }
}
