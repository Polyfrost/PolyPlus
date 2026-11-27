package org.polyfrost.polyplus.client.cosmetics.colors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.gui.wardrobe.syncWornPalettes
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ColorSaveException(message: String) : Exception(message)

class SavedColors internal constructor(
    internal val worn: Map<String, Int>?,
    internal val kept: Map<String, Int>?,
)

object CosmeticColors {
    const val MAX_SLOTS = 32
    private const val RGB_MASK = 0x00FF_FFFF
    private const val CONFIRM_TIMEOUT_MS = 5_000L
    private const val SENDS_PER_WINDOW = 8
    private const val SEND_WINDOW_MS = 11_000L
    private val REJECTION_CODES = setOf("rate_limited", "bad_request", "not_owned", "internal_server_error")
    private val TRAILING_ID = Regex("(\\d+)\\s*$")

    private val byPlayer = ConcurrentHashMap<UUID, Map<Int, Map<String, Int>>>()
    private val previews = ConcurrentHashMap<Int, Map<String, Int>>()
    private val remembered = ConcurrentHashMap<Int, Map<String, Int>>()
    private val saves = ColorSaveLedger<PendingSave>()
    private val pacer = SendPacer(SENDS_PER_WINDOW, SEND_WINDOW_MS)
    private val looks = LookChanges()
    private val auraChanges = AuraChanges()

    private class PendingSave(
        val worn: Map<String, Int>?,
        val kept: Map<String, Int>?,
        previousWorn: Map<String, Int>?,
        previousKept: Map<String, Int>?,
        baselineRequestId: Long,
    ) {
        val outcome = CompletableDeferred<Result<Unit>>()

        @Volatile
        var previousWorn = previousWorn
            private set

        @Volatile
        var previousKept = previousKept
            private set

        @Volatile
        var baselineRequestId = baselineRequestId
            private set

        @Synchronized
        fun rebase(requestId: Long, worn: Map<String, Int>?, kept: Map<String, Int>?) {
            if (requestId <= baselineRequestId) return
            previousWorn = worn
            previousKept = kept
            baselineRequestId = requestId
        }
    }

    var revision by mutableIntStateOf(0)
        private set

    @Volatile
    private var owner: UUID? = null

    @Volatile
    var palettesLoaded = false
        private set

    fun colorsOf(player: UUID): Map<Int, Map<String, Int>> = byPlayer[accountId(player)].orEmpty()

    fun get(player: UUID, cosmeticId: Int, preview: Boolean = false): Map<String, Int>? {
        val account = accountId(player)
        if (previews.isNotEmpty() && (preview || PreviewTryOn.inLobbyScope()) && account == ClientPlatform.localPlayerUuid()) {
            previews[cosmeticId]?.let { return it }
        }
        return byPlayer[account]?.get(cosmeticId)
    }

    fun get(player: UUID, cosmeticId: Int, slot: String, preview: Boolean = false): Int? = get(player, cosmeticId, preview)?.get(slot)

    fun localColors(cosmeticId: Int): Map<String, Int>? = byPlayer[account()]?.get(cosmeticId)

    fun remembered(cosmeticId: Int): Map<String, Int>? = localColors(cosmeticId) ?: remembered[cosmeticId]

    fun preview(cosmeticId: Int, colors: Map<String, Int>) {
        previews[cosmeticId] = colors.mapValues { (_, rgb) -> rgb and RGB_MASK }
        changed()
    }

    fun endPreview(cosmeticId: Int) {
        if (previews.remove(cosmeticId) != null) changed()
    }

    fun isPreviewing(cosmeticId: Int): Boolean = previews.containsKey(cosmeticId)

    suspend fun set(
        cosmeticId: Int,
        colors: Map<String, Int>?,
        account: UUID = ClientPlatform.localPlayerUuid(),
        seenLook: Long = lookGeneration(),
    ): Result<Unit> {
        val cleaned = colors
            ?.mapValues { (_, rgb) -> rgb and RGB_MASK }
            ?.takeIf { it.isNotEmpty() }
        if (cleaned != null && cleaned.size > MAX_SLOTS) {
            return Result.failure(ColorSaveException("A cosmetic can have at most $MAX_SLOTS custom colors."))
        }
        return submit(account, cosmeticId, cleaned, active = true, worn = cleaned, kept = cleaned ?: remembered[cosmeticId], seenLook)
    }

