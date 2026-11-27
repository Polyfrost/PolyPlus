package org.polyfrost.polyplus.client.pets

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class PetPlay(private val label: String, val wireName: String) {
    EVERYONE("Everyone", "everyone"),

    FRIENDS("Friends Only", "friends"),

    OFF("Off", "off");

    override fun toString(): String = label

    companion object {
        fun ofWire(name: String): PetPlay = entries.firstOrNull { it.wireName == name } ?: EVERYONE

        fun ofSwitch(on: Boolean): PetPlay = if (on) EVERYONE else OFF
    }
}

internal object PetPlaySerializer : KSerializer<PetPlay> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PetPlay", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PetPlay) = encoder.encodeString(value.wireName)

    override fun deserialize(decoder: Decoder): PetPlay {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element !is JsonPrimitive) return PetPlay.EVERYONE
            if (!element.isString) element.booleanOrNull?.let { return PetPlay.ofSwitch(it) }
            return PetPlay.ofWire(element.content)
        }
        return PetPlay.ofWire(decoder.decodeString())
    }
}

internal class PetPlayPermissions {
    private val settings = ConcurrentHashMap<UUID, PetPlay>()
    private val friends = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    fun applySnapshot(players: Collection<UUID>, off: Collection<UUID>, friendLists: Map<UUID, Collection<UUID>>) {
        for (player in players) {
            if (player in off || player in friendLists) continue
            settings[player] = PetPlay.EVERYONE
            friends.remove(player)
        }
        for ((player, listed) in friendLists) {
            if (player in off) continue
            settings[player] = PetPlay.FRIENDS
            friends.getOrPut(player) { ConcurrentHashMap.newKeySet() }.addAll(listed)
        }
        for (player in off) {
            settings[player] = PetPlay.OFF
            friends.remove(player)
        }
    }

    fun applyChange(player: UUID, play: PetPlay, listed: Collection<UUID>) {
        settings[player] = play
        if (play == PetPlay.FRIENDS) {
            friends[player] = ConcurrentHashMap.newKeySet<UUID>().apply { addAll(listed) }
        } else {
            friends.remove(player)
        }
    }

    fun forget(players: Collection<UUID>) {
        if (players.isEmpty()) return
        val gone = players.toSet()
        for (player in gone) {
            settings.remove(player)
            friends.remove(player)
        }
        for (set in friends.values) set.removeAll(gone)
    }

    fun clear() {
        settings.clear()
        friends.clear()
    }

    fun plays(owner: UUID, local: UUID, localPlay: PetPlay): Boolean = setting(owner, local, localPlay) != PetPlay.OFF

    fun allows(a: UUID, b: UUID, local: UUID, localPlay: PetPlay, localFriend: (UUID) -> Boolean): Boolean {
        val aPlay = setting(a, local, localPlay)
        val bPlay = setting(b, local, localPlay)
        if (aPlay == PetPlay.OFF || bPlay == PetPlay.OFF) return false
        if (aPlay == PetPlay.FRIENDS && !lists(a, b, local, localFriend)) return false
        return bPlay != PetPlay.FRIENDS || lists(b, a, local, localFriend)
    }

    private fun setting(owner: UUID, local: UUID, localPlay: PetPlay): PetPlay =
        if (owner == local) localPlay else settings[owner] ?: PetPlay.EVERYONE

    private fun lists(owner: UUID, other: UUID, local: UUID, localFriend: (UUID) -> Boolean): Boolean =
        if (owner == local) localFriend(other) else friends[owner]?.contains(other) == true
}
