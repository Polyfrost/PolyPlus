package org.polyfrost.polyplus.client.emoji

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File

object EmojiRecents {
    private const val MAX = 18

    @Serializable
    private data class State(val recents: List<String> = emptyList())

    private val file: File by lazy { File(Minecraft.getInstance().gameDirectory, "polyplus/emoji_recents.json") }
    private val recents = mutableListOf<String>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) {
                val state = PolyPlusClient.JSON.decodeFromString(State.serializer(), file.readText())
                recents.clear()
                state.recents.filter { EmojiRegistry.resolve(it) != null }.forEach(recents::add)
            }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(PolyPlusClient.JSON.encodeToString(State.serializer(), State(recents.toList())))
        }
    }

    @JvmStatic
    @Synchronized
    fun aliases(): List<String> {
        ensureLoaded()
        return recents.toList()
    }

    @JvmStatic
    @Synchronized
    fun entries(): List<EmojiRegistry.EmojiEntry> {
        ensureLoaded()
        val byGlyph = EmojiRegistry.catalog.associateBy { it.glyph }
        return recents.mapNotNull { alias -> EmojiRegistry.resolve(alias)?.let(byGlyph::get) }
    }

    @JvmStatic
    @Synchronized
    fun record(alias: String) {
        if (alias.isEmpty() || EmojiRegistry.resolve(alias) == null) return
        ensureLoaded()
        recents.remove(alias)
        recents.add(0, alias)
        while (recents.size > MAX) recents.removeAt(recents.lastIndex)
        persist()
    }
}
