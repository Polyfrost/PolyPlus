package org.polyfrost.polyplus.client.utils

import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class HashManager(private val file: File) {
    // loaded on first use, which always happens off the main thread
    private val hashes: ConcurrentHashMap<String, String> by lazy {
        ConcurrentHashMap<String, String>().apply {
            runCatching {
                val json = file.takeIf { it.exists() }?.readText().orEmpty()
                if (json.isNotBlank()) {
                    putAll(PolyPlusClient.JSON.decodeFromString<HashMap<String, String>>(json))
                }
            }
        }
    }

    @Volatile
    private var dirty = false

    fun isCurrent(key: String, hash: String): Boolean = hashes[key] == hash

    fun updateHash(key: String, hash: String): Boolean {
        if (hashes.put(key, hash) == hash) {
            return false
        }

        dirty = true
        return true
    }

    fun save() {
        if (!dirty) {
            return
        }

        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(PolyPlusClient.JSON.encodeToString(HashMap(hashes)))
        }
    }
}
