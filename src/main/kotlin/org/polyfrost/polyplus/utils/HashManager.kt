package org.polyfrost.polyplus.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.PolyPlus
import org.polyfrost.polyplus.network.plus.cache.CosmeticCache.DIRECTORY
import java.io.File

class HashManager(val directory: String) {
    private var hashes: HashMap<String, String>? = null
    private var updated: Boolean = false
    private var hashJob: Deferred<Unit> = CompletableDeferred();

    suspend fun awaitHashes() {
        if (hashJob.isActive || hashes == null) { hashJob.await() }
    }

    fun updateHash(key: String, hash: String): Boolean {
        hashes?.let {
            val existingHash = it[key]
            if (existingHash != null && existingHash == hash) return false
            it[key] = hash
            updated = true
            return true
        }
        return false
    }

    init {
        hashJob = PolyPlus.scope.async {
            val file = File(directory)
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }

            val json = file.readText()
            hashes = if (json.isBlank()) HashMap() else PolyPlus.json.decodeFromString<HashMap<String, String>>(json)
        }
    }

    fun saveHashes() {
        if (!updated) return
        hashJob = PolyPlus.scope.async(Dispatchers.IO)  {
            val file = File(directory)
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            val json = PolyPlus.json.encodeToString(hashes)
            file.writeText(json)
        }
    }
}