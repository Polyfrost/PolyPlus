package org.polyfrost.polyplus.client.cosmetics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.cosmetics.assets.readAtMost
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

object DevBundles {
    private val LOGGER = LogManager.getLogger("polyplus/dev-bundles")
    private const val FOLDER = "polyplus-dev-bundles"
    private const val ZIP = ".zip"
    private const val PNG = ".png"
    private const val GLOW_PNG = "_glow.png"
    private const val MANIFEST = "manifest.json"
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val GEOMETRY = ".geo.json"
    private const val SKIPPED_PREFIX = "._"
    private const val ID_BASE = 2_000_000_000
    private const val ID_MASK = 0x3FFFFFF

    class Loaded(val definition: CosmeticDefinition, val slot: BodySlot?, val installed: Boolean)

    private val loaded = LinkedHashMap<String, Loaded>()

    val folder: Path get() = FabricLoader.getInstance().gameDir.resolve(FOLDER)

    fun names(): List<String> {
        val root = folder
        if (!root.isDirectory()) return emptyList()
        return Files.list(root).use { paths ->
            paths.filter { it.isDirectory() || it.name.endsWith(ZIP) }
                .map { it.name.removeSuffix(ZIP) }
                .toList()
        }.distinct().sorted()
    }

    fun textureVariants(name: String): List<String> {
        val dir = folder.resolve(name)
        if (!dir.isDirectory()) return emptyList()
        val stems = Files.list(dir).use { paths ->
            paths.map { it.name.lowercase() }
                .filter { it.endsWith(PNG) && !it.endsWith(GLOW_PNG) }
                .map { it.removeSuffix(PNG) }
                .toList()
        }
        return stems.filter { stem -> '_' in stem && stems.count { it.startsWith(stem.substringBeforeLast('_') + "_") } > 1 }
            .map { it.substringAfterLast('_') }
            .sorted()
    }

    fun loadedNames(): List<String> = synchronized(loaded) { loaded.keys.toList() }

    fun get(name: String): Loaded? = synchronized(loaded) { loaded[name] }

    fun playEmote(emoteId: Int, anchor: EmoteAnchor?): Boolean {
        if (synchronized(loaded) { loaded.values.none { it.definition.id == emoteId } }) return false
        DuoEmotes.stopBeforeLocalPlay()
        CosmeticSync.playLocalDevEmote(emoteId, anchor)
        return true
    }

    suspend fun load(name: String, slotName: String?, variant: String? = null): Result<Loaded> = runCatching {
        val bytes = read(name)
        val manifest = manifest(bytes)
        val slotOverride = slotName?.let { wanted ->
            BodySlot.entries.firstOrNull { it.serializedName == wanted && BodySlot.isEquippableSlot(it) }
                ?: error("There is no slot called $wanted")
        }
        val (type, slot) = classify(name, manifest, slotOverride)
        if (type == CosmeticType.Cape && manifest == null && hasGeometry(bytes)) {
            error("$name is a 3D model with no manifest, so the cape slot can't show it. Add a manifest, or load it in the backpack slot.")
        }
        val id = ID_BASE + ((if (variant == null) name else "$name:$variant").hashCode() and ID_MASK)
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val definition = CosmeticDefinition(
            id = id,
            type = type,
            name = name,
            hash = hash,
            allowedSlots = listOfNotNull(slot),
            variantName = variant ?: name,
            variantKey = variant,
        )
        val installed = CosmeticAssetCache.installLocal(definition, bytes)
        val result = Loaded(definition, slot, installed)
        if (installed) {
            if (get(name)?.definition?.id.let { it != null && it != id }) forget(name)
            synchronized(loaded) { loaded[name] = result }
            CosmeticCatalog.addLocal(definition, slot)
            ClientPlatform.runOnMain { CosmeticSync.applyLocalActiveFromCatalog() }
        } else {
            forget(name)
        }
        result
    }.onFailure {
        LOGGER.warn("Could not load dev bundle {}", name, it)
        forget(name)
    }

    fun clear(): Int {
        synchronized(loaded) { loaded.clear() }
        val removed = CosmeticCatalog.clearLocal()
        ClientPlatform.runOnMain {
            CosmeticSync.dropLocalDevEmote()
            CosmeticSync.applyLocalActiveFromCatalog()
        }
        return removed
    }

