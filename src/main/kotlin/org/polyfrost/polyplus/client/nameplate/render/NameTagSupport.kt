package org.polyfrost.polyplus.client.nameplate.render

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.nameplate.NameplateBundle
import org.polyfrost.polyplus.client.nameplate.NameplateEquip
import org.polyfrost.polyplus.client.nameplate.NameplateWearers
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

class NameTagBinding(val wearer: UUID, val profileName: String, val equip: NameplateEquip, val own: Boolean) {
    //? if < 1.21.10 {
    /*var rowAlpha: Int = 0xFF
    var sneakAlpha: Int = 0
    *///?}
}

object NameTagSupport {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    const val PLATE_OPACITY: Float = 0.7f
    const val VISIBLE_ALPHA: Int = 26

    private const val MAX_BINDINGS = 256

    private val bindings = object : LinkedHashMap<UUID, NameTagBinding>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, NameTagBinding>): Boolean = size > MAX_BINDINGS
    }
    private val failedBundles: MutableSet<NameplateBundle> = Collections.newSetFromMap(WeakHashMap())

    fun binding(wearer: UUID, profileName: String, own: Boolean): NameTagBinding? {
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, wearer)) return null
        val equip = NameplateWearers.resolve(wearer) ?: return null
        val cached = bindings[wearer]
        if (cached != null && cached.equip === equip && cached.own == own && cached.profileName == profileName) return cached
        return NameTagBinding(wearer, profileName, equip, own).also { bindings[wearer] = it }
    }

    fun usable(binding: NameTagBinding): Boolean = binding.equip.bundle !in failedBundles

    fun failed(bundle: NameplateBundle): Boolean = bundle in failedBundles

    fun prepare(
        binding: NameTagBinding,
        tag: NameplateDrawTag,
        list: NameplateDrawList,
        signals: PlayerSignalRecord?,
        distanceSq: Double,
    ): Boolean {
        list.reset()
        tag.wearer = binding.wearer
        tag.profileName = binding.profileName
        tag.equip = binding.equip
        tag.flags = if (binding.own) NameplateDrawTag.FLAG_OWN else 0
        tag.badgeShown = PolyPlusBadge.shouldBadge(binding.wearer)
        tag.deferred = false
        tag.signals = signals
        tag.distanceSq = if (binding.own) 0.0 else distanceSq
        return try {
            val drawn = NameplateDrawSources.current.prepare(tag, list)
            if (!drawn) release(tag)
            drawn
        } catch (e: RuntimeException) {
            fail(binding, e)
            release(tag)
            false
        }
    }

    fun measureAndDraw(tag: NameplateDrawTag, list: NameplateDrawList) {
        NameplateDrawSources.current.measured(tag)
        NameplateDrawSources.current.draw(tag, list)
    }

    fun endFrame() {
        NameplateDrawSources.current.endFrame()
    }

    fun release(tag: NameplateDrawTag) {
        tag.equip = null
        tag.signals = null
    }

    fun plateAlpha(opacityScale: Float): Float = minOf(1f, opacityScale)

    fun hide(color: Int, hiddenAlpha: Int): Int = (color and RGB) or (hiddenAlpha shl 24)

    fun legacyPassAlpha(color: Int): Int = if (color and LEGACY_FORCED_OPAQUE == 0) 0xFF else color ushr 24

    fun redrawsText(restyle: Boolean, alpha: Int): Boolean = restyle && alpha >= VISIBLE_ALPHA

    fun wordChar(c: Char): Boolean = c == '_' || c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    fun fail(binding: NameTagBinding, e: Throwable) {
        if (!failedBundles.add(binding.equip.bundle)) return
        LOGGER.error("Nameplate {} failed to draw and is off until it reloads", binding.equip.bundle.key, e)
    }

    fun error(message: String, e: Throwable) {
        LOGGER.error(message, e)
    }

    private const val RGB = 0xFFFFFF
    private const val LEGACY_FORCED_OPAQUE = 0xFC000000.toInt()
}
