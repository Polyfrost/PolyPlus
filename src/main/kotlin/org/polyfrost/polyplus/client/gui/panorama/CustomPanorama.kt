//? if >= 1.21.11 {
package org.polyfrost.polyplus.client.gui.panorama

import com.mojang.blaze3d.platform.NativeImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.userAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.MenuPanorama
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.gui.PolyPlusOnboardingScreen
import org.polyfrost.polyplus.client.gui.mainMenuPanoramaEnabled
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

private class PanoramaPack(val cacheKey: String, val url: String, val sha1: String)

object CustomPanorama {
    private val LOGGER = LogManager.getLogger("${PolyPlusConstants.NAME}/Panorama")

    private const val FACES = 6
    private const val ENTRY_PREFIX = "assets/minecraft/textures/gui/title/background/"
    private const val OVERLAY_FILE = "panorama_overlay.png"

    private val CUBE_MAP_ID: Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/gui/title/background/panorama")
    private val OVERLAY_ID: Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/gui/title/background/panorama_overlay.png")

    private val VANILLA_CUBE_MAP_ID: Identifier =
        Identifier.withDefaultNamespace("textures/gui/title/background/panorama")

    private val PACK: PanoramaPack? =
        //? if >= 26.2 {
        /*PanoramaPack(
            cacheKey = "alt-chaos-cubed-1.1.2",
            url = "https://cdn.modrinth.com/data/abtyNK6x/versions/VNOEJAJZ/Alt%20Chaos%20Cubed%20Panorama%201.1%20Shader.zip",
            sha1 = "ea2d2987f2bae1d70f3bab797655508e5eebe419",
        )
        *///?} elif >= 26.1 {
        null
        //?} else {
        /*PanoramaPack(
            cacheKey = "mounts-of-mayhem-1.21.11",
            url = "https://cdn.modrinth.com/data/zm2Mso0b/versions/11MWbQX8/Shader%20Panorama%20of%201.21.11%EF%BC%9AMounts%20of%20Mayhem.zip",
            sha1 = "c82dbfa467713065a04161d6396433c7b2d82f80",
        )
        *///?}

    private val http by lazy {
        HttpClient(CIO) {
            defaultRequest { userAgent("${PolyPlusConstants.NAME}/${PolyPlusConstants.VERSION}") }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    private val started = AtomicBoolean(false)

    @Volatile
    private var cubeMapReady = false

    @Volatile
    private var overlayReady = false

    @JvmStatic
    fun isAvailable(): Boolean = PACK != null

    @JvmStatic
    fun initialize() {
        val pack = PACK ?: return
        if (!PolyPlusMainMenuConfig.customPanorama) return
        if (cubeMapReady) return
        if (!started.compareAndSet(false, true)) return

        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            try {
                prepare(pack)
            } catch (t: Throwable) {
                started.set(false)
                LOGGER.warn("Failed to prepare the custom main menu panorama", t)
            }
        }
    }

    @JvmStatic
    fun cubeMapTexture(original: Identifier): Identifier =
        if (cubeMapReady && original == VANILLA_CUBE_MAP_ID && shouldApply()) CUBE_MAP_ID else original

    @JvmStatic
    fun overlayTexture(original: Identifier): Identifier =
        if (overlayReady && shouldApply()) OVERLAY_ID else original

    private fun shouldApply(): Boolean {
        if (!PolyPlusMainMenuConfig.customPanorama) return false
        val screen =
            //? if >= 26.2 {
            /*Minecraft.getInstance().gui.screen()
            *///?} else {
            Minecraft.getInstance().screen
            //?}
        return when (screen) {
            is PolyPlusMainMenuScreen -> mainMenuPanoramaEnabled()
            is PolyPlusOnboardingScreen -> true
            else -> MenuPanorama.menusActive() && mainMenuPanoramaEnabled()
        }
    }

    private suspend fun prepare(pack: PanoramaPack) {
        val dir = cacheDir(pack)
        if (isUnpacked(dir) && !facesUsable(dir)) {
            LOGGER.warn(
                "Cached main menu panorama pack {} has unusable faces {}; discarding it and downloading again",
                pack.cacheKey, faceSizes(dir),
            )
            purge(dir)
        }
        if (!isUnpacked(dir)) {
            LOGGER.info("Downloading main menu panorama pack {}", pack.cacheKey)
            val bytes = http.get(pack.url) { timeout { requestTimeoutMillis = 300_000 } }.bodyAsBytes()
            val digest = sha1Hex(bytes)
            check(digest == pack.sha1) {
                "Panorama pack ${pack.cacheKey} checksum mismatch (expected ${pack.sha1}, got $digest)"
            }
            unpack(bytes, dir)
        }
        register(dir)
    }

    private fun cacheDir(pack: PanoramaPack): Path =
        File(PolyPlusConstants.NAME).resolve("panorama").resolve(pack.cacheKey).toPath()

    private fun isUnpacked(dir: Path): Boolean =
        (0 until FACES).all { Files.isRegularFile(dir.resolve(faceName(it))) }

    private fun faceName(index: Int): String = "panorama_$index.png"

    private fun faceSizes(dir: Path): List<Pair<Int, Int>?> =
        (0 until FACES).map { PanoramaFaces.readPngSize(dir.resolve(faceName(it))) }

    private fun facesUsable(dir: Path): Boolean = PanoramaFaces.facesUsable(faceSizes(dir))

    private fun unpack(bytes: ByteArray, dir: Path) {
        val wanted = buildSet {
            for (face in 0 until FACES) add(faceName(face))
            add(OVERLAY_FILE)
        }
        Files.createDirectories(dir)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.removePrefix(ENTRY_PREFIX)
                if (!entry.isDirectory && entry.name.startsWith(ENTRY_PREFIX) && name in wanted) {
                    Files.newOutputStream(dir.resolve(name)).use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        check(isUnpacked(dir)) { "Panorama pack is missing one or more panorama faces" }
        check(facesUsable(dir)) {
            "Panorama pack faces must all be square and the same size, got ${faceSizes(dir)}"
        }
    }

    private fun register(dir: Path) {
        val loaded = ClientPlatform.runOnMainSync {
            runCatching {
                val textureManager = Minecraft.getInstance().textureManager
                textureManager.registerAndLoad(CUBE_MAP_ID, DiskCubeMapTexture(CUBE_MAP_ID, dir))
                cubeMapReady = true

                val overlay = dir.resolve(OVERLAY_FILE)
                if (Files.isRegularFile(overlay)) {
                    val image = Files.newInputStream(overlay).use(NativeImage::read)
                    textureManager.register(OVERLAY_ID, DynamicTexture({ OVERLAY_ID.toString() }, image))
                    overlayReady = true
                }
            }.onFailure {
                LOGGER.warn("Discarding unusable main menu panorama pack at {}", dir, it)
            }.isSuccess
        }

        if (!loaded) {
            cubeMapReady = false
            overlayReady = false
            purge(dir)
            started.set(false)
            return
        }
        LOGGER.info("Custom main menu panorama loaded from {}", dir)
    }

    private fun purge(dir: Path) {
        runCatching {
            for (face in 0 until FACES) Files.deleteIfExists(dir.resolve(faceName(face)))
            Files.deleteIfExists(dir.resolve(OVERLAY_FILE))
        }
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
//?}
