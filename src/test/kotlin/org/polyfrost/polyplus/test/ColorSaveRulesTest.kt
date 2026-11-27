package org.polyfrost.polyplus.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.colors.AuraChanges
import org.polyfrost.polyplus.client.cosmetics.colors.auraColorCleared
import org.polyfrost.polyplus.client.cosmetics.colors.auraHold
import org.polyfrost.polyplus.client.cosmetics.colors.auraSwapBlocked
import org.polyfrost.polyplus.client.cosmetics.colors.outfitColourLocks
import org.polyfrost.polyplus.client.cosmetics.colors.outfitResultChangesLook
import org.polyfrost.polyplus.client.cosmetics.colors.outfitWritesColour
import org.polyfrost.polyplus.client.cosmetics.outfits.outfitEquipment
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.cosmetics.colors.ColorSaveException
import org.polyfrost.polyplus.client.cosmetics.colors.ColourSaveLocks
import org.polyfrost.polyplus.client.cosmetics.colors.EditorSessions
import org.polyfrost.polyplus.client.cosmetics.colors.ColourChoices
import org.polyfrost.polyplus.client.cosmetics.colors.LookChanges
import org.polyfrost.polyplus.client.cosmetics.colors.LookQueue
import org.polyfrost.polyplus.client.cosmetics.colors.PARTICLE_COLOUR_LOCK
import org.polyfrost.polyplus.client.cosmetics.colors.PaletteJobs
import org.polyfrost.polyplus.client.cosmetics.colors.holdUntilSettled
import org.polyfrost.polyplus.client.cosmetics.colors.ParticleSaveBlock
import org.polyfrost.polyplus.client.cosmetics.colors.SendPacer
import org.polyfrost.polyplus.client.cosmetics.colors.OutfitAuraStep
import org.polyfrost.polyplus.client.cosmetics.colors.outfitAuraStep
import org.polyfrost.polyplus.client.cosmetics.colors.outfitAuraWrite
import org.polyfrost.polyplus.client.cosmetics.colors.ownPalettes
import org.polyfrost.polyplus.client.cosmetics.colors.pairedOutcome
import org.polyfrost.polyplus.client.cosmetics.colors.particleSaveBlock
import org.polyfrost.polyplus.client.cosmetics.colors.snapshotCarriesAccount
import org.polyfrost.polyplus.client.cosmetics.colors.snapshotWorn
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import kotlinx.serialization.json.Json
import java.util.UUID

class ColorSaveRulesTest {
    private val fur = mapOf("fur" to 0x123456)
    private val eyes = mapOf("eyes" to 0x654321)
    private val trim = mapOf("trim" to 0x0000FF)

    private val self = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val friend = "22222222-2222-4222-8222-222222222222"

    @Test
    fun `a friend chunk never counts as the own snapshot`() {
        assertFalse(snapshotCarriesAccount(self, requested = listOf(friend), players = listOf(friend), rejected = emptyList()))
    }

    @Test
    fun `a request that included the account is the own snapshot`() {
        assertTrue(snapshotCarriesAccount(self, requested = listOf(self.toString(), friend), players = listOf(friend), rejected = emptyList()))
    }

    @Test
    fun `an untracked snapshot falls back to its players`() {
        assertTrue(snapshotCarriesAccount(self, requested = null, players = listOf(self.toString().uppercase()), rejected = emptyList()))
        assertFalse(snapshotCarriesAccount(self, requested = null, players = listOf(friend), rejected = emptyList()))
    }

    @Test
    fun `a rejected own subscription is not the own snapshot`() {
        assertFalse(snapshotCarriesAccount(self, requested = listOf(self.toString()), players = emptyList(), rejected = listOf(self.toString())))
    }

    @Test
    fun `an own snapshot drops palettes it no longer lists`() {
        val current = mapOf(1 to fur, 2 to eyes)

        val next = ownPalettes(saved = mapOf(3 to trim), worn = emptyMap(), current = current) { false }

        assertEquals(mapOf(3 to trim), next)
    }

    @Test
    fun `an own snapshot keeps palettes with a save in flight`() {
        val current = mapOf(1 to fur, 2 to eyes)

        val next = ownPalettes(saved = mapOf(1 to trim), worn = emptyMap(), current = current) { it == 1 }

        assertEquals(mapOf(1 to fur), next)
    }

