@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
//? if > 1.8.9
import com.mojang.realmsclient.RealmsMainScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
//? if = 1.8.9 {
/*import net.minecraft.realms.RealmsBridge
*///?}
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.language.I18n
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeStack
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.gui.AccountActions
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialEscape
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.replays.ReplayLibrary
import org.polyfrost.polyplus.client.gui.replays.watchSafely
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.lobby.LobbyFraming
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbyWardrobe
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent
import org.polyfrost.polyplus.client.utils.AnimationActivity
import org.polyfrost.polyplus.compat.replay.ReplayEntry
import java.nio.file.Path

internal enum class LobbyPage(val frosted: Boolean) {
    Home(false),
    Wardrobe(false),
    Multiplayer(true),
    Singleplayer(true),
    Replays(true),
    Marketplace(true),
    Social(true),
    Stats(true),
    ;

    val spokenName: String
        get() = if (this == Home) "Play" else name

    val hidden: Boolean
        get() = when (this) {
            Wardrobe -> PolyPlusMainMenuConfig.hideMainMenuCosmetics
            Social -> PolyPlusMainMenuConfig.hideMainMenuSocial
            Replays -> !ReplayLibrary.enabled
            else -> false
        }
}

internal enum class PlaySource(val page: LobbyPage) {
    Singleplayer(LobbyPage.Singleplayer),
    Multiplayer(LobbyPage.Multiplayer),
    Replays(LobbyPage.Replays),
    ;

    val shown: Boolean
        get() = this != Replays || ReplayLibrary.enabled
}

internal class ServerEdit(val server: ServerData?)

private class PlayChoice(val source: PlaySource, val server: ServerData?, val worldId: String?, val replay: Path?)

internal class LobbyShell(private val screen: Screen, firstPage: LobbyPage) : SocialHost {
    private val mc = Minecraft.getInstance()

    val servers = LobbyServers(mc)
    val worlds = LobbyWorlds(mc)

    var page by mutableStateOf(firstPage)
        private set
    var source by mutableStateOf(choice.source)
    var editing by mutableStateOf<ServerEdit?>(null)
    var directOpen by mutableStateOf(false)
    var sceneOpen by mutableStateOf(false)
    var deleting by mutableStateOf<ServerEntry?>(null)
    var hostWorldId by mutableStateOf<String?>(null)
    var selectedWorldId by mutableStateOf(choice.worldId)
        private set
    private var selectedServer by mutableStateOf(choice.server)
    private var selectedReplay by mutableStateOf(choice.replay)
    var setup by mutableStateOf(onboardingPending())
        private set
    var tour by mutableStateOf<LobbyTour?>(null)
    val anchors = mutableStateMapOf<TourAnchor, Rect>()
    var accountsOpen by mutableStateOf(false)
        private set
    var accountActions by mutableStateOf<AccountActions?>(null)
    var profileVersion by mutableIntStateOf(0)
        private set
    private var profileId = ClientPlatform.localPlayerUuid()

    fun open() {
        if (setup && !onboardingPending()) finishSetup() else if (!setup) startTour()
        LobbyFraming.onboarding = setup
        LobbyScene.arrive()
        HostWorldManager.worldOpener = ::depart
        servers.open()
        selectedServer = selectedServer?.let(servers::resolve)
        worlds.reload()
        if (!source.shown) source = PlaySource.Multiplayer
        if (ReplayLibrary.enabled) ReplayLibrary.reload()
        show(page)
    }

    fun close() {
        choice = PlayChoice(source, selectedServer, selectedWorldId, selectedReplay)
        servers.close()
        SocialChatLink.close()
        HostWorldManager.worldOpener = { it() }
    }

    fun tick() = servers.tick()

    fun finishSetup() {
        setup = false
        LobbyFraming.onboarding = false
        startTour()
    }

    private fun startTour() {
        if (PolyPlusLobbyConfig.tourSeen || tour != null) return
        tour = LobbyTour(PrivacyConsent.allowsOnlineServices())
    }

