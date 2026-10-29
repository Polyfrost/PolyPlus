package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polyfrost.polyplus.client.launcher.MicrosoftAuth
import org.polyfrost.polyplus.client.launcher.MicrosoftAuthException
import org.polyfrost.polyplus.client.launcher.OneLauncherAccounts
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.util.UUID

internal class AccountActions(private val scope: CoroutineScope) {
    var accounts by mutableStateOf<List<OneLauncherAccounts.Account>?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var errorSteps by mutableStateOf<List<String>?>(null)
        private set
    var loginSession by mutableStateOf<MicrosoftAuth.MicrosoftLoginSession?>(null)
        private set
    var refreshingId by mutableStateOf<UUID?>(null)
        private set
    var offlineEntry by mutableStateOf(false)
        private set
    private var loginJob: Job? = null

    val idle: Boolean get() = busy == null

    suspend fun reload() {
        accounts = withContext(Dispatchers.IO) { OneLauncherAccounts.list() }
    }

    fun clearError() {
        error = null
        errorSteps = null
    }

    fun openOfflineEntry() {
        clearError()
        offlineEntry = true
    }

    fun cancelOfflineEntry() {
        clearError()
        offlineEntry = false
    }

    private fun fail(throwable: Throwable, fallback: String) {
        error = throwable.message ?: fallback
        errorSteps = (throwable as? MicrosoftAuthException)?.stepsToFix?.takeIf { it.isNotEmpty() }
    }

    private fun runBusy(status: String, block: suspend () -> Unit) {
        if (busy != null) return
        scope.launch {
            busy = status
            clearError()
            try {
                block()
                reload()
            } finally {
                busy = null
            }
        }
    }

    fun switchTo(account: OneLauncherAccounts.Account) {
        if (account.active) return
        runBusy("Switching…") {
            val ok = withContext(Dispatchers.IO) { OneLauncherAccounts.switchTo(account.id) }
            if (!ok) error = "Couldn't switch to that account"
        }
    }

    fun refresh(account: OneLauncherAccounts.Account) = runBusy("Refreshing session…") {
        refreshingId = account.id
        try {
            runSuspendCatching { withContext(Dispatchers.IO) { OneLauncherAccounts.refresh(account.id) } }
                .onFailure { fail(it, "Couldn't refresh that account") }
        } finally {
            refreshingId = null
        }
    }

    fun addOffline(username: String) = runBusy("Adding account…") {
        runSuspendCatching { withContext(Dispatchers.IO) { OneLauncherAccounts.addOffline(username) } }
            .onSuccess { offlineEntry = false }
            .onFailure { error = it.message ?: "Couldn't add that account" }
    }

    fun remove(account: OneLauncherAccounts.Account) = runBusy("Removing account…") {
        val ok = withContext(Dispatchers.IO) { OneLauncherAccounts.remove(account.id) }
        if (!ok) error = "Couldn't remove that account"
    }

    fun addMicrosoft() {
        if (busy != null) return
        loginJob = scope.launch {
            clearError()
            busy = "Starting Microsoft sign-in…"
            var started: MicrosoftAuth.MicrosoftLoginSession? = null
            try {
                val session = runSuspendCatching {
                    withContext(Dispatchers.IO) { OneLauncherAccounts.beginLogin().also { started = it } }
                }
                    .onFailure { fail(it, "Couldn't start sign-in") }
                    .getOrNull()
                if (session == null) {
                    busy = null
                    loginJob = null
                    return@launch
                }
                loginSession = session
                ClientPlatform.openUri(session.browserAuthUrl)
                busy = "Waiting for you to finish signing in…"
                runSuspendCatching { withContext(Dispatchers.IO) { OneLauncherAccounts.finishLogin(session) } }
                    .onFailure { fail(it, "Microsoft sign-in failed") }
            } finally {
                started?.let { runCatching { OneLauncherAccounts.cancelLogin(it) } }
            }
            loginSession = null
            loginJob = null
            reload()
            busy = null
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginSession?.let { runCatching { OneLauncherAccounts.cancelLogin(it) } }
        loginJob = null
        loginSession = null
        busy = null
        clearError()
    }
}

@Composable
internal fun rememberAccountActions(): AccountActions {
    val scope = rememberCoroutineScope()
    return remember(scope) { AccountActions(scope) }
}