    suspend fun clear(cosmeticId: Int): Result<Unit> = set(cosmeticId, null)

    fun saved(cosmeticId: Int): SavedColors = SavedColors(localColors(cosmeticId), remembered[cosmeticId])

    private fun account(): UUID {
        val local = ClientPlatform.localPlayerUuid()
        if (owner != local) switchAccount(local)
        return local
    }

    @Synchronized
    private fun switchAccount(local: UUID) {
        if (owner == local) return
        owner = local
        for (save in saves.reset()) {
            save.value.outcome.complete(Result.failure(ColorSaveException("You switched accounts before your colors were saved.")))
        }
        previews.clear()
        remembered.clear()
        auraChanges.forget()
        palettesLoaded = false
        changed()
    }

    private fun switched(): Result<Unit> =
        Result.failure(ColorSaveException("You switched accounts before your colors were saved."))

    private fun notLoaded(): Result<Unit>? {
        account()
        if (palettesLoaded) return null
        CosmeticSync.subscribeLocalAccount()
        return Result.failure(ColorSaveException("Your saved colors are still loading. Try again in a moment."))
    }

    suspend fun restore(
        cosmeticId: Int,
        saved: SavedColors,
        account: UUID = ClientPlatform.localPlayerUuid(),
        seenLook: Long = lookGeneration(),
    ): Result<Unit> {
        val worn = saved.worn
        val kept = saved.kept
        return when {
            worn != null -> submit(account, cosmeticId, worn, active = true, worn = worn, kept = kept ?: worn, seenLook)
            kept != null -> submit(account, cosmeticId, kept, active = false, worn = null, kept = kept, seenLook)
            else -> submit(account, cosmeticId, null, active = true, worn = null, kept = null, seenLook)
        }
    }

    suspend fun deactivate(
        cosmeticId: Int,
        account: UUID = ClientPlatform.localPlayerUuid(),
        seenLook: Long = lookGeneration(),
    ): Result<Unit> {
        notLoaded()?.let { return it }
        if (account() != account) return switched()
        val kept = remembered(cosmeticId) ?: return Result.success(Unit)
        return submit(account, cosmeticId, kept, active = false, worn = null, kept = kept, seenLook)
    }

    internal suspend fun propagate(
        cosmeticId: Int,
        account: UUID,
        seenLook: Long,
        latest: () -> Map<String, Int>?,
    ): Result<Boolean> {
        preflight(account)?.let { failure -> return failure.map { false } }
        if (looks.stale(cosmeticId, seenLook)) return Result.success(false)
        val colors = latest()
            ?.mapValues { (_, rgb) -> rgb and RGB_MASK }
            ?.takeIf { it.isNotEmpty() && it.size <= MAX_SLOTS }
            ?: return Result.success(false)
        return send(account, cosmeticId, colors, active = true, worn = colors, kept = colors).map { true }
    }

    fun lookChanged(auras: Collection<Int>) {
        looks.changed(ColourSaves.group(auras) + PARTICLE_COLOUR_LOCK)
    }

    fun lookGeneration(): Long = looks.generation()

    fun lookStale(cosmeticId: Int, seenLook: Long): Boolean = looks.stale(cosmeticId, seenLook)

    fun auraEquipStarted(target: Int?) {
        auraChanges.started(target)
    }

    fun auraEquipObserved(target: Int?) {
        auraChanges.observed(target, equippedAura())
    }

    fun outfitAuraCleared(aura: Int, before: SavedColors?): Boolean {
        val local = account()
        if (saves.hasPending(aura)) return false
        val worn = before?.worn ?: return false
        val next = auraColorCleared(worn)
        apply(local, aura, next, next)
        return true
    }

    private fun equippedAura(): Int? = CosmeticCatalog.localEquipped().equipped[BodySlot.Aura]

