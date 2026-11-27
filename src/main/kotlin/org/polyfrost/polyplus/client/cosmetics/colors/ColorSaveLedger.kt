package org.polyfrost.polyplus.client.cosmetics.colors

internal enum class AckSupport { Unknown, Supported, Unsupported }

internal sealed interface SaveVerdict {
    data object Saved : SaveVerdict
    data class Rejected(val code: String) : SaveVerdict
    data object Unconfirmed : SaveVerdict
}

internal class ColorSaveLedger<T> {
    class Entry<T>(
        val requestId: Long,
        val cosmeticId: Int?,
        val expected: Any?,
        val value: T,
    ) {
        var echoed = false
            internal set
        var fallbackCode: String? = null
            internal set
    }

    private val pending = LinkedHashMap<Long, Entry<T>>()

    @Volatile
    var support = AckSupport.Unknown
        private set

    @Synchronized
    fun open(requestId: Long, cosmeticId: Int?, expected: Any?, value: (Entry<T>?) -> T): Entry<T> {
        val earlier = cosmeticId?.let { id -> pending.values.lastOrNull { it.cosmeticId == id } }
        return Entry(requestId, cosmeticId, expected, value(earlier)).also { pending[requestId] = it }
    }

    @Synchronized
    fun drop(entry: Entry<T>): Boolean = pending.remove(entry.requestId, entry)

    @Synchronized
    fun hasPending(cosmeticId: Int): Boolean = pending.values.any { it.cosmeticId == cosmeticId }

    @Synchronized
    fun pendingAfter(entry: Entry<T>): List<Entry<T>> {
        val cosmeticId = entry.cosmeticId ?: return emptyList()
        return pending.values.filter { it.cosmeticId == cosmeticId && it.requestId > entry.requestId }
    }

    @Synchronized
    fun acknowledge(requestId: Long): Entry<T>? {
        support = AckSupport.Supported
        return pending.remove(requestId)
    }

    @Synchronized
    fun reject(requestId: Long): Entry<T>? {
        val entry = pending.remove(requestId) ?: return null
        support = AckSupport.Supported
        return entry
    }

    @Synchronized
    fun echo(cosmeticId: Int?, value: Any?): List<Entry<T>> {
        val matching = pending.values.filter { it.cosmeticId == cosmeticId && it.expected == value }
        if (support != AckSupport.Unsupported) {
            matching.forEach { it.echoed = true }
            return emptyList()
        }
        matching.forEach { pending.remove(it.requestId) }
        return matching
    }

    @Synchronized
    fun uncorrelated(code: String, mentionedCosmeticId: Int?): Entry<T>? {
        if (support == AckSupport.Supported) return null
        val target = pending.values.firstOrNull { mentionedCosmeticId != null && it.cosmeticId == mentionedCosmeticId }
            ?: pending.values.firstOrNull()
            ?: return null
        if (support == AckSupport.Unsupported) {
            pending.remove(target.requestId)
            return target
        }
        if (target.fallbackCode == null) target.fallbackCode = code
        return null
    }

    @Synchronized
    fun expire(entry: Entry<T>, echoExpected: Boolean): SaveVerdict? {
        if (!pending.remove(entry.requestId, entry)) return null
        if (entry.echoed) {
            if (support == AckSupport.Unknown) support = AckSupport.Unsupported
            return SaveVerdict.Saved
        }
        entry.fallbackCode?.let { return SaveVerdict.Rejected(it) }
        return if (support == AckSupport.Supported || echoExpected) SaveVerdict.Unconfirmed else SaveVerdict.Saved
    }

    @Synchronized
    fun reset(): List<Entry<T>> {
        support = AckSupport.Unknown
        return pending.values.toList().also { pending.clear() }
    }
}
