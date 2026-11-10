package org.polyfrost.polyplus.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.apache.logging.log4j.LogManager
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class OverrideManager(private val journalPath: Path, private val bindingsFor: (String) -> List<Binding>) {
    private val pending = LinkedHashMap<String, JsonElement>()
    private val originals = LinkedHashMap<String, JsonElement>()
    private val keysByTarget = HashMap<String, MutableSet<String>>()
    private val bindingsByKey = LinkedHashMap<String, List<Binding>>()

    @Synchronized
    fun loadJournal() {
        if (!Files.exists(journalPath)) return
        val entries = runCatching { Json.parseToJsonElement(Files.readString(journalPath)) as JsonObject }
            .onFailure {
                LOGGER.error("Server override journal {} is unreadable; keeping it aside", journalPath, it)
                runCatching { Files.move(journalPath, journalPath.resolveSibling("${journalPath.fileName}.corrupt"), StandardCopyOption.REPLACE_EXISTING) }
            }
            .getOrNull() ?: return
        pending.putAll(entries)
        if (pending.isNotEmpty()) LOGGER.warn("Restoring {} setting(s) a server changed before the game last closed", pending.size)
    }

    @Synchronized
    fun pendingTargets(): Set<String> = pending.keys.toSet()

    @Synchronized
    fun replay(target: String, restore: (JsonElement) -> Unit) {
        val snapshot = pending[target] ?: return
        if (target in originals) return
        runCatching { restore(snapshot) }
            .onSuccess {
                pending.remove(target)
                writeJournal()
            }
            .onFailure { LOGGER.error("Could not restore {} from the server override journal", target, it) }
    }

    @Synchronized
    fun isOverridden(key: String): Boolean = key in bindingsByKey

    @Synchronized
    fun set(key: String, value: OptionValue) {
        val applied = ArrayList<Binding>()
        for (binding in bindingsFor(key)) {
            val target = binding.target
            val fresh = target !in originals
            if (fresh) {
                val snapshot = pending.remove(target) ?: runCatching { binding.snapshot() }.getOrNull()
                if (snapshot == null) {
                    LOGGER.warn("Not overriding {}: could not read the user's value of {}", key, target)
                    continue
                }
                originals[target] = snapshot
                if (!writeJournal()) {
                    originals.remove(target)
                    LOGGER.warn("Not overriding {}: the restore journal could not be written", key)
                    continue
                }
            }
            val ok = runCatching { binding.apply(value) }
                .onFailure { LOGGER.error("Applying server value for {} to {} failed", key, target, it) }
                .getOrDefault(false)
            if (ok) {
                keysByTarget.getOrPut(target) { LinkedHashSet() }.add(key)
                applied += binding
                runCatching { binding.setLocked(true) }
            } else if (fresh) {
                restoreTarget(binding)
            }
        }
        if (applied.isNotEmpty()) {
            bindingsByKey[key] = (bindingsByKey[key].orEmpty() + applied).distinct()
        }
    }

    @Synchronized
    fun reset(key: String) {
        val bindings = bindingsByKey.remove(key) ?: return
        for (binding in bindings) {
            val keys = keysByTarget[binding.target] ?: continue
            keys.remove(key)
            if (keys.isEmpty()) restoreTarget(binding)
        }
    }

    @Synchronized
    fun resetAll() {
        bindingsByKey.keys.toList().forEach(::reset)
    }

    private fun restoreTarget(binding: Binding) {
        val target = binding.target
        keysByTarget.remove(target)
        val snapshot = originals[target] ?: return
        val restored = runCatching { binding.restore(snapshot) }
            .onFailure { LOGGER.error("Could not restore the user's value of {}; it stays in the journal for the next launch", target, it) }
            .isSuccess
        runCatching { binding.setLocked(false) }
        originals.remove(target)
        if (!restored) pending[target] = snapshot
        writeJournal()
    }

    private fun writeJournal(): Boolean = runCatching {
        val entries = JsonObject(pending + originals)
        if (entries.isEmpty()) {
            Files.deleteIfExists(journalPath)
            return@runCatching
        }
        Files.createDirectories(journalPath.parent)
        val temp = journalPath.resolveSibling("${journalPath.fileName}.tmp")
        Files.writeString(temp, entries.toString())
        try {
            Files.move(temp, journalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, journalPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }.onFailure { LOGGER.error("Could not write the server override journal {}", journalPath, it) }.isSuccess

    private companion object {
        val LOGGER = LogManager.getLogger("PolyPlus/ServerOverrides")
    }
}
