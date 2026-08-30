package org.polyfrost.polyplus.client.privacy

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.concurrent.atomic.AtomicBoolean

object PrivacyEnforcement {
    private val LOGGER = LogManager.getLogger("PolyPlus/Privacy")

    private val syncing = AtomicBoolean(false)

    private val bootstrapped = AtomicBoolean(false)

    fun apply() {
        val allowed = PrivacyConsent.allowsOnlineServices()
        LOGGER.info(
            "Applying privacy consent: state={}, launcherManaged={}, onlineServices={}",
            PrivacyConsent.state(),
            PrivacyConsent.managedByLauncher,
            if (allowed) "enabled" else "disabled",
        )
        if (allowed) PolyPlusSentry.initialize() else PolyPlusSentry.shutdown()
        PolyConnection.applyConsent()
        P2PSessionManager.applyConsent()

        if (allowed) {
            if (bootstrapped.compareAndSet(false, true)) {
                LOGGER.info("Consent granted; bootstrapping PolyPlus online services.")
                PolyPlusClient.refreshCosmetics()
            }
        } else {
            bootstrapped.set(false)
        }
    }

    fun syncConfig() {
        syncing.set(true)
        try {
            PolyPlusConfig.acceptedLegalTerms = PrivacyConsent.allowsOnlineServices()
        } finally {
            syncing.set(false)
        }
    }

    fun onConfigChanged(accepted: Boolean) {
        if (syncing.get()) return
        if (accepted == PrivacyConsent.allowsOnlineServices()) return
        if (accepted) PrivacyConsent.accept() else PrivacyConsent.decline()
        apply()
    }
}