    suspend fun saveParticleColor(color: Int?): Result<Unit> {
        val local = account()
        val seen = looks.generation()
        val seenAuraChange = auraChanges.generation()
        val aura = equippedAura()
        awaitSendSlot(local)?.let { return it }
        val outfitApplied = looks.stale(PARTICLE_COLOUR_LOCK, seen)
        when (particleSaveBlock(outfitApplied, aura, equippedAura(), seenAuraChange, auraChanges.generation())) {
            ParticleSaveBlock.OutfitApplied ->
                return Result.failure(ColorSaveException("You applied an outfit before your aura color was saved, so the outfit's aura color was kept."))
            ParticleSaveBlock.AuraChanged ->
                return Result.failure(ColorSaveException("You changed your aura before its color was saved. Pick the color again."))
            null -> Unit
        }
        val requestId = CosmeticSync.nextRequestId()
        val packet = ServerboundPacket.SetParticleColor(color, requestId)
        if (saves.support == AckSupport.Unsupported) {
            return PolyConnection.sendPacket(packet).map { }.onSuccess { particleSaved(local, aura, color) }
        }
        val entry = saves.open(requestId, null, color) { PendingSave(null, null, null, null, 0L) }
        PolyConnection.sendPacket(packet).onFailure {
            if (saves.drop(entry)) fail(entry, ColorSaveException("Couldn't save your aura color. Check your connection and try again."))
        }
        return confirm(entry, echoExpected = false).onSuccess { particleSaved(local, aura, color) }
    }

    private fun particleSaved(local: UUID, aura: Int?, color: Int?) {
        if (aura == null || account() != local || saves.hasPending(aura)) return
        val worn = byPlayer[local]?.get(aura)
        val kept = remembered[aura]
        if (color == null && worn == null && kept != null) return
        val merged = auraPalette(kept ?: worn, color)
        apply(local, aura, merged, merged)
        if (merged != null) CustomPaletteOrder.mark(local, listOf(aura))
    }

    private suspend fun submit(
        expected: UUID,
        cosmeticId: Int,
        colors: Map<String, Int>?,
        active: Boolean,
        worn: Map<String, Int>?,
        kept: Map<String, Int>?,
        seenLook: Long,
    ): Result<Unit> {
        preflight(expected)?.let { return it }
        if (looks.stale(cosmeticId, seenLook)) {
            return Result.failure(ColorSaveException("You applied an outfit before these colors were saved. Try saving them again."))
        }
        return send(expected, cosmeticId, colors, active, worn, kept)
    }

    private suspend fun preflight(expected: UUID): Result<Unit>? {
        notLoaded()?.let { return it }
        if (account() != expected) return switched()
        awaitSendSlot(expected)?.let { return it }
        return notLoaded()
    }

    private suspend fun send(
        local: UUID,
        cosmeticId: Int,
        colors: Map<String, Int>?,
        active: Boolean,
        worn: Map<String, Int>?,
        kept: Map<String, Int>?,
    ): Result<Unit> {
        if (account() != local) return switched()
        val requestId = CosmeticSync.nextRequestId()
        val entry = saves.open(requestId, cosmeticId, worn) { earlier ->
            PendingSave(
                worn = worn,
                kept = kept,
                previousWorn = if (earlier != null) earlier.value.previousWorn else byPlayer[local]?.get(cosmeticId),
                previousKept = if (earlier != null) earlier.value.previousKept else remembered[cosmeticId],
                baselineRequestId = earlier?.value?.baselineRequestId ?: 0L,
            )
        }
        val echoExpected = CosmeticSync.equipmentKnown(local)
        apply(local, cosmeticId, worn, kept)
        PolyConnection.sendPacket(ServerboundPacket.SetCosmeticColors(cosmeticId, colors, active, requestId)).onFailure {
            if (saves.drop(entry)) fail(entry, ColorSaveException("Couldn't save your colors. Check your connection and try again."))
        }
        return confirm(entry, echoExpected)
    }

