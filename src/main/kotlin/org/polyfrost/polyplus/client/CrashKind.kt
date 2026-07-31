package org.polyfrost.polyplus.client

import io.sentry.SentryEvent
import io.sentry.SentryLevel

private const val TAG_CRASH_KIND = "crash_kind"
private const val TAG_SEVERITY = "severity"
private const val TAG_CRASH_HANDLER = "crash_handler"

/**
 * What a reported throwable actually did to the player's session.
 *
 * [level] drives Sentry's issue priority: `FATAL`/`ERROR` become high, `WARNING` becomes medium and
 * `INFO`/`DEBUG` become low.
 */
enum class CrashKind(
    val tag: String,
    val label: String,
    val severity: String,
    val level: SentryLevel,
    val handled: Boolean,
) {
    /** Nothing caught the crash and the game went down with it. */
    HARD_CRASH("hard_crash", "HARD CRASH", "high", SentryLevel.FATAL, false),

    /** The game built a crash report but kept running, i.e. CrashPatch/NotEnoughCrashes recovered. */
    CAUGHT_CRASH("caught_crash", "CAUGHT CRASH", "medium", SentryLevel.WARNING, true),

    /** PolyPlus caught and logged the exception itself; the game never noticed. */
    RUNTIME_ERROR("runtime_error", "RUNTIME ERROR", "low", SentryLevel.INFO, true),
}

/**
 * Applies the level plus the tags and extras that spell the category out in the Sentry UI, so triage
 * never has to infer it from priority alone.
 */
fun SentryEvent.markCrashKind(kind: CrashKind) {
    level = kind.level
    setTag(TAG_CRASH_KIND, kind.tag)
    setTag(TAG_SEVERITY, kind.severity)
    setTag(TAG_CRASH_HANDLER, CrashOutcomeTracker.handlerMods)
    setExtra("crash_outcome", kind.label)
}
