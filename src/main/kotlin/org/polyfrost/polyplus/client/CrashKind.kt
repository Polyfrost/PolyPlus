package org.polyfrost.polyplus.client

import org.polyfrost.polyplus.libs.sentry.SentryEvent
import org.polyfrost.polyplus.libs.sentry.SentryLevel

private const val TAG_CRASH_KIND = "crash_kind"
private const val TAG_SEVERITY = "severity"
private const val TAG_CRASH_HANDLER = "crash_handler"

enum class CrashKind(
    val tag: String,
    val label: String,
    val severity: String,
    val level: SentryLevel,
    val handled: Boolean,
) {
    HARD_CRASH("hard_crash", "HARD CRASH", "high", SentryLevel.FATAL, false),
    CAUGHT_CRASH("caught_crash", "CAUGHT CRASH", "medium", SentryLevel.WARNING, true),
    RUNTIME_ERROR("runtime_error", "RUNTIME ERROR", "low", SentryLevel.INFO, true),
}

fun SentryEvent.markCrashKind(kind: CrashKind) {
    level = kind.level
    setTag(TAG_CRASH_KIND, kind.tag)
    setTag(TAG_SEVERITY, kind.severity)
    setTag(TAG_CRASH_HANDLER, CrashOutcomeTracker.handlerMods)
    setExtra("crash_outcome", kind.label)
}
