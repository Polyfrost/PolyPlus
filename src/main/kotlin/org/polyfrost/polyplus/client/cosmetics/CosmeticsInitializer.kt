package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.utils.EarlyInitializable

// Render layers are added via mixin not here
object CosmeticsInitializer : EarlyInitializable {
    override fun earlyInitialize() {
        CosmeticSync.earlyInitialize()
    }
}
