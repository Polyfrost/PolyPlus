package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object PaletteTextures {
    const val NO_COLORWAY = CosmeticAsset.NO_COLORWAY

    private const val BUDGET_BYTES = 64L * 1024 * 1024
    private const val IN_USE_NANOS = 2_000_000_000L
    private const val TEXTURE = 0

    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")
    private val loader: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PolyPlus palette textures").apply { isDaemon = true }
    }

    internal class Entry(val texture: Identifier, val bytes: Long) {
        var used = System.nanoTime()
        var live = true
    }

    class Look {
        internal var asset: CosmeticAsset? = null
        internal var base: Identifier? = null
        internal var colorway = NO_COLORWAY
        internal var colors: Map<String, Int>? = null
        internal var slots: IntArray? = null
        internal var rgb: IntArray? = null
        internal var key = 0L
        internal var keyed = false
        internal var entry: Entry? = null
        internal var generation = -1
    }

    private val ready = LinkedHashMap<Long, Entry>(16, 0.75f, true)
    private val loading = HashSet<Long>()
    private val failed = HashSet<Long>()
    private val worn = HashMap<WornKey, Long>()
    private var bytes = 0L
    private var generation = 0

    private data class WornKey(val wearer: Long, val cosmetic: Identifier)

    @JvmStatic
    @JvmOverloads
    fun texture(
        wearer: Long,
        cosmetic: Int,
        colorway: Int = NO_COLORWAY,
        customSlots: IntArray? = null,
        customRgb: IntArray? = null,
    ): Identifier? {
        val attached = CosmeticAssetCache.getAttachedCosmetic(cosmetic) ?: return null
        return texture(wearer, attached, colorway, customSlots, customRgb)
    }

    @JvmStatic
    @JvmOverloads
    fun texture(
        wearer: Long,
        attached: AttachedCosmetic,
        colorway: Int = NO_COLORWAY,
        customSlots: IntArray? = null,
        customRgb: IntArray? = null,
    ): Identifier {
        val cosmetic = attached.id
        val asset = attached.scene.asset
        val plain = colorway == NO_COLORWAY && (customSlots == null || customSlots.isEmpty())
        if (asset == null || plain) {
            if (worn.isNotEmpty()) worn.remove(WornKey(wearer, cosmetic))
            return attached.texture
        }
        if ((customSlots?.size ?: 0) != (customRgb?.size ?: 0)) return attached.texture
        val key = try {
            asset.textureKey(TEXTURE, colorway, customSlots, customRgb)
        } catch (ex: RuntimeException) {
            LOGGER.debug("Cosmetic {} has no colourway {} or those custom colours", cosmetic, colorway, ex)
            return attached.texture
        }
        worn[WornKey(wearer, cosmetic)] = key
        return lookup(key, cosmetic, attached.scene, asset, colorway, customSlots, customRgb)?.texture ?: attached.texture
    }

    fun texture(look: Look, wearer: Long, attached: AttachedCosmetic, colorway: Int, colors: Map<String, Int>?): Identifier =
        texture(look, wearer, attached.id, attached.scene, attached.texture, colorway, colors)

    fun texture(
        look: Look,
        wearer: Long,
        cosmetic: Identifier,
        scene: CosmeticScene,
        base: Identifier,
        colorway: Int,
        colors: Map<String, Int>?,
    ): Identifier {
        val asset = scene.asset
        if (look.generation != generation || look.asset !== asset || look.base !== base || look.colorway != colorway ||
            look.colors !== colors
        ) {
            rekey(look, wearer, cosmetic, asset, base, scene.paletteSlots, colorway, colors)
        }
        val shown = look.asset
        if (!look.keyed || shown == null) return base
        val held = look.entry
        if (held != null && held.live && RemoteTextures.isRegistered(held.texture)) {
            held.used = System.nanoTime()
            return held.texture
        }
        look.entry = null
        if (look.key in failed) {
            look.keyed = false
            return base
        }
        val entry = lookup(look.key, cosmetic, scene, shown, look.colorway, look.slots, look.rgb) ?: return base
        look.entry = entry
        return entry.texture
    }

    private fun rekey(
        look: Look,
        wearer: Long,
        cosmetic: Identifier,
        asset: CosmeticAsset?,
        base: Identifier,
        paletteSlots: List<String>,
        colorway: Int,
        colors: Map<String, Int>?,
    ) {
        look.generation = generation
        look.asset = asset
        look.base = base
        look.colorway = colorway
        look.colors = colors
        look.entry = null
        look.keyed = false
        val custom = CustomColors.of(paletteSlots, colors)
        look.slots = custom?.slots
        look.rgb = custom?.rgb
        if (asset == null || (colorway == NO_COLORWAY && custom == null)) {
            if (worn.isNotEmpty()) worn.remove(WornKey(wearer, cosmetic))
            return
        }
        look.key = try {
            asset.textureKey(TEXTURE, colorway, custom?.slots, custom?.rgb)
        } catch (ex: RuntimeException) {
            LOGGER.debug("Cosmetic {} has no colourway {} or those custom colours", cosmetic, colorway, ex)
            return
        }
        look.keyed = true
        worn[WornKey(wearer, cosmetic)] = look.key
    }

    private fun lookup(
        key: Long,
        cosmetic: Identifier,
        scene: CosmeticScene,
        asset: CosmeticAsset,
        colorway: Int,
        slots: IntArray?,
        rgb: IntArray?,
    ): Entry? {
        val entry = ready[key]
        if (entry != null && RemoteTextures.isRegistered(entry.texture)) {
            entry.used = System.nanoTime()
            return entry
        }
        if (entry != null) drop(key)
        if (scene.closed || key in failed || !loading.add(key)) return null
        resolve(key, cosmetic, scene, asset, colorway, slots?.copyOf(), rgb?.copyOf())
        return null
    }

    @JvmStatic
    fun forget(wearer: Long) {
        worn.keys.removeIf { it.wearer == wearer }
    }

    @JvmStatic
    fun clear() {
        generation++
        for (key in ready.keys.toList()) drop(key)
        loading.clear()
        failed.clear()
        worn.clear()
    }

    private fun resolve(
        key: Long,
        cosmetic: Identifier,
        scene: CosmeticScene,
        asset: CosmeticAsset,
        colorway: Int,
        slots: IntArray?,
        rgb: IntArray?,
    ) {
        val stamp = generation
        loader.execute {
            val info = IntArray(CosmeticAsset.TEXTURE_INFO_INTS)
            val rgba = try {
                ByteArray(asset.textureInfo(TEXTURE, info)).also { asset.resolveTexture(TEXTURE, colorway, slots, rgb, null, it) }
            } catch (ex: RuntimeException) {
                val freed = scene.closed
                if (freed) {
                    LOGGER.debug("Skipped recolouring cosmetic {} after it was freed", cosmetic, ex)
                } else {
                    LOGGER.warn("Could not recolour cosmetic {}", cosmetic, ex)
                }
                ClientPlatform.runOnMain {
                    if (stamp != generation) return@runOnMain
                    loading.remove(key)
                    if (!freed) failed += key
                }
                return@execute
            }
            val texture = try {
                RemoteTextures.registerPixels(
                    Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/palette_${key.toULong().toString(16)}"),
                    info[CosmeticAsset.TEXTURE_WIDTH],
                    info[CosmeticAsset.TEXTURE_HEIGHT],
                    rgba,
                )
            } catch (ex: RuntimeException) {
                LOGGER.debug("Recolouring cosmetic {} didn't reach the main thread; it will be retried", cosmetic, ex)
                ClientPlatform.runOnMain { if (stamp == generation) loading.remove(key) }
                return@execute
            }
            ClientPlatform.runOnMain {
                if (stamp == generation) {
                    install(key, Entry(texture, rgba.size.toLong()))
                } else if (key !in ready && key !in loading) {
                    RemoteTextures.release(texture)
                }
            }
        }
    }

    private fun install(key: Long, entry: Entry) {

        loading.remove(key)
        ready.remove(key)?.let {
            it.live = false
            bytes -= it.bytes
        }
        ready[key] = entry
        bytes += entry.bytes
        trim()
    }

    private fun trim() {
        if (bytes <= BUDGET_BYTES) return
        val now = System.nanoTime()
        val wanted = worn.values.toHashSet()
        val iterator = ready.entries.iterator()
        while (bytes > BUDGET_BYTES && iterator.hasNext()) {
            val (key, entry) = iterator.next()
            if (key in wanted && now - entry.used < IN_USE_NANOS) continue
            iterator.remove()
            entry.live = false
            bytes -= entry.bytes
            RemoteTextures.release(entry.texture)
        }
    }

    private fun drop(key: Long) {
        val entry = ready.remove(key) ?: return
        entry.live = false
        bytes -= entry.bytes
        RemoteTextures.release(entry.texture)
    }
}
