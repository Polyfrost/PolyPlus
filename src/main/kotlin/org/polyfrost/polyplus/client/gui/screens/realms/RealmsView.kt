package org.polyfrost.polyplus.client.gui.screens.realms

//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsAvailability
import com.mojang.realmsclient.dto.PendingInvite
import com.mojang.realmsclient.dto.RealmsServer
import com.mojang.realmsclient.dto.RealmsServerPlayerLists
import java.util.UUID

internal interface RealmsView {
    val availability: RealmsAvailability.Result?
    val servers: List<RealmsServer>?
    val onlinePlayers: RealmsServerPlayerLists?
    val inviteCount: Int
    val trialAvailable: Boolean
    val newsLink: RealmsLink?
    val unreadNews: Boolean
    val notices: List<RealmsNotice>
    val slow: Boolean
    val tab: RealmsTab
    val invites: List<PendingInvite>?
    val invitesFailed: Boolean
    val inviteProblem: String?
    val answering: Set<String>
    var leaving: RealmsServer?

    fun recheck()
    fun openAvailabilityDetails()
    fun refresh()
    fun selectTab(next: RealmsTab)
    fun isMine(server: RealmsServer): Boolean
    fun play(server: RealmsServer)
    fun configure(server: RealmsServer)
    fun invite(server: RealmsServer)
    fun setUp(server: RealmsServer)
    fun renew(server: RealmsServer)
    fun leave(server: RealmsServer)
    fun buy()
    fun joinWithCode()
    fun openNews()
    fun openNotice(notice: RealmsNotice)
    fun dismissNotice(id: UUID)
    fun loadInvites()
    fun answer(invite: PendingInvite, accept: Boolean)
}
//?}
