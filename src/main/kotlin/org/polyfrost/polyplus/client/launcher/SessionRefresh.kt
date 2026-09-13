package org.polyfrost.polyplus.client.launcher

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.TransferState
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.util.UUID

object SessionRefresh {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    private const val LOGIN_FAILED_KEY = "disconnect.loginFailedInfo"
    private const val INVALID_SESSION_KEY = "disconnect.loginFailedInfo.invalidSession"
    private const val REFRESH_TIMEOUT_MS = 30_000L

    private const val PREJOIN_WAIT_MS = 1_000L

    private const val REJOIN_REFRESH_WAIT_MS = 2_500L

    private const val REFRESH_COOLDOWN_MS = 5 * 60 * 1000L

    private const val REJECTION_COOLDOWN_MS = 30 * 1000L

    private val refreshLock = Any()

    private data class RefreshTarget(
        val id: UUID,
        val account: LauncherAccountStore.StoredAccount,
    )

    private val pendingRefreshes = mutableMapOf<UUID, Deferred<Result<Unit>>>()

    private val lastRefreshStartedMs = mutableMapOf<UUID, Long>()

    @Volatile
    private var refreshProbe: CompletableDeferred<Unit>? = null

    private class ConnectionAttempt(
        val parent: Screen,
        val address: ServerAddress,
        val server: ServerData,
        val quickPlay: Boolean,
        val transferState: TransferState?,
    )

    @Volatile
    private var lastAttempt: ConnectionAttempt? = null

    /** Whether PolyPlus itself is attempting a reconnection */
    @Volatile
    private var reconnecting = false

    @Volatile
    private var refreshedForAttempt = false

    @Volatile
    private var invalidSessionPending = false

    @JvmStatic
    fun onConnectStarted(
        parent: Screen?,
        address: ServerAddress?,
        server: ServerData?,
        quickPlay: Boolean,
        transferState: TransferState?,
    ) {
        invalidSessionPending = false
        if (parent != null && address != null && server != null) {
            lastAttempt = ConnectionAttempt(parent, address, server, quickPlay, transferState)
        }
        val wasReconnecting = reconnecting
        if (wasReconnecting) reconnecting = false else refreshedForAttempt = false
        if (!wasReconnecting) startRefreshIfExpired()
    }

