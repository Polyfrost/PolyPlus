package org.polyfrost.polyplus.client.cosmetics.outfits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.cosmetics.MojangSkinService
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.util.UUID

object OutfitLibrary {
    private val LOGGER = LogManager.getLogger("polyplus/outfits")

    private val requestLock = Mutex()

    var outfits by mutableStateOf<List<OutfitInfo>>(emptyList())
        private set

    var activeOutfitId by mutableStateOf<Int?>(null)
        private set

    var state by mutableStateOf<LibraryState>(LibraryState.Idle)
        private set

    var limit by mutableIntStateOf(LIBRARY_LIMIT)
        private set

    var busy by mutableStateOf(false)
        private set

    @Volatile
    private var applying = false

    @Volatile
    private var loadedFor: UUID? = null

    suspend fun refreshIfNeeded() {
        val stale = loadedFor != null && loadedFor != ClientPlatform.localPlayerUuid()
        if (stale || state is LibraryState.Idle || state is LibraryState.Unavailable) refresh()
        SkinLibrary.recordInBackground()
    }

    suspend fun refresh(): Result<Unit> = requestLock.withLock { refreshLocked() }

    suspend fun createFromCurrent(): Result<OutfitInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        return requestLock.withLock {
            busy = true
            try {
                val skinId = SkinLibrary.recordCurrent()
                    .onFailure { LOGGER.warn("Could not add the current Minecraft skin to the library", it) }
                    .getOrNull()?.id
                MojangCapeService.refreshIfNeeded()
                val capeId = MojangCapeService.activeCape()?.id
                val particleColor = CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid())
                val body = buildJsonObject {
                    put("skin_id", skinId)
                    put("mojang_cape_id", capeId)
                    put("particle_color", particleColor)
                }
                OutfitApi.send<OutfitResponse>(HttpMethod.Post, "/outfits", body)
                    .map { it.toInfo() }
                    .onSuccess { created ->
                        outfits = outfits.filter { it.id != created.id } + created
                        activeOutfitId = created.id
                        refreshLocked()
                    }
                    .onFailure { LOGGER.error("Failed to create an outfit", it) }
            } finally {
                busy = false
            }
        }
    }

    suspend fun apply(id: Int): Result<OutfitInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        return requestLock.withLock {
            busy = true
            applying = true
            try {
                applyLocked(id)
            } finally {
                applying = false
                busy = false
            }
        }
    }

    suspend fun rename(id: Int, name: String): Result<OutfitInfo> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(OutfitException("Give the outfit a name."))
        return patch(id, buildJsonObject { put("name", trimmed) })
    }

    suspend fun setFavorite(id: Int, favorite: Boolean): Result<OutfitInfo> =
        patch(id, buildJsonObject { put("favorite", favorite) })

    suspend fun delete(id: Int): Result<Unit> {
        if (!OutfitApi.allowed()) return unavailable()
        return OutfitApi.send<Unit>(HttpMethod.Delete, "/outfits/$id")
            .onSuccess {
                outfits = outfits.filter { it.id != id }
                if (activeOutfitId == id) activeOutfitId = null
            }
            .onFailure { LOGGER.error("Failed to delete outfit {}", id, it) }
    }

    fun failureMessage(error: Throwable): String = OutfitApi.failureMessage(
        error,
        notFound = "That outfit no longer exists.",
        conflict = "You've reached the limit of $limit outfits. Delete one to make room.",
        badRequest = "PolyPlus couldn't save that outfit.",
    )

    internal fun activeOutfit(): OutfitInfo? = activeOutfitId?.let { id -> outfits.firstOrNull { it.id == id } }

    internal fun onMojangCapeChanged(capeId: String?) {
        val outfit = liveSyncTarget() ?: return
        if (outfit.mojangCapeId == capeId) return
        PolyPlusClient.SCOPE.launch { patch(outfit.id, buildJsonObject { put("mojang_cape_id", capeId) }) }
    }

    internal fun onParticleColorChanged(color: Int?) {
        val outfit = liveSyncTarget() ?: return
        if (outfit.particleColor == color) return
        PolyPlusClient.SCOPE.launch { patch(outfit.id, buildJsonObject { put("particle_color", color) }) }
    }

    internal suspend fun onSkinWorn(skinId: Int): Result<Unit> {
        val id = activeOutfitId ?: return Result.success(Unit)
        if (activeOutfit()?.skinId == skinId) return Result.success(Unit)
        return patch(id, buildJsonObject { put("skin_id", skinId) }).map { }
    }

    internal fun onSkinDeleted(skinId: Int) {
        outfits = outfits.map { if (it.skinId == skinId) it.copy(skinId = null) else it }
    }

    private fun liveSyncTarget(): OutfitInfo? {
        if (applying || !OutfitApi.allowed()) return null
        return activeOutfit()
    }

    private suspend fun patch(id: Int, body: JsonObject): Result<OutfitInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        return OutfitApi.send<OutfitResponse>(HttpMethod.Patch, "/outfits/$id", body)
            .map { it.toInfo() }
            .onSuccess(::replace)
            .onFailure { LOGGER.error("Failed to update outfit {}", id, it) }
    }

    private suspend fun refreshLocked(): Result<Unit> {
        if (!OutfitApi.allowed()) {
            outfits = emptyList()
            activeOutfitId = null
            state = LibraryState.Unavailable(OutfitApi.PRIVACY_MESSAGE)
            return OutfitApi.privacyFailure()
        }
        val owner = ClientPlatform.localPlayerUuid()
        if (state !is LibraryState.Loaded || loadedFor != owner) state = LibraryState.Loading
        return OutfitApi.send<OutfitListResponse>(HttpMethod.Get, "/outfits")
            .onSuccess { response ->
                outfits = response.outfits.map { it.toInfo() }
                activeOutfitId = response.activeOutfitId
                limit = response.limit
                loadedFor = owner
                state = LibraryState.Loaded
                activeOutfit()?.takeIf { it.blank }?.let { blank ->
                    PolyPlusClient.SCOPE.launch { adoptCurrentLook(blank) }
                }
            }
            .onFailure { error ->
                LOGGER.error("Failed to fetch outfits", error)
                if (state !is LibraryState.Loaded) state = LibraryState.Unavailable(failureMessage(error))
            }
            .map { }
    }

    private suspend fun applyLocked(id: Int): Result<OutfitInfo> {
        val response = OutfitApi.send<ApplyOutfitResponse>(HttpMethod.Post, "/outfits/$id/apply")
            .onFailure { LOGGER.error("Failed to apply outfit {}", id, it) }
            .getOrElse { return Result.failure(it) }
        val outfit = response.outfit.toInfo()
        replace(outfit)
        activeOutfitId = outfit.id
        response.skin?.let { skin -> SkinLibrary.remember(skin.toInfo()) }

        val problems = mutableListOf<String>()
        if (outfit.blank) {
            adoptCurrentLook(outfit)
        }
        response.skin?.toInfo()?.let { skin ->
            val current = MojangSkinService.activeSkin().getOrNull()
            if (current == null || current.textureHash != skin.hash || current.model != skin.model) {
                MojangSkinService.setSkin(skin.url, skin.model)
                    .onSuccess { SkinLibrary.recordInBackground(force = true) }
                    .onFailure {
                        problems += "your Minecraft skin wasn't changed (${MojangSkinService.failureMessage(it)})"
                    }
            }
        }
        if (!outfit.blank) {
            applyMojangCape(outfit.mojangCapeId).onFailure {
                problems += "your Minecraft cape wasn't changed (${MojangCapeService.failureMessage(it)})"
            }
        }

        CosmeticService.syncLocalActive().onFailure { LOGGER.warn("Failed to resync cosmetics after applying outfit {}", id, it) }
        val uuid = ClientPlatform.localPlayerUuid()
        val color = outfit.particleColor
        ClientPlatform.runOnMain {
            if (color != null) {
                CosmeticCatalog.setParticleColor(uuid, color)
            } else {
                CosmeticCatalog.clearParticleColor(uuid)
            }
        }

        if (problems.isEmpty()) return Result.success(outfit)
        val message = "Outfit applied, but " + problems.joinToString(" and ") + "."
        LOGGER.warn("Outfit {} applied with problems: {}", id, message)
        return Result.failure(OutfitException(message))
    }

    private suspend fun adoptCurrentLook(outfit: OutfitInfo) {
        val skinId = SkinLibrary.recordCurrent()
            .onFailure { LOGGER.warn("Could not add the current Minecraft skin to the library", it) }
            .getOrElse { return }?.id
        MojangCapeService.refreshIfNeeded()
        if (MojangCapeService.state !is MojangCapeState.Loaded) return
        val capeId = MojangCapeService.activeCape()?.id
        patch(outfit.id, buildJsonObject {
            if (skinId != null) put("skin_id", skinId)
            put("mojang_cape_id", capeId)
        })
    }

    private suspend fun applyMojangCape(capeId: String?): Result<Unit> = runSuspendCatching {
        MojangCapeService.refreshIfNeeded()
        val loaded = MojangCapeService.state is MojangCapeState.Loaded
        val current = MojangCapeService.activeCape()?.id
        if (capeId == null) {
            if (!loaded || current == null) return@runSuspendCatching
            MojangCapeService.clear().getOrThrow()
        } else {
            if (loaded && current == capeId) return@runSuspendCatching
            MojangCapeService.select(capeId).getOrThrow()
        }
        Unit
    }

    private fun replace(outfit: OutfitInfo) {
        val existing = outfits
        outfits = if (existing.any { it.id == outfit.id }) {
            existing.map { if (it.id == outfit.id) outfit else it }
        } else {
            existing + outfit
        }
    }

    private fun <T> unavailable(): Result<T> {
        state = LibraryState.Unavailable(OutfitApi.PRIVACY_MESSAGE)
        return OutfitApi.privacyFailure()
    }
}
