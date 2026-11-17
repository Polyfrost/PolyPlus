package org.polyfrost.polyplus.client.gui.screens.packs

//? if >= 26.3 {
import com.mojang.blaze3d.Blaze3D
//?}
//? if >= 1.21.11 {
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
//?} elif > 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.Util
*///?} else {
/*import net.minecraft.client.Minecraft
import net.minecraft.util.Utils
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.utils.v1.Multithreading
*///?}
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal object PackFiles {
    private enum class Os { Windows, Mac, Linux, Other }

    //? if > 1.8.9 {
    private val os: Os
        get() = when (Util.getPlatform()) {
            Util.OS.WINDOWS -> Os.Windows
            Util.OS.OSX -> Os.Mac
            Util.OS.LINUX -> Os.Linux
            else -> Os.Other
        }

    private fun onMain(action: () -> Unit) = Minecraft.getInstance().execute { action() }

    //? if >= 26.3 {
    fun openPath(path: Path) = Blaze3D.openPath(path)
    //?} else {
    /*fun openPath(path: Path) = Util.getPlatform().openPath(path)
    *///?}
    //?} else {
    /*private val os: Os
        get() = when (Utils.getOS()) {
            Utils.OS.WINDOWS -> Os.Windows
            Utils.OS.MACOS -> Os.Mac
            Utils.OS.LINUX -> Os.Linux
            else -> Os.Other
        }

    private fun onMain(action: () -> Unit) {
        Minecraft.getInstance().executeTask(Runnable { action() })
    }

    fun openPath(path: Path) {
        Multithreading.submit { DesktopHelper.open(path.toFile()) }
    }
    *///?}

    val trashLabel: String
        get() = if (os == Os.Windows) "recycle bin" else "trash"

    fun validName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Give the folder a name."
            trimmed == "." || trimmed == ".." || trimmed.startsWith(".") -> "Folder names can't start with a dot."
            trimmed.any { it in INVALID || it.code < 32 } -> "Folder names can't use \\ / : * ? \" < > |"
            trimmed.endsWith(".") -> "Folder names can't end with a dot."
            trimmed.uppercase(Locale.ROOT).substringBefore('.') in RESERVED -> "That name is reserved by the system."
            trimmed.length > 64 -> "Use 64 characters or fewer."
            else -> null
        }
    }

    fun isEmptyDir(dir: Path): Boolean =
        runCatching { Files.list(dir).use { !it.findAny().isPresent } }.getOrDefault(false)

    fun isPack(path: Path): Boolean = when {
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) -> Files.isRegularFile(path.resolve("pack.mcmeta"))
        else -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
    }

    fun move(source: Path, target: Path, overwrite: Boolean) {
        Files.createDirectories(target.parent)
        if (!overwrite || !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.move(source, target)
            return
        }
        if (!isPack(target) || !isPack(source)) throw IOException("${target.fileName} is not a pack")
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return
            } catch (_: AtomicMoveNotSupportedException) {
            }
        }
        val backup = (0..99).asSequence()
            .map { target.resolveSibling(".polyplus-replaced-$it-${target.fileName}") }
            .firstOrNull { !Files.exists(it, LinkOption.NOFOLLOW_LINKS) }
            ?: throw IOException("No free name to set ${target.fileName} aside")
        Files.move(target, backup)
        try {
            Files.move(source, target)
        } catch (error: Exception) {
            runCatching { Files.move(backup, target) }
            throw error
        }
        runCatching { deleteHard(backup) }
    }

    fun deleteHard(path: Path) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        } else {
            Files.deleteIfExists(path)
        }
    }

    fun remove(paths: List<Path>, trash: Boolean, done: (List<Path>) -> Unit) {
        CompletableFuture.supplyAsync {
            paths.filter { path ->
                if (trash) toTrash(path) else runCatching { deleteHard(path) }
                Files.exists(path, LinkOption.NOFOLLOW_LINKS)
            }
        }.whenComplete { failed, error ->
            onMain { done(failed ?: if (error != null) paths else emptyList()) }
        }
    }

    fun reveal(path: Path) {
        val command = when (os) {
            Os.Windows -> listOf("explorer.exe", "/select,${path.toAbsolutePath()}")
            Os.Mac -> listOf("open", "-R", path.toAbsolutePath().toString())
            else -> null
        }
        if (command == null || runCatching { ProcessBuilder(command).start() }.isFailure) {
            openPath(path.parent ?: path)
        }
    }

    private fun toTrash(path: Path): Boolean {
        val absolute = path.toAbsolutePath().toString()
        val command = when (os) {
            Os.Windows -> {
                val quoted = absolute.replace("'", "''")
                val method = if (Files.isDirectory(path)) "DeleteDirectory" else "DeleteFile"
                listOf(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::$method('$quoted','OnlyErrorDialogs','SendToRecycleBin')",
                )
            }
            Os.Mac -> listOf("osascript", "-e", "tell application \"Finder\" to delete POSIX file \"${absolute.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            Os.Linux -> listOf("gio", "trash", absolute)
            else -> return false
        }
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(File(if (os == Os.Windows) "NUL" else "/dev/null")).start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0 && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
            }
        } catch (_: IOException) {
            false
        } catch (_: InterruptedException) {
            false
        }
    }

    val trashSupported: Boolean
        get() = os == Os.Windows || os == Os.Mac || os == Os.Linux

    private val INVALID = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    private val RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
}
