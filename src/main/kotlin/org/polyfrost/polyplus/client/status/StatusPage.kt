package org.polyfrost.polyplus.client.status

import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.privacy.PrivacyConsent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StatusFeed(
    val data: StatusFeedData? = null,
    val included: List<StatusFeedIncluded> = emptyList(),
)

@Serializable
internal data class StatusFeedData(
    val attributes: StatusFeedAttributes? = null,
)

@Serializable
internal data class StatusFeedAttributes(
    @SerialName("aggregate_state") val aggregateState: String? = null,
    val announcement: String? = null,
)

@Serializable
internal data class StatusFeedIncluded(
    val type: String? = null,
    val attributes: StatusFeedResource? = null,
)

@Serializable
internal data class StatusFeedResource(
    @SerialName("public_name") val publicName: String? = null,
    val status: String? = null,
)

data class ServiceStatus(
    val state: String,
    val announcement: String?,
    val affected: List<String>,
) {
    val operational: Boolean get() = state == StatusPage.OPERATIONAL
    val maintenance: Boolean get() = state == StatusPage.MAINTENANCE

    fun affects(service: String): Boolean = !operational && affected.any { it.equals(service, ignoreCase = true) }

    fun summary(): String {
        val names = affected.joinToString(", ")
        return when {
            maintenance && names.isNotEmpty() -> "OneClient maintenance on $names"
            maintenance -> "OneClient services are under maintenance"
            names.isNotEmpty() -> "OneClient services with problems: $names"
            else -> "Some OneClient services have problems"
        }
    }
}

object StatusPage {
    const val PAGE_URL = "https://status.polyfrost.org"
    const val OPERATIONAL = "operational"
    const val MAINTENANCE = "maintenance"

    private const val FEED_URL = "https://status.polyfrost.org/index.json"
    private const val POLL_INTERVAL_MS = 5 * 60 * 1000L
    private const val RESOURCE_TYPE = "status_page_resource"
    private const val BACKEND_SERVICE = "Poly+"
    private const val TITLE = "PolyPlus"

    private val LOGGER = LogManager.getLogger("PolyPlus/Status")

    private val _status = MutableStateFlow<ServiceStatus?>(null)
    val status: StateFlow<ServiceStatus?> = _status.asStateFlow()

    private var poller: Job? = null
    private var notifiedState: String = OPERATIONAL
    private var notifiedAnnouncement: String? = null

    fun start() {
        if (poller?.isActive == true) return
        poller = PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            while (isActive) {
                if (PrivacyConsent.allowsOnlineServices()) poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun disconnectHint(): String =
        if (_status.value?.affects(BACKEND_SERVICE) == true) " Updates at status.polyfrost.org." else ""

    private suspend fun poll() {
        runSuspendCatching { fetch() }
            .onSuccess { update(it) }
            .onFailure { LOGGER.debug("Could not fetch the status page", it) }
    }

    private suspend fun fetch(): ServiceStatus {
        val response = PolyPlusClient.HTTP.get(FEED_URL)
        check(response.status.isSuccess()) { "Status page returned ${response.status}" }
        val feed = PolyPlusClient.JSON.decodeFromString(StatusFeed.serializer(), response.bodyAsText())
        val attributes = feed.data?.attributes
        val affected = feed.included
            .filter { it.type == RESOURCE_TYPE }
            .mapNotNull { it.attributes }
            .filter { it.status != null && it.status != OPERATIONAL }
            .mapNotNull { it.publicName?.takeIf(String::isNotBlank) }
        return ServiceStatus(
            state = attributes?.aggregateState?.takeIf { it.isNotBlank() } ?: OPERATIONAL,
            announcement = attributes?.announcement?.takeIf { it.isNotBlank() },
            affected = affected,
        )
    }

    private fun update(current: ServiceStatus) {
        _status.value = current
        val stateChanged = current.state != notifiedState
        val announcement = current.announcement?.takeIf { it != notifiedAnnouncement }
        notifiedAnnouncement = current.announcement
        if (stateChanged) notifiedState = current.state

        when {
            stateChanged && current.operational -> notify { Notifications.success(TITLE, "All OneClient services are working again.") }
            stateChanged -> {
                val message = listOfNotNull("${current.summary()}.", announcement).joinToString(" ")
                notify {
                    if (current.maintenance) Notifications.info(TITLE, message) else Notifications.error(TITLE, message)
                }
            }
            announcement != null -> notify { Notifications.info(TITLE, announcement) }
        }
    }

    private inline fun notify(block: () -> Unit) {
        runCatching(block).onFailure { LOGGER.debug("Could not show a status notification", it) }
    }
}
