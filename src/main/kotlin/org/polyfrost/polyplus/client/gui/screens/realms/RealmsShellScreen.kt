@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.realms

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
//? if >= 26.3 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mojang.blaze3d.Blaze3D
import com.mojang.logging.LogUtils
import com.mojang.realmsclient.RealmsAvailability
import com.mojang.realmsclient.RealmsMainScreen
import com.mojang.realmsclient.client.Ping
import com.mojang.realmsclient.client.RealmsClient
import com.mojang.realmsclient.dto.PendingInvite
import com.mojang.realmsclient.dto.PingResult
import com.mojang.realmsclient.dto.RealmsNotification
import com.mojang.realmsclient.dto.RealmsServer
import com.mojang.realmsclient.dto.RealmsServerPlayerLists
import com.mojang.realmsclient.exception.RealmsServiceException
import com.mojang.realmsclient.gui.screens.RealmsCreateRealmScreen
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen
import com.mojang.realmsclient.gui.screens.RealmsJoinRealmWithCodeScreen
import com.mojang.realmsclient.gui.screens.RealmsPdpScreen
import com.mojang.realmsclient.gui.screens.configuration.RealmsConfigureWorldScreen
import com.mojang.realmsclient.gui.screens.configuration.RealmsInviteScreen
import com.mojang.realmsclient.gui.task.DataFetcher
import com.mojang.realmsclient.util.RealmsPersistence
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.util.CommonLinks
import net.minecraft.util.Util
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.RealmsMainScreenAccessor
import org.polyfrost.polyplus.mixin.client.access.RealmsVisitUrlAccessor
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal enum class RealmsTab { Realms, Invites }

internal class RealmsNotice(val id: UUID, val message: String, val label: String, val url: URI, val dismissable: Boolean)