    @Test
    fun `a snapshot without saved colors is told apart from one with none saved`() {
        val json = Json { ignoreUnknownKeys = true }
        fun snapshot(extra: String) = json.decodeFromString<ClientboundPacket>(
            """{ "type": "SubscriptionSnapshot", "equipped": {}, "active_emotes": {} $extra }""",
        ) as ClientboundPacket.SubscriptionSnapshot

        assertNull(snapshot("").savedColors)
        assertNull(snapshot(""", "saved_colors": null""").savedColors)
        assertEquals(emptyMap<String, Map<String, Int>>(), snapshot(""", "saved_colors": {}""").savedColors)
        assertEquals(mapOf("4" to fur), snapshot(""", "saved_colors": { "4": { "fur": ${fur.getValue("fur")} } }""").savedColors)
    }

    @Test
    fun `an own snapshot wears every active saved palette, equipped or not`() {
        val next = snapshotWorn(activeSaved = mapOf(1 to fur, 2 to eyes), worn = mapOf(2 to trim), equipped = listOf(2), current = emptyMap())

        assertEquals(mapOf(1 to fur, 2 to trim), next)
    }

    @Test
    fun `a snapshot without saved colors keeps palettes for unequipped cosmetics`() {
        val current = mapOf(1 to fur, 2 to eyes, 3 to trim)

        val next = snapshotWorn(activeSaved = null, worn = mapOf(2 to trim), equipped = listOf(2, 3), current = current)

        assertEquals(mapOf(1 to fur, 2 to trim), next)
    }

    @Test
    fun `worn palettes win over kept ones`() {
        val next = ownPalettes(saved = mapOf(1 to fur), worn = mapOf(1 to eyes), current = emptyMap()) { false }

        assertEquals(mapOf(1 to eyes), next)
    }

    @Test
    fun `both sides saving needs no undo`() = runBlocking {
        var undone = false

        val result = pairedOutcome(listOf(Result.success(Unit), Result.success(Unit))) { undone = true; true }

        assertTrue(result.isSuccess)
        assertFalse(undone)
    }

    @Test
    fun `both sides failing reports the first failure without an undo`() = runBlocking {
        val first = ColorSaveException("first")
        var undone = false

        val result = pairedOutcome(listOf(Result.failure(first), Result.failure(ColorSaveException("second")))) {
            undone = true
            true
        }

        assertSame(first, result.exceptionOrNull())
        assertFalse(undone)
    }

