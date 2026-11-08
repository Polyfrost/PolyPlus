package org.polyfrost.polyplus.client.lobby

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
//? if > 1.8.9
import net.minecraft.client.resources.DefaultPlayerSkin
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?} else {
/*import net.minecraft.client.resource.skin.DefaultSkinUtils
import org.polyfrost.oneconfig.internal.legacy.PlayerSkin
*///?}
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyScene
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
//? if = 1.8.9
/*import java.util.concurrent.CompletableFuture*/
//? if >= 1.21.10
import java.util.function.Supplier

class LobbyFriend(val uuid: UUID, val name: String, val whereabouts: String? = null)

class LobbyCast(val party: List<LobbyFriend>, val background: List<LobbyFriend>)

object LobbyFriends {
    private const val REFRESH_TICKS = 1200

    private val EMPTY = LobbyCast(emptyList(), emptyList())

    private val profiles = ConcurrentHashMap<UUID, GameProfile>()
    private val fetching = ConcurrentHashMap.newKeySet<UUID>()
    private val retries = ConcurrentHashMap<UUID, Retry>()
    private val RETRY_MILLIS = longArrayOf(30_000L, 120_000L, 600_000L)

    private class Retry(val attempt: Int, val at: Long)
    //? if >= 1.21.10 {
    private val lookups = HashMap<UUID, Supplier<PlayerSkin>>()
    //?} elif = 1.8.9 {
    /*private val lookups = HashMap<UUID, CompletableFuture<PlayerSkin>>()
    *///?}
    private var ticks = 0

    fun refresh() {
        if (PrivacyConsent.allowsOnlineServices()) FriendsRepository.refreshAll()
    }

    fun tick() {
        if (++ticks < REFRESH_TICKS) return
        ticks = 0
        refresh()
    }

    fun cast(): LobbyCast {
        if (!PrivacyConsent.allowsOnlineServices()) return EMPTY
        val members = PartyRepository.party.value?.members.orEmpty()
        val figures = PartyScene.figures().orEmpty()
        val party = figures.map { it.player }
        val others = FriendsRepository.friends.value
            .filter { friend -> friend.online && members.none { PartyRepository.samePlayer(it.player, friend.player) } }
            .map { it.player }
        val everyone = party + others
        if (everyone.isEmpty()) return EMPTY
        PlayerNamesRepository.resolve(everyone)
        CosmeticSync.subscribeFriends(everyone)
        val names = PlayerNamesRepository.names.value
        val whereabouts = figures.associate { it.player to it.whereabouts }
        return LobbyCast(known(party, names, whereabouts), known(others, names, emptyMap()))
    }

    private fun known(players: List<String>, names: Map<String, String>, whereabouts: Map<String, String?>): List<LobbyFriend> = players.mapNotNull { id ->
        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
        val name = names[id]
        if (uuid != null && name != null) LobbyFriend(uuid, name, whereabouts[id]) else null
    }

    fun skin(friend: LobbyFriend): PlayerSkin {
        val profile = profiles[friend.uuid]
        if (profile == null) {
            fetch(friend)
            return fallback(friend.uuid)
        }
        //? if >= 1.21.10 {
        return lookups.getOrPut(friend.uuid) { Minecraft.getInstance().skinManager.createLookup(profile, false) }.get()
        //?} elif > 1.8.9 {
        /*return Minecraft.getInstance().skinManager.getInsecureSkin(profile)
        *///?} else {
        /*return lookups.getOrPut(friend.uuid) { Minecraft.getInstance().skinManager.getOrLoad(profile) }.getNow(null) ?: fallback(friend.uuid)
        *///?}
    }

    private fun fallback(uuid: UUID): PlayerSkin {
        //? if > 1.8.9 {
        return DefaultPlayerSkin.get(uuid)
        //?} else {
        /*return PlayerSkin(DefaultSkinUtils.getDefaultSkin(uuid))
        *///?}
    }

    private fun fetch(friend: LobbyFriend) {
        val retry = retries[friend.uuid]
        if (retry != null && System.currentTimeMillis() < retry.at) return
        if (!fetching.add(friend.uuid)) return
        val attempt = retry?.attempt ?: 0
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            try {
                val mc = Minecraft.getInstance()
                val fetched = runCatching {
                    //? if >= 1.21.10 {
                    mc.services().sessionService().fetchProfile(friend.uuid, false)?.profile()
                    //?} elif > 1.8.9 {
                    /*mc.minecraftSessionService.fetchProfile(friend.uuid, false)?.profile()
                    *///?} else {
                    /*mc.sessionService.fillProfileProperties(GameProfile(friend.uuid, friend.name), false)?.takeIf { it.properties.containsKey("textures") }
                    *///?}
                }.getOrNull()
                when {
                    fetched != null -> {
                        profiles[friend.uuid] = fetched
                        retries.remove(friend.uuid)
                    }
                    attempt < RETRY_MILLIS.size -> retries[friend.uuid] = Retry(attempt + 1, System.currentTimeMillis() + RETRY_MILLIS[attempt])
                    else -> {
                        profiles[friend.uuid] = GameProfile(friend.uuid, friend.name)
                        retries.remove(friend.uuid)
                    }
                }
            } finally {
                fetching.remove(friend.uuid)
            }
        }
    }
}