    private fun forget(name: String) {
        val (gone, kept) = synchronized(loaded) {
            val gone = loaded.remove(name) ?: return
            gone to loaded.values.toList()
        }
        CosmeticCatalog.clearLocal()
        for (each in kept) CosmeticCatalog.addLocal(each.definition, each.slot)
        ClientPlatform.runOnMain {
            if (kept.none { it.definition.id == gone.definition.id }) CosmeticSync.dropLocalDevEmote(gone.definition.id)
            CosmeticSync.applyLocalActiveFromCatalog()
        }
    }

    private fun read(name: String): ByteArray {
        val root = folder
        val dir = root.resolve(name)
        val zip = root.resolve(name + ZIP)
        return when {
            dir.isDirectory() -> zipDirectory(dir)
            zip.isRegularFile() -> Files.readAllBytes(zip)
            else -> error("No bundle called $name in $root")
        }
    }

    private fun zipDirectory(dir: Path): ByteArray {
        val files = Files.walk(dir).use { paths ->
            paths.filter { it.isRegularFile() }
                .map { dir.relativize(it).toString().replace('\\', '/') to it }
                .filter { (entry, _) -> entry.split('/').none { it.startsWith(SKIPPED_PREFIX) } }
                .toList()
        }.sortedBy { it.first }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((entry, file) in files) {
                zip.putNextEntry(ZipEntry(entry))
                Files.copy(file, zip)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifest(bytes: ByteArray): JsonObject? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name.substringAfterLast('/') != MANIFEST) continue
                val text = zip.readAtMost(MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
                return runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            }
        }
    }

    private fun hasGeometry(bytes: ByteArray): Boolean {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return false
                if (entry.name.lowercase().endsWith(GEOMETRY)) return true
            }
        }
    }

    private fun classify(name: String, manifest: JsonObject?, slot: BodySlot?): Pair<CosmeticType, BodySlot?> {
        val kind = manifest?.get("kind")?.takeIf { it.isJsonPrimitive }?.asString
        val attach = manifest?.get("attach")?.takeIf { it.isJsonObject }?.asJsonObject
        return when (kind) {
            "nameplate" -> CosmeticType.Nameplate to BodySlot.Nameplate
            "pet" -> CosmeticType.Pet to BodySlot.Pet
            "emote" -> CosmeticType.Emote to null
            "effect" -> CosmeticType.Effect to (slot?.takeIf { it == BodySlot.EffectA || it == BodySlot.EffectB } ?: effectSlot(manifest))
            "item" -> {
                val worn = slot ?: namedSlot(name) ?: wornSlot(attach?.get("bone")?.asString)
                worn.cosmeticType() to worn
            }
            else -> {
                if (slot == null && "_emote" in "_${name.lowercase()}") return CosmeticType.Emote to null
                val worn = slot ?: error("This bundle has no manifest kind. Add a slot after its name.")
                worn.cosmeticType() to worn
            }
        }
    }

    private fun effectSlot(manifest: JsonObject): BodySlot {
        val named = manifest.get("effects")?.takeIf { it.isJsonObject }?.asJsonObject?.keySet().orEmpty()
        return if (named.any { it in MOMENTS }) BodySlot.EffectB else BodySlot.EffectA
    }

    private val MOMENTS = setOf("join", "respawn", "death", "kill")

    private val NAMED_SLOTS = listOf(
        BodySlot.Tail,
        BodySlot.Glasses,
        BodySlot.Wings,
        BodySlot.Shoulder,
        BodySlot.Aura,
        BodySlot.Hat,
        BodySlot.Backpack,
        BodySlot.Boots,
        BodySlot.LeftHand,
        BodySlot.RightHand,
    )

    private fun namedSlot(name: String): BodySlot? {
        val padded = "_${name.lowercase()}_"
        return NAMED_SLOTS.firstOrNull { "_${it.serializedName}_" in padded }
    }

    private fun wornSlot(bone: String?): BodySlot = when (bone) {
        "head", "hat" -> BodySlot.Hat
        "rightArm", "right_arm" -> BodySlot.RightHand
        "leftArm", "left_arm" -> BodySlot.LeftHand
        "rightLeg", "leftLeg", "right_leg", "left_leg" -> BodySlot.Boots
        else -> BodySlot.Backpack
    }
}