    @Test
    fun `one side failing undoes the side that saved`() = runBlocking {
        var undoneSides: List<Int>? = null

        val result = pairedOutcome(listOf(Result.failure(ColorSaveException("left")), Result.success(Unit))) {
            undoneSides = it
            true
        }

        assertEquals(listOf(1), undoneSides)
        assertEquals("Only one side saved, so neither changed. Try again.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `a failed undo says the sides may not match`() = runBlocking {
        val result = pairedOutcome(listOf(Result.success(Unit), Result.failure(ColorSaveException("right")))) { false }

        assertEquals("Only one side saved. Try again to match both sides.", result.exceptionOrNull()?.message)
        assertNull(result.getOrNull())
    }

    @Test
    fun `a partly failed save across many pieces says some colours didn't save`() = runBlocking {
        val results = listOf(Result.success(Unit), Result.failure(ColorSaveException("slim")), Result.success(Unit))
        assertEquals("Some of your colors didn't save, so nothing changed. Try again.", pairedOutcome(results) { true }.exceptionOrNull()?.message)
        assertEquals("Some of your colors didn't save. Try again.", pairedOutcome(results) { false }.exceptionOrNull()?.message)
    }

    @Test
    fun `a burst of colour sends is spread so no window holds more than the limit`() {
        val pacer = SendPacer(limit = 8, windowMs = 11_000L)
        val slots = List(20) { pacer.reserve(1_000L) }
        assertEquals(List(8) { 1_000L }, slots.take(8))
        assertEquals(List(8) { 12_000L }, slots.subList(8, 16))
        assertEquals(List(4) { 23_000L }, slots.drop(16))
        for (start in slots) {
            assertTrue(slots.count { it >= start && it < start + 11_000L } <= 8)
        }
    }

    @Test
    fun `a save and its rollback fit in the pacer without waiting past the window`() {
        val pacer = SendPacer(limit = 8, windowMs = 11_000L)
        val save = List(6) { pacer.reserve(0L) }
        val rollback = List(5) { pacer.reserve(2_000L) }
        assertEquals(List(6) { 0L }, save)
        assertEquals(listOf(2_000L, 2_000L, 11_000L, 11_000L, 11_000L), rollback)
    }

    @Test
    fun `sends spaced past the window never wait`() {
        val pacer = SendPacer(limit = 2, windowMs = 1_000L)
        assertEquals(0L, pacer.reserve(0L))
        assertEquals(0L, pacer.reserve(0L))
        assertEquals(5_000L, pacer.reserve(5_000L))
    }

    @Test
    fun `an aura palette queued before an outfit is applied is dropped`() {
        val looks = LookChanges()
        val seen = looks.generation()
        looks.changed(listOf(10, 20, PARTICLE_COLOUR_LOCK))
        assertTrue(looks.stale(10, seen))
        assertTrue(looks.stale(20, seen))
        assertFalse(looks.stale(10, looks.generation()))
    }

    @Test
    fun `an outfit applied while a non-aura palette waits does not drop it`() {
        val looks = LookChanges()
        val seen = looks.generation()
        looks.changed(listOf(10, PARTICLE_COLOUR_LOCK))
        assertFalse(looks.stale(5, seen))
    }

    @Test
    fun `an outfit only drops palette saves for the auras it swaps`() {
        val looks = LookChanges()
        val delayed = looks.generation()
        looks.changed(listOf(10, 11, 20, PARTICLE_COLOUR_LOCK))
        assertFalse(looks.stale(30, delayed))
        assertTrue(looks.stale(11, delayed))
        assertTrue(looks.stale(PARTICLE_COLOUR_LOCK, delayed))
        val later = looks.generation()
        looks.changed(listOf(40, PARTICLE_COLOUR_LOCK))
        assertFalse(looks.stale(10, later))
        assertTrue(looks.stale(10, delayed))
        assertTrue(looks.stale(40, delayed))
    }

    @Test
    fun `an aura color queued before an outfit is applied is dropped`() {
        assertEquals(ParticleSaveBlock.OutfitApplied, particleSaveBlock(outfitApplied = true, selectedAura = 7, equippedAura = 7))
    }

    @Test
    fun `an aura color queued before another aura is equipped is dropped`() {
        assertEquals(ParticleSaveBlock.AuraChanged, particleSaveBlock(outfitApplied = false, selectedAura = 7, equippedAura = 8))
        assertEquals(ParticleSaveBlock.AuraChanged, particleSaveBlock(outfitApplied = false, selectedAura = 7, equippedAura = null))
    }

    @Test
    fun `an aura color with the same look and aura is sent`() {
        assertNull(particleSaveBlock(outfitApplied = false, selectedAura = 7, equippedAura = 7))
        assertNull(particleSaveBlock(outfitApplied = false, selectedAura = null, equippedAura = null))
    }

    @Test
    fun `an aura color queued before an aura equip starts is dropped even before the equipment refresh`() {
        assertEquals(
            ParticleSaveBlock.AuraChanged,
            particleSaveBlock(outfitApplied = false, selectedAura = 7, equippedAura = 7, seenAuraChange = 3L, auraChange = 4L),
        )
        assertNull(particleSaveBlock(outfitApplied = false, selectedAura = 7, equippedAura = 7, seenAuraChange = 4L, auraChange = 4L))
    }

    @Test
    fun `starting an aura equip always advances the aura generation`() {
        val changes = AuraChanges()
        changes.started(8)
        assertEquals(1L, changes.generation())
        changes.started(8)
        assertEquals(2L, changes.generation())
    }

    @Test
    fun `the server echo of our own aura equip doesn't advance the generation again`() {
        val changes = AuraChanges()
        changes.started(8)
        changes.observed(8, equipped = 7)
        assertEquals(1L, changes.generation())
    }

    @Test
    fun `an aura equip observed from another device advances the generation`() {
        val changes = AuraChanges()
        changes.observed(7, equipped = 7)
        assertEquals(0L, changes.generation())
        changes.observed(9, equipped = 7)
        assertEquals(1L, changes.generation())
        changes.observed(null, equipped = 7)
        assertEquals(2L, changes.generation())
    }

    @Test
    fun `an outfit aura colour merges into a newer shared palette`() {
        val oldWhite = mapOf("glow" to 0xFFFFFF, "aura" to 0x111111)
        val newGreen = mapOf("glow" to 0x00FF00)
        assertEquals(mapOf("glow" to 0x00FF00, "aura" to 0x222222), outfitAuraWrite(oldWhite, newGreen, 0x222222))
    }

    @Test
    fun `an outfit aura colour on the newest palette needs no extra write`() {
        val own = mapOf("glow" to 0xFFFFFF, "aura" to 0x111111)
        assertNull(outfitAuraWrite(own, own, 0x222222))
        assertNull(outfitAuraWrite(own, null, 0x222222))
        assertNull(outfitAuraWrite(own, mapOf("glow" to 0xFFFFFF, "aura" to 0x222222), 0x222222))
    }

    @Test
    fun `an outfit without an aura colour drops only the aura slot`() {
        assertNull(auraColorCleared(mapOf("aura" to 0x111111)))
        assertEquals(mapOf("glow" to 0xFFFFFF), auraColorCleared(mapOf("glow" to 0xFFFFFF, "aura" to 0x111111)))
    }

    @Test
    fun `an outfit without an aura colour takes the newest palette but keeps its aura default`() {
        val own = mapOf("glow" to 0xFFFFFF)
        val newest = mapOf("glow" to 0x00FF00, "aura" to 0x333333)
        assertEquals(mapOf("glow" to 0x00FF00), outfitAuraWrite(own, newest, null))
        assertNull(outfitAuraWrite(own, mapOf("glow" to 0xFFFFFF, "aura" to 0x333333), null))
        assertNull(outfitAuraWrite(own, null, null))
    }

    @Test
    fun `an outfit that empties an aura-only palette still takes a newer sibling palette`() {
        val cleared = auraColorCleared(mapOf("aura" to 0x111111))
        val step = outfitAuraStep(cleared, mapOf("glow" to 0x00FF00, "aura" to 0x333333), null, resend = false)
        assertEquals(mapOf("glow" to 0x00FF00), (step as OutfitAuraStep.Write).colors)
        assertSame(OutfitAuraStep.Settled, outfitAuraStep(cleared, null, null, resend = false))
    }

    @Test
    fun `an outfit aura queued behind another write is sent again even when it already matches`() {
        val own = mapOf("glow" to 0xFFFFFF, "aura" to 0x222222)
        assertSame(OutfitAuraStep.Settled, outfitAuraStep(own, own, 0x222222, resend = false))
        val resent = outfitAuraStep(own, own, 0x222222, resend = true)
        assertEquals(own, (resent as OutfitAuraStep.Write).colors)
        val cleared = outfitAuraStep(mapOf("glow" to 0xFFFFFF), null, null, resend = true)
        assertEquals(mapOf("glow" to 0xFFFFFF), (cleared as OutfitAuraStep.Write).colors)
        assertSame(OutfitAuraStep.Clear, outfitAuraStep(null, null, null, resend = true))
    }

    @Test
    fun `palette jobs on one cosmetic run one after another`() = runBlocking {
        val jobs = PaletteJobs()
        val ran = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        assertTrue(jobs.claim(5))
        val sync = launch {
            jobs.runClaimed(5) {
                gate.await()
                ran += "sync"
            }
        }
        val first = launch { jobs.run(5) { waited -> ran += "first outfit $waited" } }
        val second = launch { jobs.run(5) { waited -> ran += "second outfit $waited" } }
        yield()
        yield()
        assertEquals(emptyList<String>(), ran)
        assertTrue(jobs.busy(5))
        gate.complete(Unit)
        sync.join()
        first.join()
        second.join()
        assertEquals(listOf("sync", "first outfit true", "second outfit true"), ran)
        assertFalse(jobs.busy(5))
        jobs.run(5) { waited -> ran += "idle $waited" }
        assertEquals("idle false", ran.last())
    }

    @Test
    fun `a failed palette job frees its cosmetic`() {
        val jobs = PaletteJobs()
        assertTrue(jobs.claim(5))
        runCatching { runBlocking { jobs.runClaimed(5) { throw IllegalStateException() } } }
        assertFalse(jobs.busy(5))
        runCatching { runBlocking { jobs.run(5) { throw IllegalStateException() } } }
        assertFalse(jobs.busy(5))
    }

    @Test
    fun `an outfit aura applied during a colour save runs after the save and resends`() {
        val jobs = PaletteJobs()
        val ran = mutableListOf<String>()
        runBlocking {
            jobs.hold(listOf(5, 6)) {
                assertTrue(jobs.busy(5))
                assertTrue(jobs.busy(6))
                launch { jobs.run(5) { waited -> ran += "outfit $waited" } }
                yield()
                ran += "save"
            }
        }
        assertEquals(listOf("save", "outfit true"), ran)
        assertFalse(jobs.busy(5))
        assertFalse(jobs.busy(6))
    }

    @Test
    fun `a colour save waits for a running palette job on the same cosmetic`() {
        val jobs = PaletteJobs()
        val ran = mutableListOf<String>()
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            assertTrue(jobs.claim(5))
            launch {
                jobs.runClaimed(5) {
                    gate.await()
                    ran += "sync"
                }
            }
            launch { jobs.hold(listOf(6, 5)) { ran += "save" } }
            yield()
            yield()
            assertEquals(emptyList<String>(), ran)
            assertFalse(jobs.busy(6))
            gate.complete(Unit)
        }
        assertEquals(listOf("sync", "save"), ran)
        assertFalse(jobs.busy(5))
    }

