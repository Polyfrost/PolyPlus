package org.polyfrost.polyplus.client.features

import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.nio.file.Files
import java.nio.file.Paths
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.oneconfig.api.notifications.v1.NotificationAction
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.RamGuideScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import oshi.SystemInfo
import oshi.util.platform.mac.SysctlUtil

object JvmAdvisor {
    private val logger = LogManager.getLogger("PolyPlus/JvmAdvisor")

    const val PRESSURE_UNKNOWN = 0
    const val PRESSURE_NORMAL = 1
    const val PRESSURE_WARN = 2
    const val PRESSURE_CRITICAL = 4
    const val RESERVED_SYSTEM_MB = 2048L
    const val HEAP_TIGHT_RATIO = 0.70
    const val HEAP_LOOSE_RATIO = 0.35
    const val HEAP_OVERSIZED_RATIO = 0.5
    const val GC_TIME_BUDGET = 0.02
    const val ZGC_MIN_CORES = 12
    const val ZGC_MIN_HEAP_MB = 8192L
    const val MIN_GC_SPIKE_RATIO = 0.5
    const val HEAP_STEP_MB = 2048L
    const val LINUX_PSI_PERCENT = 10.0
    const val WINDOWS_LOAD_WARN = 90
    const val WINDOWS_LOAD_CRITICAL = 95
    private const val MAC_PRESSURE_SYSCTL = "kern.memorystatus_vm_pressure_level"
    private const val LINUX_PSI_PATH = "/proc/pressure/memory"
    private const val SAMPLE_WINDOW_MS = 5L * 60L * 1000L
    private const val WARMUP_FRAMES = 120L
    private const val SPIKE_FACTOR = 2.0
    private const val ADVICE_INTERVAL_MS = 2L * 24L * 60L * 60L * 1000L
    private const val NOTIFICATION_TITLE = "OneClient RAM Analysis"
    private const val NOTIFICATION_DURATION_MS = 60_000f

    enum class Collector { G1, ZGC, SHENANDOAH, PARALLEL, SERIAL, UNKNOWN }

    enum class Kind { RAISE_HEAP, LOWER_HEAP, FREE_SYSTEM_MEMORY, SWITCH_TO_ZGC, SWITCH_TO_G1 }

    data class HostMemory(
        val totalMb: Long,
        val availableMb: Long,
        val pressure: Int,
    )

    data class Snapshot(
        val maxHeapMb: Long,
        val liveSetMb: Long,
        val nonHeapMb: Long,
        val gcTimeFraction: Double,
        val collector: Collector,
        val cores: Int,
        val gcSpikeRatio: Double,
        val host: HostMemory?,
    )

    data class Advice(
        val kind: Kind,
        val currentMb: Long,
        val suggestedMb: Long,
        val message: String,
    )

