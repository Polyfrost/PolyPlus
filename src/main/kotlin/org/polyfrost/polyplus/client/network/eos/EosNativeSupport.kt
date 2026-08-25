package org.polyfrost.polyplus.client.network.eos

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import javax.management.ObjectName
import org.apache.logging.log4j.LogManager

object EosNativeSupport {
    private val LOGGER = LogManager.getLogger()

    private val BUNDLED_PLATFORMS = setOf(
        "windows-x86_64",
        "linux-x86_64",
        "macos-x86_64",
        "macos-aarch64",
    )

    private val REQUIRED_X86_FEATURES = setOf("sse3")

    private val FEATURE_ALIASES = mapOf(
        "pni" to "sse3",
        "sse4_1" to "sse4.1",
        "sse4_2" to "sse4.2",
    )

    private val osName: String = System.getProperty("os.name").orEmpty()
    private val osArch: String = System.getProperty("os.arch").orEmpty()

    val platform: String = run {
        val os = osName.lowercase()
        val arch = osArch.lowercase()
        val osKey = when {
            os.contains("win") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "macos"
            os.contains("linux") -> "linux"
            else -> "unknown"
        }
        val archKey = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch == "amd64" || arch == "x86_64" || arch == "x64" -> "x86_64"
            else -> "unknown"
        }
        "$osKey-$archKey"
    }

    val unsupportedReason: String? = run {
        when {
            platform !in BUNDLED_PLATFORMS ->
                "Poly+ multiplayer isn't supported on your processor ($osArch, $osName)."

            !platform.endsWith("-x86_64") -> null

            else -> missingX86Features()?.let { missing ->
                "Poly+ multiplayer needs a processor with ${missing.joinToString(", ") { it.uppercase() }}, " +
                    "and yours doesn't support it."
            }
        }
    }

    val isSupported: Boolean = unsupportedReason == null

    private fun missingX86Features(): Set<String>? {
        val features = readCpuFeatures()
        if (features == null) {
            LOGGER.warn("Couldn't read this CPU's extensions; letting EOS start and hoping for the best")
            return null
        }

        if ("sse2" !in features) {
            LOGGER.warn("CPU extension list looks wrong (no SSE2 in {}); letting EOS start anyway", features)
            return null
        }

        val missing = REQUIRED_X86_FEATURES - features
        return missing.ifEmpty { null }
    }

    private fun readCpuFeatures(): Set<String>? = procCpuInfoFeatures() ?: hotSpotCpuFeatures()

    private fun procCpuInfoFeatures(): Set<String>? = runCatching {
        val path = Path.of("/proc/cpuinfo")
        if (!Files.isReadable(path)) return@runCatching null
        Files.newBufferedReader(path).use { reader ->
            reader.lineSequence()
                .firstOrNull { it.startsWith("flags") }
                ?.let { parseFeatures(it.substringAfter(':')) }
        }
    }.getOrNull()

    private fun hotSpotCpuFeatures(): Set<String>? = runCatching {
        val server = ManagementFactory.getPlatformMBeanServer()
        val diagnostic = ObjectName("com.sun.management:type=DiagnosticCommand")
        val vmInfo = server.invoke(
            diagnostic,
            "vmInfo",
            arrayOf<Any?>(null),
            arrayOf("[Ljava.lang.String;"),
        ) as String

        vmInfo.lineSequence()
            .firstOrNull { it.startsWith("CPU:") }
            ?.let { parseFeatures(it) }
    }.getOrNull()

    private fun parseFeatures(raw: String): Set<String> =
        raw.split(',', ' ', '\t')
            .mapNotNull { token -> token.trim().lowercase().takeIf { it.isNotEmpty() } }
            .mapTo(mutableSetOf()) { FEATURE_ALIASES[it] ?: it }
}
