package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.lobby.LobbySnapshot
import org.polyfrost.polyplus.client.lobby.LobbyTime
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.network.http.responses.MemberLocation
import org.polyfrost.polyplus.client.network.http.responses.MemberScene
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.SceneKind
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PartyFigure(val player: String, val whereabouts: String?)

object PartyScene {
    private val LOGGER = LogManager.getLogger()

    private const val CLOCK_STEP = 200
    private const val DAY_TICKS = 24000
    private const val SWITCH_SETTLE_MS = 5000L
    private const val IN_A_WORLD = "In a world"

    private val TWINS = listOf(
        "bundled:snowy_village" to "bundled:snowy_hills",
        "bundled:desert_village" to "bundled:desert_dunes",
        "bundled:cherry_grove" to "bundled:village_green",
    )

    val mcVersion: String by lazy {
        FabricLoader.getInstance().getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("")
    }

    private val hashes = ConcurrentHashMap<String, String>()
    private val hashing = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var published: MemberScene? = null
    private var appliedKey: String? = null
    private var appliedIds: Set<String> = emptySet()
    @Volatile private var appliedAt = 0L
    private var following = false
    private var releasing = false

    var consentPrompt by mutableStateOf<LobbySceneEntry?>(null)
        private set

    private var keepRevision by mutableStateOf(0)

    val keepOwnBackdrop: Boolean
        get() {
            keepRevision
            return PolyPlusPartyConfig.partyKeepOwnBackdrop
        }

    fun setKeepOwnBackdrop(keep: Boolean) {
        if (PolyPlusPartyConfig.partyKeepOwnBackdrop == keep) return
        PolyPlusPartyConfig.partyKeepOwnBackdrop = keep
        PolyPlusPartyConfig.save()
        keepRevision++
        appliedAt = 0L
    }

    fun isMember(party: Party? = PartyRepository.party.value): Boolean =
        party != null && party.members.size >= 2 && !PartyRepository.isLeader(party)

    fun followedLabel(): String? {
        val scene = followed() ?: return null
        if (scene.kind == SceneKind.Capture) return "Your leader's capture"
        return bundledEntry(scene.id)?.label
    }

    private var consents: Map<String, Boolean> = parseConsents(PolyPlusPartyConfig.partySceneConsents)

    fun localScene(): MemberScene? {
        followed()?.let { return it }
        val entry = ownEntry() ?: return published
        val scene = describe(entry)
        val previous = published
        val next = if (previous != null && previous.kind == scene.kind && previous.id == scene.id && clockDistance(previous.clock, scene.clock) <= CLOCK_STEP) {
            previous
        } else {
            scene
        }
        published = next
        return next
    }

    private fun clockDistance(a: Int, b: Int): Int {
        val diff = abs(a - b) % DAY_TICKS
        return minOf(diff, DAY_TICKS - diff)
    }

    private fun currentEntry(): LobbySceneEntry? {
        if (LobbyScene.phase != LobbyScene.Phase.READY) return null
        return LobbyScene.findScene(LobbyScene.currentId ?: return null)
    }

    private fun ownEntry(): LobbySceneEntry? {
        if (following && LobbyScene.phase == LobbyScene.Phase.READY) LobbySceneStore.queue().firstOrNull()?.let { return it }
        return currentEntry()
    }

    private fun describe(entry: LobbySceneEntry): MemberScene {
        val clock = LobbyTime.clock(entry.clock).toInt()
        val fallback = if (entry.bundled) entry.id else fallbackFor(entry).id
        if (entry.bundled) return MemberScene(SceneKind.Bundled, entry.id, entry.id, clock, mcVersion)
        val hash = if (entry.origin == LobbySceneEntry.ORIGIN_WORLD) hashOf(entry) else null
        if (hash != null && shares(entry, hash)) return MemberScene(SceneKind.Capture, hash, fallback, clock, mcVersion)
        return MemberScene(SceneKind.Bundled, fallback, fallback, clock, mcVersion)
    }