    @JvmStatic
    fun evaluate(s: Snapshot): Advice? {
        val host = s.host

        if (host != null && host.pressure >= PRESSURE_WARN) {
            val footprintMb = s.maxHeapMb + s.nonHeapMb
            val budgetMb = host.totalMb - RESERVED_SYSTEM_MB
            if (budgetMb > 0 && footprintMb > budgetMb) {
                val suggested = (budgetMb - s.nonHeapMb).coerceAtLeast(0L)
                if (s.liveSetMb <= 0 || suggested > s.liveSetMb) {
                    return Advice(
                        Kind.LOWER_HEAP,
                        s.maxHeapMb,
                        suggested,
                        "To boost performance and decrease FPS spikes, we recommend lowering your allocated " +
                            "memory from ${s.maxHeapMb} MB to $suggested MB. Currently, your Minecraft is " +
                            "allocating more RAM than is required, which is killing your total computer " +
                            "RAM and affecting performance across the board.",
                    )
                }
            }
            return Advice(
                Kind.FREE_SYSTEM_MEMORY,
                s.maxHeapMb,
                s.maxHeapMb,
                "To boost performance and decrease FPS spikes, we recommend closing other apps on your " +
                    "computer that are taking up RAM. Currently, your RAM is full, which is affecting " +
                    "performance across the board. However, we do not recommend changing your Minecraft " +
                    "RAM allocation.",
            )
        }

        if (s.gcSpikeRatio in 0.0..<MIN_GC_SPIKE_RATIO) return null

        if (s.liveSetMb > 0 && s.liveSetMb > s.maxHeapMb * HEAP_TIGHT_RATIO) {
            if (host == null) return null
            if (host.availableMb < HEAP_STEP_MB + RESERVED_SYSTEM_MB) {
                return Advice(
                    Kind.FREE_SYSTEM_MEMORY,
                    s.maxHeapMb,
                    s.maxHeapMb,
                    "To boost performance and decrease FPS spikes, we recommend closing some background " +
                        "apps. Minecraft is running low on RAM, and closing apps would allow Minecraft to " +
                        "use more RAM.",
                )
            }
            return Advice(
                Kind.RAISE_HEAP,
                s.maxHeapMb,
                s.maxHeapMb + HEAP_STEP_MB,
                "To boost performance and decrease FPS spikes, we recommend raising Minecraft's allocated " +
                    "RAM from ${s.maxHeapMb} MB to ${s.maxHeapMb + HEAP_STEP_MB} MB. Your computer can " +
                    "support more RAM to Minecraft, and Minecraft would benefit from the increased RAM.",
            )
        }

        if (host != null &&
            s.liveSetMb > 0 &&
            s.liveSetMb < s.maxHeapMb * HEAP_LOOSE_RATIO &&
            s.maxHeapMb > host.totalMb * HEAP_OVERSIZED_RATIO
        ) {
            val suggested = (s.maxHeapMb - HEAP_STEP_MB).coerceAtLeast(s.liveSetMb * 2)
            if (suggested < s.maxHeapMb) {
                return Advice(
                    Kind.LOWER_HEAP,
                    s.maxHeapMb,
                    suggested,
                    "We recommend lowering Minecraft's allocated RAM from ${s.maxHeapMb} MB to $suggested " +
                        "MB. Minecraft is currently taking up much less RAM than you have allocated, and " +
                        "your overall computer performance may benefit with a lower RAM allocation.",
                )
            }
        }

        if (s.collector == Collector.ZGC && (s.cores < ZGC_MIN_CORES || s.maxHeapMb < ZGC_MIN_HEAP_MB)) {
            return Advice(
                Kind.SWITCH_TO_G1,
                s.maxHeapMb,
                s.maxHeapMb,
                "To boost performance and decrease FPS spikes, we recommend swapping -XX:+UseZGC for " +
                    "-XX:+UseG1GC in your launch arguments. ZGC needs more spare cores and more spare RAM " +
                    "than your computer has, so it is costing you frames rather than saving them.",
            )
        }
        return null
    }

    private var lastFrameNanos = 0L
    private var lastGcMillis = -1L
    private var frames = 0L
    private var frameNanosSum = 0L
    private var gcMillisSum = 0L
    private var spikes = 0L
    private var gcSpikes = 0L
    private var done = false

    fun initialize() {
        eventHandler { _: FramebufferRenderEvent.End -> onFrame() }
    }

    private fun onFrame() {
        if (done) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || !mc.isWindowActive) {
            lastFrameNanos = 0L
            lastGcMillis = -1L
            return
        }
        val now = System.nanoTime()
        val gcMillis = totalGcMillis()

        if (lastFrameNanos != 0L) {
            val frameNanos = now - lastFrameNanos
            frames++
            frameNanosSum += frameNanos
            if (lastGcMillis >= 0) gcMillisSum += gcMillis - lastGcMillis
            if (frames > WARMUP_FRAMES) {
                val meanNanos = frameNanosSum.toDouble() / frames
                if (frameNanos > meanNanos * SPIKE_FACTOR) {
                    spikes++
                    if (lastGcMillis >= 0 && gcMillis > lastGcMillis) gcSpikes++
                }
            }
        }
        lastFrameNanos = now
        lastGcMillis = gcMillis

