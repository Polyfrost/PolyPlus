package org.polyfrost.polyplus.client.utils

import org.polyfrost.polyplus.client.PolyPlusClient
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class Batcher(val delay: Duration, val onBatch: suspend (Set<String>) -> Unit) {
    private val lock = ReentrantLock()
    private val pending = HashSet<String>()
    private var job: Job? = null

    fun add(item: String) {
        if (job == null) {
            job = PolyPlusClient.SCOPE.launch {
                kotlinx.coroutines.delay(delay.toMillis())
                val batch = lock.withLock { pending.toSet().also { pending.clear() } }
                onBatch(batch)
                job = null
            }
        }

        lock.withLock {
            pending.add(item)
        }
    }
}