    private fun shares(entry: LobbySceneEntry, hash: String): Boolean {
        val party = PartyRepository.party.value
        if (party == null || party.members.size < 2 || !PartyRepository.isLeader(party)) return false
        return when (PolyPlusPartyConfig.partySceneSharing) {
            SceneSharing.ALWAYS -> true
            SceneSharing.NEVER -> false
            SceneSharing.ASK -> consents[hash] ?: run {
                if (consentPrompt?.id != entry.id) consentPrompt = entry
                false
            }
        }
    }

    fun answerConsent(share: Boolean) {
        val entry = consentPrompt ?: return
        consentPrompt = null
        val hash = hashes[entry.id] ?: return
        consents = consents + (hash to share)
        PolyPlusPartyConfig.partySceneConsents = consents.entries.joinToString(",") { "${it.key}=${if (it.value) 1 else 0}" }
        PolyPlusPartyConfig.save()
    }

    private fun parseConsents(raw: String): Map<String, Boolean> = raw.split(',').mapNotNull { pair ->
        val key = pair.substringBefore('=', "").trim()
        val value = pair.substringAfter('=', "").trim()
        if (key.isEmpty() || value.isEmpty()) null else key to (value == "1")
    }.toMap()

    private fun hashOf(entry: LobbySceneEntry): String? {
        hashes[entry.id]?.let { return it }
        val path = LobbySceneStore.fileOf(entry) ?: return null
        if (hashing.add(entry.id)) {
            PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
                runCatching { hashes[entry.id] = sha256(Files.readAllBytes(path)) }
                    .onFailure { LOGGER.warn("Could not hash lobby scene {}", entry.id, it) }
                hashing.remove(entry.id)
            }
        }
        return null
    }

    fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).let { HexFormat.of().formatHex(it) }

    fun sharedCapture(): Pair<String, Path>? {
        val scene = published ?: return null
        if (scene.kind != SceneKind.Capture || !PartyRepository.isLeader()) return null
        val entry = currentEntry() ?: return null
        if (hashes[entry.id] != scene.id) return null
        return scene.id to (LobbySceneStore.fileOf(entry) ?: return null)
    }

    private fun fallbackFor(entry: LobbySceneEntry): LobbySceneEntry {
        val bundled = LobbySceneStore.bundledScenes
        val biome = entry.biome.lowercase()
        val index = when {
            "cherry" in biome -> 2
            listOf("snow", "ice", "frozen", "taiga", "peak", "grove", "hills").any { it in biome } -> 0
            listOf("desert", "badlands", "mesa", "savanna", "beach").any { it in biome } -> 1
            else -> 2
        }
        return bundled.getOrElse(index) { bundled.first() }
    }

    private fun bundledEntry(id: String): LobbySceneEntry? {
        val bundled = LobbySceneStore.bundledScenes
        bundled.firstOrNull { it.id == id }?.let { return it }
        val twin = TWINS.firstOrNull { it.first == id }?.second ?: TWINS.firstOrNull { it.second == id }?.first ?: return null
        return bundled.firstOrNull { it.id == twin }
    }

    fun followed(): MemberScene? {
        val party = PartyRepository.party.value ?: return null
        if (party.members.size < 2 || PartyRepository.isLeader(party) || PolyPlusPartyConfig.partyKeepOwnBackdrop) return null
        val leader = party.member(party.leader) ?: return null
        if (!leader.online) return null
        return leader.state?.scene
    }

    fun followedClock(): Long? = followed()?.clock?.toLong()

    private fun loadable(scene: MemberScene): Boolean = scene.kind == SceneKind.Capture && scene.mcVersion == mcVersion

    private fun desired(scene: MemberScene): List<LobbySceneEntry> {
        val fallback = bundledEntry(scene.fallback) ?: LobbySceneStore.bundledScenes.first()
        if (scene.kind != SceneKind.Capture) return listOf(bundledEntry(scene.id) ?: fallback)
        if (!loadable(scene)) return listOf(fallback)
        val cached = SceneTransfer.cached(scene.id) ?: return listOf(fallback)
        SceneTransfer.touch(cached)
        val entry = LobbySceneEntry(
            "party:${scene.id}",
            cached.toString(),
            "party",
            "Party scene",
            "",
            scene.clock.toLong(),
            0.0,
            0L,
            Files.size(cached),
            0,
            0,
            LobbySnapshot.dataVersion,
            LobbySceneEntry.ORIGIN_PARTY,
        )
        return listOf(entry, fallback)
    }

    private fun leaderPuid(): String? {
        val party = PartyRepository.party.value ?: return null
        return party.member(party.leader)?.eosProductUserId
    }

    fun startEntries(): List<LobbySceneEntry> = followed()?.let(::desired).orEmpty()

    fun tick() {
        val scene = followed()
        if (scene == null) {
            release()
            return
        }
        releasing = false
        if (loadable(scene) && SceneTransfer.cached(scene.id) == null) leaderPuid()?.let { SceneTransfer.fetch(it, scene.id) }
        if (LobbyScene.phase != LobbyScene.Phase.READY) return
        val key = "${scene.kind.serializedName}:${scene.id}:${scene.fallback}:${SceneTransfer.cached(scene.id) != null}"
        val now = System.currentTimeMillis()
        if (key == appliedKey && (LobbyScene.currentId in appliedIds || now - appliedAt < SWITCH_SETTLE_MS)) return
        val entries = desired(scene)
        if (LobbyScene.currentId == entries.first().id || LobbyScene.switchTo(*entries.toTypedArray(), quiet = true)) {
            following = true
            appliedKey = key
            appliedIds = entries.mapTo(HashSet()) { it.id }
            appliedAt = now
        }
    }

    private fun release() {
        if (!following) {
            appliedKey = null
            return
        }
        if (LobbyScene.phase != LobbyScene.Phase.READY) return
        val own = LobbySceneStore.queue().take(2)
        val current = LobbyScene.currentId
        if (own.firstOrNull()?.id == current || (releasing && own.any { it.id == current })) {
            following = false
            releasing = false
            appliedKey = null
            return
        }
        val now = System.currentTimeMillis()
        if (now - appliedAt >= SWITCH_SETTLE_MS && LobbyScene.switchTo(*own.toTypedArray(), quiet = true)) {
            releasing = true
            appliedAt = now
        }
    }

    fun figures(): List<PartyFigure>? {
        val party = PartyRepository.party.value ?: return null
        val others = PartyRepository.others(party)
        if (others.isEmpty()) return null
        val figures = others.filter { it.online }.mapNotNull { member ->
            member.state?.location?.let { PartyFigure(member.player, whereabouts(it)) }
        }
        return ordered(party, figures)
    }

    private fun whereabouts(location: MemberLocation): String? = when (location.kind) {
        LocationKind.Menu -> null
        LocationKind.Server -> location.display?.trim()?.takeIf { it.isNotEmpty() }?.let { "On $it" } ?: IN_A_WORLD
        else -> IN_A_WORLD
    }

    private fun ordered(party: Party, figures: List<PartyFigure>): List<PartyFigure> {
        val joined = party.members.associate { it.player to (it.joinedAt ?: "") }
        return figures.sortedWith(compareBy<PartyFigure> { !PartyRepository.samePlayer(it.player, party.leader) }.thenBy { joined[it.player].orEmpty() })
    }

    fun speaking(): Set<UUID> = PartyVoice.ui.value.speaking.mapNotNullTo(HashSet()) { runCatching { UUID.fromString(it) }.getOrNull() }

    fun reset() {
        published = null
        consentPrompt = null
    }
}
