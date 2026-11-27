package org.polyfrost.polyplus.client.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.PauseScreen
//?} else {
/*import net.minecraft.client.gui.screen.menu.GameMenuScreen as PauseScreen
import net.minecraft.client.options.GameOptions
*///?}
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.lobby.xRot
import org.polyfrost.polyplus.client.lobby.yRot
*///?}
import org.polyfrost.polyplus.client.network.http.postAuthorized
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.SparkCompat
import org.polyfrost.polyplus.libs.ktor.client.request.parameter
import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode
import org.polyfrost.polyplus.libs.ktor.http.contentType
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths

object AfkProfiler {
    private val logger = LogManager.getLogger("PolyPlus/AfkProfiler")

    private const val AFK_MILLIS = 3L * 60L * 1000L
    private const val PROFILE_SECONDS = 60
    private const val SAVE_LIMIT_MILLIS = (PROFILE_SECONDS + 60) * 1000L
    private const val MAX_BYTES = 8L * 1024L * 1024L

    private var pose: List<Double>? = null
    private var activeAt = 0L
    private var startedAt = 0L
    private var done = false
    private var restorePause: Boolean? = null
    private var restoreDynamicFps = false

    fun initialize() {
        eventHandler { _: TickEvent.End -> tick() }
    }

    private fun tick() {
        if (done || !SparkCompat.installed) return
        val now = System.currentTimeMillis()
        val current = pose()
        val afk = current != null && current == pose && !ClientPlatform.windowMinimized() && screenAllowed()
        pose = current
        if (!afk || !PerfTelemetry.enabled()) {
            activeAt = now
            if (startedAt != 0L) {
                startedAt = 0L
                release()
                SparkCompat.cancelQuietProfile()
            }
            return
        }
        if (startedAt == 0L) {
            if (now - activeAt < AFK_MILLIS) return
            hold()
            startedAt = SparkCompat.startQuietProfile(PROFILE_SECONDS) ?: 0L
            if (startedAt == 0L) {
                release()
                activeAt = now
            } else if (ClientPlatform.currentScreen() != null) {
                ClientPlatform.setScreen(null)
            }
            return
        }
        keepAwake()
        val file = SparkCompat.quietProfileFile(startedAt)
        if (file == null) {
            if (now - startedAt > SAVE_LIMIT_MILLIS) {
                startedAt = 0L
                activeAt = now
                release()
                SparkCompat.restoreBroadcast()
            }
            return
        }
        startedAt = 0L
        done = true
        release()
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) { upload(file) }
    }

    private fun screenAllowed(): Boolean {
        val screen = ClientPlatform.currentScreen() ?: return true
        return startedAt == 0L && !ClientPlatform.windowFocused() && !singleplayer() &&
            (screen is PauseScreen || screen is OneClientPauseScreen)
    }

    private fun singleplayer(): Boolean {
        //? if > 1.8.9 {
        return Minecraft.getInstance().hasSingleplayerServer()
        //?} else {
        /*return Minecraft.getInstance().isIntegratedServerRunning
        *///?}
    }

    private fun hold() {
        if (!singleplayer()) {
            val options = Minecraft.getInstance().options
            restorePause = options.pauseOnLostFocus
            options.pauseOnLostFocus = false
        }
        if (dynamicFpsCall("disabledByUser") == false) {
            restoreDynamicFps = dynamicFpsCall("toggleDisabled") != null
        }
        keepAwake()
    }

    private fun release() {
        restorePause?.let { Minecraft.getInstance().options.pauseOnLostFocus = it }
        restorePause = null
        if (restoreDynamicFps) {
            restoreDynamicFps = false
            if (dynamicFpsCall("disabledByUser") == true) dynamicFpsCall("toggleDisabled")
        }
    }

    private fun dynamicFpsClass(name: String): Class<*>? =
        listOf("dynamic_fps.impl.$name", "net.lostluma.dynamic_fps.impl.fabric.$name").firstNotNullOfOrNull {
            runCatching { Class.forName(it) }.getOrNull()
        }

    private val dynamicFpsMod: Class<*>? by lazy { dynamicFpsClass("DynamicFPSMod") }

    private fun dynamicFpsCall(name: String): Any? =
        dynamicFpsMod?.let { runCatching { it.getMethod(name).invoke(null) ?: Unit }.getOrNull() }

    private val dynamicFpsIdle: Class<*>? by lazy { dynamicFpsClass("feature.state.IdleHandler") ?: dynamicFpsClass("util.IdleHandler") }
    private val dynamicFpsActivity: Method? by lazy { runCatching { dynamicFpsIdle?.getMethod("onActivity") }.getOrNull() }
    private val dynamicFpsIsIdle: Method? by lazy { runCatching { dynamicFpsIdle?.getMethod("isIdle") }.getOrNull() }
    private val dynamicFpsDisabled: Method? by lazy { runCatching { dynamicFpsMod?.getMethod("isDisabled") }.getOrNull() }

    private fun Method?.isTrue(): Boolean = this != null && runCatching { invoke(null) == true }.getOrDefault(false)

    fun idleThrottled(): Boolean {
        //? if >= 1.21.4 {
        val mc = Minecraft.getInstance()
        if (mc.framerateLimitTracker.framerateLimit < mc.options.framerateLimit().get()) return true
        //?}
        return dynamicFpsIsIdle.isTrue() && !dynamicFpsDisabled.isTrue()
    }

    private fun keepAwake() {
        //? if >= 1.21.4
        Minecraft.getInstance().framerateLimitTracker.onInputReceived()
        dynamicFpsActivity?.let { runCatching { it.invoke(null) } }
    }

    private fun pose(): List<Double>? {
        val player = Minecraft.getInstance().player ?: return null
        return listOf(player.x, player.y, player.z, player.yRot.toDouble(), player.xRot.toDouble())
    }

    private suspend fun upload(file: String) {
        val path = Paths.get(file)
        try {
            runCatching {
                if (Files.size(path) > MAX_BYTES) return
                val vmArgs = ManagementFactory.getRuntimeMXBean().inputArguments.joinToString(" ")
                val data = scrub(Files.readAllBytes(path), System.getProperty("user.home").orEmpty(), vmArgs) ?: return
                val response = PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/account/perf/profile") {
                    parameter("pack_hash", PerfTelemetry.packHash)
                    parameter("client_version", PolyPlusConstants.VERSION)
                    parameter("minecraft_version", PolyPlusConstants.MINECRAFT_VERSION)
                    parameter("loader", PolyPlusConstants.LOADER)
                    parameter("seconds", PROFILE_SECONDS)
                    contentType(ContentType.Application.OctetStream)
                    setBody(data)
                }
                if (!response.status.isSuccess() && response.status != HttpStatusCode.TooManyRequests) {
                    logger.debug("Profile rejected: {}", response.status)
                }
            }.onFailure { logger.debug("Could not send profile", it) }
        } finally {
            runCatching { Files.deleteIfExists(path) }
        }
    }

    @JvmStatic
    fun scrub(data: ByteArray, home: String, vmArgs: String): ByteArray? {
        val written = sparkRedactions.fold(vmArgs) { args, (regex, replacement) -> args.replace(regex) { replacement } }
        if (written.isNotEmpty() && !mask(data, written.toByteArray(Charsets.UTF_8))) return null
        for (variant in setOf(home, home.replace('\\', '/'))) {
            val needle = variant.toByteArray(Charsets.UTF_8)
            if (needle.size >= 4) mask(data, needle)
        }
        return data
    }

    private val sparkRedactions = listOf(
        Regex("""C:\\Users\\\w+""") to """C:\Users\<redacted>""",
        Regex("""/Users/\w+""") to "/Users/<redacted>",
        Regex("""/home/\w+""") to "/home/<redacted>",
    )

    private fun mask(data: ByteArray, needle: ByteArray): Boolean {
        var found = false
        var i = 0
        while (i <= data.size - needle.size) {
            if (matches(data, i, needle)) {
                needle.indices.forEach { data[i + it] = '~'.code.toByte() }
                found = true
                i += needle.size
            } else i++
        }
        return found
    }

    private fun matches(data: ByteArray, at: Int, needle: ByteArray): Boolean {
        for (j in needle.indices) if (data[at + j] != needle[j]) return false
        return true
    }
}

//? if = 1.8.9 {
/*private var GameOptions.pauseOnLostFocus: Boolean
    get() = pauseOnUnfocus
    set(value) {
        pauseOnUnfocus = value
    }
*///?}