    fun replayTour() {
        if (setup) return
        show(LobbyPage.Home)
        tour = LobbyTour(PrivacyConsent.allowsOnlineServices())
    }

    fun endTour() {
        tour = null
        if (PolyPlusLobbyConfig.tourSeen) return
        PolyPlusLobbyConfig.tourSeen = true
        PolyPlusLobbyConfig.save()
    }

    fun skipIntro(): Boolean = (!setup || LobbyScene.overlayAlpha <= 0f) && !LobbyWardrobe.open && LobbyScene.skipIntro()

    fun show(target: LobbyPage) {
        val shown = if (target.hidden) LobbyPage.Home else target
        if (shown != page) {
            AnimationActivity.hold(0.6)
            A11y.announce(shown.spokenName + " page")
        }
        page = shown
        LobbyWardrobe.open = shown == LobbyPage.Wardrobe
    }

    fun openAccounts() {
        if (!PolyPlusMainMenuConfig.hideMainMenuAltManager) accountsOpen = true
    }

    fun closeAccounts() {
        if (accountActions?.idle != false) accountsOpen = false
    }

    fun syncAccount() = ClientPlatform.runOnMain {
        val id = ClientPlatform.localPlayerUuid()
        if (id != profileId) {
            profileId = id
            profileVersion++
            if (page == LobbyPage.Wardrobe) show(LobbyPage.Home)
            LobbyScene.reload()
        }
    }

    private fun escapeAccounts() {
        val actions = accountActions
        when {
            actions?.loginSession != null -> actions.cancelLogin()
            actions?.offlineEntry == true -> if (actions.idle) actions.cancelOfflineEntry()
            else -> closeAccounts()
        }
    }

    fun quickServers(): List<ServerEntry> = servers.quickRows(selectedServer)

    fun quickWorlds(): List<LobbyWorld> {
        val rows = worlds.saved.filter { it.playable }.take(LobbyServers.QUICK_ROWS)
        val pinned = activeWorld()?.takeIf { it.playable }
        if (pinned == null || rows.any { it.id == pinned.id }) return rows
        return (listOf(pinned) + rows).take(LobbyServers.QUICK_ROWS)
    }

    fun quickReplays(): List<ReplayEntry> {
        val rows = ReplayLibrary.entries.take(LobbyServers.QUICK_ROWS)
        val pinned = activeReplay()
        if (pinned == null || rows.any { it.path == pinned.path }) return rows
        return (listOf(pinned) + rows).take(LobbyServers.QUICK_ROWS)
    }

    fun isSelected(data: ServerData): Boolean = data.ip.equals(activeServer()?.ip, ignoreCase = true)

    fun isSelected(world: LobbyWorld): Boolean = world.id == activeWorld()?.id

    fun isSelected(entry: ReplayEntry): Boolean = entry.path == activeReplay()?.path

    val selectedReplayPath: Path? get() = selectedReplay

    fun select(data: ServerData) {
        selectedServer = data
        source = PlaySource.Multiplayer
    }

    fun select(world: LobbyWorld) {
        selectedWorldId = world.id
        source = PlaySource.Singleplayer
    }

    fun select(entry: ReplayEntry) {
        if (!ReplayLibrary.enabled) return
        selectedReplay = entry.path
        source = PlaySource.Replays
    }

    fun selectWorld(id: String) {
        worlds.saved.firstOrNull { it.id == id }?.let(::select)
    }

    fun activeServer(): ServerData? = selectedServer ?: quickServers().firstOrNull()?.data

    fun activeWorld(): LobbyWorld? = worlds.saved.firstOrNull { it.id == selectedWorldId } ?: worlds.saved.firstOrNull { it.playable }

    fun activeReplay(): ReplayEntry? =
        if (ReplayLibrary.enabled) ReplayLibrary.find(selectedReplay) ?: ReplayLibrary.entries.firstOrNull { it.playable } ?: ReplayLibrary.entries.firstOrNull() else null

