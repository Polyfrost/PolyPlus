package org.polyfrost.polyplus.client

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.sentry.Attachment
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CancellationException
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
        "https://e48ba6e979b72c09075eff3ec145a5e4@o4511714343124992.ingest.us.sentry.io/4511826505891840"

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

    /** Mechanism type Sentry's own uncaught-exception handler stamps on the events it raises. */
    private const val UNCAUGHT_MECHANISM = "UncaughtExceptionHandler"

    /** Tag naming the thread the reported throwable was raised on. */
    private const val TAG_THREAD = "thread"

    /** Netty's event-loop body, a frame every stack that ran on a network thread goes through. */
    private const val NETTY_EVENT_LOOP_CLASS = "io.netty.util.concurrent.SingleThreadEventExecutor"

    /** Minecraft names its Netty event loops "Netty Client IO #0", "Netty Local Client IO #0"… */
    private const val NETTY_THREAD_PREFIX = "Netty "

    /** Package holding Essential's authentication exceptions, all of which are account state. */
    private const val ESSENTIAL_AUTH_EXCEPTIONS = "gg.essential.minecraftauth.exception."

    private val started = AtomicBoolean(false)

    /**
     * The thread the client runs on, taken at pre-launch before Minecraft renames it to
     * "Render thread". An uncaught exception only takes the game down when it happened here.
     */
    @Volatile
    private var gameThread: Thread? = null

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

    /** Called from the pre-launch entrypoint, which still runs on the thread the client will use. */
    @JvmStatic
    fun markGameThread() {
        gameThread = Thread.currentThread()
    }

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
                if (t != null && (isTransientNetworkFailure(t) || isReporterArtifact(t) || isBenignCancellation(t) || isForeignPacketNoise(t) || isMemoryExhaustion(t) || isExpectedAccountState(t))) {
                    null
                } else {
                    rateUncaughtException(event)
                    event
                }
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
        if (throwable is CancellationException) return
        initialize()
        if (isTransientNetworkFailure(throwable)) return
        if (isExpectedAccountState(throwable)) return
        if (isBenignCancellation(throwable)) return
        if (isReporterArtifact(throwable)) return
        if (!seen.add(throwable)) return
        if (!allowBySignature(throwable)) return
        send(throwable, CrashKind.RUNTIME_ERROR, null, Thread.currentThread())
    }

    /**
     * Rates the events Sentry's own uncaught-exception handler raises, which arrive levelled `FATAL`
     * no matter where they came from. Only the game thread dying takes the client with it; anywhere
     * else the exception kills one worker and the session carries on, which is the same outcome as
     * something PolyPlus caught itself.
     */
    private fun rateUncaughtException(event: SentryEvent) {
        val wrapper = event.throwableMechanism as? ExceptionMechanismException ?: return
        if (wrapper.exceptionMechanism?.type != UNCAUGHT_MECHANISM) return

        val onGameThread = gameThread?.let { it === wrapper.thread } == true
        val kind = if (onGameThread) CrashKind.HARD_CRASH else CrashKind.RUNTIME_ERROR
        wrapper.thread?.name?.let { event.setTag(TAG_THREAD, it) }
        event.markCrashKind(kind)
        // Keep Sentry's stack-trace grouping but never merge outcomes of different severity.
        event.fingerprints = listOf("{{ default }}", kind.tag)
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
                is java.nio.channels.ClosedChannelException,      // write raced the connection closing
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
        if (isDeliberateCrash(throwable)) return true

        var cause: Throwable? = throwable
        while (cause != null) {
            // Watchdog hang-on-exit dump (ServerWatchdog/ClientShutdownWatchdog)
            // createWatchdogCrashReport builds a synthetic Error("Watchdog (" + message + ")") — not a fault.
            if (cause is Error && cause.message?.startsWith("Watchdog (") == true) return true

            if (cause is RuntimeException && cause.message == "Crash requested by CrashPatch") return true

            // KeyboardHandler's F3+C debug crash: the player asked for it.
            if (cause.message == DEBUG_CRASH) return true

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
            // Essential's session tokens expiring, rate-limiting the refresh, or rejecting it.
            if (name.startsWith(ESSENTIAL_AUTH_EXCEPTIONS)) return true
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

    private const val DEBUG_CRASH = "Manually triggered debug crash"

    /** SkyHanni's opt-in "crash on <event>" features, which live under this class name prefix. */
    private const val DELIBERATE_CRASH_CLASS = "at.hannibal2.skyhanni.features.misc.CrashOn"

    private val SHUTDOWN_WATCHDOG = Regex("""Client shutdown.*java\.lang\.Error: Watchdog""")

    /**
     * Crash reports that are never worth reporting, matched on a description of the form
     * `<crash report title>: <throwable>` so both live and postmortem reports are covered.
     *
     * - Shutdown watchdogs fire because some mod left a long-running thread non-daemon. Commonplace
     *   from Minecraft 26.2 onwards and harmless beyond delaying the exit.
     * - Debug crashes are what the player asked for by holding F3+C.
     */
    internal fun isIgnoredCrashReport(description: String?): Boolean {
        if (description == null) return false
        return SHUTDOWN_WATCHDOG.containsMatchIn(description) || description.contains(DEBUG_CRASH)
    }

    /**
     * A crash a mod threw on purpose because the player switched it on. Matched by class, since the
     * messages are jokes that get reworded.
     */
    internal fun isDeliberateCrash(crashReport: String): Boolean =
        crashReport.contains(DELIBERATE_CRASH_CLASS)

    private fun isDeliberateCrash(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause.stackTrace.any { it.className.startsWith(DELIBERATE_CRASH_CLASS) }) return true
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
        val kind = CrashKind.RUNTIME_ERROR
        val event = SentryEvent()
        event.message = Message().apply { formatted = "[${kind.label}] $message" }
        event.setTag(TAG_THREAD, Thread.currentThread().name)
        event.markCrashKind(kind)
        Sentry.captureEvent(event)
    }

    /**
     * Reports a crash report the game built. Severity depends on what happened next, which
     * [CrashOutcomeTracker] resolves a few moments later, so the send is deferred until then.
     */
    @JvmStatic
    fun captureCrashReport(title: String?, throwable: Throwable) {
        if (!PrivacyConsent.allowsOnlineServices()) return
        initialize()
        if (!Sentry.isEnabled()) return

        val description = if (title.isNullOrBlank()) throwable.toString() else "$title: $throwable"
        if (isIgnoredCrashReport(description) || isDeliberateCrash(throwable)) {
            PolyPlusCrashLogUploader.recordIgnoredCrash()
            return
        }
        // Recovering from an ignored crash leaves the client half torn down (NotEnoughCrashes nulls
        // the player and level), so whatever crashes next is fallout from it rather than a fault.
        if (PolyPlusCrashLogUploader.isSideEffectOfIgnoredCrash(System.currentTimeMillis())) {
            PolyPlusCrashLogUploader.recordIgnoredCrash()
            return
        }
        if (!seen.add(throwable)) return
        if (!allowBySignature(throwable)) return

        val thread = Thread.currentThread()
        CrashOutcomeTracker.track { kind -> deliver(throwable, kind, description, thread) }
    }

    /**
     * Whether the throwable was raised on a Netty event loop, either because the crash report was
     * built there or because the stack still bottoms out in the event loop's own run body.
     *
     * `Connection` catches everything the pipeline throws and disconnects, so the client outlives it
     * whatever [CrashOutcomeTracker] made of the moments that followed — a player leaving as the
     * connection dies is indistinguishable from one whose client stopped ticking because it crashed.
     */
    private fun isNetworkThreadFailure(throwable: Throwable, thread: Thread): Boolean {
        if (thread.name.startsWith(NETTY_THREAD_PREFIX)) return true
        return runCatching {
            throwable.stackTrace.any {
                it.className.startsWith(NETTY_EVENT_LOOP_CLASS) && it.methodName == "run"
            }
        }.getOrDefault(false)
    }

    private fun deliver(throwable: Throwable, kind: CrashKind, description: String?, thread: Thread) {
        val rated = if (kind == CrashKind.HARD_CRASH && isNetworkThreadFailure(throwable, thread)) {
            CrashKind.CAUGHT_CRASH
        } else {
            kind
        }
        val root = runCatching { mostInformativeCause(throwable) }.getOrNull() ?: throwable
        val id = send(throwable, rated, description, thread) { event ->
            event.setTag("mechanism", "crash_report")
            runCatching {
                if (root !== throwable) {
                    // Keeps entrypoint failures distinguishable from mixin failures at a glance.
                    event.setTag("wrapped_by", throwable.javaClass.simpleName)
                    entrypointModId(throwable)?.let { event.setTag("entrypoint_mod", it) }
                }
                val top = root.stackTrace.firstOrNull()
                val explicit = explicitFingerprint(throwable, root) ?: listOfNotNull(
                    root.javaClass.name,
                    top?.let { "${it.className}.${normalizeMethodName(it.methodName)}" },
                )
                event.fingerprints = explicit + rated.tag
            }
        }
        if (id != SentryId.EMPTY_ID) PolyPlusCrashLogUploader.recordLiveCapture(throwable)
        Sentry.flush(5_000)
    }

    private fun send(
        throwable: Throwable,
        kind: CrashKind,
        description: String?,
        thread: Thread,
        customise: (SentryEvent) -> Unit = {},
    ): SentryId {
        val mechanism = Mechanism()
        mechanism.type = kind.tag
        mechanism.description = description
        mechanism.isHandled = kind.handled

        val event = SentryEvent(ExceptionMechanismException(mechanism, throwable, thread))
        // Keep Sentry's stack-trace grouping but never merge outcomes of different severity.
        event.fingerprints = listOf("{{ default }}", kind.tag)
        event.setTag(TAG_THREAD, thread.name)
        event.markCrashKind(kind)
        customise(event)
        return Sentry.captureEvent(event)
    }
}
