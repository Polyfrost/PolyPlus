package org.polyfrost.polyplus.client.emotes.duo

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
*///?}
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

internal object DuoPlayers {
    fun local(): AbstractClientPlayer? = Minecraft.getInstance().player

    fun localOrLobby(): AbstractClientPlayer? = local() ?: lobbyLocal()

    private fun lobbyLocal(): AbstractClientPlayer? {
        val uuid = runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() ?: return null
        return LobbyScene.emoteAvatar(uuid)?.takeIf { it.isLocal }
    }

    fun loaded(): List<AbstractClientPlayer> {
        //? if > 1.8.9 {
        return Minecraft.getInstance().level?.players().orEmpty()
        //?} else {
        /*return Minecraft.getInstance().world?.players.orEmpty().filterIsInstance<AbstractClientPlayer>()
        *///?}
    }

    fun find(uuid: UUID): AbstractClientPlayer? {
        val local = local()
        if (local != null && (local.uuid == uuid || isLocalAccount(uuid))) return local
        return loaded().firstOrNull { it.uuid == uuid }
    }

    fun isLocal(player: AbstractClientPlayer): Boolean = player === local()

    fun isLocalAccount(uuid: UUID): Boolean =
        runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() == uuid

    fun x(player: AbstractClientPlayer): Double = player.x

    fun y(player: AbstractClientPlayer): Double = player.y

    fun z(player: AbstractClientPlayer): Double = player.z

    fun headYaw(player: AbstractClientPlayer): Float =
        //? if > 1.8.9 {
        player.yHeadRot
        //?} else {
        /*player.headYaw
        *///?}

    fun bodyYaw(player: AbstractClientPlayer): Float =
        //? if > 1.8.9 {
        player.yBodyRot
        //?} else {
        /*player.bodyYaw
        *///?}

    fun lookYaw(player: AbstractClientPlayer): Float =
        //? if > 1.8.9 {
        player.yRot
        //?} else {
        /*player.yaw
        *///?}

    fun pitch(player: AbstractClientPlayer): Float =
        //? if > 1.8.9 {
        player.xRot
        //?} else {
        /*player.pitch
        *///?}

    fun name(player: AbstractClientPlayer): String =
        //? if > 1.8.9 {
        player.name.string
        //?} else {
        /*player.name
        *///?}

    fun eyeHeight(): Double = EYE_HEIGHT

    private const val EYE_HEIGHT = 1.62
}
