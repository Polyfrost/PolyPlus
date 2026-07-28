package org.polyfrost.polyplus.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class HashManager(val file: File) {
    @Volatile
    private var hashes: ConcurrentHashMap<String, String>? = null

    @Volatile
    private var isUpToDate: Boolean = false

    @Volatile
    private var hashJob: Deferred<Unit> = CompletableDeferred()

    init {
        hashJob = PolyPlusClient.SCOPE.async(Dispatchers.IO) {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            val json = file.readText()
            hashes = ConcurrentHashMap<String, String>().apply {
                if (json.isNotBlank()) {
                    putAll(PolyPlusClient.JSON.decodeFromString<HashMap<String, String>>(json))
                }
            }
        }
    }

    suspend fun awaitHashes() {
        if (hashJob.isActive || hashes == null) {
            hashJob.await()
        }
    }

    fun isCurrent(key: String, hash: String): Boolean = hashes?.get(key) == hash

    fun updateHash(key: String, hash: String): Boolean {
        hashes?.let {
            val existingHash = it[key]
            if (existingHash != null && existingHash == hash) {
                return false
            }

            it[key] = hash
            isUpToDate = true
            return true
        }

        return false
    }

    fun saveHashes() {
        if (!isUpToDate) {
            return
        }

        val snapshot = HashMap(hashes ?: return)
        hashJob = PolyPlusClient.SCOPE.async(Dispatchers.IO)  {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            val json = PolyPlusClient.JSON.encodeToString(snapshot)
            file.writeText(json)
        }
    }
}
