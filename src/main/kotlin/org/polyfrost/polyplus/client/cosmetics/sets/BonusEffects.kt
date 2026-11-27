package org.polyfrost.polyplus.client.cosmetics.sets

import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BonusEffect internal constructor(val ref: BonusRef, val cosmetic: AttachedCosmetic)

object BonusEffects {
    val SLOT = BodySlot.EffectA

    private const val RETRY_MS = 60_000L
    private const val INSTALL_MS = 2_000L

    private val loading: MutableSet<BonusRef> = ConcurrentHashMap.newKeySet()
    private val retryAt = ConcurrentHashMap<BonusRef, Long>()
    private val wrapped = ConcurrentHashMap<BonusRef, BonusEffect>()

    fun forPlayer(owner: UUID): List<BonusEffect> {
        PreviewTryOn.lobbyBonuses()?.let { return loaded(it) }
        if (!PolyPlusCosmeticsConfig.isVisible(SLOT, owner)) return emptyList()
        return loaded(BonusState.effectsFor(owner))
    }

    fun active(owner: UUID?, preview: Boolean): Boolean =
        if (preview) {
            PreviewTryOn.previewBonuses().isNotEmpty()
        } else {
            PreviewTryOn.lobbyBonuses()?.isNotEmpty() ?: (owner != null && BonusState.hasBonuses(owner))
        }

    fun loaded(refs: List<BonusRef>): List<BonusEffect> {
        if (refs.isEmpty()) return emptyList()
        var out: ArrayList<BonusEffect>? = null
        for (ref in refs) {
            val effect = CosmeticAssetCache.getBonusEffect(ref)
            if (effect == null) {
                load(ref)
                continue
            }
            val list = out ?: ArrayList<BonusEffect>(refs.size).also { out = it }
            list += wrap(ref, effect)
        }
        return out.orEmpty()
    }

    internal fun retainLoaded() {
        wrapped.entries.removeIf { (ref, effect) -> CosmeticAssetCache.getBonusEffect(ref) !== effect.cosmetic }
    }

    internal fun reset() {
        wrapped.clear()
        retryAt.clear()
    }

    private fun wrap(ref: BonusRef, effect: AttachedCosmetic): BonusEffect {
        val cached = wrapped[ref]
        if (cached != null && cached.cosmetic === effect) return cached
        return BonusEffect(ref, effect).also { wrapped[ref] = it }
    }

    private fun load(ref: BonusRef) {
        if (ref.url == null) return
        val retry = retryAt[ref]
        if (retry != null && System.currentTimeMillis() < retry) return
        if (!loading.add(ref)) return
        PolyPlusClient.SCOPE.launch {
            try {
                val loaded = CosmeticAssetCache.ensureBonusLoaded(ref)
                retryAt[ref] = System.currentTimeMillis() + if (loaded) INSTALL_MS else RETRY_MS
            } finally {
                loading.remove(ref)
            }
        }
    }
}
