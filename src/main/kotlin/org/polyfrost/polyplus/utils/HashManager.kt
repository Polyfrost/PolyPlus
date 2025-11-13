package org.polyfrost.polyplus.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File

class HashManager(val file: File) {
    private var hashes: HashMap<String, String>? = null
    private var isUpToDate: Boolean = false
    private var hashJob: Deferred<Unit> = CompletableDeferred()

    init {
        hashJob = PolyPlusClient.SCOPE.async(Dispatchers.IO) {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            val json = file.readText()
            hashes = if (json.isNotBlank()) {
                PolyPlusClient.JSON.decodeFromString<HashMap<String, String>>(json)
            } else HashMap()
        }
    }

    suspend fun awaitHashes() {
        if (hashJob.isActive || hashes == null) {
            hashJob.await()
        }
    }

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

        hashJob = PolyPlusClient.SCOPE.async(Dispatchers.IO)  {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            val json = PolyPlusClient.JSON.encodeToString(hashes)
            file.writeText(json)
        }
    }
}
