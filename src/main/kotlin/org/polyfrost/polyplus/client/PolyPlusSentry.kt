package org.polyfrost.polyplus.client

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.sentry.Attachment
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object PolyPlusSentry {
    private const val DSN =
        "https://8aad59841c698c55f86ec3992b853628@o4511714343124992.ingest.us.sentry.io/4511714567979008"

    private const val MAX_EVENTS_PER_SIGNATURE = 5

    private const val MAX_TRACKED_SIGNATURES = 500

    private const val SIGNATURE_FRAMES = 3

    private const val MAX_UNWRAP_DEPTH = 20

    private val ENTRYPOINT_MOD_ID = Regex("provided by '([^']+)'")

    private val FJP_WORKER_INDEX = Regex("worker-\\d+")

    private val MIXIN_TRANSFORM_TARGET = Regex("Mixin transformation of ([\\w.$]+) failed")

    private val MIXIN_HASH_SEGMENT = Regex("\\$[a-z]{3}\\d{3}\\$")

    private val DIGIT_RUN = Regex("\\d+")

    private const val MIXIN_HASH_PLACEHOLDER = "\$hash\$"

    private const val FINGERPRINT_FRAMES = 8

    private val started = AtomicBoolean(false)

    private val seen: MutableSet<Throwable> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    private val signatureCounts = ConcurrentHashMap<String, AtomicInteger>()

    private fun signatureOf(throwable: Throwable): String {
        val frames = throwable.stackTrace.take(SIGNATURE_FRAMES)
            .joinToString("|") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        return "${throwable.javaClass.name}@$frames"
    }

    private fun allowBySignature(throwable: Throwable): Boolean {
        val signature = runCatching { signatureOf(throwable) }.getOrNull() ?: return true
        var counter = signatureCounts[signature]
        if (counter == null) {
            if (signatureCounts.size >= MAX_TRACKED_SIGNATURES) return false
            val fresh = AtomicInteger(0)
            counter = signatureCounts.putIfAbsent(signature, fresh) ?: fresh
        }
        return counter.incrementAndGet() <= MAX_EVENTS_PER_SIGNATURE
    }

    private fun mostInformativeCause(throwable: Throwable): Throwable {
        var cause: Throwable = throwable
        var hops = 0
        while (hops++ < MAX_UNWRAP_DEPTH) {
            val next = cause.cause ?: break
            if (next === cause) break // self-referencing chain
            val isWrapper = when (cause) {
                is java.lang.reflect.InvocationTargetException,
                is java.util.concurrent.ExecutionException,
                is java.util.concurrent.CompletionException,
                is ExceptionInInitializerError,
                is BootstrapMethodError,
                -> true
                is RuntimeException -> cause.message?.let { m ->
                    m.startsWith("Could not execute entrypoint stage") ||
                        m.startsWith("Mixin transformation of")
                } == true
                else -> false
            }
            if (!isWrapper) break
            cause = next
        }
        return cause
    }

    private fun entrypointModId(throwable: Throwable): String? = runCatching {
        var cause: Throwable? = throwable
        var hops = 0
        while (cause != null && hops++ < MAX_UNWRAP_DEPTH) {
            val message = cause.message
            if (message != null && message.startsWith("Could not execute entrypoint stage")) {
                return@runCatching ENTRYPOINT_MOD_ID.find(message)?.groupValues?.getOrNull(1)
            }
            val next = cause.cause
            if (next === cause) break // self-referencing chain
            cause = next
        }
        null
    }.getOrNull()

    private fun normalizeMessage(message: String): String =
        DIGIT_RUN.replace(FJP_WORKER_INDEX.replace(message, "worker-N"), "N")

    private fun normalizeMethodName(methodName: String): String =
        MIXIN_HASH_SEGMENT.replace(methodName, MIXIN_HASH_PLACEHOLDER)

    private fun explicitFingerprint(throwable: Throwable, root: Throwable): List<String>? = runCatching {
        val messages = listOfNotNull(throwable.message, root.message.takeIf { it !== throwable.message })

        messages.firstOrNull { it.contains("ThreadLocalRandom accessed from a different thread") }?.let {
            return@runCatching listOf("c2me-threadlocalrandom", normalizeMessage(it))
        }

        entrypointModId(throwable)?.let {
            return@runCatching listOf("entrypoint-failure", it)
        }

        for (message in messages) {
            MIXIN_TRANSFORM_TARGET.find(message)?.groupValues?.getOrNull(1)?.let {
                return@runCatching listOf("mixin-transform-failure", it)
            }
        }

        root.stackTrace.take(FINGERPRINT_FRAMES).firstOrNull {
            MIXIN_HASH_SEGMENT.containsMatchIn(it.methodName)
        }?.let { frame ->
            return@runCatching listOf(
                root.javaClass.name,
                "${frame.className}.${normalizeMethodName(frame.methodName)}",
            )
        }

        null
    }.getOrNull()

    fun initialize() {
        if (!PrivacyConsent.allowsOnlineServices()) return
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
            options.isEnableUncaughtExceptionHandler = true
            options.isAttachStacktrace = true
            options.setBeforeSend { event, _ ->
                val t = event.throwable
                if (t != null && (isTransientNetworkFailure(t) || isReporterArtifact(t) || isBenignCancellation(t) || isForeignPacketNoise(t) || isMemoryExhaustion(t) || isExpectedAccountState(t))) null else event
            }
        }

        installRuntimeContext(minecraftVersion)
    }

    fun shutdown() {
        if (!started.compareAndSet(true, false)) return
        signatureCounts.clear()
        runCatching { Sentry.close() }
    }

    private fun installRuntimeContext(minecraftVersion: String) {
        runCatching {
            val loader = FabricLoader.getInstance()
            val mods = loader.allMods
                .map { "${it.metadata.id}@${it.metadata.version.friendlyString}" }
                .sorted()

            val loaderVersion = loader.getModContainer("fabricloader")
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")

            val runtime = Runtime.getRuntime()

            Sentry.configureScope { scope ->
                scope.setTag("loader", loaderVersion)
                scope.setTag("java", System.getProperty("java.version") ?: "unknown")
                scope.setTag("os", System.getProperty("os.name") ?: "unknown")
                scope.setContexts(
                    "polyplus_runtime",
                    mapOf(
                        "minecraft" to minecraftVersion,
                        "polyplus" to PolyPlusConstants.VERSION,
                        "fabric_loader" to loaderVersion,
                        "mod_count" to mods.size,
                        "java_version" to (System.getProperty("java.version") ?: "unknown"),
                        "java_vendor" to (System.getProperty("java.vendor") ?: "unknown"),
                        "os_name" to (System.getProperty("os.name") ?: "unknown"),
                        "os_version" to (System.getProperty("os.version") ?: "unknown"),
                        "os_arch" to (System.getProperty("os.arch") ?: "unknown"),
                        "max_memory_mb" to runtime.maxMemory() / (1024L * 1024L),
                    ),
                )
                scope.addAttachment(
                    Attachment(
                        mods.joinToString("\n").toByteArray(Charsets.UTF_8),
                        "modlist.txt",
                        "text/plain",
                    ),
                )
            }
        }
    }

    @JvmStatic
    fun capture(throwable: Throwable) {
        if (!PrivacyConsent.allowsOnlineServices()) return
        if (throwable is kotlinx.coroutines.CancellationException) return
        initialize()
        if (isTransientNetworkFailure(throwable)) return
        if (isExpectedAccountState(throwable)) return
        if (isBenignCancellation(throwable)) return
        if (isReporterArtifact(throwable)) return
        if (!seen.add(throwable)) return
        if (!allowBySignature(throwable)) return
        Sentry.captureException(throwable)
    }

    private fun isTransientNetworkFailure(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause.javaClass.name.startsWith("com.mojang.authlib.exceptions.")) return true
            // io.ktor.websocket ping timeout: an idle/slow socket the client just reconnects.
            if (cause.message?.contains("Ping timeout", ignoreCase = true) == true) return true
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
            if (cause.message?.contains("The coroutine scope left the composition", ignoreCase = true) == true) {
                return true
            }
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

            if (cause is RuntimeException && cause.message == "Crash requested by CrashPatch") return true

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

    private fun isExpectedAccountState(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            val name = cause.javaClass.name
            if (name.contains("UserBannedException")) return true
            if (name.contains("AuthenticationUnavailable")) return true
            if (cause is ClientRequestException) {
                when (cause.response.status) {
                    HttpStatusCode.Forbidden, HttpStatusCode.Conflict -> return true
                    else -> {}
                }
            }
            cause.message?.let { m ->
                if (m.contains("bad crack, try again.", ignoreCase = true)) return true
                if (m.contains("expected status code 101 but was 401", ignoreCase = true)) return true
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
        if (!PrivacyConsent.allowsOnlineServices()) return
        initialize()
        Sentry.captureMessage(message, io.sentry.SentryLevel.ERROR)
    }

    @JvmStatic
    fun captureFatal(throwable: Throwable) {
        if (!PrivacyConsent.allowsOnlineServices()) return
        initialize()
        if (!Sentry.isEnabled()) return
        if (!seen.add(throwable)) return
        if (!allowBySignature(throwable)) return
        val root = runCatching { mostInformativeCause(throwable) }.getOrNull()
        val id = Sentry.captureException(throwable) { scope ->
            scope.setTag("mechanism", "crash_report")
            runCatching {
                val target = root ?: throwable
                if (target !== throwable) {
                    // Keeps entrypoint failures distinguishable from mixin failures at a glance.
                    scope.setTag("wrapped_by", throwable.javaClass.simpleName)
                    entrypointModId(throwable)?.let { scope.setTag("entrypoint_mod", it) }
                }
                val top = target.stackTrace.firstOrNull()
                scope.fingerprint = explicitFingerprint(throwable, target) ?: listOfNotNull(
                    target.javaClass.name,
                    top?.let { "${it.className}.${normalizeMethodName(it.methodName)}" },
                )
            }
        }
        if (id != SentryId.EMPTY_ID) PolyPlusCrashLogUploader.recordLiveCapture(throwable)
        Sentry.flush(5_000)
    }
}
