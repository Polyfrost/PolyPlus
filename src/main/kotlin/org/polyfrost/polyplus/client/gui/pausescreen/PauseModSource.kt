package org.polyfrost.polyplus.client.gui.pausescreen

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path
import java.nio.file.Paths

internal object PauseModSource {
    private val IGNORED = setOf("minecraft", "java", "fabricloader")

    private val origins: Map<Path, String> by lazy {
        val found = HashMap<Path, String>()
        for (mod in FabricLoader.getInstance().allMods) {
            val metadata = mod.metadata
            if (metadata.id in IGNORED) continue
            runCatching { mod.origin.paths }.getOrNull()?.forEach { found[normal(it)] = metadata.name }
        }
        found
    }

    private fun normal(path: Path): Path = runCatching { path.toAbsolutePath().normalize() }.getOrDefault(path)

    private fun owner(type: Class<*>): String? = runCatching {
        val location = type.protectionDomain?.codeSource?.location ?: return@runCatching null
        origins[normal(Paths.get(location.toURI()))]
    }.getOrNull()

    fun of(widget: Class<*>, handler: Class<*>?): String? = handler?.let(::owner) ?: owner(widget)
}
