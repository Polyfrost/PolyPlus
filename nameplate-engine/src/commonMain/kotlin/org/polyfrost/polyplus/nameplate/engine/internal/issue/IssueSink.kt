package org.polyfrost.polyplus.nameplate.engine.internal.issue

import org.polyfrost.polyplus.molang.MolangIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode

internal fun interface IssueSink {
    fun issue(issue: BundleIssue)

    operator fun invoke(path: String, message: String) {
        issue(BundleIssue(path, message))
    }

    fun issue(
        path: String,
        message: String,
        code: IssueCode,
        values: Map<String, Double> = emptyMap(),
        names: Map<String, String> = emptyMap(),
    ) {
        issue(BundleIssue(path, message, code, values, names.entries.associate { clean(it.key) to clean(it.value) }))
    }

    fun coded(code: IssueCode): IssueSink = CodedSink(this, code)

    fun onIssue(seen: () -> Unit): IssueSink = WatchedSink(this, seen)

    fun rewritten(path: (String) -> String, message: (String) -> String): IssueSink = RewrittenSink(this, path, message)

    fun molang(): (MolangIssue) -> Unit =
        { issue(it.path, it.message, IssueCode.of(it.problem), it.values(), it.names) }

    private fun clean(text: String): String = text.map { if (it <= LAST_CONTROL) REPLACEMENT else it }.joinToString("")

    private companion object {
        const val LAST_CONTROL = '\u001f'
        const val REPLACEMENT = '?'
    }
}

private class CodedSink(private val sink: IssueSink, private val code: IssueCode) : IssueSink {
    override fun issue(issue: BundleIssue) {
        sink.issue(issue)
    }

    override fun invoke(path: String, message: String) {
        sink.issue(BundleIssue(path, message, code))
    }
}

private class WatchedSink(private val sink: IssueSink, private val seen: () -> Unit) : IssueSink {
    override fun issue(issue: BundleIssue) {
        seen()
        sink.issue(issue)
    }

    override fun invoke(path: String, message: String) {
        seen()
        sink(path, message)
    }
}

private class RewrittenSink(
    private val sink: IssueSink,
    private val path: (String) -> String,
    private val message: (String) -> String,
) : IssueSink {
    override fun issue(issue: BundleIssue) {
        sink.issue(BundleIssue(path(issue.path), message(issue.message), issue.code, issue.values, issue.names))
    }

    override fun invoke(path: String, message: String) {
        sink(path(path), message(message))
    }
}

internal fun issueSink(into: MutableList<BundleIssue>): IssueSink = IssueSink { into += it }

internal fun issueValues(vararg values: Pair<String, Number>): Map<String, Double> =
    values.associate { (name, value) -> name to value.toDouble() }

internal fun keyValues(step: Int, length: Int?): Map<String, Double> =
    if (length == null) issueValues("step" to step) else issueValues("step" to step, "length" to length)

internal fun MolangIssue.values(): Map<String, Double> =
    values.mapValues { it.value.toDouble() } + ("column" to column.toDouble())

internal fun MolangIssue.toBundleIssue(path: String = this.path): BundleIssue =
    BundleIssue(path, message, IssueCode.of(problem), values(), names)