    @Test
    fun `a failed colour save frees its cosmetics`() {
        val jobs = PaletteJobs()
        runCatching { runBlocking { jobs.hold(listOf(5)) { throw IllegalStateException() } } }
        assertFalse(jobs.busy(5))
        assertTrue(jobs.claim(5))
    }

    @Test
    fun `a pending colour save locks every colourway of its cosmetic`() {
        val locks = ColourSaveLocks()
        val token = locks.acquire(listOf(10, 11, 12))
        assertTrue(token != null)
        assertTrue(locks.locked(listOf(11)))
        assertTrue(locks.locked(listOf(12, 40)))
        assertFalse(locks.locked(listOf(40)))
        assertNull(locks.acquire(listOf(12)))
        assertTrue(locks.acquire(listOf(40)) != null)
    }

    @Test
    fun `a finished colour save unlocks its cosmetic once`() {
        val locks = ColourSaveLocks()
        val token = locks.acquire(listOf(10, 11))!!
        assertTrue(locks.release(token))
        assertFalse(locks.locked(listOf(10, 11)))
        val next = locks.acquire(listOf(11))!!
        assertFalse(locks.release(token))
        assertTrue(locks.locked(listOf(11)))
        assertTrue(locks.release(next))
        assertFalse(locks.locked(listOf(11)))
    }

