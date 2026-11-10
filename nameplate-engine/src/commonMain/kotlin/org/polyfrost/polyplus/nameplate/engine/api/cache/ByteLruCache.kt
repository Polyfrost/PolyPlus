package org.polyfrost.polyplus.nameplate.engine.api.cache

class ByteLruCache<K : Any, V : Any>(
    private val maxBytes: Long,
    private val sizeOf: (V) -> Int,
) {
    internal class Node<K, V>(val key: K, var value: V, var size: Int) {
        var newer: Node<K, V>? = null
        var older: Node<K, V>? = null
        var live: Boolean = true
    }

    private val nodes = HashMap<K, Node<K, V>>()
    private var newest: Node<K, V>? = null
    private var oldest: Node<K, V>? = null

    init {
        require(maxBytes >= 0) { "Budget must not be negative: $maxBytes" }
    }

    var bytes: Long = 0
        private set

    val size: Int
        get() = nodes.size

    var hits: Long = 0
        private set

    var misses: Long = 0
        private set

    var evictions: Long = 0
        private set

    operator fun get(key: K): V? {
        val node = nodes[key]
        if (node == null) {
            misses++
            return null
        }

        hits++
        touch(node)
        return node.value
    }

    fun getOrPut(key: K, create: () -> V): V {
        val node = nodes[key]
        if (node != null) {
            hits++
            touch(node)
            return node.value
        }

        misses++
        val value = create()
        put(key, value)
        return value
    }

    internal inline fun getOrPut(probe: K, keep: (K) -> K, create: () -> V): V {
        val value = get(probe)
        if (value != null) return value

        val created = create()
        put(keep(probe), created)
        return created
    }

    internal fun entry(key: K): Node<K, V>? {
        val node = nodes[key]
        if (node == null) {
            misses++
            return null
        }

        hits++
        touch(node)
        return node
    }

    internal fun recall(node: Node<K, V>): V? {
        if (!node.live) return null

        hits++
        touch(node)
        return node.value
    }

    internal fun store(key: K, value: V): Node<K, V>? = putNode(key, value)

    fun put(key: K, value: V) {
        putNode(key, value)
    }

    private fun putNode(key: K, value: V): Node<K, V>? {
        val size = sizeOf(value)
        require(size >= 0) { "Value size must not be negative: $size" }
        val existing = nodes[key]
        if (size > maxBytes) {
            if (existing != null) {
                drop(existing)
            }

            return null
        }

        val stored: Node<K, V>
        if (existing != null) {
            bytes += size - existing.size
            existing.value = value
            existing.size = size
            touch(existing)
            stored = existing
        } else {
            stored = Node(key, value, size)
            nodes[key] = stored
            bytes += size
            linkNewest(stored)
        }

        while (bytes > maxBytes) {
            val victim = oldest ?: break
            drop(victim)
            evictions++
        }
        return stored.takeIf { it.live }
    }

    fun remove(key: K): V? {
        val node = nodes[key] ?: return null
        drop(node)
        return node.value
    }

    fun clear() {
        var node = newest
        while (node != null) {
            node.live = false
            node = node.older
        }
        nodes.clear()
        newest = null
        oldest = null
        bytes = 0
    }

    private fun drop(node: Node<K, V>) {
        nodes.remove(node.key)
        unlink(node)
        bytes -= node.size
        node.live = false
    }

    private fun touch(node: Node<K, V>) {
        if (newest === node) return

        unlink(node)
        linkNewest(node)
    }

    private fun unlink(node: Node<K, V>) {
        val newer = node.newer
        val older = node.older
        if (newer != null) {
            newer.older = older
        } else {
            newest = older
        }

        if (older != null) {
            older.newer = newer
        } else {
            oldest = newer
        }

        node.newer = null
        node.older = null
    }

    private fun linkNewest(node: Node<K, V>) {
        val head = newest
        node.older = head
        node.newer = null
        if (head != null) {
            head.newer = node
        } else {
            oldest = node
        }

        newest = node
    }
}
