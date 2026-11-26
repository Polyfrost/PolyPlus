package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.client.utils.ClientPlatform

/**
 * Settings screens of mods that register one for Mod Menu (Sodium, Iris, most Fabric mods), found by
 * reflection so neither Mod Menu nor the mods have to be installed. Used by "Mod settings" buttons.
 */
internal object ModConfigScreens {
    const val PREFIX = "modmenu:"

    private class Entry(val id: String, val name: String, val api: Any)

    private val entries: List<Entry> by lazy {
        runCatching {
            FabricLoader.getInstance().getEntrypointContainers("modmenu", Any::class.java).mapNotNull { container ->
                runCatching {
                    val meta = container.provider.metadata
                    Entry(meta.id, meta.name.ifBlank { meta.id }, container.entrypoint)
                }.getOrNull()
            }.filter { factory(it.api) != null }
        }.getOrDefault(emptyList())
    }

    /** Mods that have a settings screen, id to name. */
    fun mods(): List<Pair<String, String>> = entries.map { it.id to it.name }.distinctBy { it.first }

    fun open(id: String, parent: Screen) {
        val entry = entries.firstOrNull { it.id == id } ?: return
        runCatching {
            val factory = factory(entry.api) ?: return
            val create = factory.javaClass.interfaces.flatMap { it.methods.toList() }
                .firstOrNull { it.name == "create" && it.parameterCount == 1 }
                ?: factory.javaClass.methods.first { it.name == "create" && it.parameterCount == 1 }
            create.isAccessible = true
            val screen = create.invoke(factory, parent) as? Screen ?: return
            ClientPlatform.setScreen(screen)
        }
    }

    private fun factory(api: Any): Any? = runCatching {
        val method = api.javaClass.methods.firstOrNull { it.name == "getModConfigScreenFactory" && it.parameterCount == 0 }
            ?: return@runCatching null
        method.isAccessible = true
        method.invoke(api)
    }.getOrNull()
}
//?}