    @Test
    fun `an action asked for during a colour save is refused`() {
        val locks = ColourSaveLocks()
        locks.acquire(listOf(10, 11))!!
        val ticket = locks.ticket()
        assertTrue(locks.locked(listOf(11), ticket))
        assertFalse(locks.locked(listOf(40), ticket))
    }

    @Test
    fun `a colour save started after an action was asked for doesn't refuse it`() {
        val locks = ColourSaveLocks()
        val ticket = locks.ticket()
        locks.acquire(listOf(10, 11))!!
        assertFalse(locks.locked(listOf(11), ticket))
        assertTrue(locks.locked(listOf(11)))
    }

    @Test
    fun `a colour save that finished before an action was asked for doesn't refuse it`() {
        val locks = ColourSaveLocks()
        val token = locks.acquire(listOf(10, 11))!!
        locks.release(token)
        assertFalse(locks.locked(listOf(10), locks.ticket()))
    }

    @Test
    fun `replacing or removing the worn aura waits for its colour save`() {
        val locks = ColourSaveLocks()
        locks.acquire(listOf(10, 11))!!
        val saving = { id: Int -> locks.locked(listOf(id)) }
        assertTrue(auraSwapBlocked(10, 20, saving))
        assertTrue(auraSwapBlocked(10, null, saving))
        assertFalse(auraSwapBlocked(10, 10, saving))
    }

