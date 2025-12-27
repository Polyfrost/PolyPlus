package org.polyfrost.polyplus.client.cosmetics

import dev.deftu.omnicore.api.client.OmniClientRuntime
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.util.ResourceLocation
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.http.responses.Cosmetic
import org.polyfrost.polyplus.client.utils.HashManager
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

object CosmeticManager {
    private val LOGGER = LogManager.getLogger()
    private val CACHE = HashMap<String, HashMap<Int, CachedCosmetic>>()
    @JvmField val DIRECTORY = File("${PolyPlusConstants.NAME}/cosmetics")

    private val hashManager = HashManager(DIRECTORY.resolve("hashes.json"))

    @JvmStatic
    fun get(uuid: UUID, type: String): ResourceLocation? {
        val id = PolyCosmetics.getFor(uuid)?.get(type) ?: return null
        return CACHE[type]?.get(id)?.asResource()
    }

    fun reset() {
        CACHE.clear()
    }

    suspend fun putAll(cosmetics: List<Cosmetic>) {
        withContext(Dispatchers.IO) {
            hashManager.awaitHashes()

            try {
                if (!DIRECTORY.exists() && !DIRECTORY.mkdirs()) {
                    LOGGER.error("Failed to create cosmetics directory at ${DIRECTORY.absolutePath}")
                    return@withContext
                }

                for (cosmetic in cosmetics) {
                    val name = "${cosmetic.type}_${cosmetic.id}"
                    val file = File(DIRECTORY, name)
                    val isCached = !hashManager.updateHash(name, cosmetic.hash) && !file.createNewFile()
                    val stream = if (!isCached) {
                        val bytes = PolyPlusClient.HTTP.get(cosmetic.url).bodyAsBytes()
                        val outputStream = file.outputStream()
                        bytes.inputStream().use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }

                        bytes.inputStream()
                    } else file.inputStream()

                    OmniClientRuntime.runOnMain {
                        CACHE.getOrPut(cosmetic.type) {
                            HashMap()
                        }[cosmetic.id] = when (cosmetic.type) {
                            "cape" -> CachedCosmetic.Cape(ImageIO.read(stream))
                            else -> CachedCosmetic.None
                        }
                    }
                }

                hashManager.saveHashes()
            } catch (t: Throwable) {
                LOGGER.error("Failed to cache cosmetics", t)
            }
        }
    }
}
