package org.polyfrost.polyplus.client.launcher

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

object LauncherAccountStore {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")
    private const val AUTH_FILE = "auth.json"
    private const val MAX_WALK_UP = 5

    private val WRITE_JSON = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @Serializable
    data class StoredAccount(
        val id: String,
        val username: String,
        @SerialName("access_token") val accessToken: String = "",
        @SerialName("refresh_token") val refreshToken: String = "",
        val expires: String = "",
        val kind: String = "microsoft",
    )

    @Serializable
    data class CredentialsStore(
        val users: Map<String, StoredAccount> = emptyMap(),
        @SerialName("default_user") val defaultUser: String? = null,
    )

    fun load(): CredentialsStore {
        val file = authFile() ?: return CredentialsStore()
        return runCatching {
            PolyPlusClient.JSON.decodeFromString(CredentialsStore.serializer(), file.readText())
        }.getOrElse {
            LOGGER.warn("Failed to read launcher auth file at {}", file, it)
            CredentialsStore()
        }
    }

    fun save(store: CredentialsStore) {
        val file = authFileForWrite() ?: run {
            LOGGER.warn("No writable launcher directory found; cannot save accounts")
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "$AUTH_FILE.tmp")
            tmp.writeText(WRITE_JSON.encodeToString(CredentialsStore.serializer(), store))
            runCatching {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { LOGGER.warn("Failed to write launcher auth file at {}", file, it) }
    }

    fun hasMicrosoftAccount(store: CredentialsStore): Boolean =
        store.users.values.any { it.kind.equals("microsoft", ignoreCase = true) }

    fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()

    private fun authFile(): File? {
        walkUpForAuth()?.let { return it }
        for (dir in platformLauncherDirs()) {
            val candidate = File(dir, AUTH_FILE)
            if (candidate.isFile) return candidate
        }
        return null
    }

    private fun authFileForWrite(): File? =
        authFile() ?: platformLauncherDirs().firstOrNull()?.let { File(it, AUTH_FILE) }

    private fun walkUpForAuth(): File? {
        var dir: File? = Minecraft.getInstance().gameDirectory.absoluteFile
        var depth = 0
        while (dir != null && depth <= MAX_WALK_UP) {
            val candidate = File(dir, AUTH_FILE)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
            depth++
        }
        return null
    }

    private fun platformLauncherDirs(): List<File> {
        val home = File(System.getProperty("user.home") ?: return emptyList())
        return when {
            ClientPlatform.isWindows -> {
                val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local")
                listOf(
                    File(base, "Polyfrost/OneClient/data"),
                    File(base, "Polyfrost/OneClient-dev/data"),
                )
            }
            ClientPlatform.isMac -> {
                val base = File(home, "Library/Application Support")
                listOf(
                    File(base, "org.Polyfrost.OneClient"),
                    File(base, "org.Polyfrost.OneClient-dev"),
                )
            }
            else -> {
                val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
                    ?: File(home, ".local/share")
                listOf(File(base, "oneclient"), File(base, "oneclient-dev"))
            }
        }
    }
}