    fun play() {
        when (source) {
            PlaySource.Multiplayer -> activeServer()?.let(::join) ?: show(LobbyPage.Multiplayer)
            PlaySource.Singleplayer -> activeWorld()?.takeIf { it.playable }?.let(::enter) ?: show(LobbyPage.Singleplayer)
            PlaySource.Replays -> when (val entry = activeReplay()) {
                null -> show(LobbyPage.Replays)
                else -> if (entry.playable) watch(entry)
            }
        }
    }

    //? if > 1.8.9 {
    fun openRealms() = ClientPlatform.setScreen(RealmsMainScreen(screen))
    //?} else {
    /*fun openRealms() = RealmsBridge().createMainMenuScreen(screen)
    *///?}

    val hostedWorldName: String? get() = SocialHosting.hostedName

    fun host(worldId: String) {
        SocialHosting.starting(worlds.saved.firstOrNull { it.id == worldId }?.name)
        hostWorldId = worldId
    }

    fun stopHosting() = SocialHosting.stop()

    override fun join(data: ServerData) = depart {
        //? if > 1.8.9 {
        ConnectScreen.startConnecting(screen, mc, ServerAddress.parseString(data.ip), data, false, null)
        //?} else {
        /*ClientPlatform.setScreen(ConnectScreen(screen, mc, data.entry))
        *///?}
    }

    fun enter(world: LobbyWorld) = depart {
        //? if > 1.8.9 {
        mc.createWorldOpenFlows().openWorld(world.id) { ClientPlatform.setScreen(screen) }
        //?} else {
        /*HostWorldManager.openWorld(world.id)
        *///?}
    }

    fun watch(entry: ReplayEntry) {
        if (entry.playable) depart(entry::watchSafely)
    }

    private fun depart(action: () -> Unit) {
        if (page == LobbyPage.Wardrobe) show(LobbyPage.Home)
        LobbyScene.depart(action)
    }

    fun directConnect(address: String): Boolean {
        val trimmed = address.trim()
        if (!ServerAddress.isValidAddress(trimmed)) return false
        val list = servers.serverList
        val known = list.get(trimmed)
        if (known != null) {
            join(known)
            return true
        }
        val draft = ServerData(I18n.get("selectServer.defaultName"), trimmed, ServerData.Type.OTHER)
        list.add(draft, true)
        list.save()
        join(draft)
        return true
    }

    fun addServer() {
        editing = ServerEdit(null)
    }

    fun editServer(entry: ServerEntry) {
        editing = ServerEdit(entry.data)
    }

    private fun escapeForm(): Boolean {
        when {
            hostWorldId != null -> hostWorldId = null
            editing != null -> editing = null
            deleting != null -> deleting = null
            directOpen -> directOpen = false
            else -> return false
        }
        return true
    }

    fun escape(): Boolean {
        when {
            accountsOpen -> escapeAccounts()
            EscapeStack.closeTop() || escapeForm() || SocialEscape.closeDialog() -> Unit
            page != LobbyPage.Home -> show(LobbyPage.Home)
            else -> return false
        }
        return true
    }

    fun openSocial(): Boolean {
        tour?.let { current ->
            if (TextInputFocus.isTyping) return false
            if (!current.next()) endTour()
            return true
        }
        val covered = accountsOpen || EscapeStack.hasOpen || hostWorldId != null || editing != null || deleting != null || directOpen
        if (setup || page == LobbyPage.Social || covered || TextInputFocus.isTyping) return false
        show(LobbyPage.Social)
        return true
    }

    fun finishEdit() {
        editing = null
        servers.reload()
    }

    fun finishDelete() {
        selectedServer = null
        finishEdit()
    }

    fun forget(entry: ServerEntry) {
        FeaturedServers.dismissMultiplayerByAddress(entry.data.ip)
        servers.serverList.remove(entry.data)
        servers.serverList.save()
        selectedServer = null
        deleting = null
        servers.rebuild()
    }

    private companion object {
        var choice = PlayChoice(PlaySource.Multiplayer, null, null, null)
    }
}
