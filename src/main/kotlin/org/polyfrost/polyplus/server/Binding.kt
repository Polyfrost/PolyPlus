package org.polyfrost.polyplus.server

import kotlinx.serialization.json.JsonElement
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface Binding {
    val key: String

    val target: String
    val mod: String
    val type: String
    val bounds: List<Double>? get() = null

    fun snapshot(): JsonElement?
    fun apply(value: OptionValue): Boolean
    fun restore(snapshot: JsonElement)
    fun setLocked(locked: Boolean) {}
}

object OptionRegistry {
    private val LOGGER = LogManager.getLogger("PolyPlus/ServerOptions")

    data class Report(val mod: String, val modVersion: String, val status: String, val type: String?, val bounds: List<Double>?)

    private val bindings = ConcurrentHashMap<String, CopyOnWriteArrayList<Binding>>()
    private val reports = ConcurrentHashMap<String, Report>()

    fun register(binding: Binding) {
        ServerIntegration.overrides.replay(binding.target, binding::restore)
        bindings.computeIfAbsent(binding.key) { CopyOnWriteArrayList() }.add(binding)
        reports[binding.key] = Report(binding.mod, modVersion(binding.mod), "resolved", binding.type, binding.bounds)
    }

    fun unresolved(key: String, mod: String, reason: String) {
        LOGGER.info("Server option {} is unsupported ({} {}): {}", key, mod, modVersion(mod), reason)
        if (reports[key]?.status != "resolved") reports[key] = Report(mod, modVersion(mod), "unresolved: $reason", null, null)
    }

    fun bindings(key: String): List<Binding> = bindings[key].orEmpty()

    fun keys(): Set<String> = bindings.keys.toSet()

    fun reports(): Map<String, Report> = reports.toSortedMap()

    private fun modVersion(mod: String): String =
        FabricLoader.getInstance().getModContainer(mod).map { it.metadata.version.friendlyString }.orElse("unknown")
}
