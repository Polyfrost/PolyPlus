package org.polyfrost.polyplus.client.cosmetics.outfits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.libs.ktor.client.plugins.ClientRequestException
import org.polyfrost.polyplus.libs.ktor.http.HttpMethod
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
import org.polyfrost.polyplus.client.cosmetics.MojangSkinService
import org.polyfrost.polyplus.client.cosmetics.outfitLoadout
import org.polyfrost.polyplus.client.cosmetics.colors.ColourSaves
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.colors.holdUntilSettled
import org.polyfrost.polyplus.client.cosmetics.colors.outfitResultChangesLook
import org.polyfrost.polyplus.client.cosmetics.colors.outfitWritesColour
import org.polyfrost.polyplus.client.gui.wardrobe.applyOutfitAura
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object OutfitLibrary {
    private val LOGGER = LogManager.getLogger("polyplus/outfits")

    private val requestLock = Mutex()
    private val adopting = AtomicBoolean(false)
    private val adoptionAttempts = ConcurrentHashMap<Pair<UUID, Int>, Long>()
    private const val ADOPTION_RETRY_MS = 10 * 60_000L

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

    @Volatile
    private var refreshJob: Deferred<Result<Unit>>? = null

    suspend fun refreshIfNeeded(reload: Boolean = false) {
        val stale = loadedFor != null && loadedFor != ClientPlatform.localPlayerUuid()
        if (reload || stale || state is LibraryState.Idle || state is LibraryState.Unavailable) refresh()
        SkinLibrary.recordInBackground()
    }

    suspend fun refresh(): Result<Unit> {
        val job = synchronized(this) {
            refreshJob?.takeIf { it.isActive }
                ?: PolyPlusClient.SCOPE.async { requestLock.withLock { refreshLocked() } }.also { refreshJob = it }
        }
        return job.await()
    }

    suspend fun createFromCurrent(): Result<OutfitInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        return requestLock.withLock {
            busy = true
            try {
                MojangCapeService.refresh().onFailure {
                    return@withLock Result.failure(
                        OutfitException(
                            "Couldn't read your Minecraft cape, so the outfit wasn't saved (${MojangCapeService.failureMessage(it)}).",
                        ),
                    )
                }
                val skinId = SkinLibrary.recordCurrent()
                    .getOrElse {
                        LOGGER.warn("Could not add the current Minecraft skin to the library", it)
                        return@withLock Result.failure(
                            OutfitException(
                                "Couldn't save your current skin, so the outfit wasn't saved (${SkinLibrary.failureMessage(it)}).",
                            ),
                        )
                    }?.id
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
        return CosmeticService.equipQueue.handOff(requestLock, { applyQueued(id) }) { applied ->
            try {
                applied.fold({ finishApply(id, it) }, { Result.failure(it) })
            } finally {
                applyDone()
            }
        }
    }

    private suspend fun applyQueued(id: Int): Result<Applied> {
        busy = true
        applying = true
        try {
            return applyColours(id)
        } catch (e: Throwable) {
            applyDone()
            throw e
        }
    }

    private fun applyDone() {
        applying = false
        busy = false
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
            .onFailure {
                LOGGER.error("Failed to delete outfit {}", id, it)
                forgetIfMissing(id, it)
            }
    }

    fun failureMessage(error: Throwable): String = OutfitApi.failureMessage(
        error,
        notFound = "That outfit no longer exists.",
        conflict = "You've reached the limit of $limit outfits. Delete one to make room.",
        badRequest = "PolyPlus couldn't save that outfit.",
    )

    internal fun activeOutfit(): OutfitInfo? = activeOutfitId?.let { id -> outfits.firstOrNull { it.id == id } }

    internal fun onMojangCapeChanged(capeId: String?) {
        if (applying || !OutfitApi.allowed()) return
        PolyPlusClient.SCOPE.launch {
            val outfit = liveSyncTarget() ?: return@launch
            if (outfit.blank) {
                adoptLive(outfit).onFailure {
                    LOGGER.debug("Could not adopt the current look into outfit {}: {}", outfit.id, it.message)
                }
                return@launch
            }
            if (outfit.mojangCapeId == capeId) return@launch
            patch(outfit.id, buildJsonObject { put("mojang_cape_id", capeId) })
        }
    }

    internal fun onEquipped(changes: Map<BodySlot, Int?>) {
        val outfit = activeOutfit() ?: return
        val equipped = outfitEquipment(outfit.equipped, changes)
        if (equipped != outfit.equipped) replace(outfit.copy(equipped = equipped))
    }

    internal fun onParticleColorChanged(color: Int?) {
        val outfit = activeOutfit() ?: return
        if (outfit.particleColor != color) replace(outfit.copy(particleColor = color))
    }

    internal suspend fun onSkinWorn(skinId: Int): Result<Unit> {
        val outfit = liveSyncTarget() ?: return Result.success(Unit)
        if (outfit.blank) {
            adoptLive(outfit, skinId).onFailure {
                LOGGER.debug("Could not adopt the current look into outfit {}: {}", outfit.id, it.message)
            }
            return Result.success(Unit)
        }
        if (outfit.skinId == skinId) return Result.success(Unit)
        return patch(outfit.id, buildJsonObject { put("skin_id", skinId) }).map { }
    }

    internal fun onSkinDeleted(skinId: Int) {
        outfits = outfits.map { if (it.skinId == skinId) it.copy(skinId = null) else it }
    }

    private suspend fun liveSyncTarget(): OutfitInfo? {
        if (applying || !OutfitApi.allowed() || state !is LibraryState.Loaded) return null
        if (refresh().isFailure || applying) return null
        return activeOutfit()
    }

    private suspend fun adoptLive(target: OutfitInfo, wornSkinId: Int? = null): Result<Unit> = requestLock.withLock {
        val outfit = activeOutfit()?.takeIf { it.id == target.id } ?: return@withLock Result.success(Unit)
        when {
            outfit.blank -> adoptCurrentLook(outfit, wornSkinId = wornSkinId)
            wornSkinId != null && outfit.skinId != wornSkinId ->
                patch(outfit.id, buildJsonObject { put("skin_id", wornSkinId) }).map { }
            else -> Result.success(Unit)
        }
    }

    private suspend fun patch(id: Int, body: JsonObject): Result<OutfitInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        return OutfitApi.send<OutfitResponse>(HttpMethod.Patch, "/outfits/$id", body)
            .map { it.toInfo() }
            .onSuccess(::replace)
            .onFailure {
                LOGGER.error("Failed to update outfit {}", id, it)
                forgetIfMissing(id, it)
            }
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
        try {
            return OutfitApi.send<OutfitListResponse>(HttpMethod.Get, "/outfits")
                .onSuccess { response ->
                    outfits = response.outfits.map { it.toInfo() }
                    activeOutfitId = response.activeOutfitId
                    limit = response.limit
                    loadedFor = owner
                    state = LibraryState.Loaded
                    activeOutfit()?.takeIf { it.blank && claimBackgroundAdoption(owner, it.id) }?.let { target ->
                        PolyPlusClient.SCOPE.launch {
                            requestLock.withLock {
                                activeOutfit()?.takeIf { it.id == target.id && it.blank }?.let { outfit ->
                                    adoptCurrentLook(outfit, background = true).onFailure {
                                        LOGGER.debug("Could not adopt the current look into outfit {}: {}", outfit.id, it.message)
                                    }
                                }
                            }
                        }
                    }
                }
                .onFailure { error ->
                    LOGGER.error("Failed to fetch outfits", error)
                    if (state !is LibraryState.Loaded) state = LibraryState.Unavailable(failureMessage(error))
                }
                .map { }
        } finally {
            if (state is LibraryState.Loading) state = LibraryState.Idle
        }
    }

    private class Applied(
        val response: ApplyOutfitResponse,
        val outfit: OutfitInfo,
        val keepLook: Boolean,
        val changesSkin: Boolean,
    )

    private suspend fun applyColours(id: Int): Result<Applied> {
        val target = outfits.firstOrNull { it.id == id }
        val wornAura = CosmeticCatalog.localEquipped().equipped[BodySlot.Aura]
        val nextAura = if (target?.blank == true) wornAura else target?.equipped?.get(BodySlot.Aura)
        val writesColour = outfitWritesColour(target?.blank, target?.particleColor)
        val hold = ColourSaves.reserveOutfit(nextAura, writesColour)
            ?: return Result.failure(OutfitException(ColourSaves.STILL_SAVING))
        val auras = listOfNotNull(wornAura, nextAura, target?.equipped?.get(BodySlot.Aura)).distinct()
        return holdUntilSettled(hold::release) { settling -> applyHolding(id, auras, writesColour, settling) }
    }

    private suspend fun applyHolding(id: Int, auras: List<Int>, writesColour: Boolean, settling: (Job) -> Unit): Result<Applied> {
        val owner = ClientPlatform.localPlayerUuid()
        val colorBefore = CosmeticCatalog.confirmedParticleColor(owner)
        val choice = ColourSaves.particleChoices.seen()
        val aurasBefore = auras.associateWith(CosmeticColors::saved)
        if (writesColour) CosmeticColors.lookChanged(auras)
        val response = OutfitApi.send<ApplyOutfitResponse>(HttpMethod.Post, "/outfits/$id/apply")
            .onFailure {
                LOGGER.error("Failed to apply outfit {}", id, it)
                forgetIfMissing(id, it)
            }
            .getOrElse { return Result.failure(it) }
        val outfit = response.outfit.toInfo()
        val keepLook = response.adopted || outfit.blank
        val fresh = !ColourSaves.particleChoices.since(choice)
        val changesLook = fresh && outfitResultChangesLook(keepLook, outfit.particleColor, colorBefore)
        val aura = if (keepLook) {
            CosmeticCatalog.localEquipped().equipped[BodySlot.Aura]
        } else {
            outfit.equipped[BodySlot.Aura]
        }
        if (writesColour || changesLook) CosmeticColors.lookChanged(auras + listOfNotNull(aura))
        if (aura != null && changesLook) {
            settling(applyOutfitAura(aura, outfit.particleColor, aurasBefore[aura]))
        }
        val color = outfit.particleColor
        ClientPlatform.runOnMain {
            if (ColourSaves.particleChoices.since(choice)) return@runOnMain
            ColourSaves.particleChoices.made()
            if (color != null) {
                CosmeticCatalog.setParticleColor(owner, color)
            } else {
                CosmeticCatalog.clearParticleColor(owner)
            }
        }
        replace(outfit)
        activeOutfitId = outfit.id
        response.skin?.let { skin -> SkinLibrary.remember(skin.toInfo()) }
        val expected = if (keepLook) {
            CosmeticCatalog.serverEquipment()
        } else {
            val pieces = outfit.equipped.filterKeys { it != BodySlot.Unknown }
            val kept = outfitLoadout(pieces, CosmeticCatalog.ownedCosmeticIds()) { cosmeticId, slot ->
                CosmeticCatalog.getCosmeticDefinition(cosmeticId)?.allowsSlot(slot) ?: false
            }
            kept.takeIf { it == pieces }
        }
        CosmeticService.confirmLocalActive(expected).onFailure { LOGGER.warn("Failed to resync cosmetics after applying outfit {}", id, it) }
        val changesSkin = !keepLook && response.skin != null
        if (changesSkin) CosmeticService.skinChanges.begin()
        return Result.success(Applied(response, outfit, keepLook, changesSkin))
    }

    private suspend fun finishApply(id: Int, applied: Applied): Result<OutfitInfo> {
        val response = applied.response
        val outfit = applied.outfit
        val keepLook = applied.keepLook
        val owner = ClientPlatform.localPlayerUuid()
        val problems = mutableListOf<String>()
        if (response.adopted) {
            adoptCurrentLook(outfit).onFailure {
                problems += "your current skin and cape weren't saved to it (${it.message})"
            }
        }
        try {
            response.skin?.toInfo()?.takeIf { !keepLook }?.let { skin ->
                val current = MojangSkinService.activeSkin().getOrNull()
                if (current == null || current.textureHash != skin.hash || current.model != skin.model) {
                    MojangSkinService.setSkin(skin.url, skin.model, owner)
                        .onSuccess { SkinLibrary.recordInBackground(force = true) }
                        .onFailure {
                            problems += "your Minecraft skin wasn't changed (${MojangSkinService.failureMessage(it)})"
                        }
                }
            }
        } finally {
            if (applied.changesSkin) CosmeticService.skinChanges.end()
        }
        if (!keepLook) {
            applyMojangCape(outfit.mojangCapeId).onFailure {
                problems += "your Minecraft cape wasn't changed (${MojangCapeService.failureMessage(it)})"
            }
        }

        if (problems.isEmpty()) return Result.success(outfit)
        val message = "Outfit applied, but " + problems.joinToString(" and ") + "."
        LOGGER.warn("Outfit {} applied with problems: {}", id, message)
        return Result.failure(OutfitException(message))
    }

    private suspend fun adoptCurrentLook(
        outfit: OutfitInfo,
        background: Boolean = false,
        wornSkinId: Int? = null,
    ): Result<Unit> {
        if (!adopting.compareAndSet(false, true)) return Result.success(Unit)
        try {
            MojangCapeService.refresh().onFailure {
                return Result.failure(OutfitException(MojangCapeService.failureMessage(it)))
            }
            if (!MojangCapeService.isLoaded()) return Result.failure(OutfitException("Couldn't read your Minecraft cape."))
            val capeId = MojangCapeService.activeCape()?.id
            val skinId = wornSkinId ?: SkinLibrary.recordCurrent(background)
                .onFailure { LOGGER.warn("Could not add the current Minecraft skin to the library", it) }
                .getOrElse { return Result.failure(OutfitException(SkinLibrary.failureMessage(it))) }
                ?.id
            patch(outfit.id, buildJsonObject {
                put("skin_id", skinId)
                put("mojang_cape_id", capeId)
                put("blank", false)
            }).onFailure { return Result.failure(OutfitException(failureMessage(it))) }
            return Result.success(Unit)
        } finally {
            adopting.set(false)
        }
    }

    private fun claimBackgroundAdoption(owner: UUID, id: Int): Boolean {
        val now = System.currentTimeMillis()
        val key = owner to id
        val last = adoptionAttempts[key]
        if (last != null && now - last < ADOPTION_RETRY_MS) return false
        adoptionAttempts[key] = now
        return true
    }

    private suspend fun applyMojangCape(capeId: String?): Result<Unit> = runSuspendCatching {
        MojangCapeService.refresh()
        val loaded = MojangCapeService.isLoaded()
        val current = MojangCapeService.activeCape()?.id
        if (capeId == null) {
            if (loaded && current == null) return@runSuspendCatching
            MojangCapeService.clear().getOrThrow()
        } else {
            if (loaded && current == capeId) return@runSuspendCatching
            MojangCapeService.select(capeId).getOrThrow()
        }
        Unit
    }

    private fun forgetIfMissing(id: Int, error: Throwable) {
        if ((error as? ClientRequestException)?.response?.status != HttpStatusCode.NotFound) return
        outfits = outfits.filter { it.id != id }
        if (activeOutfitId == id) activeOutfitId = null
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
