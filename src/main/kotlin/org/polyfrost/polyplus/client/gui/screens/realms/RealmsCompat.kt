package org.polyfrost.polyplus.client.gui.screens.realms

//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsMainScreen
import com.mojang.realmsclient.client.RealmsClient
import com.mojang.realmsclient.dto.PendingInvite
import com.mojang.realmsclient.dto.PingResult
import com.mojang.realmsclient.dto.RealmsNotification
import com.mojang.realmsclient.dto.RealmsServer
import com.mojang.realmsclient.dto.RealmsServerPlayerLists
import com.mojang.realmsclient.dto.RegionPingResult
import com.mojang.realmsclient.gui.screens.RealmsCreateRealmScreen
import com.mojang.realmsclient.util.RealmsUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.CommonLinks
import net.minecraft.world.level.GameType
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.RealmsVisitUrlAccessor
import java.util.UUID
import java.util.concurrent.Executor
//? if >= 26.3 {
import com.mojang.blaze3d.Blaze3D
import com.mojang.realmsclient.gui.screens.RealmsJoinRealmWithCodeScreen
import com.mojang.realmsclient.gui.screens.RealmsPdpScreen
import java.net.URI
//?} else {
/*import com.mojang.realmsclient.gui.screens.AddRealmPopupScreen
*///?}
//? if >= 1.21.8 {
import com.mojang.realmsclient.gui.screens.configuration.RealmsConfigureWorldScreen
import com.mojang.realmsclient.gui.screens.configuration.RealmsInviteScreen
//?} else {
/*import com.mojang.realmsclient.gui.screens.RealmsConfigureWorldScreen
import com.mojang.realmsclient.gui.screens.RealmsInviteScreen
*///?}
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}

//? if >= 26.3 {
internal typealias RealmsLink = URI
//?} else {
/*internal typealias RealmsLink = String
*///?}

internal object RealmsCompat {
    //? if >= 26.3 {
    const val JOIN_WITH_CODE = true
    //?} else {
    /*const val JOIN_WITH_CODE = false
    *///?}

    val ioPool: Executor get() = Util.nonCriticalIoPool()

    fun client(): RealmsClient =
        //? if >= 1.21.5 {
        RealmsClient.getOrCreate()
        //?} else {
        /*RealmsClient.create()
        *///?}

    fun noticeLink(notice: RealmsNotification.VisitUrl): RealmsLink = (notice as RealmsVisitUrlAccessor).`polyplus$url`()

    fun confirmLink(parent: Screen, link: RealmsLink) = ConfirmLinkScreen.confirmLinkNow(parent, link)

    fun renew(server: RealmsServer, back: Screen) {
        val profile = Minecraft.getInstance().user.profileId
        //? if >= 26.3 {
        val reference = if (server.expiredTrial) CommonLinks.ExtensionReference.EXPIRED_TRIAL else CommonLinks.ExtensionReference.EXPIRED_REALM
        val link = CommonLinks.extendRealms(server.remoteSubscriptionId, profile, reference)
        //?} else {
        /*val link = CommonLinks.extendRealms(server.remoteSubscriptionId, profile, server.expiredTrial)
        *///?}
        ClientPlatform.setScreen(ConfirmLinkScreen({ accepted ->
            if (accepted) openUri(link)
            ClientPlatform.setScreen(back)
        }, link, true))
    }

    private fun openUri(link: RealmsLink) {
        //? if >= 26.3 {
        Blaze3D.openUri(link)
        //?} else {
        /*Util.getPlatform().openUri(link)
        *///?}
    }

    fun configureScreen(vanilla: RealmsMainScreen, server: RealmsServer): Screen = RealmsConfigureWorldScreen(vanilla, server.id)

    fun inviteScreen(vanilla: RealmsMainScreen, server: RealmsServer): Screen {
        val configure = RealmsConfigureWorldScreen(vanilla, server.id)
        //? if >= 1.21.8 {
        return RealmsInviteScreen(configure, server)
        //?} else {
        /*return RealmsInviteScreen(configure, configure, server)
        *///?}
    }

