package org.polyfrost.polyplus.client.gui.screens.packs

import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService

internal class PackTreeWatcher(private val root: Path) {
    private var service: WatchService? = null
    private val keys = HashMap<WatchKey, Path>()
    private val watched = HashMap<Path, WatchKey>()
    private var settle = 0

    fun poll(): Boolean {
        val active = service ?: open() ?: return false
        var dirty = false
        try {
            while (true) {
                val key = active.poll() ?: break
                val dir = keys[key]
                for (event in key.pollEvents()) {
                    dirty = true
                    if (dir == null || event.kind() != StandardWatchEventKinds.ENTRY_CREATE) continue
                    val child = dir.resolve(event.context() as Path)
                    if (child.fileName.toString().startsWith(".")) continue
                    if (Files.isDirectory(child)) register(active, child, depthOf(child))
                }
                if (!key.reset()) keys.remove(key)
            }
        } catch (_: ClosedWatchServiceException) {
            return false
        }
        if (dirty) settle = SETTLE_TICKS
        if (settle == 0) return false
        return --settle == 0
    }

    fun close() {
        keys.clear()
        watched.clear()
        settle = 0
        val active = service ?: return
        service = null
        try {
            active.close()
        } catch (_: IOException) {
        }
    }

    private fun open(): WatchService? {
        val created = try {
            root.fileSystem.newWatchService()
        } catch (_: IOException) {
            return null
        } catch (_: UnsupportedOperationException) {
            return null
        }
        service = created
        register(created, root, 0)
        return created
    }

    private fun depthOf(dir: Path): Int = root.relativize(dir).nameCount

    private fun register(active: WatchService, dir: Path, depth: Int) {
        if (depth > PackFolders.MAX_DEPTH || PackFolders.forbidden(dir)) return
        try {
            val real = dir.toRealPath()
            val existing = watched[real]
            if (existing?.isValid == true) {
                if (keys[existing]?.let(Files::isDirectory) != true) keys[existing] = dir
                return
            }
            val key = dir.register(
                active,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY,
            )
            keys[key] = dir
            watched[real] = key
            Files.newDirectoryStream(dir).use { children ->
                for (child in children) {
                    if (child.fileName.toString().startsWith(".")) continue
                    if (Files.isDirectory(child)) register(active, child, depth + 1)
                }
            }
        } catch (_: IOException) {
        } catch (_: UnsupportedOperationException) {
        }
    }

    private companion object {
        const val SETTLE_TICKS = 10
    }
}
