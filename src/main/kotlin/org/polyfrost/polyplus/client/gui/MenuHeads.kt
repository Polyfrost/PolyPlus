package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import net.minecraft.client.resources.DefaultPlayerSkin
//?} else {
/*import net.minecraft.client.resource.skin.DefaultSkinUtils
*///?}
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.PolyPlusClient
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object MenuHeadCache {
    private class Entry {
        var shown by mutableStateOf<ImageBitmap?>(null)

        var settled = false
    }

    private val entries = object : LinkedHashMap<UUID, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<UUID, Entry>) = size > MAX_ENTRIES
    }

    private val fetching = HashMap<UUID, Int>()

    private const val MAX_ENTRIES = 256

    private val loaders = Executors.newFixedThreadPool(3) { task ->
        Thread(task, "polyplus-account-head").apply { isDaemon = true }
    }

    private fun <T> withEntry(id: UUID, block: (Entry) -> T): T? =
        synchronized(entries) { entries[id]?.let(block) }

    fun get(id: UUID): ImageBitmap? {
        var fetch = false
        val entry = synchronized(entries) {
            val entry = entries.getOrPut(id) { Entry() }
            if (!entry.settled && id !in fetching) {
                fetching[id] = 0
                fetch = true
            }
            entry
        }
        if (fetch) startFetch(id)
        return entry.shown
    }

    private fun startFetch(id: UUID) {
        loaders.execute {
            val face = runCatching {
                val outcome = loadFaceByUuid(id)
                outcome.exceptionOrNull()?.let { HEAD_LOG.debug("Failed to load head for {}; will retry", id, it) }
                if (outcome.isSuccess) outcome.getOrNull() ?: defaultSkinFace(id) else null
            }.onFailure { HEAD_LOG.warn("Loading the head for {} failed unexpectedly; will retry", id, it) }
                .getOrNull()

            val needsFallback = face == null && withEntry(id) { it.shown == null } == true
            val fallback = if (needsFallback) runCatching { defaultSkinFace(id) }.getOrNull() else null

            val retryIn = synchronized(entries) {
                val entry = entries[id] ?: run {
                    fetching.remove(id)
                    return@synchronized null
                }
                if (face != null) {
                    entry.shown = face
                    entry.settled = true
                    fetching.remove(id)
                    return@synchronized null
                }

                if (entry.shown == null) entry.shown = fallback
                val attempts = (fetching[id] ?: 0) + 1
                fetching[id] = attempts
                if (HeadFetchPolicy.shouldRetry(attempts)) {
                    HeadFetchPolicy.retryDelayMs(attempts)
                } else {
                    HEAD_LOG.debug("Giving up on the head for {} after {} attempts", id, attempts)
                    entry.settled = true
                    fetching.remove(id)
                    null
                }
            }

            if (retryIn != null) {
                val retry = PolyPlusClient.SCOPE.launch {
                    delay(retryIn)
                    startFetch(id)
                }
                retry.invokeOnCompletion { cause ->
                    if (cause != null) synchronized(entries) { fetching.remove(id) }
                }
            }
        }
    }
}

private const val MENU_HEAD_SIZE = 64
private val HEAD_LOG = LogManager.getLogger("PolyPlus/Heads")

private fun loadFaceByUuid(uuid: UUID): Result<ImageBitmap?> {
    val url = mojangSkinUrl(uuid).getOrElse { return Result.failure(it) } ?: return Result.success(null)
    val skin = runCatching { ImageIO.read(URI(url).toURL()) }
        .getOrElse { return Result.failure(it) }
    if (skin == null) {
        HEAD_LOG.debug("Skin texture at {} was not decodable for {}", url, uuid)
        return Result.success(null)
    }
    return Result.success(
        runCatching { buildFace(skin) }
            .onFailure { HEAD_LOG.debug("Skin texture at {} is not a usable skin for {}", url, uuid, it) }
            .getOrNull(),
    )
}

internal fun defaultSkinFace(uuid: UUID): ImageBitmap? = runCatching {
    //? if > 1.8.9 {
    val skinAsset = DefaultPlayerSkin.get(uuid)
    //? if >= 1.21.10 {
    val location = skinAsset.body().texturePath()
    //?} else {
    /*val location = skinAsset.texture()
    *///?}
    val manager = Minecraft.getInstance().resourceManager
    val resource = manager.getResource(location).orElse(null) ?: return null
    val skin = resource.open().use { ImageIO.read(it) } ?: return null
    //?} else {
    /*val location = DefaultSkinUtils.getDefaultSkin(uuid)
    val resource = Minecraft.getInstance().resourceManager.getResource(location)
    val skin = resource.asStream().use { ImageIO.read(it) } ?: return null
    *///?}
    buildFace(skin)
}.getOrNull()

private fun mojangSkinUrl(uuid: UUID): Result<String?> = runCatching {
    val id = uuid.toString().replace("-", "")
    val body = httpGetString("https://sessionserver.mojang.com/session/minecraft/profile/$id")
        ?: run {
            HEAD_LOG.debug("No Mojang profile for {} (offline, or not a premium account)", uuid)
            return Result.success(null)
        }
    val textures = PolyPlusClient.JSON.parseToJsonElement(body).jsonObject["properties"]?.jsonArray
        ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == "textures" }
        ?.jsonObject?.get("value")?.jsonPrimitive?.content
        ?: return Result.success(null)
    PolyPlusClient.JSON.parseToJsonElement(String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8))
        .jsonObject["textures"]?.jsonObject?.get("SKIN")?.jsonObject?.get("url")?.jsonPrimitive?.content
}.onFailure { HEAD_LOG.debug("Failed to read Mojang profile for {}", uuid, it) }

// NOTE: authlib's fetchProfile swallows connection errors and 5xx as "no profile"
private fun httpGetString(url: String): String? {
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val code = conn.responseCode
        when {
            code == 200 -> conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            HeadFetchPolicy.isDefinitiveMiss(code) -> {
                HEAD_LOG.debug("GET {} returned {}", url, code)
                null
            }
            else -> throw IOException("GET $url returned $code")
        }
    } finally {
        conn.disconnect()
    }
}

private fun buildFace(skin: BufferedImage): ImageBitmap {
    val size = MENU_HEAD_SIZE
    //? if > 1.8.9 {
    val hatClearable = skin.height <= 32
    //?} else {
    /*val hatClearable = true
    *///?}
    val showHat = !hatClearable || !isOpaque(skin, 32, 0, 64, 32)
    val out = ByteArray(size * size * 4)
    for (y in 0 until size) {
        val oy = y * 8 / size
        for (x in 0 until size) {
            val ox = x * 8 / size
            val base = skin.getRGB(8 + ox, 8 + oy)
            val hat = if (showHat) runCatching { skin.getRGB(40 + ox, 8 + oy) }.getOrDefault(0) else 0
            val argb = if ((hat ushr 24) != 0) hat else base
            val i = (y * size + x) * 4
            out[i] = (argb ushr 16).toByte()     // R
            out[i + 1] = (argb ushr 8).toByte()  // G
            out[i + 2] = argb.toByte()           // B
            out[i + 3] = 0xFF.toByte()           // A
        }
    }
    return PolyBitmap(size, size, out).asComposeImageBitmap()
}

private fun isOpaque(skin: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
    for (x in x0 until minOf(x1, skin.width)) {
        for (y in y0 until minOf(y1, skin.height)) {
            if (skin.getRGB(x, y) ushr 24 < 128) return false
        }
    }
    return true
}
