package org.polyfrost.polyplus.client.nameplate.render

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.nameplate.BoundNameplate
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateRegistry
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIris
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

object NameplateGate {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val failed: MutableSet<NameplateBundle> = Collections.newSetFromMap(WeakHashMap())

    fun active(): Boolean = NameplateBinding.settings.enabled && !NameplateIris.shadowPass()

    fun usable(nameplate: BoundNameplate?): Boolean = nameplate != null && nameplate.bundle !in failed && !NameplateIris.shadowPass()

    fun bound(uuid: UUID): BoundNameplate? = NameplateRegistry.boundFor(uuid)?.takeIf(::usable)

    fun fail(nameplate: BoundNameplate, error: Throwable) {
        if (failed.add(nameplate.bundle)) {
            LOGGER.error("Nameplate {} failed to render and is off until it reloads", nameplate.bundle.id, error)
        }
    }
}
