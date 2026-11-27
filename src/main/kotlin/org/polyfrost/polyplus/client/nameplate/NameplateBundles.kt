package org.polyfrost.polyplus.client.nameplate

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class NameplateBundle(val key: String, val hash: String, val file: Path) {
    fun readBytes(): ByteArray = Files.readAllBytes(file)
}

class NameplateEquip(val cosmeticId: Int, val bundle: NameplateBundle, val variantKey: String?)

interface NameplateBundleListener {
    fun bundleInstalled(bundle: NameplateBundle) {}

    fun bundleReleased(bundle: NameplateBundle) {}

    fun wearerBound(wearer: UUID, equip: NameplateEquip?) {}

    fun wearerForgotten(wearer: UUID) {}
}

object NameplateBundles {
    private val bundles = ConcurrentHashMap<String, NameplateBundle>()
    private val equips = ConcurrentHashMap<Int, NameplateEquip>()
    private val revisions = AtomicInteger()

    @Volatile
    var listener: NameplateBundleListener? = null

    fun get(cosmeticId: Int): NameplateEquip? = equips[cosmeticId]

    fun revision(): Int = revisions.get()

    fun bundle(key: String): NameplateBundle? = bundles[key]

    fun isLoaded(cosmeticId: Int, hash: String, variantKey: String?): Boolean =
        equips[cosmeticId]?.let { it.bundle.hash == hash && it.variantKey == variantKey } == true

    fun loaded(): Collection<NameplateBundle> = bundles.values

    internal fun install(cosmeticId: Int, variantKey: String?, key: String, hash: String, file: Path) {
        val existing = bundles[key]
        val bundle = existing?.takeIf { it.hash == hash && it.file == file } ?: NameplateBundle(key, hash, file)
        if (bundle !== existing) {
            bundles[key] = bundle
            existing?.let { listener?.bundleReleased(it) }
            listener?.bundleInstalled(bundle)
        }
        val previous = equips.put(cosmeticId, NameplateEquip(cosmeticId, bundle, variantKey))
        revisions.incrementAndGet()
        if (previous != null && previous.bundle.key != key) releaseUnused(previous.bundle)
    }

    internal fun retain(keep: Set<Int>): Boolean {
        val dropped = equips.keys.filter { it !in keep }.mapNotNull(equips::remove)
        if (dropped.isNotEmpty()) revisions.incrementAndGet()
        dropped.map { it.bundle }.distinct().forEach(::releaseUnused)
        return dropped.isNotEmpty()
    }

    internal fun clear() {
        val released = bundles.values.toList()
        equips.clear()
        revisions.incrementAndGet()
        bundles.clear()
        released.forEach { listener?.bundleReleased(it) }
    }

    private fun releaseUnused(bundle: NameplateBundle) {
        if (equips.values.any { it.bundle.key == bundle.key }) return
        if (bundles.remove(bundle.key, bundle)) listener?.bundleReleased(bundle)
    }
}
