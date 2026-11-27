package org.polyfrost.polyplus.client.cosmetics

internal class RetiredTextures<T : Any> {
    private val waiting = LinkedHashSet<T>()

    fun retire(textures: Collection<T>) {
        waiting += textures
    }

    fun forget(texture: T) {
        waiting -= texture
    }

    fun isEmpty(): Boolean = waiting.isEmpty()

    fun clear() = waiting.clear()

    fun sweep(current: Set<T>, held: Set<T>, release: (T) -> Unit) {
        val iterator = waiting.iterator()
        while (iterator.hasNext()) {
            val texture = iterator.next()
            if (texture in current) {
                iterator.remove()
            } else if (texture !in held) {
                iterator.remove()
                release(texture)
            }
        }
    }
}
