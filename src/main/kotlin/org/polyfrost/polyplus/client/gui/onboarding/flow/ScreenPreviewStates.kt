package org.polyfrost.polyplus.client.gui.onboarding.flow

import androidx.compose.ui.geometry.Rect
//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsAvailability
import com.mojang.realmsclient.dto.PendingInvite
import com.mojang.realmsclient.dto.RealmsServer
import com.mojang.realmsclient.dto.RealmsServerPlayerLists
//?}
import net.minecraft.SharedConstants
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.gui.pausescreen.PauseEntry
import org.polyfrost.polyplus.client.gui.pausescreen.PauseMenu
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle
import org.polyfrost.polyplus.client.gui.pausescreen.PauseSlot
import org.polyfrost.polyplus.client.gui.pausescreen.PauseView
//? if > 1.8.9 {
import org.polyfrost.polyplus.client.gui.screens.realms.RealmsLink
import org.polyfrost.polyplus.client.gui.screens.realms.RealmsNotice
import org.polyfrost.polyplus.client.gui.screens.realms.RealmsTab
import org.polyfrost.polyplus.client.gui.screens.realms.RealmsView
//?}
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.UUID

private class PreviewPauseMenu : PauseMenu {
    override val entries: List<PauseEntry> = listOf(
        entry(PauseSlot.RESUME, "menu.returnToGame", GAME, "dots-grid"),
        entry(PauseSlot.OPTIONS, "menu.options", GAME, "cog"),
        entry(PauseSlot.WORLD, "menu.shareToLan", GAME, "assets/polyplus/mainmenu/globe-01.svg"),
        entry(PauseSlot.ADVANCEMENTS, "gui.advancements", GAME, "star"),
        entry(PauseSlot.STATS, "gui.stats", GAME, "align"),
        entry(PauseSlot.MORE, "menu.sendFeedback", HELP, "dots-grid"),
        entry(PauseSlot.MORE, "menu.reportBugs", HELP, "dots-grid"),
        entry(PauseSlot.QUIT, "menu.returnToMenu", GAME, "assets/polyplus/mainmenu/log-out-04.svg"),
    )

    override val more: List<PauseEntry> get() = entries.filter { it.slot == PauseSlot.MORE }

    override val uncaptured: Int = 0

    override fun entry(slot: PauseSlot): PauseEntry? = entries.firstOrNull { it.slot == slot }

    private fun entry(slot: PauseSlot, key: String, section: String, glyph: String) =
        PauseEntry(key.hashCode(), slot, Component.translatable(key).string, section, true, null, null, glyph) {}

    private companion object {
        const val GAME = "Game"
        const val HELP = "Help"
    }
}

internal class PreviewPause : PauseView {
    private val style = PolyPlusPauseConfig.pauseScreenStyle.takeIf { it != PauseScreenStyle.VANILLA } ?: PauseScreenStyle.STEP_BACK_FLAT

    override val host: Screen? = null
    override val capture: PauseMenu = PreviewPauseMenu()
    override val stepBack: Boolean = style == PauseScreenStyle.STEP_BACK || style == PauseScreenStyle.STEP_BACK_FLAT
    override val classic: Boolean = style == PauseScreenStyle.CLASSIC
    override val staged: Boolean = false
    override var moreOpen: Boolean = false
    override var moreLinked: Boolean = false
    override var customizeOpen: Boolean = false
    override val settingsOpen: Boolean = false
    override var playerPreview: Boolean = style != PauseScreenStyle.STEP_BACK_FLAT || PolyPlusPauseConfig.flatPausePlayerPreview
    override var resumeBounds: Rect = Rect.Zero
    override val fromVanilla: Boolean = false
    override val online: Boolean get() = PrivacyConsent.allowsOnlineServices()
    override val canHost: Boolean = false

    override fun resume() = Unit
    override fun closeIfCurrent() = Unit
    override fun resyncMenu() = Unit
    override fun toggleCustomize() = Unit
    override fun closeCustomize() = Unit
    override fun openClassic() = Unit
    override fun restyle(next: PauseScreenStyle) = Unit
    override fun openSocial() = Unit
    override fun openHost() = Unit
    override fun openWardrobe() = Unit
    override fun openSettings() = Unit
    override fun openMods() = Unit
    override fun closeGameSettings() = Unit
}

//? if > 1.8.9 {
internal class PreviewRealms : RealmsView {
    override val availability: RealmsAvailability.Result = RealmsAvailability.Result(RealmsAvailability.Type.SUCCESS)
    override val servers: List<RealmsServer> = listOf(
        realm(1L, "Weekend survival", "Back at the base by nine", ClientPlatform.localPlayerName(), ClientPlatform.localPlayerUuid(), RealmsServer.State.OPEN),
        realm(2L, "Skyblock with friends", "New islands every season", "Alex", UUID(0L, 2L), RealmsServer.State.OPEN),
        realm(3L, "Creative builds", "City project, plot 14", "Steve", UUID(0L, 3L), RealmsServer.State.CLOSED),
    )
    override val onlinePlayers: RealmsServerPlayerLists? = null
    override val inviteCount: Int = 0
    override val trialAvailable: Boolean = false
    override val newsLink: RealmsLink? = null
    override val unreadNews: Boolean = false
    override val notices: List<RealmsNotice> = emptyList()
    override val slow: Boolean = false
    override val tab: RealmsTab = RealmsTab.Realms
    override val invites: List<PendingInvite>? = null
    override val invitesFailed: Boolean = false
    override val inviteProblem: String? = null
    override val answering: Set<String> = emptySet()
    override var leaving: RealmsServer? = null

    override fun recheck() = Unit
    override fun openAvailabilityDetails() = Unit
    override fun refresh() = Unit
    override fun selectTab(next: RealmsTab) = Unit
    override fun isMine(server: RealmsServer): Boolean = server.ownerUUID == ClientPlatform.localPlayerUuid()
    override fun play(server: RealmsServer) = Unit
    override fun configure(server: RealmsServer) = Unit
    override fun invite(server: RealmsServer) = Unit
    override fun setUp(server: RealmsServer) = Unit
    override fun renew(server: RealmsServer) = Unit
    override fun leave(server: RealmsServer) = Unit
    override fun buy() = Unit
    override fun joinWithCode() = Unit
    override fun openNews() = Unit
    override fun openNotice(notice: RealmsNotice) = Unit
    override fun dismissNotice(id: UUID) = Unit
    override fun loadInvites() = Unit
    override fun answer(invite: PendingInvite, accept: Boolean) = Unit

    private fun realm(id: Long, name: String, motd: String, owner: String, ownerId: UUID, state: RealmsServer.State) = RealmsServer().apply {
        this.id = id
        this.name = name
        this.motd = motd
        this.owner = owner
        this.ownerUUID = ownerId
        this.state = state
        players = emptyList()
        slots = emptyMap()
        worldType = RealmsServer.WorldType.NORMAL
        compatibility = RealmsServer.Compatibility.COMPATIBLE
        //? if >= 1.21.8 {
        activeVersion = SharedConstants.getCurrentVersion().name()
        //?} else {
        /*activeVersion = SharedConstants.getCurrentVersion().name
        *///?}
        daysLeft = DAYS_LEFT
    }

    private companion object {
        const val DAYS_LEFT = 24
    }
}
//?}
