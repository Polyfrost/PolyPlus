package org.polyfrost.polyplus.client.launcher

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

object SessionRefresh {
    private val LOGGER = LogManager.getLogger("PolyPlus/Accounts")

    private const val LOGIN_FAILED_KEY = "disconnect.loginFailedInfo"
    private const val INVALID_SESSION_KEY = "disconnect.loginFailedInfo.invalidSession"
    private const val REFRESH_TIMEOUT_MS = 30_000L

    private val refreshLock = Any()

    @Volatile
    private var inFlightRefresh: Deferred<Result<Unit>>? = null

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
        if (reconnecting) reconnecting = false else refreshedForAttempt = false
    }

    @JvmStatic
    fun beforeAuthenticate() {
        runBlocking {
            awaitPendingRefresh()
            if (!PolyPlusConfig.autoRefreshSession || refreshedForAttempt) return@runBlocking
            val account = refreshableActiveAccount() ?: return@runBlocking
            if (!LauncherAccountStore.isExpired(account.expires)) return@runBlocking
            LOGGER.info("Session for {} has expired; refreshing it before joining the server", account.username)
            refreshedForAttempt = true
            refreshActiveSession()
        }
    }

    @JvmStatic
    fun refreshAfterRejection(): Boolean {
        if (!PolyPlusConfig.autoRefreshSession || refreshedForAttempt) return false
        if (refreshableActiveAccount() == null) return false
        LOGGER.info("The session server rejected the account; refreshing the session and retrying the join")
        refreshedForAttempt = true
        return runBlocking { refreshActiveSession() }.isSuccess
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
        if (lastAttempt == null || refreshableActiveAccount() == null) return null
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
        LOGGER.info("Session for {} has expired; refreshing it in the background", account.username)
        pendingRefresh()
    }

    internal suspend fun refreshActiveSession(): Result<Unit> = pendingRefresh().await()

    private fun pendingRefresh(): Deferred<Result<Unit>> = synchronized(refreshLock) {
        inFlightRefresh?.takeIf { it.isActive }
            ?: PolyPlusClient.SCOPE.async(Dispatchers.IO) { performRefresh() }.also { inFlightRefresh = it }
    }

    private suspend fun awaitPendingRefresh() {
        synchronized(refreshLock) { inFlightRefresh?.takeIf { it.isActive } }?.await()
    }

    private suspend fun performRefresh(): Result<Unit> {
        val account = refreshableActiveAccount()
            ?: return Result.failure(IllegalStateException("The active account cannot be refreshed"))
        val id = LauncherAccountStore.parseUuid(account.id)
            ?: return Result.failure(IllegalStateException("The active account has a malformed ID"))
        return runCatching {
            withTimeout(REFRESH_TIMEOUT_MS) { OneLauncherAccounts.refresh(id) }
            LOGGER.info("Refreshed the session for {}", account.username)
        }.onFailure { LOGGER.warn("Could not refresh the session for {}", account.username, it) }
    }

    private fun refreshableActiveAccount(): LauncherAccountStore.StoredAccount? {
        SessionAccounts.capture()
        val id = runCatching { Minecraft.getInstance().user.profileId }.getOrNull() ?: return null
        return LauncherAccountStore.load().users.values
            .firstOrNull { LauncherAccountStore.parseUuid(it.id) == id }
            ?.takeIf(LauncherAccountStore::isRefreshable)
    }
}
