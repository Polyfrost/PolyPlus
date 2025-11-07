package org.polyfrost.polyplus.client.discord

import dev.deftu.omnicore.api.client.OmniDesktop
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipInputStream

object DiscordSDK {
    const val SDK_URL = "https://dl-game-sdk.discordapp.net/3.1.0/discord_game_sdk.zip"
    const val NAME = "discord_game_sdk"

    private val fileSuffix: String
        get() {
            return when {
                OmniDesktop.isWindows -> ".dll"
                OmniDesktop.isMac -> ".dylib"
                OmniDesktop.isLinux -> ".so"
                else -> throw UnsupportedOperationException("Unsupported OS for Discord SDK")
            }
        }

    private val architecture: String
        get() {
            return System.getProperty("os.arch").lowercase(Locale.ROOT).let { arch ->
                if (arch == "amd64") "x86_64" else arch
            }
        }

    suspend fun download(): File? {
        return withContext(Dispatchers.IO) {
            val fileName = NAME + fileSuffix
            val path = "lib/${architecture}/${fileName}"
            val url = URI.create(SDK_URL).toURL()
            val test = PolyPlusClient.HTTP.get(url)

            ZipInputStream(BufferedInputStream(test.bodyAsChannel().toInputStream())).use { stream ->
                for (entry in generateSequence { stream.nextEntry }) {
                    if (entry.name != path) {
                        continue
                    }

                    val gameSdk = File("./${NAME}${fileSuffix}")
                    if (!gameSdk.exists()) {
                        Files.copy(stream, gameSdk.toPath())
                    }

                    stream.close()
                    return@withContext gameSdk
                }

                return@withContext null
            }
        }
    }
}
