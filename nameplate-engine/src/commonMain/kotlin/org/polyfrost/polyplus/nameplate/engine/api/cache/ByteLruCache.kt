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

    operator fun get(key: K): V? = entry(key)?.value

    internal inline fun getOrPut(probe: K, keep: (K) -> K, create: () -> V): V {
        val value = get(probe)
        if (value != null) return value

        val created = create()
        store(keep(probe), created)
        return created
    }

    internal fun entry(key: K): Node<K, V>? {
        val node = nodes[key] ?: return null
        touch(node)
        return node
    }

    internal fun recall(node: Node<K, V>): V? {
        if (!node.live) return null

        touch(node)
        return node.value
    }

    internal fun store(key: K, value: V): Node<K, V>? {
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
        }
        return stored.takeIf { it.live }
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