class RealmsShellScreen(shell: ScreenShell, val vanilla: RealmsMainScreen) :
    ShellScreen(shell, (vanilla as RealmsMainScreenAccessor).`polyplus$lastScreen`()) {
    private val mc = Minecraft.getInstance()
    private var subscription: DataFetcher.Subscription? = null
    private var checking = false
    private var vanillaReady = false
    private var loadingSince = 0L
    private val seenNotices = HashSet<UUID>()

    internal var availability by mutableStateOf<RealmsAvailability.Result?>(null)
        private set
    internal var servers by mutableStateOf<List<RealmsServer>?>(null)
        private set
    internal var onlinePlayers by mutableStateOf<RealmsServerPlayerLists?>(null)
        private set
    internal var inviteCount by mutableIntStateOf(0)
        private set
    internal var trialAvailable by mutableStateOf(false)
        private set
    internal var newsLink by mutableStateOf<URI?>(null)
        private set
    internal var unreadNews by mutableStateOf(false)
        private set
    internal var notices by mutableStateOf<List<RealmsNotice>>(emptyList())
        private set
    internal var slow by mutableStateOf(false)
        private set
    internal var tab by mutableStateOf(RealmsTab.Realms)
        private set
    internal var invites by mutableStateOf<List<PendingInvite>?>(null)
        private set
    internal var invitesFailed by mutableStateOf(false)
        private set
    internal var inviteProblem by mutableStateOf<String?>(null)
        private set
    internal var answering by mutableStateOf<Set<String>>(emptySet())
        private set
    internal var leaving by mutableStateOf<RealmsServer?>(null)

    override val title: String get() = "Realms"

    override fun tick() {
        super.tick()
        if (availability == null && !checking) check()
        subscription?.tick()
        if (servers == null && subscription != null && !slow && System.currentTimeMillis() - loadingSince > SLOW_MILLIS) slow = true
    }

    private fun check() {
        checking = true
        RealmsAvailability.get().thenAcceptAsync({ result ->
            checking = false
            availability = result
            if (result.type() == RealmsAvailability.Type.SUCCESS && subscription == null) subscribe()
        }, mc)
    }

    internal fun recheck() {
        if (checking) return
        availability = null
    }

    internal fun openAvailabilityDetails() {
        availability?.createErrorScreen(this)?.let(::open)
    }

    private fun subscribe() {
        val fetcher = mc.realmsDataFetcher()
        val sub = fetcher.dataFetcher.createSubscription()
        loadingSince = System.currentTimeMillis()
        sub.subscribe(fetcher.serverListUpdateTask) { data ->
            val sorted = data.serverList().sortedWith(RealmsServer.McoServerComparator(mc.user.name))
            servers = sorted
            slow = false
            pingRegions(sorted)
        }
        sub.subscribe(fetcher.pendingInvitesTask) { inviteCount = it }
        sub.subscribe(fetcher.trialAvailabilityTask) { trialAvailable = it }
        sub.subscribe(fetcher.onlinePlayersTask) { onlinePlayers = it }
        sub.subscribe(fetcher.newsTask) { news ->
            fetcher.newsManager.updateUnreadNews(news)
            newsLink = fetcher.newsManager.newsLink()
            unreadNews = fetcher.newsManager.hasUnreadNews()
        }
        subscription = sub
        loadNotifications()
    }

    internal fun refresh() {
        slow = false
        loadingSince = System.currentTimeMillis()
        RealmsMainScreen.refreshServerList()
        RealmsMainScreen.refreshPendingInvites()
        if (tab == RealmsTab.Invites) loadInvites()
    }

    internal fun selectTab(next: RealmsTab) {
        tab = next
        if (next == RealmsTab.Invites) loadInvites()
    }

    internal fun isMine(server: RealmsServer): Boolean = mc.isLocalPlayer(server.ownerUUID)

    internal fun play(server: RealmsServer) = RealmsMainScreen.play(server, this)

    internal fun configure(server: RealmsServer) {
        if (!isMine(server)) return
        ensureVanilla()
        open(RealmsConfigureWorldScreen(vanilla, server.id))
    }

    internal fun invite(server: RealmsServer) {
        if (!isMine(server)) return
        ensureVanilla()
        open(RealmsInviteScreen(RealmsConfigureWorldScreen(vanilla, server.id), server))
    }

    internal fun setUp(server: RealmsServer) {
        ensureVanilla()
        open(RealmsCreateRealmScreen(vanilla, server, server.isSnapshotRealm))
    }

    internal fun renew(server: RealmsServer) {
        val reference = if (server.expiredTrial) CommonLinks.ExtensionReference.EXPIRED_TRIAL else CommonLinks.ExtensionReference.EXPIRED_REALM
        val uri = CommonLinks.extendRealms(server.remoteSubscriptionId, mc.user.profileId, reference)
        open(ConfirmLinkScreen({ accepted ->
            if (accepted) Blaze3D.openUri(uri)
            ClientPlatform.setScreen(this)
        }, uri, true))
    }

    internal fun leave(server: RealmsServer) {
        leaving = null
        servers = servers?.filterNot { it.id == server.id }
        async({ it.uninviteMyselfFrom(server.id) }, { RealmsMainScreen.refreshServerList() }) { error ->
            RealmsMainScreen.refreshServerList()
            if (error is RealmsServiceException) open(RealmsGenericErrorScreen(error, this))
        }
    }

    internal fun buy() = open(RealmsPdpScreen(this, trialAvailable) { joinWithCode() })

    internal fun joinWithCode() {
        val from = ClientPlatform.currentScreen() ?: this
        open(RealmsJoinRealmWithCodeScreen(from) {
            ClientPlatform.setScreen(this)
            refresh()
        })
    }

    internal fun openNews() {
        val link = newsLink ?: return
        ConfirmLinkScreen.confirmLinkNow(this, link)
        if (!unreadNews) return
        val data = RealmsPersistence.readFile()
        data.hasUnreadNews = false
        RealmsPersistence.writeFile(data)
        unreadNews = false
    }

    internal fun openNotice(notice: RealmsNotice) = ConfirmLinkScreen.confirmLinkNow(this, notice.url)

    internal fun dismissNotice(id: UUID) {
        async({ it.notificationsDismiss(listOf(id)) }, {
            notices = notices.filterNot { it.dismissable && it.id == id }
        })
    }

    private fun loadNotifications() {
        async({ it.notifications }, { list -> onNotifications(list) })
    }

    private fun onNotifications(list: List<RealmsNotification>) {
        val visit = list.filterIsInstance<RealmsNotification.VisitUrl>().firstOrNull()
        notices = listOfNotNull(visit?.let {
            RealmsNotice(it.uuid(), it.message.string, it.buildOpenLinkButton(this).message.string, (it as RealmsVisitUrlAccessor).`polyplus$url`(), it.dismissable())
        })
        visit?.let { markSeen(listOf(it)) }
        if (ClientPlatform.currentScreen() !== this) return
        for (popup in list.filterIsInstance<RealmsNotification.InfoPopup>()) {
            val screen = popup.buildScreen(this, ::dismissNotice) ?: continue
            open(screen)
            markSeen(listOf(popup))
            break
        }
    }

    private fun markSeen(list: List<RealmsNotification>) {
        val ids = list.filter { !it.seen() && it.uuid() !in seenNotices }.map { it.uuid() }
        if (ids.isEmpty()) return
        async({ it.notificationsSeen(ids) }, { seenNotices.addAll(ids) })
    }

    internal fun loadInvites() {
        invitesFailed = false
        inviteProblem = null
        RealmsMainScreen.refreshPendingInvites()
        async({ it.pendingInvites().pendingInvites() }, { invites = it }) {
            invites = emptyList()
            invitesFailed = true
        }
    }

    internal fun answer(invite: PendingInvite, accept: Boolean) {
        val id = invite.invitationId()
        if (id in answering) return
        answering = answering + id
        inviteProblem = null
        async({ client ->
            if (accept) client.acceptInvitation(id) else client.rejectInvitation(id)
        }, {
            answering = answering - id
            invites = invites?.filterNot { it.invitationId() == id }
            val fetcher = mc.realmsDataFetcher()
            if (accept) fetcher.serverListUpdateTask.reset()
            fetcher.pendingInvitesTask.reset()
        }) {
            answering = answering - id
            inviteProblem = if (accept) "Couldn't accept the invite. Try again." else "Couldn't decline the invite. Try again."
        }
    }

    private fun ensureVanilla() {
        if (vanillaReady) return
        vanillaReady = true
        val window = mc.window
        vanilla.init(window.guiScaledWidth, window.guiScaledHeight)
    }

    private fun pingRegions(list: List<RealmsServer>) {
        if (regionsPinged) return
        val owned = list.filter { isMine(it) && !it.expired }.map { it.id }
        if (owned.isEmpty()) return
        regionsPinged = true
        Util.nonCriticalIoPool().execute {
            runCatching { RealmsClient.getOrCreate().sendPingResults(PingResult(Ping.pingAllRegions(), owned)) }
                .onFailure { LOGGER.warn("Could not send ping result to Realms", it) }
        }
    }

    private fun <T> async(request: (RealmsClient) -> T, done: (T) -> Unit, failed: (Throwable) -> Unit = {}) {
        CompletableFuture.supplyAsync({ request(RealmsClient.getOrCreate()) }, Util.nonCriticalIoPool())
            .whenCompleteAsync({ value, error ->
                if (error == null) done(value) else {
                    LOGGER.warn("Realms request failed", error)
                    failed(error.cause ?: error)
                }
            }, mc)
    }

    @Composable
    override fun Body() = RealmsBody(this)

    private companion object {
        const val SLOW_MILLIS = 15_000L
        val LOGGER = LogUtils.getLogger()
        var regionsPinged = false
    }
}
//?}
