@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.realms

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
//? if > 1.8.9 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mojang.logging.LogUtils
import com.mojang.realmsclient.RealmsAvailability
import com.mojang.realmsclient.RealmsMainScreen
import com.mojang.realmsclient.client.Ping
import com.mojang.realmsclient.client.RealmsClient
import com.mojang.realmsclient.dto.PendingInvite
import com.mojang.realmsclient.dto.RealmsNotification
import com.mojang.realmsclient.dto.RealmsServer
import com.mojang.realmsclient.dto.RealmsServerPlayerLists
import com.mojang.realmsclient.exception.RealmsServiceException
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen
import com.mojang.realmsclient.gui.task.DataFetcher
import com.mojang.realmsclient.util.RealmsPersistence
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.RealmsMainScreenAccessor
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal enum class RealmsTab { Realms, Invites }

internal class RealmsNotice(val id: UUID, val message: String, val label: String, val url: RealmsLink, val dismissable: Boolean)

class RealmsShellScreen(shell: ScreenShell, val vanilla: RealmsMainScreen) :
    ShellScreen(shell, (vanilla as RealmsMainScreenAccessor).`polyplus$lastScreen`()) {
    private val mc = Minecraft.getInstance()
    private var subscription: DataFetcher.Subscription? = null
    private var checking = false
    private var vanillaReady = false
    private var loadingSince = 0L
    private val seenNotices = HashSet<UUID>()
    private var pendingPopups: List<RealmsNotification.InfoPopup> = emptyList()
    private var visitNotifications: List<RealmsNotification.VisitUrl> = emptyList()

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
    internal var newsLink by mutableStateOf<RealmsLink?>(null)
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
    private val answered = HashSet<String>()
    private var unseenNotice: RealmsNotification? = null
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
        if (pendingPopups.isNotEmpty() && ClientPlatform.currentScreen() === this) showPopup()
        if (unseenNotice != null && ClientPlatform.currentScreen() === this) markShownNotice()
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
        open(RealmsCompat.configureScreen(vanilla, server))
    }

    internal fun invite(server: RealmsServer) {
        if (!isMine(server)) return
        ensureVanilla()
        open(RealmsCompat.inviteScreen(vanilla, server))
    }

    internal fun setUp(server: RealmsServer) {
        ensureVanilla()
        open(RealmsCompat.createRealmScreen(vanilla, server))
    }

    internal fun renew(server: RealmsServer) = RealmsCompat.renew(server, this)

    internal fun leave(server: RealmsServer) {
        leaving = null
        servers = servers?.filterNot { it.id == server.id }
        async({ it.uninviteMyselfFrom(server.id) }, { RealmsMainScreen.refreshServerList() }) { error ->
            RealmsMainScreen.refreshServerList()
            if (error is RealmsServiceException && ClientPlatform.currentScreen() === this) open(RealmsGenericErrorScreen(error, this))
        }
    }

    internal fun buy() = open(RealmsCompat.buyScreen(this, trialAvailable) { joinWithCode() })

    internal fun joinWithCode() {
        val from = ClientPlatform.currentScreen() ?: this
        val screen = RealmsCompat.joinWithCodeScreen(from) {
            ClientPlatform.setScreen(this)
            refresh()
        } ?: return
        open(screen)
    }

    internal fun openNews() {
        val link = newsLink ?: return
        RealmsCompat.confirmLink(this, link)
        if (!unreadNews) return
        val data = RealmsPersistence.readFile()
        data.hasUnreadNews = false
        RealmsPersistence.writeFile(data)
        unreadNews = false
    }

    internal fun openNotice(notice: RealmsNotice) = RealmsCompat.confirmLink(this, notice.url)

    internal fun dismissNotice(id: UUID) {
        async({ it.notificationsDismiss(listOf(id)) }, {
            visitNotifications = visitNotifications.filterNot { it.dismissable() && it.uuid() == id }
            notices = notices.filterNot { it.dismissable && it.id == id }
            if (notices.isEmpty()) showNextNotice()
        })
    }

    private fun loadNotifications() {
        async({ it.notifications }, { list -> onNotifications(list) })
    }

    private fun onNotifications(list: List<RealmsNotification>) {
        visitNotifications = list.filterIsInstance<RealmsNotification.VisitUrl>()
        showNextNotice()
        pendingPopups = list.filterIsInstance<RealmsNotification.InfoPopup>()
        if (ClientPlatform.currentScreen() === this) showPopup()
    }

    private fun showNextNotice() {
        val visit = visitNotifications.firstOrNull()
        notices = listOfNotNull(visit?.let {
            RealmsNotice(it.uuid(), it.message.string, it.buildOpenLinkButton(this).message.string, RealmsCompat.noticeLink(it), it.dismissable())
        })
        unseenNotice = visit
        if (ClientPlatform.currentScreen() === this) markShownNotice()
    }

    private fun markShownNotice() {
        val notice = unseenNotice ?: return
        unseenNotice = null
        markSeen(listOf(notice))
    }

    private fun showPopup() {
        val popups = pendingPopups
        pendingPopups = emptyList()
        for (popup in popups) {
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
        async({ RealmsCompat.invites(it) }, { list -> invites = list.filterNot { it.inviteId in answered } }) {
            invites = emptyList()
            invitesFailed = true
        }
    }

    internal fun answer(invite: PendingInvite, accept: Boolean) {
        val id = invite.inviteId
        if (id in answering) return
        answering = answering + id
        inviteProblem = null
        async({ client ->
            if (accept) client.acceptInvitation(id) else client.rejectInvitation(id)
        }, {
            answering = answering - id
            answered += id
            invites = invites?.filterNot { it.inviteId == id }
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
        RealmsCompat.initVanilla(vanilla, window.guiScaledWidth, window.guiScaledHeight)
    }

    private fun pingRegions(list: List<RealmsServer>) {
        if (regionsPinged) return
        val owned = list.filter { isMine(it) && !it.expired }.map { it.id }
        if (owned.isEmpty()) return
        regionsPinged = true
        RealmsCompat.ioPool.execute {
            runCatching { RealmsCompat.client().sendPingResults(RealmsCompat.pingResult(Ping.pingAllRegions(), owned)) }
                .onFailure { LOGGER.warn("Could not send ping result to Realms", it) }
        }
    }

    private fun <T> async(request: (RealmsClient) -> T, done: (T) -> Unit, failed: (Throwable) -> Unit = {}) {
        CompletableFuture.supplyAsync({ request(RealmsCompat.client()) }, RealmsCompat.ioPool)
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
