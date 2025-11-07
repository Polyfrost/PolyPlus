package org.polyfrost.polyplus.network.plus.cache

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.util.ResourceLocation
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.polyplus.PolyPlus
import org.polyfrost.polyplus.network.plus.Cosmetics.players
import org.polyfrost.polyplus.network.plus.cache.CachedCosmetic
import org.polyfrost.polyplus.network.plus.responses.Cosmetic
import org.polyfrost.polyplus.utils.HashManager
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.uuid.Uuid

object CosmeticCache {
    val cache = HashMap<String, HashMap<Int, CachedCosmetic>>()
    val hashManager = HashManager("${DIRECTORY}hashes.txt")
    const val DIRECTORY = "./polyplus/cosmetics/"

    suspend fun put(cosmetics: List<Cosmetic>) = withContext(Dispatchers.IO) {
        hashManager.awaitHashes()
        try {
            val directory = File(DIRECTORY)
            if (!directory.exists()) directory.mkdirs()
            for (cosmetic in cosmetics) {
                val cosmeticName = "${cosmetic.type}_${cosmetic.id}"
                val file = File("${DIRECTORY}$cosmeticName")

                val cached = !hashManager.updateHash(cosmeticName, cosmetic.hash) && !file.createNewFile()

                val cosmeticStream = if (cached) file.inputStream() else {
                    val bytes = PolyPlus.client.get(cosmetic.url).bodyAsBytes()
                    val outputStream = FileOutputStream(file)
                    bytes.inputStream().use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    bytes.inputStream()
                }

                mc.addScheduledTask { // we need a thread with opengl context to create textures
                    cache.getOrPut(cosmetic.type) { HashMap() }[cosmetic.id] = when (cosmetic.type) {
                        "cape" -> CachedCosmetic.Cape(ImageIO.read(cosmeticStream))
                        else -> CachedCosmetic.InvalidType
                    }
                }
            }
            hashManager.saveHashes()
        } catch (e: Exception) {
            PolyPlus.logger.warning("Failed to cache cosmetics: ${e.message}")
        }
    }

    @JvmStatic
    fun getCosmetic(Uuid: UUID, type: String): ResourceLocation? {
        val id = players[Uuid]?.get(type) ?: return null
        return cache[type]?.get(id)?.asResource().also { if (it == null) println("no cached cosmetic with type $type for id $id") }
    }

}