package org.polyfrost.polyplus.client.utils

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import java.time.Duration

class Batcher<T, C: MutableCollection<T>>(val delay: Duration, val set: C, val onBatch: suspend CoroutineScope.(C) -> Unit) {
    private val lock = ReentrantLock()
    private var job: Job? = null

    fun add(item: T) {
        if (job == null) {
            job = PolyPlusClient.SCOPE.launch {
                delay(delay.toMillis())
                lock.withLock {
                    onBatch(set)
                    set.clear()
                }
                job = null
            }
        }

        lock.withLock {
            set.add(item)
        }
    }
}