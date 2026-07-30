package org.polyfrost.polyplus.client

import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object PolyPlusCrashLogUploader {
    private const val MAX_ATTACHMENT_BYTES = 512 * 1024
    private const val MAX_UPLOADS_PER_RUN = 5
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val UPLOADED_HISTORY = 200
    private const val LIVE_HISTORY = 20

    private const val LIVE_MATCH_WINDOW_MS = 900_000L

    private val ran = AtomicBoolean(false)
    private val stateLock = Any()

    private val gameDir: File get() = FabricLoader.getInstance().gameDir.toFile()
    private val stateDir: File get() = File(gameDir, "polyplus/sentry")
    private val uploadedFile: File get() = File(stateDir, "uploaded.txt")
    private val liveCaptureFile: File get() = File(stateDir, "live-captures.txt")

    private data class LiveCapture(val at: Long, val throwableClass: String, val topFrame: String)

    fun recordLiveCapture(throwable: Throwable) {
        runCatching {
            val top = throwable.stackTrace.firstOrNull()
            val frame = if (top != null) "${top.className}.${top.methodName}" else ""
            val line = "${System.currentTimeMillis()}\t${throwable.javaClass.name}\t$frame"
            synchronized(stateLock) {
                stateDir.mkdirs()
                val stamps = readLines(liveCaptureFile) + line
                liveCaptureFile.writeText(stamps.takeLast(LIVE_HISTORY).joinToString("\n"))
            }
        }
    }

    fun uploadPending() {
        if (!PrivacyConsent.allowsOnlineServices()) return
        if (!ran.compareAndSet(false, true)) return
        runCatching { scanAndUpload() }
    }

    private fun scanAndUpload() {
        PolyPlusSentry.initialize()
        if (!Sentry.isEnabled()) return

        val candidates = collectCandidates()
        if (candidates.isEmpty()) return

        synchronized(stateLock) {
            stateDir.mkdirs()
            val firstRun = !uploadedFile.exists()
            val uploaded = readLines(uploadedFile).toMutableSet()

            if (firstRun) {
                writeUploaded(candidates.map(::keyOf))
                return
            }

            val liveCaptures = readLines(liveCaptureFile).mapNotNull(::parseLiveCapture)
            val now = System.currentTimeMillis()
            var sent = 0

            for (file in candidates.sortedByDescending { it.lastModified() }) {
                val key = keyOf(file)
                if (key in uploaded) continue
                uploaded += key

                if (now - file.lastModified() > MAX_AGE_MS) continue
                if (sent >= MAX_UPLOADS_PER_RUN) continue

                val isJvmFatal = file.name.startsWith("hs_err_pid")
                val body = prepare(file, isJvmFatal) ?: continue
                val fingerprint = fingerprint(body, isJvmFatal)
                if (!isJvmFatal && alreadyReportedLive(liveCaptures, fingerprint, file.lastModified())) continue

                if (send(file, body, fingerprint, isJvmFatal)) sent++
            }

            writeUploaded(uploaded)
        }

        Sentry.flush(10_000)
    }

    private fun collectCandidates(): List<File> {
        val crashReports = File(gameDir, "crash-reports")
            .listFiles { f: File -> f.isFile && f.name.endsWith(".txt") }
            ?.toList()
            .orEmpty()

        val jvmFatal = gameDir
            .listFiles { f: File -> f.isFile && f.name.startsWith("hs_err_pid") && f.name.endsWith(".log") }
            ?.toList()
            .orEmpty()

        return crashReports + jvmFatal
    }

    private fun parseLiveCapture(line: String): LiveCapture? {
        val parts = line.split('\t')
        val at = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: return null
        return LiveCapture(at, parts.getOrNull(1).orEmpty().trim(), parts.getOrNull(2).orEmpty().trim())
    }

    private fun alreadyReportedLive(
        liveCaptures: List<LiveCapture>,
        fingerprint: List<String>,
        fileTime: Long,
    ): Boolean {
        val throwableClass = fingerprint.getOrNull(1).orEmpty()
        val topFrame = fingerprint.getOrNull(2).orEmpty()
        if (throwableClass.isEmpty() || topFrame.isEmpty()) return false
        return liveCaptures.any {
            it.throwableClass == throwableClass &&
                it.topFrame == topFrame &&
                kotlin.math.abs(it.at - fileTime) <= LIVE_MATCH_WINDOW_MS
        }
    }

    private fun prepare(file: File, isJvmFatal: Boolean): String? = runCatching {
        val raw = file.readText()
        scrub(if (isJvmFatal) trimJvmFatalLog(raw) else raw).take(MAX_ATTACHMENT_BYTES)
    }.getOrNull()

    private fun send(file: File, body: String, fingerprint: List<String>, isJvmFatal: Boolean): Boolean = runCatching {
        val event = SentryEvent().apply {
            level = SentryLevel.FATAL
            message = Message().apply { formatted = summarize(body, isJvmFatal) }
            fingerprints = fingerprint
            exceptions = listOf(syntheticException(body, isJvmFatal))
            setTag("mechanism", if (isJvmFatal) "jvm_fatal_log" else "crash_report_file")
            setTag("source", "postmortem")
            setExtra("crash_file", file.name)
            setExtra("crash_file_time", file.lastModified())
        }

        val hint = Hint().apply {
            addAttachment(Attachment(body.toByteArray(Charsets.UTF_8), file.name, "text/plain"))
        }

        Sentry.captureEvent(event, hint)
        true
    }.getOrDefault(false)

    private fun trimJvmFatalLog(text: String): String {
        val cut = text.indexOf("---------------  P R O C E S S")
        return if (cut > 0) text.substring(0, cut) else text
    }

    private val REDACTED_ARGS = Regex(
        "(--(?:accessToken|session|clientId|xuid|userProperties|password|uuid))([\\s=]+)\\S+",
        RegexOption.IGNORE_CASE,
    )
    private val JWT = Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+")
    private val COMMAND_LINE = Regex("(?m)^Command Line:.*$")

    private fun scrub(text: String): String {
        var out = text
        out = COMMAND_LINE.replace(out, "Command Line: <redacted>")
        out = REDACTED_ARGS.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
        out = JWT.replace(out, "<redacted-token>")
        val home = System.getProperty("user.home")
        if (!home.isNullOrBlank() && home.length > 3) out = out.replace(home, "~")
        return out
    }

    private fun summarize(body: String, isJvmFatal: Boolean): String {
        if (isJvmFatal) {
            val signal = lineAfterMarker(body, "#  ", contains = "at pc=") ?: "unknown fault"
            val frame = valueAfterLine(body, "# Problematic frame:") ?: "unknown frame"
            return "JVM fatal error: ${signal.trim()} in ${frame.removePrefix("#").trim()}"
        }
        val description = valueAfter(body, "Description:") ?: "Minecraft crash"
        val throwable = THROWABLE_LINE.find(body)?.value?.trim()
        return if (throwable != null) "$description: $throwable" else description
    }

    private fun fingerprint(body: String, isJvmFatal: Boolean): List<String> {
        if (isJvmFatal) {
            val signal = lineAfterMarker(body, "#  ", contains = "at pc=")
                ?.substringBefore(" (")?.trim()?.removePrefix("#")?.trim()
            val frame = valueAfterLine(body, "# Problematic frame:")
                ?.substringAfter('[', "")?.substringBefore('+')?.substringBefore(']')?.trim()
            return listOf("jvm-fatal", signal.orEmpty(), frame.orEmpty())
        }
        val throwable = THROWABLE_LINE.find(body)?.value?.substringBefore(':')?.trim()
        val frame = TOP_FRAME.find(body)?.groupValues?.getOrNull(1)
            ?.substringAfter("//")?.substringBefore('(')?.trim()
        return listOf("mc-crash-report", throwable.orEmpty(), frame.orEmpty())
    }

    private val THROWABLE_LINE = Regex("(?m)^[\\w.$]+(?:Exception|Error|Throwable)(?::.*)?$")
    private val TOP_FRAME = Regex("(?m)^\\s+at (\\S+)")

    private const val MAX_FRAMES = 250

    private fun syntheticException(body: String, isJvmFatal: Boolean): SentryException {
        if (isJvmFatal) {
            return SentryException().apply {
                setType("JvmFatalError")
                setValue(summarize(body, true))
                setMechanism(crashMechanism("jvm_fatal_log"))
            }
        }

        val header = THROWABLE_LINE.find(body)?.value?.trim()
        val qualified = header?.substringBefore(':')?.trim().orEmpty()
        return SentryException().apply {
            setType(qualified.substringAfterLast('.').ifEmpty { "MinecraftCrash" })
            setModule(qualified.substringBeforeLast('.', "").takeIf { it.isNotEmpty() })
            setValue(
                header?.substringAfter(':', "")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: valueAfter(body, "Description:"),
            )
            setMechanism(crashMechanism("crash_report_file"))
            parseFrames(body).takeIf { it.isNotEmpty() }?.let {
                setStacktrace(SentryStackTrace(it).apply { setSnapshot(false) })
            }
        }
    }

    private fun crashMechanism(type: String): Mechanism = Mechanism().apply {
        setType(type)
        setHandled(false)
        setSynthetic(true)
    }

    private fun parseFrames(body: String): List<SentryStackFrame> {
        val lines = body.lines()
        val start = lines.indexOfFirst { THROWABLE_LINE.matches(it) }
        if (start < 0) return emptyList()

        val frames = ArrayList<SentryStackFrame>()
        for (index in start + 1 until lines.size) {
            val line = lines[index].trim()
            when {
                line.startsWith("at ") -> frames += stackFrame(line.removePrefix("at ").trim())
                line.startsWith("...") -> Unit
                line.isEmpty() && frames.isEmpty() -> Unit
                else -> break
            }
            if (frames.size >= MAX_FRAMES) break
        }
        return frames.reversed()
    }

    private fun stackFrame(raw: String): SentryStackFrame {
        val signature = raw.substringAfter("//") // drop the Fabric class-loader prefix
        val qualified = signature.substringBefore('(')
        val locator = signature.substringAfter('(', "").substringBefore(')')
        return SentryStackFrame().apply {
            setModule(qualified.substringBeforeLast('.', "").takeIf { it.isNotEmpty() })
            setFunction(qualified.substringAfterLast('.').takeIf { it.isNotEmpty() })
            setFilename(locator.substringBefore(':').takeIf { it.isNotEmpty() })
            setLineno(locator.substringAfter(':', "").toIntOrNull())
            setInApp(qualified.startsWith("org.polyfrost."))
        }
    }

    private fun valueAfter(text: String, prefix: String): String? = text.lineSequence()
        .firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun lineAfterMarker(text: String, prefix: String, contains: String): String? = text.lineSequence()
        .firstOrNull { it.startsWith(prefix) && it.contains(contains) }
        ?.removePrefix(prefix)
        ?.takeIf { it.isNotBlank() }

    private fun valueAfterLine(text: String, marker: String): String? {
        val lines = text.lines()
        val index = lines.indexOfFirst { it.trim() == marker }
        if (index < 0) return null
        return lines.getOrNull(index + 1)?.takeIf { it.isNotBlank() }
    }

    private fun keyOf(file: File): String = "${file.name}:${file.lastModified()}"

    private fun readLines(file: File): List<String> =
        runCatching { if (file.isFile) file.readLines().filter { it.isNotBlank() } else emptyList() }
            .getOrDefault(emptyList())

    private fun writeUploaded(keys: Collection<String>) {
        runCatching { uploadedFile.writeText(keys.toList().takeLast(UPLOADED_HISTORY).joinToString("\n")) }
    }
}
