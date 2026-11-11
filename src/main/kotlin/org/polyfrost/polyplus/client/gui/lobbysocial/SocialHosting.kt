package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
//? if = 1.8.9
//import org.polyfrost.polyplus.client.social.singleplayerServer

internal object SocialHosting {
    var hostedName by mutableStateOf<String?>(null)
        private set

    private var invitedSession by mutableStateOf<String?>(null)
    private var invitedPlayers by mutableStateOf(emptySet<String>())

    val singleplayer: Boolean
        get() = Minecraft.getInstance().singleplayerServer != null

    val lanOpen: Boolean
        get() = Minecraft.getInstance().singleplayerServer?.isPublished == true

    val worldName: String?
        //? if > 1.8.9 {
        get() = Minecraft.getInstance().singleplayerServer?.worldData?.levelName ?: hostedName
        //?} else
        //get() = Minecraft.getInstance().singleplayerServer?.worldName ?: hostedName

    fun starting(name: String?) {
        hostedName = name
    }

    fun stop() {
        hostedName = null
        P2PSessionManager.stopHosting()
    }

    fun invited(session: String?): Set<String> = if (session == invitedSession) invitedPlayers else emptySet()

    fun invite(session: String, players: Collection<String>) {
        players.forEach { player ->
            SessionsRepository.invite(session, player) {
                ClientPlatform.runOnMain {
                    if (invitedSession == session) invitedPlayers = invitedPlayers - player
                }
            }
        }
        invitedPlayers = invited(session) + players
        invitedSession = session
    }

    fun invite(player: String): Boolean {
        invite(P2PSessionManager.currentSessionId ?: return false, listOf(player))
        return true
    }

    fun inviteGroup(group: GroupSummary, self: String) {
        val session = P2PSessionManager.currentSessionId
        if (session == null) {
            SocialErrors.emit("Host a world first to invite people to your session")
            return
        }
        invite(session, group.members.filterNot { it == self })
        SocialNotices.emit("Invited the group to your session")
    }
}
