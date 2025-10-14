package org.polyfrost.polyplus.discordrpc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipInputStream

object DownloadSDK {
    const val NAME = "discord_game_sdk"

    val osName = System.getProperty("os.name").lowercase(Locale.ROOT)

    val suffix = when {
        osName.contains("windows") -> ".dll"
        osName.contains("mac os") -> ".dylib"
        osName.contains("linux") -> ".so"
        else -> null
    }

    val arch = System.getProperty("os.arch").lowercase(Locale.ROOT).let { arch ->
        if (arch == "amd64") "x86_64" else arch
    }

    val path = "lib/${arch}/${NAME}${suffix}"
    const val SDK_URL = "https://dl-game-sdk.discordapp.net/2.5.6/discord_game_sdk.zip"
    val url = URI.create(SDK_URL).toURL()

    suspend fun download(): File? = withContext(Dispatchers.IO) {
        val connection = url.openConnection().apply {
            setRequestProperty("User-Agent", "todo")
        }

        ZipInputStream(BufferedInputStream(connection.getInputStream())).use { stream ->
            for (entry in generateSequence { stream.nextEntry }) {
                if (entry.name != path) continue

                val gameSDK = File("./${NAME + suffix}")
                if (!gameSDK.exists()) Files.copy(stream, gameSDK.toPath())

                stream.close()
                return@withContext gameSDK
            }

            return@withContext null
        }
    }
}