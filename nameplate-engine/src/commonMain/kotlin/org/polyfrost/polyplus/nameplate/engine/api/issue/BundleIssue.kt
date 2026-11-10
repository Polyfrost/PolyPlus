package org.polyfrost.polyplus.nameplate.engine.api.issue

class BundleIssue(
    val path: String,
    val message: String,
    val code: IssueCode = IssueCode.of(path),
    val values: Map<String, Double> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
) {
    val level: IssueLevel
        get() = code.level

    override fun equals(other: Any?): Boolean = other is BundleIssue && other.path == path && other.message == message

    override fun hashCode(): Int = path.hashCode() * HASH + message.hashCode()

    override fun toString(): String = "$path: $message"

    private companion object {
        internal const val HASH = 31
    }
}