    private suspend fun awaitSendSlot(local: UUID): Result<Unit>? {
        val now = System.currentTimeMillis()
        val wait = pacer.reserve(now) - now
        if (wait > 0) delay(wait)
        if (account() == local) return null
        return switched()
    }

    private suspend fun confirm(entry: ColorSaveLedger.Entry<PendingSave>, echoExpected: Boolean): Result<Unit> {
        val outcome = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { entry.value.outcome.await() }
        if (outcome != null) return outcome
        when (val verdict = saves.expire(entry, echoExpected)) {
            SaveVerdict.Saved -> succeed(entry)
            is SaveVerdict.Rejected -> fail(entry, ColorSaveException(rejectionMessage(verdict.code)))
            SaveVerdict.Unconfirmed -> fail(entry, ColorSaveException("Poly+ didn't confirm your colors. Try saving again."))
            null -> Unit
        }
        return entry.value.outcome.await()
    }

    private fun apply(player: UUID, cosmeticId: Int, worn: Map<String, Int>?, kept: Map<String, Int>?) {
        byPlayer.compute(player) { _, current ->
            val next = current.orEmpty().toMutableMap()
            if (worn == null) next.remove(cosmeticId) else next[cosmeticId] = worn
            next.takeIf { it.isNotEmpty() }
        }
        if (kept == null) remembered.remove(cosmeticId) else remembered[cosmeticId] = kept
        changed()
    }

    private fun succeed(entry: ColorSaveLedger.Entry<PendingSave>) {
        entry.value.outcome.complete(Result.success(Unit))
        for (later in saves.pendingAfter(entry)) later.value.rebase(entry.requestId, entry.value.worn, entry.value.kept)
    }

    private fun fail(entry: ColorSaveLedger.Entry<PendingSave>, error: ColorSaveException) {
        entry.value.outcome.complete(Result.failure(error))
        val cosmeticId = entry.cosmeticId ?: return
        val local = ClientPlatform.localPlayerUuid()
        if (saves.hasPending(cosmeticId) || byPlayer[local]?.get(cosmeticId) != entry.expected) return
        apply(local, cosmeticId, entry.value.previousWorn, entry.value.previousKept)
    }

    internal fun handle(packet: ClientboundPacket.CosmeticColorsSaved) {
        account()
        val entry = saves.acknowledge(packet.requestId) ?: return
        if (!saves.hasPending(packet.cosmeticId)) {
            val colors = packet.colors?.takeIf { it.isNotEmpty() }
            val worn = colors?.takeIf { packet.active }
            byPlayer.compute(ClientPlatform.localPlayerUuid()) { _, current ->
                val next = current.orEmpty().toMutableMap()
                if (worn == null) next.remove(packet.cosmeticId) else next[packet.cosmeticId] = worn
                next.takeIf { it.isNotEmpty() }
            }
            if (colors != null) remembered[packet.cosmeticId] = colors
            changed()
        }
        succeed(entry)
    }

    internal fun handle(packet: ClientboundPacket.ParticleColorSaved) {
        saves.acknowledge(packet.requestId)?.let(::succeed)
    }

    internal fun handleError(packet: ClientboundPacket.Error) {
        val requestId = packet.requestId
        if (requestId != null) {
            saves.reject(requestId)?.let { fail(it, ColorSaveException(rejectionMessage(packet.code))) }
            return
        }
        if (packet.code !in REJECTION_CODES) return
        val mentioned = TRAILING_ID.find(packet.message)?.groupValues?.get(1)?.toIntOrNull()
        saves.uncorrelated(packet.code, mentioned)?.let { fail(it, ColorSaveException(rejectionMessage(packet.code))) }
    }

    internal fun handleParticleEcho(color: Int?) {
        saves.echo(null, color).forEach(::succeed)
    }

    private fun rejectionMessage(code: String): String = when (code) {
        "rate_limited" -> "You're changing colors too fast. Wait a few seconds and try again."
        "not_owned" -> "Custom colors aren't available for this cosmetic."
        "bad_request" -> "Poly+ didn't accept those colors."
        else -> "Couldn't save your colors. Try again."
    }