        if (frameNanosSum / 1_000_000L >= SAMPLE_WINDOW_MS) {
            done = true
            report()
        }
    }

    private fun report() {
        val advice = runCatching { evaluate(snapshot()) }
            .onFailure { logger.warn("Could not evaluate JVM memory advice", it) }
            .getOrNull() ?: return
        logger.info("JVM advice: {}", advice.message)

        if (!PolyPlusConfig.ramAdviceNotifications) return
        val now = System.currentTimeMillis()
        if (now - PolyPlusConfig.jvmAdviceShownAt < ADVICE_INTERVAL_MS) return
        PolyPlusConfig.jvmAdviceShownAt = now
        PolyPlusConfig.save()

        runCatching { notify(advice) }
            .onFailure { logger.warn("Could not show the JVM advice notification", it) }
    }

    private fun notify(advice: Advice) {
        val builder = Notifications.builder(NOTIFICATION_TITLE, advice.message)
            .duration(NOTIFICATION_DURATION_MS)
        if (advice.kind != Kind.FREE_SYSTEM_MEMORY) {
            builder.action(NotificationAction("How do I do this?", primary = true, onClick = { showGuide(advice) }))
        }
        builder.action(NotificationAction("Don't show again", onClick = { disable() }))
        builder.send()
    }

    private fun showGuide(advice: Advice) {
        Minecraft.getInstance().execute { ClientPlatform.setScreen(RamGuideScreen(advice)) }
    }

    private fun disable() {
        PolyPlusConfig.ramAdviceNotifications = false
        PolyPlusConfig.save()
    }

    private fun snapshot(): Snapshot {
        val mb = 1024L * 1024L
        val activeMillis = frameNanosSum / 1_000_000L
        return Snapshot(
            maxHeapMb = Runtime.getRuntime().maxMemory() / mb,
            liveSetMb = liveSetMb(),
            nonHeapMb = ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage.used / mb,
            gcTimeFraction = if (activeMillis > 0) gcMillisSum.toDouble() / activeMillis else 0.0,
            collector = collector(),
            cores = Runtime.getRuntime().availableProcessors(),
            gcSpikeRatio = if (spikes > 0) gcSpikes.toDouble() / spikes else -1.0,
            host = hostMemory(),
        )
    }

    private fun totalGcMillis(): Long =
        ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0L) }

    private fun liveSetMb(): Long {
        val pools = ManagementFactory.getMemoryPoolMXBeans()
            .filter { it.type == MemoryType.HEAP }
            .mapNotNull { pool -> pool.collectionUsage?.let { pool.name to it.used } }
        val old = pools.firstOrNull { it.first.contains("Old", true) || it.first.contains("Tenured", true) }
        val used = (old ?: pools.maxByOrNull { it.second })?.second ?: return -1L
        return used / (1024L * 1024L)
    }

    private fun collector(): Collector {
        val names = ManagementFactory.getGarbageCollectorMXBeans().map { it.name }
        return when {
            names.any { it.startsWith("ZGC") } -> Collector.ZGC
            names.any { it.startsWith("G1") } -> Collector.G1
            names.any { it.startsWith("Shenandoah") } -> Collector.SHENANDOAH
            names.any { it.startsWith("PS ") } -> Collector.PARALLEL
            names.any { it == "Copy" || it == "MarkSweepCompact" } -> Collector.SERIAL
            else -> Collector.UNKNOWN
        }
    }

    private fun hostMemory(): HostMemory? = runCatching {
        val mb = 1024L * 1024L
        val memory = SystemInfo().hardware.memory
        val totalMb = memory.total / mb
        val availableMb = memory.available / mb
        HostMemory(
            totalMb = totalMb,
            availableMb = availableMb,
            pressure = systemPressure(totalMb, availableMb),
        )
    }.onFailure { logger.warn("Could not read host memory", it) }.getOrNull()

    private fun systemPressure(totalMb: Long, availableMb: Long): Int {
        val os = System.getProperty("os.name", "")
        return runCatching {
            when {
                os.startsWith("Mac") -> SysctlUtil.sysctl(MAC_PRESSURE_SYSCTL, PRESSURE_UNKNOWN)
                os.startsWith("Linux") -> linuxPressure(Files.readString(Paths.get(LINUX_PSI_PATH)))
                os.startsWith("Windows") -> loadPressure(totalMb, availableMb)
                else -> PRESSURE_UNKNOWN
            }
        }.getOrDefault(PRESSURE_UNKNOWN)
    }

    @JvmStatic
    fun linuxPressure(psi: String): Int {
        val avg300 = Regex("""^(some|full) .*\bavg300=([0-9.]+)""", RegexOption.MULTILINE)
            .findAll(psi)
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
        val some = avg300["some"] ?: return PRESSURE_UNKNOWN
        val full = avg300["full"] ?: return PRESSURE_UNKNOWN
        return when {
            full >= LINUX_PSI_PERCENT -> PRESSURE_CRITICAL
            some >= LINUX_PSI_PERCENT -> PRESSURE_WARN
            else -> PRESSURE_NORMAL
        }
    }

    @JvmStatic
    fun loadPressure(totalMb: Long, availableMb: Long): Int {
        if (totalMb <= 0) return PRESSURE_UNKNOWN
        val load = 100 - 100 * availableMb / totalMb
        return when {
            load >= WINDOWS_LOAD_CRITICAL -> PRESSURE_CRITICAL
            load >= WINDOWS_LOAD_WARN -> PRESSURE_WARN
            else -> PRESSURE_NORMAL
        }
    }
}
