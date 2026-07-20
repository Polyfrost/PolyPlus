package org.polyfrost.polyplus.client

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.sentry.Sentry
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.PolyPlusConstants
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PolyPlusSentry {
    private const val DSN =
        "https://8aad59841c698c55f86ec3992b853628@o4511714343124992.ingest.us.sentry.io/4511714567979008"

    private val started = AtomicBoolean(false)

    private val seen: MutableSet<Throwable> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    fun initialize() {
        if (!started.compareAndSet(false, true)) return

        val dev = FabricLoader.getInstance().isDevelopmentEnvironment

        val minecraftVersion = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        Sentry.init { options ->
            options.dsn = DSN
            options.release = "${PolyPlusConstants.ID}@${PolyPlusConstants.VERSION}"
            options.environment = if (dev) "development" else "production"
            options.setTag("minecraft", minecraftVersion)
            // Verbose SDK logging only in dev.
            options.isDebug = dev
            options.setBeforeSend { event, _ ->
                val t = event.throwable
                if (t != null && (isTransientNetworkFailure(t) || isReporterArtifact(t) || isBenignCancellation(t) || isForeignPacketNoise(t) || isMemoryExhaustion(t))) null else event
            }
        }
    }

    @JvmStatic
    fun capture(throwable: Throwable) {
        initialize()
        if (isTransientNetworkFailure(throwable)) return
        if (!seen.add(throwable)) return
        Sentry.captureException(throwable)
    }

    private fun isTransientNetworkFailure(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause.javaClass.name.startsWith("com.mojang.authlib.exceptions.")) return true
            when (cause) {
                is ServerResponseException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException,
                is SocketTimeoutException,
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.net.UnknownHostException,
                is java.net.SocketException,
                is java.nio.channels.UnresolvedAddressException, // DNS resolution failed
                is java.io.EOFException,                          // premature close / not enough data
                is java.nio.file.FileSystemException,             // disk/fs error materializing assets
                -> return true
                is ClientRequestException ->
                    if (cause.response.status == HttpStatusCode.Unauthorized) return true
                is IllegalStateException ->
                    // Truncated HTTP body (e.g. "Content-Length ... doesn't match").
                    if (cause.message?.contains("Content-Length", ignoreCase = true) == true) return true
                is java.io.IOException ->
                    // Disk-full / out-of-space IOException while writing cosmetic assets.
                    cause.message?.let { m ->
                        if (m.contains("No space left", ignoreCase = true) ||
                            m.contains("not enough space", ignoreCase = true) ||
                            m.contains("Content-Length", ignoreCase = true)
                        ) {
                            return true
                        }
                    }
            }
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    private fun isBenignCancellation(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is java.util.concurrent.CancellationException) return true
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    /**
     * "Reporter artifacts": crash-report events uploaded by the CrashReport mixin that carry no
     * diagnostic value. Only very specific self-noise signatures are matched here; every other
     * foreign/vanilla/other-mod crash is intentionally kept.
     */
    private fun isReporterArtifact(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            // Watchdog hang-on-exit dump (ServerWatchdog/ClientShutdownWatchdog
            // createWatchdogCrashReport builds a synthetic Error("Watchdog (" + message + ")"),
            if (cause is Error && cause.message?.startsWith("Watchdog (") == true) return true

            val top = cause.stackTrace.firstOrNull()
            if (top != null) {
                val cn = top.className
                val mn = top.methodName
                // CrashReport.preload() startup warmup ("Don't panic!"): a synthetic throwable
                // whose only frame is the preload call itself — no usable application/mod frame.
                if ((cn == "net.minecraft.CrashReport" || cn == "net.minecraft.class_128") &&
                    (mn == "preload" || mn == "method_24305")
                ) {
                    return true
                }
                // Reporter self-crash while formatting someone else's crash: NPE thrown inside
                // CrashReportCategory.validateStackTrace (StackTraceElement.getFileName() == null).
                if (cause is NullPointerException &&
                    (cn == "net.minecraft.CrashReportCategory" || cn == "net.minecraft.class_129") &&
                    (mn == "validateStackTrace" || mn == "method_584")
                ) {
                    return true
                }
            }
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    private fun isForeignPacketNoise(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            val message = cause.message
            if (message?.contains("Terminal message received in bundle", ignoreCase = true) == true &&
                cause.stackTrace.any {
                    it.className == "net.minecraft.network.PacketBundlePacker" || // mojmap
                        it.className == "net.minecraft.class_8035"                  // intermediary
                }
            ) {
                return true
            }
            if (message?.contains("Failed to decode packet", ignoreCase = true) == true &&
                cause.stackTrace.any {
                    it.className == "net.minecraft.network.PacketDecoder" || // mojmap
                        it.className == "net.minecraft.class_2543"             // intermediary
                }
            ) {
                return true
            }
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    private fun isMemoryExhaustion(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is OutOfMemoryError) return true
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    @JvmStatic
    fun captureMessage(message: String) {
        initialize()
        Sentry.captureMessage(message, io.sentry.SentryLevel.ERROR)
    }

    @JvmStatic
    fun captureFatal(throwable: Throwable) {
        initialize()
        if (!Sentry.isEnabled()) return
        if (!seen.add(throwable)) return
        Sentry.captureException(throwable)
        Sentry.flush(5_000)
    }
}