    fun createRealmScreen(vanilla: RealmsMainScreen, server: RealmsServer): Screen =
        //? if >= 1.21.4 {
        RealmsCreateRealmScreen(vanilla, server, server.isSnapshotRealm)
        //?} else {
        /*RealmsCreateRealmScreen(vanilla, server)
        *///?}

    fun buyScreen(parent: Screen, trial: Boolean, joinWithCode: () -> Unit): Screen =
        //? if >= 26.3 {
        RealmsPdpScreen(parent, trial) { joinWithCode() }
        //?} else {
        /*AddRealmPopupScreen(parent, trial)
        *///?}

    fun joinWithCodeScreen(parent: Screen, done: () -> Unit): Screen? =
        //? if >= 26.3 {
        RealmsJoinRealmWithCodeScreen(parent) { done() }
        //?} else {
        /*null
        *///?}

    fun pingResult(regions: List<RegionPingResult>, realms: List<Long>): PingResult =
        //? if >= 1.21.11 {
        PingResult(regions, realms)
        //?} else {
        /*PingResult().also {
            it.pingResults = regions
            it.realmIds = realms
        }
        *///?}

    fun initVanilla(screen: Screen, width: Int, height: Int) {
        //? if >= 1.21.11 {
        screen.init(width, height)
        //?} else {
        /*screen.init(Minecraft.getInstance(), width, height)
        *///?}
    }

    fun invites(client: RealmsClient): List<PendingInvite> =
        //? if >= 1.21.11 {
        client.pendingInvites().pendingInvites()
        //?} else {
        /*client.pendingInvites().pendingInvites.orEmpty()
        *///?}
}

//? if >= 1.21.11 {
internal val PendingInvite.inviteId: String get() = invitationId()
internal val PendingInvite.inviteRealm: String get() = realmName()
internal val PendingInvite.inviteOwner: String get() = realmOwnerName()
internal val PendingInvite.inviteOwnerId: UUID get() = realmOwnerUuid()
internal val PendingInvite.inviteAge: String get() = RealmsUtil.convertToAgePresentationFromInstant(date()).string
//?} else {
/*internal val PendingInvite.inviteId: String get() = invitationId
internal val PendingInvite.inviteRealm: String get() = realmName.orEmpty()
internal val PendingInvite.inviteOwner: String get() = realmOwnerName.orEmpty()
internal val PendingInvite.inviteOwnerId: UUID get() = realmOwnerUuid
internal val PendingInvite.inviteAge: String get() = RealmsUtil.convertToAgePresentationFromInstant(date).string
*///?}

internal fun RealmsServerPlayerLists.playersIn(id: Long): List<UUID> =
    //? if >= 1.21.10 {
    getProfileResultsFor(id)?.map { it.partialProfile().id() }.orEmpty()
    //?} else {
    /*getProfileResultsFor(id)?.mapNotNull { it.profile().id }.orEmpty()
    *///?}

internal val RealmsServer.slotImage: String?
    get() =
        //? if >= 1.21.8 {
        slots[activeSlot]?.options?.templateImage
        //?} else {
        /*slots[activeSlot]?.templateImage
        *///?}

internal val RealmsServer.gameModeLabel: String?
    get() =
        //? if >= 1.21.4 {
        gameMode.takeIf { !isMinigameActive && GameType.isValidId(it) }?.let { RealmsMainScreen.getGameModeComponent(it, isHardcore).string }
        //?} else {
        /*slots[activeSlot]?.gameMode?.takeIf { mode -> !isMinigameActive && GameType.entries.any { it.id == mode } }?.let { GameType.byId(it).longDisplayName.string }
        *///?}

internal val RealmsServer.playable: Boolean
    get() =
        //? if >= 1.21.8 {
        shouldPlayButtonBeActive()
        //?} else {
        /*!expired && state == RealmsServer.State.OPEN && (isCompatible || needsUpgrade() || Minecraft.getInstance().isLocalPlayer(ownerUUID))
        *///?}
//?}
