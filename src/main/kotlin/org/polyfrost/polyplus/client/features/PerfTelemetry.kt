package org.polyfrost.polyplus.client.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.lobby.LobbySky
import org.polyfrost.polyplus.client.network.http.postAuthorized
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.contentType
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import org.polyfrost.polyplus.privacy.PrivacyConsent
//? if > 1.8.9 {
import oshi.SystemInfo
//?} else {
/*import net.minecraft.client.options.GameOptions
*///?}
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import java.util.UUID

object PerfTelemetry {
    private val logger = LogManager.getLogger("PolyPlus/PerfTelemetry")

    private const val FINE_NANOS = 50_000L
    private const val FINE_LIMIT_NANOS = 50_000_000L
    private const val COARSE_NANOS = 1_000_000L
    private const val MAX_FRAME_NANOS = 2_000_000_000L
    private const val FINE_BUCKETS = (FINE_LIMIT_NANOS / FINE_NANOS).toInt()
    private const val BUCKETS = FINE_BUCKETS + ((MAX_FRAME_NANOS - FINE_LIMIT_NANOS) / COARSE_NANOS).toInt()

    private const val WARMUP_NANOS = 15_000_000_000L
    private const val REPORT_NANOS = 5L * 60L * 1_000_000_000L
    private const val MIN_REPORT_NANOS = 60L * 1_000_000_000L

    private val histogram = IntArray(BUCKETS)
    private var frames = 0
    private var sampledNanos = 0L
    private var lastFrameNanos = 0L
    private var inWorldSince = 0L
    private var lastUploadNanos = 0L
    private var sampling = false
    private var windowAccount: UUID? = null

    @Volatile
    private var gpu: String? = null

    fun initialize() {
        val sampler: (FramebufferRenderEvent.End) -> Boolean = {
            onFrame()
            false
        }
        eventHandler(sampler)
    }

    private fun enabled(): Boolean = PolyPlusConfig.performanceTelemetry && PrivacyConsent.allowsOnlineServices()

    private fun onFrame() {
        if (!PolyPlusConfig.performanceTelemetry) {
            if (frames != 0) reset()
            lastFrameNanos = 0L
            return
        }
        val now = System.nanoTime()
        val inWorld = Minecraft.getInstance().player != null
        if (!inWorld) {
            if (inWorldSince != 0L) {
                inWorldSince = 0L
                if (sampledNanos >= MIN_REPORT_NANOS && (lastUploadNanos == 0L || now - lastUploadNanos >= REPORT_NANOS)) flush()
            }
            lastFrameNanos = 0L
            return
        }
        if (inWorldSince == 0L) {
            inWorldSince = now
            if (gpu == null) gpu = runCatching { LobbySky.gpuName() }.getOrNull()
            sampling = PrivacyConsent.allowsOnlineServices()
            val account = ClientPlatform.localPlayerUuid()
            if (!sampling || account != windowAccount) reset()
            windowAccount = account
        }
        if (!sampling || now - inWorldSince < WARMUP_NANOS || !ClientPlatform.windowFocused() || ClientPlatform.currentScreen() != null) {
            lastFrameNanos = 0L
            return
        }

        val last = lastFrameNanos
        lastFrameNanos = now
        if (last == 0L) return
        val delta = now - last
        if (delta <= 0L || delta >= MAX_FRAME_NANOS) return
        histogram[bucket(delta)]++
        frames++
        sampledNanos += delta
        if (sampledNanos >= REPORT_NANOS) flush()
    }

    @JvmStatic
    fun bucket(nanos: Long): Int =
        if (nanos < FINE_LIMIT_NANOS) (nanos / FINE_NANOS).toInt()
        else (FINE_BUCKETS + (nanos - FINE_LIMIT_NANOS) / COARSE_NANOS).toInt().coerceAtMost(BUCKETS - 1)

    private fun bucketNanos(index: Int): Long =
        if (index < FINE_BUCKETS) (index + 1L) * FINE_NANOS
        else FINE_LIMIT_NANOS + (index - FINE_BUCKETS + 1L) * COARSE_NANOS

    @JvmStatic
    fun quantileNanos(histogram: IntArray, frames: Int, quantile: Double): Long {
        val target = (frames * quantile).toLong().coerceIn(1L, frames.toLong())
        var seen = 0L
        for (i in histogram.indices) {
            seen += histogram[i]
            if (seen >= target) return bucketNanos(i)
        }
        return bucketNanos(histogram.lastIndex)
    }

    private fun reset() {
        histogram.fill(0)
        frames = 0
        sampledNanos = 0L
    }

