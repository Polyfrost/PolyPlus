package org.polyfrost.polyplus.client.nameplate.material

import java.util.Collections
import java.util.IdentityHashMap

interface NameplatePackSurface {
    fun resolve(): Boolean

    fun active(): Boolean

    fun unlockBlend()

    fun redirect(): Boolean

    fun restore()
}

class NameplateOwnedPipelines {
    private val items: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    fun add(pipeline: Any?) {
        if (pipeline != null) items.add(pipeline)
    }

    fun remove(pipeline: Any?) {
        if (pipeline != null) items.remove(pipeline)
    }

    operator fun contains(pipeline: Any?): Boolean = pipeline != null && pipeline in items

    fun clear() = items.clear()
}

class NameplatePackGate(
    private val target: () -> NameplatePackTarget?,
    private val surface: () -> NameplatePackSurface?,
    private val failed: (Throwable) -> Unit,
) {
    var packInUse = false
        private set
    var broken = false
        private set
    private var ready = false
    private var redirected = false

    fun update(packInUse: Boolean) {
        this.packInUse = packInUse
        ready = packInUse && !broken && guard(false) { surface()?.resolve() != false && target()?.resolve() == true }
    }

    fun gpuAllowed(): Boolean = !packInUse || ready

    fun redirecting(): Boolean = packInUse && ready

    fun fail(e: Throwable) {
        if (broken) return
        broken = true
        ready = false
        failed(e)
    }

    fun unlockBlend(pipeline: Any?) {
        if (pipeline == null || !redirecting()) return
        guard(Unit) { if (target()?.owns(pipeline) == true) surface()?.unlockBlend() }
    }

    fun redirect(pipeline: Any?) {
        if (redirected) restore()
        if (pipeline == null || !redirecting()) return
        guard(Unit) {
            val out = surface() ?: return@guard
            if (target()?.owns(pipeline) == true && out.redirect()) redirected = true
        }
    }

    fun restore() {
        if (!redirected) return
        redirected = false
        guard(Unit) { surface()?.restore() }
    }

    fun fogOff(): Boolean = redirecting() && guard(false) { surface()?.active() == true }

    private inline fun <T> guard(fallback: T, action: () -> T): T = try {
        action()
    } catch (e: RuntimeException) {
        fail(e)
        fallback
    } catch (e: LinkageError) {
        fail(e)
        fallback
    }
}
