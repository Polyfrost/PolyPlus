package org.polyfrost.polyplus.client.nameplate

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
//? if >= 1.21.1 {
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialAssets
//?}
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object NameplateRegistry {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val UNKNOWN_REFRESH_INTERVAL_MS = 60_000L
    private const val MAX_BOUND_PLAYERS = 1024
    private const val UUID_HASH_BYTES = 16
    private const val REAL_PLAYER_UUID_VERSION = 4

    private class Installed(val hash: String, val key: String, val bundle: NameplateBundle)

    private val installed = ConcurrentHashMap<Int, Installed>()
    private val bound = ConcurrentHashMap<UUID, BoundNameplate>()
    private val lastUnknownRefresh = AtomicLong(0L)
    private val failureLogged = AtomicBoolean(false)

    fun initialize() {
        NameplateBinding.source = NameplateSource(::boundFor)
        PolyPlusCosmeticsConfig.publishNameplateSettings()
    }

    fun install(id: Int, hash: String, key: String, bundle: NameplateBundle) {
        installAssets(bundle)
        val previous = installed.put(id, Installed(hash, key, bundle))
        if (previous != null && previous.bundle !== bundle) {
            releaseAssets(previous.bundle)
        }
    }

    fun isLoaded(id: Int, hash: String): Boolean = installed[id]?.hash == hash

    fun isLoaded(id: Int): Boolean = installed.containsKey(id)

    fun installedBundle(id: Int): NameplateBundle? = installed[id]?.bundle

    fun clear() {
        installed.values.forEach { releaseAssets(it.bundle) }
        installed.clear()
        bound.clear()
    }

    fun retain(keep: Set<Int>): Boolean {
        installed.filterKeys { it !in keep }.values.forEach { releaseAssets(it.bundle) }
        val evicted = installed.keys.retainAll(keep)
        NameplateLoader.retain(installed.values.mapTo(HashSet()) { it.key })
        return evicted
    }

    fun boundFor(player: UUID): BoundNameplate? = try {
        resolve(player)
    } catch (e: Exception) {
        if (failureLogged.compareAndSet(false, true)) {
            LOGGER.error("Failed to resolve the nameplate for {}; drawing vanilla tags", player, e)
        }
        null
    }

    fun seedOf(player: UUID): Int {
        val most = player.mostSignificantBits
        val least = player.leastSignificantBits
        var hash = NameplateHash.mix(0, (most ushr 32).toInt())
        hash = NameplateHash.mix(hash, most.toInt())
        hash = NameplateHash.mix(hash, (least ushr 32).toInt())
        hash = NameplateHash.mix(hash, least.toInt())
        return NameplateHash.finish(hash, UUID_HASH_BYTES)
    }

    internal fun bind(player: UUID, bundle: NameplateBundle, variant: NameplateBundle.Variant): BoundNameplate {
        bound[player]?.let { if (it.bundle === bundle && it.variant === variant) return it }
        if (bound.size >= MAX_BOUND_PLAYERS) bound.clear()
        return BoundNameplate(bundle, variant, seedOf(player)).also { bound[player] = it }
    }

    private fun resolve(player: UUID): BoundNameplate? {
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, player)) return null
        if (player.version() != REAL_PLAYER_UUID_VERSION) return null
        val id = CosmeticCatalog.getActiveId(player, BodySlot.Nameplate) ?: return null
        val definition = CosmeticCatalog.getCosmeticDefinition(id)
        if (definition == null) {
            requestCatalogRefresh()
            return null
        }
        if (definition.type != CosmeticType.Nameplate) return null
        val bundle = installed[id]?.bundle ?: return null
        return bind(player, bundle, bundle.variant(definition.variantKey))
    }

    internal fun installAssets(bundle: NameplateBundle) {
        //? if >= 1.21.1 {
        NameplateMaterialAssets.install(bundle)
        //?}
    }

    internal fun releaseAssets(bundle: NameplateBundle) {
        //? if >= 1.21.1 {
        NameplateMaterialAssets.release(bundle)
        //?}
    }

    private fun requestCatalogRefresh() {
        val now = System.currentTimeMillis()
        val last = lastUnknownRefresh.get()
        if (now - last < UNKNOWN_REFRESH_INTERVAL_MS) return
        if (!lastUnknownRefresh.compareAndSet(last, now)) return
        PolyPlusClient.refreshCosmetics()
    }
}