    @Test
    fun `aura changes go ahead when the worn aura isn't saving`() {
        val locks = ColourSaveLocks()
        locks.acquire(listOf(30))!!
        val saving = { id: Int -> locks.locked(listOf(id)) }
        assertFalse(auraSwapBlocked(10, 20, saving))
        assertFalse(auraSwapBlocked(10, null, saving))
        assertFalse(auraSwapBlocked(null, 30, saving))
    }

    @Test
    fun `an outfit that keeps the worn aura still waits when it would change that aura's colour`() {
        val locks = ColourSaveLocks()
        locks.acquire(auraHold(10) + 11)!!
        val saving = { id: Int -> locks.locked(listOf(id)) }
        assertTrue(outfitColourLocks(10, 10, true).any(saving))
        assertFalse(outfitColourLocks(10, 10, false).any(saving))
        assertTrue(outfitColourLocks(10, 20, false).any(saving))
    }

    @Test
    fun `an outfit waits when its own aura is saving colours`() {
        val locks = ColourSaveLocks()
        locks.acquire(listOf(20))!!
        val saving = { id: Int -> locks.locked(listOf(id)) }
        assertTrue(outfitColourLocks(10, 20, true).any(saving))
        assertFalse(outfitColourLocks(10, 30, true).any(saving))
    }

    @Test
    fun `an outfit apply holds the aura colour locks so a colour save started during it is refused`() {
        val locks = ColourSaveLocks()
        val outfit = locks.acquire(outfitColourLocks(10, 20, writesColour = true))!!
        assertNull(locks.acquire(auraHold(10)))
        assertNull(locks.acquire(listOf(20)))
        assertNull(locks.acquire(auraHold(null)))
        assertTrue(locks.acquire(listOf(30)) != null)
        locks.release(outfit)
        assertTrue(locks.acquire(auraHold(10)) != null)
    }

    @Test
    fun `an outfit apply can't start while an aura colour save holds its locks`() {
        val locks = ColourSaveLocks()
        locks.acquire(auraHold(10))!!
        assertNull(locks.acquire(outfitColourLocks(10, 10, writesColour = true)))
        assertNull(locks.acquire(outfitColourLocks(10, 20, writesColour = false)))
    }

    @Test
    fun `a blank outfit without a colour reserves nothing`() {
        assertTrue(outfitColourLocks(10, 10, writesColour = false).isEmpty())
        assertTrue(outfitColourLocks(null, null, writesColour = false).isEmpty())
        assertEquals(auraHold(10), outfitColourLocks(10, null, writesColour = false))
        assertEquals(auraHold(10) + 20, outfitColourLocks(10, 20, writesColour = true))
        assertEquals(auraHold(10), outfitColourLocks(10, 10, writesColour = true))
    }

    @Test
    fun `an outfit that keeps the look and colour doesn't make pending aura saves stale`() {
        assertFalse(outfitResultChangesLook(keepLook = true, color = 0x123456, colorBefore = 0x123456))
        assertFalse(outfitResultChangesLook(keepLook = true, color = null, colorBefore = null))
        assertTrue(outfitResultChangesLook(keepLook = true, color = 0x654321, colorBefore = 0x123456))
        assertTrue(outfitResultChangesLook(keepLook = false, color = 0x123456, colorBefore = 0x123456))
        assertNull(particleSaveBlock(outfitApplied = false, selectedAura = 10, equippedAura = 10))
    }

    @Test
    fun `only blank outfits without a colour leave the aura colour alone`() {
        assertTrue(outfitWritesColour(false, null))
        assertTrue(outfitWritesColour(null, null))
        assertTrue(outfitWritesColour(true, 0x123456))
        assertFalse(outfitWritesColour(true, null))
    }