    private fun flush() {
        if (frames == 0 || !enabled()) return reset()
        lastUploadNanos = System.nanoTime()
        val seconds = sampledNanos / 1e9
        val fps = { nanos: Long -> 1e9 / nanos }
        val window = Window(
            seconds = seconds,
            frames = frames,
            avgFps = frames / seconds,
            medianFps = fps(quantileNanos(histogram, frames, 0.5)),
            low1Fps = fps(quantileNanos(histogram, frames, 0.99)),
            low01Fps = fps(quantileNanos(histogram, frames, 0.999)),
        )
        reset()
        val settings = runCatching { settings() }.getOrNull()
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) { upload(window, settings) }
    }

    private class Window(
        val seconds: Double,
        val frames: Int,
        val avgFps: Double,
        val medianFps: Double,
        val low1Fps: Double,
        val low01Fps: Double,
    )

    private class Settings(val renderDistance: Int, val maxFps: Int?, val vsync: Boolean)

    private fun settings(): Settings {
        val options = Minecraft.getInstance().options
        //? if > 1.8.9 {
        return Settings(options.renderDistance().get(), cap(options.framerateLimit().get()), options.enableVsync().get())
        //?} else {
        /*return Settings(
            options.getFloat(GameOptions.Option.RENDER_DISTANCE).toInt(),
            cap(options.getFloat(GameOptions.Option.FRAMERATE_LIMIT).toInt()),
            options.getBoolean(GameOptions.Option.ENABLE_VSYNC),
        )
        *///?}
    }

    private fun cap(limit: Int): Int? = limit.takeIf { it < ClientPlatform.unlimitedFramerate }

    private class Hardware(val cpu: String?, val cores: Int, val ramMb: Long?)

    private val hardware: Hardware by lazy {
        Hardware(
            cpu = cpuName(),
            cores = Runtime.getRuntime().availableProcessors(),
            ramMb = runCatching {
                (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalMemorySize / MB
            }.getOrNull(),
        )
    }

    //? if > 1.8.9 {
    private fun cpuName(): String? = runCatching { SystemInfo().hardware.processor.processorIdentifier.name.trim() }.getOrNull()
    //?} else {
    /*private fun cpuName(): String? = System.getenv("PROCESSOR_IDENTIFIER")
    *///?}

    private val mods: Map<String, String> by lazy {
        FabricLoader.getInstance().allMods
            .filter { it.containingMod.isEmpty && it.origin.kind == ModOrigin.Kind.PATH }
            .associate { it.metadata.id to it.metadata.version.friendlyString }
    }

    private val packHash: String by lazy { packHash(mods) }

    @Volatile
    private var modsSent = false

    @JvmStatic
    fun packHash(mods: Map<String, String>): String {
        val canonical = mods.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}@${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private val modCount: Int by lazy { FabricLoader.getInstance().allMods.count { it.containingMod.isEmpty } }

    private val irisInUse: (() -> Boolean)? by lazy {
        runCatching {
            val api = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            val instance = api.getMethod("getInstance").invoke(null)
            val inUse = api.getMethod("isShaderPackInUse")
            ({ inUse.invoke(instance) as Boolean })
        }.getOrNull()
    }

    private suspend fun upload(window: Window, settings: Settings?) {
        if (!PolyPlusConfig.performanceTelemetry) return
        val hw = hardware
        val report = PerfReport(
            clientVersion = PolyPlusConstants.VERSION,
            minecraftVersion = PolyPlusConstants.MINECRAFT_VERSION,
            loader = PolyPlusConstants.LOADER,
            os = System.getProperty("os.name") ?: "unknown",
            osVersion = System.getProperty("os.version"),
            javaVersion = System.getProperty("java.version"),
            cpu = hw.cpu,
            cpuCores = hw.cores,
            ramMb = hw.ramMb,
            heapMb = Runtime.getRuntime().maxMemory() / MB,
            gpu = gpu,
            renderDistance = settings?.renderDistance,
            maxFps = settings?.maxFps,
            vsync = settings?.vsync,
            shaders = irisInUse?.let { runCatching(it).getOrNull() },
            modCount = modCount,
            packHash = packHash,
            mods = if (modsSent) null else mods,
            seconds = window.seconds,
            frames = window.frames,
            avgFps = window.avgFps,
            medianFps = window.medianFps,
            low1Fps = window.low1Fps,
            low01Fps = window.low01Fps,
        )
        runCatching {
            val response = PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/account/perf") {
                contentType(ContentType.Application.Json)
                setBody(report)
            }
            if (response.status.isSuccess()) modsSent = true
            else logger.debug("Performance report rejected: {}", response.status)
        }.onFailure { logger.debug("Could not send performance report", it) }
    }

    private const val MB = 1024L * 1024L

    @Serializable
    private class PerfReport(
        @SerialName("client_version") val clientVersion: String,
        @SerialName("minecraft_version") val minecraftVersion: String,
        val loader: String,
        val os: String,
        @SerialName("os_version") val osVersion: String?,
        @SerialName("java_version") val javaVersion: String?,
        val cpu: String?,
        @SerialName("cpu_cores") val cpuCores: Int,
        @SerialName("ram_mb") val ramMb: Long?,
        @SerialName("heap_mb") val heapMb: Long,
        val gpu: String?,
        @SerialName("render_distance") val renderDistance: Int?,
        @SerialName("max_fps") val maxFps: Int?,
        val vsync: Boolean?,
        val shaders: Boolean?,
        @SerialName("mod_count") val modCount: Int,
        @SerialName("pack_hash") val packHash: String,
        val mods: Map<String, String>?,
        val seconds: Double,
        val frames: Int,
        @SerialName("avg_fps") val avgFps: Double,
        @SerialName("median_fps") val medianFps: Double,
        @SerialName("low1_fps") val low1Fps: Double,
        @SerialName("low01_fps") val low01Fps: Double,
    )
}