    internal fun applySnapshot(
        players: Collection<String>,
        colors: Map<String, Map<String, Map<String, Int>>>,
        saved: Map<String, Map<String, Int>>? = null,
        savedActive: List<Int>? = null,
        requested: Collection<String>? = null,
        rejected: Collection<String> = emptyList(),
        equipped: Map<String, Map<BodySlot, Int>> = emptyMap(),
    ) {
        val local = account()
        val self = snapshotCarriesAccount(local, requested, players, rejected)
        val keptSaved = if (self && saved != null) byCosmetic(saved) else null
        val activeSaved = keptSaved?.let { kept -> savedActive.orEmpty().mapNotNull { id -> kept[id]?.let { id to it } }.toMap() }
        val localEquipped = equipped.entries.firstOrNull { parse(it.key) == local }?.value?.values.orEmpty()
        var localWorn: Map<Int, Map<String, Int>>? = null
        for (uuidString in players + colors.keys) {
            val uuid = parse(uuidString) ?: continue
            val worn = byCosmetic(colors[uuidString].orEmpty())
            val forPlayer = if (uuid == local) snapshotWorn(activeSaved, worn, localEquipped, byPlayer[uuid].orEmpty()) else worn
            if (forPlayer.isEmpty()) byPlayer.remove(uuid) else byPlayer[uuid] = forPlayer
            if (uuid == local) localWorn = forPlayer
        }
        if (localWorn == null && !activeSaved.isNullOrEmpty()) {
            byPlayer.compute(local) { _, current -> activeSaved + current.orEmpty() }
        }
        if (self) {
            if (keptSaved != null) {
                val next = ownPalettes(keptSaved, localWorn.orEmpty(), remembered, saves::hasPending)
                remembered.keys.retainAll(next.keys)
                remembered.putAll(next)
            } else {
                localWorn?.let(remembered::putAll)
            }
            val loadedNow = !palettesLoaded
            palettesLoaded = true
            if (loadedNow) ClientPlatform.runOnMain { syncWornPalettes(CosmeticCatalog.localEquipped().ids()) }
        } else {
            localWorn?.let(remembered::putAll)
        }
        changed()
    }

    private fun byCosmetic(colors: Map<String, Map<String, Int>>): Map<Int, Map<String, Int>> =
        colors.entries
            .mapNotNull { (id, slots) -> id.toIntOrNull()?.let { it to slots } }
            .filter { (_, slots) -> slots.isNotEmpty() }
            .toMap()

    internal fun handle(packet: ClientboundPacket.PlayerColorsChanged) {
        val uuid = parse(packet.player) ?: return
        val colors = packet.colors?.takeIf { it.isNotEmpty() }
        put(uuid, packet.cosmeticId, colors)
        if (uuid == ClientPlatform.localPlayerUuid()) saves.echo(packet.cosmeticId, colors).forEach(::succeed)
    }

    internal fun forget(player: UUID) {
        if (byPlayer.remove(player) != null) changed()
    }

    internal fun reset() {
        for (save in saves.reset()) {
            save.value.outcome.complete(Result.failure(ColorSaveException("Lost connection to Poly+ before your colors were saved.")))
        }
        byPlayer.clear()
        previews.clear()
        remembered.clear()
        auraChanges.forget()
        palettesLoaded = false
        changed()
    }

    private fun put(player: UUID, cosmeticId: Int, colors: Map<String, Int>?) {
        byPlayer.compute(player) { _, current ->
            val next = current.orEmpty().toMutableMap()
            if (colors == null) next.remove(cosmeticId) else next[cosmeticId] = colors
            next.takeIf { it.isNotEmpty() }
        }
        if (colors != null && player == account()) remembered[cosmeticId] = colors
        changed()
    }

    private fun changed() {
        ClientPlatform.runOnMain { revision++ }
    }

    private fun parse(uuid: String): UUID? = runCatching { UUID.fromString(uuid) }.getOrNull()

    private fun accountId(uuid: UUID): UUID =
        if (uuid == Minecraft.getInstance().player?.uuid) ClientPlatform.localPlayerUuid() else uuid
}
