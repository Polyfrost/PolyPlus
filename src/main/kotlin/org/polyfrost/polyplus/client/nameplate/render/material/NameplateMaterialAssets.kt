package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 1.21.1 {
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.shader.TexturePixels
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
//? if < 1.21.4 {
/*import net.minecraft.util.FastColor
*///?}

object NameplateMaterialAssets {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val TEXTURE_PATH = "nameplate/texture/"
    private const val DIGEST_BYTES = 16
    private const val HEX_DIGITS = 2
    private const val BYTE = 0xFF
    private const val RECT = 4
    private const val GRACE_NANOS = 60_000_000_000L
    private const val SWEEP_NANOS = 1_000_000_000L

    private val VANILLA: Map<String, Identifier> = mapOf(
        "end_sky" to Identifier.withDefaultNamespace("textures/environment/end_sky.png"),
        //? if >= 26.1 {
        "end_portal" to Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png"),
        //?} else {
        /*"end_portal" to Identifier.withDefaultNamespace("textures/entity/end_portal.png"),
        *///?}
        "glint" to Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png"),
        //? if >= 26.1 {
        "beacon_beam" to Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png"),
        //?} else {
        /*"beacon_beam" to Identifier.withDefaultNamespace("textures/entity/beacon_beam.png"),
        *///?}
        "creeper_armor" to Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png"),
        "breeze_wind" to Identifier.withDefaultNamespace("textures/entity/breeze/breeze_wind.png"),
    )

    private class Texture(val id: Identifier, val width: Int, val height: Int, val argb: IntArray) {
        val pixels = TexturePixels(width, height, argb)
    }

    private class Prepared(val textures: Map<String, Texture>) {
        val ids = HashMap<MaterialLayer, List<Identifier>>()
        val missing: MutableSet<MaterialLayer> = Collections.newSetFromMap(HashMap())
    }

    private class Registered {
        var users = 0
    }

    private class Use {
        var users = 0
        var lastUsed = System.nanoTime()
    }

    private val prepared: MutableMap<NameplateBundle, Prepared> = Collections.synchronizedMap(WeakHashMap())
    private val installed = HashMap<NameplateBundle, Prepared>()
    private val registered = HashMap<Identifier, Registered>()
    private val programs = ConcurrentHashMap<String, NameplateShaderProgram>()
    private val uses = HashMap<String, Use>()
    private val vanillaPixels = HashMap<String, TexturePixels?>()
    private val logged = HashSet<String>()
    private var lastSweep = System.nanoTime()

    fun prepare(result: BundleLoad, bytes: Map<String, ByteArray>): BundleLoad {
        if (result !is BundleLoad.Loaded) {
            return result
        }
        val issues = ArrayList<BundleIssue>()
        val textures = HashMap<String, Texture>()
        for ((_, layer) in result.bundle.materialLayers) {
            for (ref in layer.material.textures) {
                if (ref !is TextureRef.Bundle || ref.id in textures) continue
                val path = texturePath(ref.id)
                decode(path, bytes[path], issues)?.let { textures[ref.id] = it }
            }
        }
        if (issues.isNotEmpty()) {
            return BundleLoad.Invalid(issues)
        }
        if (textures.isNotEmpty()) {
            prepared[result.bundle] = Prepared(textures)
        }
        return result
    }

    fun pixels(bundle: NameplateBundle, ref: TextureRef): TexturePixels? = when (ref) {
        is TextureRef.Bundle -> prepared[bundle]?.textures?.get(ref.id)?.pixels
        is TextureRef.Vanilla -> if (RenderSystem.isOnRenderThread()) vanillaPixels(ref.name) else null
        is TextureRef.Atlas -> if (RenderSystem.isOnRenderThread()) NameplateAtlasSprites.pixels(ref.sprite) else null
    }

    fun install(bundle: NameplateBundle) {
        ClientPlatform.runOnMain {
            if (bundle !in installed) {
                val assets = Prepared(prepared[bundle]?.textures ?: emptyMap())
                installed[bundle] = assets
                assets.textures.values.forEach(::acquire)
                for (key in programKeys(bundle)) {
                    uses.getOrPut(key) { Use() }.users++
                }
                bundle.materialLayers.forEach { (_, layer) -> program(layer) }
            }
        }
    }

    fun release(bundle: NameplateBundle) {
        ClientPlatform.runOnMain {
            val assets = installed.remove(bundle) ?: return@runOnMain
            assets.textures.values.forEach(::drop)
            val now = System.nanoTime()
            for (key in programKeys(bundle)) {
                val use = uses[key] ?: continue
                use.users = maxOf(0, use.users - 1)
                use.lastUsed = now
            }
        }
    }

    fun program(layer: MaterialLayer): NameplateShaderProgram {
        val key = layer.key
        if (RenderSystem.isOnRenderThread()) {
            uses.getOrPut(key) { Use() }.lastUsed = System.nanoTime()
        }
        programs[key]?.let { return it }
        val program = programs.computeIfAbsent(key) {
            NameplateShaderProgram.of(it, NameplateShaderProgram.shader(layer), layer.material.blend, layer.material.emissive)
        }
        NameplateMaterialPipelines.register(program)
        return program
    }

    fun sweep() {
        val now = System.nanoTime()
        if (now - lastSweep < SWEEP_NANOS) {
            return
        }
        lastSweep = now
        val iterator = uses.entries.iterator()
        while (iterator.hasNext()) {
            val (key, use) = iterator.next()
            if (use.users > 0 || now - use.lastUsed < GRACE_NANOS) continue
            iterator.remove()
            val program = programs.remove(key) ?: continue
            NameplateMaterialDraw.release(program.hash)
            NameplateMaterialPipelines.release(program)
            LOGGER.info("Nameplate material pipeline {} released: no loaded nameplate uses it", program.hash)
        }
    }

    fun textures(bundle: NameplateBundle, layer: MaterialLayer): List<Identifier>? {
        val refs = layer.material.textures
        if (refs.isEmpty()) {
            return emptyList()
        }
        val assets = installed[bundle] ?: return null
        assets.ids[layer]?.let { return it }
        if (layer in assets.missing) {
            return null
        }
        val ids = ArrayList<Identifier>(refs.size)
        for (ref in refs) {
            val id = when (ref) {
                is TextureRef.Bundle -> assets.textures[ref.id]?.id?.takeIf { it in registered } ?: run {
                    assets.missing += layer
                    return null
                }
                is TextureRef.Vanilla -> VANILLA[ref.name] ?: return null
                is TextureRef.Atlas -> NameplateAtlasSprites.blockAtlas()?.location() ?: return null
            }
            ids += id
        }
        assets.ids[layer] = ids
        return ids
    }

    fun atlasRects(layer: MaterialLayer): FloatArray? {
        val refs = layer.material.textures
        if (refs.none { it is TextureRef.Atlas }) {
            return null
        }
        val atlas = NameplateAtlasSprites.blockAtlas() ?: return null
        //? if >= 1.21.5 {
        val texture = atlas.texture
        val width = texture.getWidth(0)
        val height = texture.getHeight(0)
        //?}
        val rects = FloatArray(MaterialLimits.MAX_TEXTURE_SAMPLES * RECT)
        for ((k, ref) in refs.withIndex()) {
            if (ref !is TextureRef.Atlas || k >= MaterialLimits.MAX_TEXTURE_SAMPLES) continue
            val sprite = NameplateAtlasSprites.sprite(atlas, ref.sprite) ?: continue
            val at = k * RECT
            //? if < 1.21.5 {
            /*val width = sprite.contents().width() / (sprite.u1 - sprite.u0)
            val height = sprite.contents().height() / (sprite.v1 - sprite.v0)
            *///?}
            rects[at] = (sprite.u0 * width).roundToInt().toFloat()
            rects[at + 1] = (sprite.v0 * height).roundToInt().toFloat()
            rects[at + 2] = sprite.contents().width().toFloat()
            rects[at + 3] = sprite.contents().height().toFloat()
        }
        return rects
    }

    fun onResourcesReloaded() {
        vanillaPixels.clear()
        NameplateAtlasSprites.clear()
    }

    private val MaterialLayer.key: String
        get() = shader.key

    private fun programKeys(bundle: NameplateBundle): Set<String> = bundle.materialLayers.mapTo(HashSet()) { (_, layer) -> layer.key }

    private fun vanillaPixels(name: String): TexturePixels? {
        if (name in vanillaPixels) {
            return vanillaPixels[name]
        }
        val id = VANILLA[name] ?: return null
        val pixels = try {
            Minecraft.getInstance().resourceManager.open(id).use { stream ->
                NativeImage.read(stream).use { image ->
                    val argb = IntArray(image.width * image.height)
                    for (y in 0 until image.height) {
                        for (x in 0 until image.width) {
                            argb[y * image.width + x] = argbAt(image, x, y)
                        }
                    }
                    TexturePixels(image.width, image.height, argb)
                }
            }
        } catch (e: IOException) {
            logOnce("vanilla:$name", "Vanilla texture {} could not be read for the nameplate CPU fallback; it draws transparent there ({})", id, e.message)
            null
        }
        vanillaPixels[name] = pixels
        return pixels
    }

    private fun logOnce(key: String, message: String, vararg args: Any?) {
        if (logged.add(key)) {
            LOGGER.warn(message, *args)
        }
    }

    private fun texturePath(id: String): String = "textures/$id.png"

    private fun argbAt(image: NativeImage, x: Int, y: Int): Int =
        //? if >= 1.21.4 {
        image.getPixel(x, y)
        //?} else {
        /*FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y))
        *///?}

    private fun decode(path: String, data: ByteArray?, issues: MutableList<BundleIssue>): Texture? {
        if (data == null) {
            issues += BundleIssue(path, "missing")
            return null
        }
        return try {
            NativeImage.read(data).use { image ->
                val width = image.width
                val height = image.height
                if (width !in 1..MaterialLimits.MAX_TEXTURE_SIZE || height !in 1..MaterialLimits.MAX_TEXTURE_SIZE) {
                    issues += BundleIssue(path, "decodes to $width x $height; at most ${MaterialLimits.MAX_TEXTURE_SIZE} each way")
                    return null
                }
                val argb = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        argb[y * width + x] = argbAt(image, x, y)
                    }
                }
                Texture(textureId(data), width, height, argb)
            }
        } catch (e: IOException) {
            issues += BundleIssue(path, "could not be decoded: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            issues += BundleIssue(path, "could not be decoded: ${e.message}")
            null
        }
    }

    private fun textureId(data: ByteArray): Identifier {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val hex = StringBuilder(DIGEST_BYTES * HEX_DIGITS)
        for (i in 0 until DIGEST_BYTES) {
            hex.append(Integer.toHexString((digest[i].toInt() and BYTE) or (BYTE + 1)).substring(1))
        }
        return Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, TEXTURE_PATH + hex)
    }

    private fun acquire(texture: Texture) {
        val entry = registered.getOrPut(texture.id) { Registered() }
        if (entry.users++ > 0) {
            return
        }
        val image = NativeImage(texture.width, texture.height, false)
        try {
            for (y in 0 until texture.height) {
                for (x in 0 until texture.width) {
                    //? if >= 1.21.4 {
                    image.setPixel(x, y, texture.argb[y * texture.width + x])
                    //?} else {
                    /*image.setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(texture.argb[y * texture.width + x]))
                    *///?}
                }
            }
            //? if >= 1.21.5 {
            val dynamic = DynamicTexture({ texture.id.toString() }, image)
            //?} else {
            /*val dynamic = DynamicTexture(image)
            *///?}
            Minecraft.getInstance().textureManager.register(texture.id, dynamic)
        } catch (e: RuntimeException) {
            image.close()
            registered.remove(texture.id)
            LOGGER.error("Nameplate texture {} could not be uploaded; its materials use the CPU fallback", texture.id, e)
        }
    }

    private fun drop(texture: Texture) {
        val entry = registered[texture.id] ?: return
        if (--entry.users > 0) {
            return
        }
        registered.remove(texture.id)
        Minecraft.getInstance().textureManager.release(texture.id)
    }
}
//?} else {
/*import com.google.gson.JsonParseException
import net.minecraft.client.Minecraft
import net.minecraft.client.resource.metadata.AnimationMetadata
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.shader.TexturePixels
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import javax.imageio.ImageIO

object NameplateMaterialAssets {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val VANILLA_NAMESPACE = "minecraft"
    private const val MODERN_BLOCKS = "block/"
    private const val LEGACY_BLOCKS = "blocks/"
    private const val ANIMATION = "animation"
    private const val LIGHT_GRAY = "light_gray"
    private const val SILVER = "silver"

    private val COLORS = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        LIGHT_GRAY, "cyan", "purple", "blue", "brown", "green", "red", "black",
    )
    private val WOODS = mapOf("oak" to "oak", "spruce" to "spruce", "birch" to "birch", "jungle" to "jungle", "acacia" to "acacia", "dark_oak" to "big_oak")
    private val CROPS = mapOf("wheat" to 8, "carrots" to 4, "potatoes" to 4, "nether_wart" to 3, "cocoa" to 3)

    private val RENAMED: Map<String, String> = buildMap {
        for (color in COLORS) {
            val legacy = if (color == LIGHT_GRAY) SILVER else color
            put("${color}_wool", "wool_colored_$legacy")
            put("${color}_stained_glass", "glass_$legacy")
            put("${color}_stained_glass_pane_top", "glass_pane_top_$legacy")
            put("${color}_terracotta", "hardened_clay_stained_$legacy")
        }
        for ((wood, legacy) in WOODS) {
            put("${wood}_planks", "planks_$legacy")
            put("${wood}_log", "log_$legacy")
            put("${wood}_log_top", "log_${legacy}_top")
            put("${wood}_leaves", "leaves_$legacy")
            put("${wood}_sapling", if (wood == "dark_oak") "sapling_roofed_oak" else "sapling_$legacy")
            val door = if (wood == "oak") "wood" else wood
            put("${wood}_door_bottom", "door_${door}_lower")
            put("${wood}_door_top", "door_${door}_upper")
        }
        put("iron_door_bottom", "door_iron_lower")
        put("iron_door_top", "door_iron_upper")
        for ((crop, stages) in CROPS) {
            for (stage in 0 until stages) {
                put("${crop}_stage$stage", "${crop}_stage_$stage")
            }
        }
        putAll(
            listOf(
                "damaged_anvil_top" to "anvil_top_damaged_2", "chipped_anvil_top" to "anvil_top_damaged_1", "anvil_top" to "anvil_top_damaged_0",
                "anvil" to "anvil_base", "bricks" to "brick", "mossy_cobblestone" to "cobblestone_mossy", "command_block_side" to "command_block",
                "comparator" to "comparator_off", "dead_bush" to "deadbush", "podzol_side" to "dirt_podzol_side",
                "podzol_top" to "dirt_podzol_top", "dispenser_front" to "dispenser_front_horizontal",
                "dropper_front" to "dropper_front_horizontal", "end_portal_frame_eye" to "endframe_eye",
                "end_portal_frame_side" to "endframe_side", "end_portal_frame_top" to "endframe_top", "farmland" to "farmland_dry",
                "farmland_moist" to "farmland_wet", "fire_0" to "fire_layer_0", "fire_1" to "fire_layer_1", "allium" to "flower_allium",
                "blue_orchid" to "flower_blue_orchid", "dandelion" to "flower_dandelion", "azure_bluet" to "flower_houstonia",
                "oxeye_daisy" to "flower_oxeye_daisy", "poppy" to "flower_rose", "orange_tulip" to "flower_tulip_orange",
                "pink_tulip" to "flower_tulip_pink", "red_tulip" to "flower_tulip_red", "white_tulip" to "flower_tulip_white",
                "furnace_front" to "furnace_front_off", "grass_block_side" to "grass_side", "grass_block_side_overlay" to "grass_side_overlay",
                "grass_block_snow" to "grass_side_snowed", "grass_block_top" to "grass_top", "terracotta" to "hardened_clay",
                "packed_ice" to "ice_packed", "attached_melon_stem" to "melon_stem_connected", "melon_stem" to "melon_stem_disconnected",
                "attached_pumpkin_stem" to "pumpkin_stem_connected", "pumpkin_stem" to "pumpkin_stem_disconnected", "spawner" to "mob_spawner",
                "brown_mushroom_block" to "mushroom_block_skin_brown", "red_mushroom_block" to "mushroom_block_skin_red",
                "mushroom_stem" to "mushroom_block_skin_stem", "brown_mushroom" to "mushroom_brown", "red_mushroom" to "mushroom_red",
                "nether_bricks" to "nether_brick", "note_block" to "noteblock", "piston_top" to "piston_top_normal", "nether_portal" to "portal",
                "dark_prismarine" to "prismarine_dark", "prismarine" to "prismarine_rough", "carved_pumpkin" to "pumpkin_face_off",
                "jack_o_lantern" to "pumpkin_face_on", "chiseled_quartz_block" to "quartz_block_chiseled",
                "chiseled_quartz_block_top" to "quartz_block_chiseled_top", "quartz_pillar_side" to "quartz_block_lines",
                "quartz_pillar_top" to "quartz_block_lines_top", "nether_quartz_ore" to "quartz_ore", "activator_rail" to "rail_activator",
                "activator_rail_on" to "rail_activator_powered", "detector_rail" to "rail_detector", "detector_rail_on" to "rail_detector_powered",
                "powered_rail" to "rail_golden", "powered_rail_on" to "rail_golden_powered", "rail" to "rail_normal",
                "rail_corner" to "rail_normal_turned", "chiseled_red_sandstone" to "red_sandstone_carved",
                "red_sandstone" to "red_sandstone_normal", "cut_red_sandstone" to "red_sandstone_smooth",
                "chiseled_sandstone" to "sandstone_carved", "sandstone" to "sandstone_normal", "cut_sandstone" to "sandstone_smooth",
                "redstone_dust_dot" to "redstone_dust_cross", "redstone_dust_line0" to "redstone_dust_line",
                "redstone_dust_overlay" to "redstone_dust_line_overlay", "redstone_lamp" to "redstone_lamp_off",
                "redstone_torch" to "redstone_torch_on", "sugar_cane" to "reeds", "repeater" to "repeater_off", "slime_block" to "slime",
                "wet_sponge" to "sponge_wet", "andesite" to "stone_andesite", "polished_andesite" to "stone_andesite_smooth",
                "diorite" to "stone_diorite", "polished_diorite" to "stone_diorite_smooth", "granite" to "stone_granite",
                "polished_granite" to "stone_granite_smooth", "smooth_stone_slab_side" to "stone_slab_side", "smooth_stone" to "stone_slab_top",
                "stone_bricks" to "stonebrick", "chiseled_stone_bricks" to "stonebrick_carved", "cracked_stone_bricks" to "stonebrick_cracked",
                "mossy_stone_bricks" to "stonebrick_mossy", "short_grass" to "tallgrass", "torch" to "torch_on", "oak_trapdoor" to "trapdoor",
                "tripwire" to "trip_wire", "tripwire_hook" to "trip_wire_source", "lily_pad" to "waterlily", "cobweb" to "web",
                "large_fern_bottom" to "double_plant_fern_bottom", "large_fern_top" to "double_plant_fern_top",
                "tall_grass_bottom" to "double_plant_grass_bottom", "tall_grass_top" to "double_plant_grass_top",
                "peony_bottom" to "double_plant_paeonia_bottom", "peony_top" to "double_plant_paeonia_top",
                "rose_bush_bottom" to "double_plant_rose_bottom", "rose_bush_top" to "double_plant_rose_top",
                "sunflower_back" to "double_plant_sunflower_back", "sunflower_bottom" to "double_plant_sunflower_bottom",
                "sunflower_front" to "double_plant_sunflower_front", "sunflower_top" to "double_plant_sunflower_top",
                "lilac_bottom" to "double_plant_syringa_bottom", "lilac_top" to "double_plant_syringa_top", "item_frame" to "itemframe_background",
            ),
        )
    }

    private val VANILLA: Map<String, Identifier> = mapOf(
        "end_sky" to vanilla("textures/environment/end_sky.png"),
        "end_portal" to vanilla("textures/entity/end_portal.png"),
        "glint" to vanilla("textures/misc/enchanted_item_glint.png"),
        "beacon_beam" to vanilla("textures/entity/beacon_beam.png"),
        "creeper_armor" to vanilla("textures/entity/creeper/creeper_armor.png"),
    )

    private val prepared: MutableMap<NameplateBundle, Map<String, TexturePixels>> = Collections.synchronizedMap(WeakHashMap())
    private val vanillaPixels = HashMap<String, TexturePixels?>()
    private val atlasPixels = HashMap<String, TexturePixels?>()
    private val logged = HashSet<String>()

    fun prepare(result: BundleLoad, bytes: Map<String, ByteArray>): BundleLoad {
        if (result !is BundleLoad.Loaded) {
            return result
        }
        val issues = ArrayList<BundleIssue>()
        val textures = HashMap<String, TexturePixels>()
        for ((_, layer) in result.bundle.materialLayers) {
            for (ref in layer.material.textures) {
                if (ref !is TextureRef.Bundle || ref.id in textures) continue
                val path = "textures/${ref.id}.png"
                decode(path, bytes[path], issues)?.let { textures[ref.id] = it }
            }
        }
        if (issues.isNotEmpty()) {
            return BundleLoad.Invalid(issues)
        }
        if (textures.isNotEmpty()) {
            prepared[result.bundle] = textures
        }
        return result
    }

    fun pixels(bundle: NameplateBundle, ref: TextureRef): TexturePixels? = when (ref) {
        is TextureRef.Bundle -> prepared[bundle]?.get(ref.id)
        is TextureRef.Vanilla -> if (Minecraft.getInstance().isOnSameThread) vanillaPixels(ref.name) else null
        is TextureRef.Atlas -> if (Minecraft.getInstance().isOnSameThread) atlasPixels(ref.sprite) else null
    }

    fun atlasSprite(name: String): String {
        val namespace = name.substringBefore(':', VANILLA_NAMESPACE)
        val path = name.substringAfter(':')
        val legacy = if (path.startsWith(MODERN_BLOCKS)) LEGACY_BLOCKS + path.removePrefix(MODERN_BLOCKS).let { RENAMED[it] ?: it } else path
        return "$namespace:$legacy"
    }

    private fun vanillaPixels(name: String): TexturePixels? {
        if (name in vanillaPixels) {
            return vanillaPixels[name]
        }
        val id = VANILLA[name]
        val pixels = if (id == null) {
            logOnce("vanilla:$name", "Vanilla texture {} does not exist on 1.8.9; nameplate materials draw it transparent", name)
            null
        } else {
            read(id)
        }
        vanillaPixels[name] = pixels
        return pixels
    }

    private fun atlasPixels(name: String): TexturePixels? {
        if (name in atlasPixels) {
            return atlasPixels[name]
        }
        val namespace = name.substringBefore(':', VANILLA_NAMESPACE)
        val path = name.substringAfter(':')
        val legacy = if (path.startsWith(MODERN_BLOCKS)) LEGACY_BLOCKS + path.removePrefix(MODERN_BLOCKS).let { RENAMED[it] ?: it } else path
        val pixels = sprite(Identifier.fromNamespaceAndPath(namespace, "textures/$legacy.png"))
        atlasPixels[name] = pixels
        return pixels
    }

    private fun read(id: Identifier): TexturePixels? = try {
        val image = Minecraft.getInstance().resourceManager.getResource(id).asStream().use(ImageIO::read)
        if (image == null) {
            logOnce("read:$id", "Texture {} could not be decoded for the nameplate CPU fallback; it draws transparent there", id)
            null
        } else {
            pixelsOf(image, image.height)
        }
    } catch (e: IOException) {
        logOnce("read:$id", "Texture {} could not be read for the nameplate CPU fallback; it draws transparent there ({})", id, e.message)
        null
    }

    private fun sprite(id: Identifier): TexturePixels? = try {
        val resource = Minecraft.getInstance().resourceManager.getResource(id)
        val image = resource.asStream().use(ImageIO::read)
        val animation: AnimationMetadata? = resource.getMetadata(ANIMATION)
        if (image == null) {
            logOnce("read:$id", "Texture {} could not be decoded for the nameplate CPU fallback; it draws transparent there", id)
            null
        } else {
            spritePixels(image, animation)
        }
    } catch (e: IOException) {
        logOnce("read:$id", "Texture {} could not be read for the nameplate CPU fallback; it draws transparent there ({})", id, e.message)
        null
    } catch (e: JsonParseException) {
        logOnce("read:$id", "Texture {} has unreadable animation data; it draws transparent on the nameplate CPU fallback ({})", id, e.message)
        null
    } catch (e: IllegalStateException) {
        logOnce("read:$id", "Texture {} has unreadable animation data; it draws transparent on the nameplate CPU fallback ({})", id, e.message)
        null
    }

    private fun spritePixels(image: BufferedImage, animation: AnimationMetadata?): TexturePixels {
        val width = image.width
        val total = maxOf(1, image.height / width)
        if (animation == null || total == 1) {
            return pixelsOf(image, minOf(width, image.height))
        }
        val frames = ArrayList<IntArray>()
        if (animation.frameCount == 0) {
            for (index in 0 until total) {
                frames += intArrayOf(index, animation.defaultFrameTime)
            }
        } else {
            for (k in 0 until animation.frameCount) {
                frames += intArrayOf(animation.getFrameIndex(k), animation.getFrameTime(k))
            }
        }
        val valid = frames.filter { (index, time) -> time > 0 && index in 0 until total }
        if (valid.size <= 1) {
            return pixelsOf(image, width)
        }
        val used = valid.map { it[0] }.distinct().sorted()
        val size = width * width
        val argb = IntArray(size * used.size)
        for ((slot, frame) in used.withIndex()) {
            image.getRGB(0, frame * width, width, width, argb, slot * size, width)
        }
        val sequence = IntArray(valid.size) { used.indexOf(valid[it][0]) }
        return TexturePixels(width, width, argb, sequence, IntArray(valid.size) { valid[it][1] })
    }

    private fun pixelsOf(image: BufferedImage, height: Int): TexturePixels {
        val width = image.width
        return TexturePixels(width, height, image.getRGB(0, 0, width, height, null, 0, width))
    }

    private fun decode(path: String, data: ByteArray?, issues: MutableList<BundleIssue>): TexturePixels? {
        if (data == null) {
            issues += BundleIssue(path, "missing")
            return null
        }
        return try {
            val image = ByteArrayInputStream(data).use(ImageIO::read)
            when {
                image == null -> {
                    issues += BundleIssue(path, "could not be decoded: not a PNG")
                    null
                }
                image.width !in 1..MaterialLimits.MAX_TEXTURE_SIZE || image.height !in 1..MaterialLimits.MAX_TEXTURE_SIZE -> {
                    issues += BundleIssue(path, "decodes to ${image.width} x ${image.height}; at most ${MaterialLimits.MAX_TEXTURE_SIZE} each way")
                    null
                }
                else -> pixelsOf(image, image.height)
            }
        } catch (e: IOException) {
            issues += BundleIssue(path, "could not be decoded: ${e.message}")
            null
        }
    }

    private fun logOnce(key: String, message: String, vararg args: Any?) {
        if (logged.add(key)) {
            LOGGER.warn(message, *args)
        }
    }

    private fun vanilla(path: String): Identifier = Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, path)
}
*///?}
