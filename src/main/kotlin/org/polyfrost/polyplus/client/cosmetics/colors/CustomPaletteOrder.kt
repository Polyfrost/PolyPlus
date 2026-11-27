package org.polyfrost.polyplus.client.cosmetics.colors

import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

object CustomPaletteOrder {
    private val LOGGER = LogManager.getLogger("polyplus/colors")
    private const val MAX_PER_ACCOUNT = 512

    @Serializable
    private data class State(
        val next: Long = 1L,
        val accounts: Map<String, Map<Int, Long>> = emptyMap(),
    )

    private val file: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/custom_palette_order.json")

    private var state: State? = null

    @Synchronized
    fun of(account: UUID, cosmeticId: Int): Long = load().accounts[account.toString()]?.get(cosmeticId) ?: 0L

    @Synchronized
    fun mark(account: UUID, cosmeticIds: Collection<Int>) {
        if (cosmeticIds.isEmpty()) return
        val current = load()
        val stamp = current.next
        store(current, account, cosmeticIds.associateWith { stamp }, stamp + 1)
    }

    @Synchronized
    fun raise(account: UUID, cosmeticId: Int, stamp: Long) {
        if (stamp <= 0L) return
        val current = load()
        if ((current.accounts[account.toString()]?.get(cosmeticId) ?: 0L) >= stamp) return
        store(current, account, mapOf(cosmeticId to stamp), maxOf(current.next, stamp + 1))
    }

    private fun store(current: State, account: UUID, stamps: Map<Int, Long>, next: Long) {
        val key = account.toString()
        val updated = (current.accounts[key].orEmpty() + stamps)
            .entries
            .sortedByDescending { it.value }
            .take(MAX_PER_ACCOUNT)
            .associate { it.key to it.value }
        val written = State(next, current.accounts + (key to updated))
        state = written
        try {
            writeAtomically(file, PolyPlusClient.JSON.encodeToString(State.serializer(), written))
        } catch (ex: Exception) {
            LOGGER.warn("Couldn't save the custom colour order; it will be saved with the next change", ex)
        }
    }

    private fun load(): State {
        state?.let { return it }
        val loaded = runCatching {
            if (file.exists()) PolyPlusClient.JSON.decodeFromString(State.serializer(), file.readText()) else null
        }.getOrNull() ?: State()
        state = loaded
        return loaded
    }
}

internal fun writeAtomically(target: File, text: String) {
    val directory = target.absoluteFile.parentFile ?: throw IOException("No directory for $target")
    Files.createDirectories(directory.toPath())
    val temp = Files.createTempFile(directory.toPath(), target.name, ".tmp")
    try {
        Files.write(temp, text.toByteArray(Charsets.UTF_8))
        try {
            Files.move(temp, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}