    @Test
    fun `an aura colour save without a worn aura still holds the colour controls`() {
        val locks = ColourSaveLocks()
        val token = locks.acquire(auraHold(null))!!
        val saving = { id: Int -> locks.locked(listOf(id)) }
        assertTrue(auraHold(null).any(saving))
        assertNull(locks.acquire(auraHold(null)))
        assertNull(locks.acquire(auraHold(10)))
        assertTrue(auraSwapBlocked(null, 10, saving))
        assertTrue(outfitColourLocks(null, null, true).any(saving))
        assertFalse(outfitColourLocks(null, null, false).any(saving))
        locks.release(token)
        assertFalse(auraHold(null).any(saving))
    }

    @Test
    fun `a colour reset from the same click doesn't block that click's aura equip`() {
        val locks = ColourSaveLocks()
        val ticket = locks.ticket()
        locks.acquire(listOf(10, 11))!!
        assertFalse(auraSwapBlocked(10, 11) { locks.locked(listOf(it), ticket) })
        assertTrue(auraSwapBlocked(10, 11) { locks.locked(listOf(it)) })
    }

    @Test
    fun `equipping keeps the active outfit's cached slots in step`() {
        val outfit = mapOf(BodySlot.Aura to 2, BodySlot.Hat to 5)
        assertEquals(
            mapOf(BodySlot.Aura to 1, BodySlot.Hat to 5),
            outfitEquipment(outfit, mapOf(BodySlot.Aura to 1)),
        )
        assertEquals(mapOf(BodySlot.Aura to 2), outfitEquipment(outfit, mapOf(BodySlot.Hat to null)))
    }

    @Test
    fun `an empty colour save takes no lock`() {
        assertNull(ColourSaveLocks().acquire(emptyList()))
    }

    @Test
    fun `a finished save only closes the editor that started it`() {
        val sessions = EditorSessions()
        val first = sessions.open()
        assertTrue(sessions.owns(first))
        sessions.close()
        assertFalse(sessions.owns(first))
        val second = sessions.open()
        assertFalse(sessions.owns(first))
        assertTrue(sessions.owns(second))
        sessions.close()
        assertFalse(sessions.owns(second))
    }

    @Test
    fun `an aura equip queued before an outfit runs before the outfit reserves its colours`() = runBlocking {
        val queue = LookQueue()
        val locks = ColourSaveLocks()
        val slow = CompletableDeferred<Unit>()
        val ran = mutableListOf<String>()
        val first = launch { queue.run { slow.await() } }
        yield()
        val ticket = locks.ticket()
        val equip = launch {
            queue.run { ran += if (auraSwapBlocked(10, 11) { locks.locked(listOf(it), ticket) }) "equip refused" else "equip" }
        }
        val outfit = launch {
            queue.run {
                val token = locks.acquire(outfitColourLocks(10, 12, writesColour = true))
                ran += if (token == null) "outfit refused" else "outfit"
            }
        }
        yield()
        yield()
        assertTrue(ran.isEmpty())
        slow.complete(Unit)
        first.join()
        equip.join()
        outfit.join()
        assertEquals(listOf("equip", "outfit"), ran)
    }

    @Test
    fun `a colour picked while a blank outfit applies keeps its place`() {
        val choices = ColourChoices()
        val seen = choices.seen()
        assertFalse(choices.since(seen))
        choices.made()
        assertTrue(choices.since(seen))
        assertFalse(choices.since(choices.seen()))
    }

    @Test
    fun `an outfit frees its colour locks once its aura colour settles`() = runBlocking {
        val locks = ColourSaveLocks()
        val token = locks.acquire(auraHold(10))!!
        val palette = CompletableDeferred<Unit>()
        val result = holdUntilSettled({ locks.release(token) }) { settling ->
            settling(launch { palette.await() })
            "applied"
        }
        assertEquals("applied", result)
        assertTrue(locks.locked(auraHold(10)))
        palette.complete(Unit)
        yield()
        assertFalse(locks.locked(auraHold(10)))
    }

    @Test
    fun `an outfit without aura colour work frees its locks when the apply returns`() = runBlocking {
        val locks = ColourSaveLocks()
        val token = locks.acquire(auraHold(10))!!
        holdUntilSettled({ locks.release(token) }) { "applied" }
        assertFalse(locks.locked(auraHold(10)))
        val failed = locks.acquire(auraHold(10))!!
        runCatching { holdUntilSettled<Unit>({ locks.release(failed) }) { throw IllegalStateException() } }
        assertFalse(locks.locked(auraHold(10)))
    }
}
