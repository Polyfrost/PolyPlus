package org.polyfrost.polyplus.client.utils

import org.polyfrost.polyplus.client.PolyPlusClient
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.launch

class Batcher(val delay: Duration, val onBatch: suspend (Set<String>) -> Unit) {
    private val lock = ReentrantLock()
    private val pending = HashSet<String>()
    private var scheduled = false

    fun add(item: String) {
        lock.withLock {
            pending.add(item)
            if (scheduled) return
            scheduled = true
        }

        PolyPlusClient.SCOPE.launch {
            kotlinx.coroutines.delay(delay.toMillis())
            // Reset before onBatch rather than after, so a failing onBatch can't stall every later flush
            val batch = lock.withLock {
                scheduled = false
                pending.toSet().also { pending.clear() }
            }
            onBatch(batch)
        }
    }
}
