package org.polyfrost.polyplus.client.network.eos

import gg.sona.eos.EosRtcBackgroundMode
import gg.sona.eos.EosRtcOptions
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object EosRtcSupport {
    private val LOGGER = LogManager.getLogger()

    private const val XAUDIO = "xaudio2_9redist.dll"

    val platformSupported: Boolean
        get() = EosNativeSupport.platform.startsWith("windows-") || isMac || (isLinux && PolyPlusConfig.voiceLinuxRtc)

    val isMac: Boolean get() = EosNativeSupport.platform.startsWith("macos-")

    val isLinux: Boolean get() = EosNativeSupport.platform.startsWith("linux-")

    @Volatile var unavailableReason: String? = null
        private set

    fun options(): EosRtcOptions? {
        if (!PolyPlusConfig.voiceEnabled) return unavailable("party voice is turned off")
        if (!platformSupported) return unavailable("EOS voice isn't available on ${EosNativeSupport.platform}")
        val options = EosRtcOptions().apply { backgroundMode = EosRtcBackgroundMode.KeepRoomsAlive }
        if (EosNativeSupport.platform.startsWith("windows-")) {
            options.xAudio29DllPath = extractXAudio() ?: return unavailable("$XAUDIO isn't bundled")
        }
        unavailableReason = null
        return options
    }

    fun markFailed(reason: String) {
        unavailable(reason)
    }

    private fun unavailable(reason: String): EosRtcOptions? {
        unavailableReason = reason
        LOGGER.info("EOS voice rooms are off: {}", reason)
        return null
    }

    private fun extractXAudio(): String? = runCatching {
        val stream = EosRtcSupport::class.java.classLoader.getResourceAsStream("natives/$XAUDIO") ?: return null
        val folder: Path = Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "polyplus-eos"))
        val target = folder.resolve(XAUDIO)
        stream.use { input ->
            runCatching { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
                .onFailure { if (!Files.exists(target)) throw it }
        }
        target.toAbsolutePath().toString()
    }.onFailure { LOGGER.warn("Could not extract {}", XAUDIO, it) }.getOrNull()
}