    private fun startRefreshIfExpired() {
        if (!PolyPlusConfig.autoRefreshSession) return
        val probe = CompletableDeferred<Unit>()
        refreshProbe = probe
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val target = refreshableActiveTarget() ?: return@launch
            if (!LauncherAccountStore.isExpired(target.account.expires)) return@launch
            if (automaticRefresh(target) == null) return@launch
            LOGGER.info("Session for {} has expired; refreshing it before joining the server", target.account.username)
        }.invokeOnCompletion { probe.complete(Unit) }
    }

    @JvmStatic
    fun beforeAuthenticate() {
        if (!hasPendingRefresh()) {
            refreshedForAttempt = false
            startRefreshIfExpired()
        }
        failOpenIfInterrupted(Unit) {
            runBlocking {
                val finished = withTimeoutOrNull(PREJOIN_WAIT_MS) { awaitPendingRefresh() }
                if (finished == null) {
                    LOGGER.warn("Refreshing the session took too long; joining with the token we already have")
                }
            }
        }
    }

    @JvmStatic
    fun refreshAfterRejection(): Boolean {
        if (!PolyPlusConfig.autoRefreshSession || refreshedForAttempt) return false
        val target = refreshableActiveTarget() ?: return false
        val refresh = automaticRefresh(target, REJECTION_COOLDOWN_MS, reuseCompleted = true) ?: return false
        LOGGER.info("The session server rejected the account; refreshing the session and retrying the join")
        refreshedForAttempt = true
        return failOpenIfInterrupted(false) {
            runBlocking {
                val result = withTimeoutOrNull(REJOIN_REFRESH_WAIT_MS) {
                    refresh.await().also { consumeRefreshResult(target.id, refresh) }
                }
                if (result == null) {
                    LOGGER.warn("The refresh outran the server's login timeout; leaving the retry to the prompt")
                }
                result?.isSuccess == true
            }
        }
    }

    private fun hasPendingRefresh(): Boolean = synchronized(refreshLock) {
        val id = activeProfileId() ?: return@synchronized false
        pendingRefreshes[id]?.isActive == true
    }

    private fun <T> failOpenIfInterrupted(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            LOGGER.debug("Session refresh was interrupted; joining with the token we already have", interrupted)
            fallback
        }

    @JvmStatic
    fun onInvalidSession() {
        invalidSessionPending = true
    }

    @JvmStatic
    fun isInvalidSession(reason: Component): Boolean {
        val contents = reason.contents as? TranslatableContents ?: return false
        if (contents.key != LOGIN_FAILED_KEY) return false
        return contents.args.any { arg ->
            arg is Component && (arg.contents as? TranslatableContents)?.key == INVALID_SESSION_KEY
        }
    }

    @JvmStatic
    fun createPrompt(): SessionRefreshPrompt? {
        if (!invalidSessionPending) return null
        invalidSessionPending = false
        if (lastAttempt == null || refreshableActiveTarget() == null) return null
        return SessionRefreshPrompt()
    }

    internal fun reconnect(): Boolean {
        val attempt = lastAttempt ?: return false
        reconnecting = true
        return runCatching {
            ConnectScreen.startConnecting(
                attempt.parent,
                Minecraft.getInstance(),
                attempt.address,
                attempt.server,
                attempt.quickPlay,
                attempt.transferState,
            )
        }.onFailure {
            reconnecting = false
            LOGGER.error("Could not reconnect to {} after refreshing the session", attempt.server.ip, it)
        }.isSuccess
    }

    internal fun refreshAfterSwitch(account: LauncherAccountStore.StoredAccount) {
        if (!PolyPlusConfig.autoRefreshSession) return
        if (!LauncherAccountStore.isRefreshable(account) || !LauncherAccountStore.isExpired(account.expires)) return
        val target = refreshTarget(account) ?: return
        if (automaticRefresh(target) == null) return
        LOGGER.info("Session for {} has expired; refreshing it in the background", account.username)
    }

    internal suspend fun refreshActiveSession(): Result<Unit> {
        val target = refreshableActiveTarget()
            ?: return Result.failure(IllegalStateException("The active account cannot be refreshed"))
        val refresh = promptedRefresh(target)
        return refresh.await().also { consumeRefreshResult(target.id, refresh) }
    }

    private fun automaticRefresh(
        target: RefreshTarget,
        cooldownMs: Long = REFRESH_COOLDOWN_MS,
        reuseCompleted: Boolean = false,
    ): Deferred<Result<Unit>>? = synchronized(refreshLock) {
        pendingRefreshes[target.id]?.takeIf { it.isActive || reuseCompleted }?.let { return@synchronized it }
        val last = lastRefreshStartedMs[target.id]
        if (last != null && System.currentTimeMillis() - last < cooldownMs) return@synchronized null
        newRefresh(target)
    }

    private fun promptedRefresh(target: RefreshTarget): Deferred<Result<Unit>> = synchronized(refreshLock) {
        pendingRefreshes[target.id]?.takeIf { it.isActive } ?: newRefresh(target)
    }

    private suspend fun awaitPendingRefresh() {
        refreshProbe?.await()
        val id = activeProfileId() ?: return
        val refresh = synchronized(refreshLock) {
            pendingRefreshes[id]
        } ?: return
        refresh.await()
        consumeRefreshResult(id, refresh)
    }

    private fun newRefresh(target: RefreshTarget): Deferred<Result<Unit>> {
        val now = System.currentTimeMillis()
        for (id in lastRefreshStartedMs.keys.toList()) {
            if (id == target.id || now - lastRefreshStartedMs[id]!! < REFRESH_COOLDOWN_MS) continue
            if (pendingRefreshes[id]?.isActive == true) continue
            pendingRefreshes.remove(id)
            lastRefreshStartedMs.remove(id)
        }
        lastRefreshStartedMs[target.id] = now
        return PolyPlusClient.SCOPE.async(Dispatchers.IO) { performRefresh(target) }.also {
            pendingRefreshes[target.id] = it
        }
    }

    private fun consumeRefreshResult(id: UUID, refresh: Deferred<Result<Unit>>) = synchronized(refreshLock) {
        if (pendingRefreshes[id] === refresh) pendingRefreshes.remove(id)
    }

    private suspend fun performRefresh(target: RefreshTarget): Result<Unit> {
        return runCatching {
            withTimeout(REFRESH_TIMEOUT_MS) { OneLauncherAccounts.refresh(target.id, refreshClient = false) }
            LOGGER.info("Refreshed the session for {}", target.account.username)
        }.onFailure { LOGGER.warn("Could not refresh the session for {}", target.account.username, it) }
    }

    private fun refreshableActiveTarget(): RefreshTarget? {
        SessionAccounts.capture()
        val id = activeProfileId() ?: return null
        val account = LauncherAccountStore.load().users.values
            .firstOrNull { LauncherAccountStore.parseUuid(it.id) == id }
            ?.takeIf(LauncherAccountStore::isRefreshable)
            ?: return null
        return RefreshTarget(id, account)
    }

    private fun refreshTarget(account: LauncherAccountStore.StoredAccount): RefreshTarget? =
        LauncherAccountStore.parseUuid(account.id)?.let { RefreshTarget(it, account) }

    private fun activeProfileId(): UUID? =
        runCatching { Minecraft.getInstance().user.profileId }.getOrNull()
}
