package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.AccountApi
import org.polyfrost.polyplus.client.network.http.responses.LinkedAccountInfo
import org.polyfrost.polyplus.client.privacy.OnlineServicesDisabledException
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.libs.ktor.client.plugins.ClientRequestException
import org.polyfrost.polyplus.libs.ktor.client.plugins.ServerResponseException
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.UUID
//? if <= 1.8.9 {
/*import org.lwjgl.opengl.Display
*///?}

object DiscordLink {
    private val LOGGER = LogManager.getLogger()

    private const val PROVIDER = "discord"
    private const val POLL_MS = 3_000L
    private const val WAIT_MS = 3 * 60_000L
    private const val TICK_MS = 250L

    enum class Unavailable { SignedOut }

    data class State(
        val link: LinkedAccountInfo? = null,
        val loaded: Boolean = false,
        val loading: Boolean = false,
        val waiting: Boolean = false,
        val unlinking: Boolean = false,
        val unavailable: Unavailable? = null,
        val joinServer: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val lock = Mutex()

    @Volatile private var waitJob: Job? = null

    @Volatile private var loadedFor: UUID? = null

    fun available(): Boolean = PrivacyConsent.allowsOnlineServices()

    fun refresh() {
        if (!available()) {
            cancelLink()
            _state.value = State()
            return
        }
        val owner = ClientPlatform.localPlayerUuid()
        if (owner != loadedFor) {
            cancelLink()
            _state.value = State()
            loadedFor = owner
        }
        PolyPlusClient.SCOPE.launch { lock.withLock { load() } }
    }

    fun startLink() {
        if (!available() || _state.value.waiting) return
        waitJob?.cancel()
        _state.update { it.copy(waiting = true) }
        val job = PolyPlusClient.SCOPE.launch(start = CoroutineStart.LAZY) {
            try {
                val url = AccountApi.startDiscordLink().getOrElse {
                    report("Couldn't start linking Discord", it)
                    return@launch
                }
                ClientPlatform.openUri(url)
                val before = _state.value.link?.let { it.providerUserId to it.linkedAt }
                val deadline = System.currentTimeMillis() + WAIT_MS
                var nextPoll = System.currentTimeMillis() + POLL_MS
                var focused = windowFocused()
                while (System.currentTimeMillis() < deadline) {
                    delay(TICK_MS)
                    val nowFocused = windowFocused()
                    val regained = nowFocused && !focused
                    focused = nowFocused
                    if (!regained && System.currentTimeMillis() < nextPoll) continue
                    nextPoll = System.currentTimeMillis() + POLL_MS
                    val link = AccountApi.links().getOrNull()?.discord() ?: continue
                    if ((link.providerUserId to link.linkedAt) == before) continue
                    _state.update { it.copy(link = link, loaded = true, unavailable = null) }
                    notify("Discord linked as ${link.label()}")
                    if (_state.value.joinServer) ClientPlatform.openUri(PolyPlusConstants.DISCORD_URL)
                    return@launch
                }
                notify("Didn't hear back from Discord. Try linking again.")
            } finally {
                if (waitJob === coroutineContext.job) {
                    waitJob = null
                    _state.update { it.copy(waiting = false) }
                }
            }
        }
        waitJob = job
        job.start()
    }

    fun setJoinServer(join: Boolean) {
        _state.update { it.copy(joinServer = join) }
    }

    fun cancelLink() {
        waitJob?.cancel()
        waitJob = null
        _state.update { it.copy(waiting = false) }
    }

    fun unlink() {
        if (!available() || _state.value.unlinking) return
        cancelLink()
        _state.update { it.copy(unlinking = true) }
        PolyPlusClient.SCOPE.launch {
            lock.withLock {
                AccountApi.unlink(PROVIDER)
                    .onSuccess { _state.update { it.copy(link = null, loaded = true) } }
                    .onFailure { report("Couldn't unlink Discord", it) }
                _state.update { it.copy(unlinking = false) }
            }
        }
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true) }
        AccountApi.links()
            .onSuccess { links -> _state.update { it.copy(link = links.discord(), loaded = true, loading = false, unavailable = null) } }
            .onFailure { error ->
                LOGGER.warn("Failed to load linked accounts", error)
                _state.update {
                    when {
                        error is OnlineServicesDisabledException -> State()
                        error.signedOut() -> it.copy(link = null, loaded = true, loading = false, unavailable = Unavailable.SignedOut)
                        else -> it.copy(loaded = true, loading = false)
                    }
                }
            }
    }

    private fun report(fallback: String, error: Throwable) {
        if (error is CancellationException) return
        LOGGER.warn(fallback, error)
        if (error.signedOut()) _state.update { it.copy(unavailable = Unavailable.SignedOut) }
        val message = when {
            error is OnlineServicesDisabledException -> "Online features are off. Turn them on under Privacy in the PolyPlus settings."
            error.signedOut() -> "$fallback. Couldn't sign in to PolyPlus."
            error is ClientRequestException && error.response.status == HttpStatusCode.TooManyRequests -> "$fallback. Try again in a moment."
            error is ServerResponseException -> "$fallback. The PolyPlus servers are having trouble."
            else -> "$fallback. Check your connection."
        }
        runCatching { Notifications.error("PolyPlus", message) }
    }

    private fun notify(message: String) {
        runCatching { Notifications.info("PolyPlus", message) }
    }

    private fun Throwable.signedOut(): Boolean =
        this is ClientRequestException && (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden)

    private fun List<LinkedAccountInfo>.discord(): LinkedAccountInfo? = firstOrNull { it.provider == PROVIDER }

    fun LinkedAccountInfo.label(): String = username?.takeIf { it.isNotBlank() } ?: providerUserId

    private fun windowFocused(): Boolean = runCatching {
        //? if > 1.8.9 {
        Minecraft.getInstance().isWindowActive
        //?} else {
        /*Display.isActive()
        *///?}
    }.getOrDefault(true)
}
