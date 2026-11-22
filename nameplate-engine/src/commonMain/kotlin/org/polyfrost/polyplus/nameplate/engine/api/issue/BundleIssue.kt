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

    override fun hashCode(): Int = path.hashCode() * 31 + message.hashCode()

    override fun toString(): String = "$path: $message"
}
