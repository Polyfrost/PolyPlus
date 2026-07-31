package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.postBodyAuthorized
import org.polyfrost.polyplus.client.network.http.getBodyAuthorized
import org.polyfrost.polyplus.client.network.http.responses.CreateCheckoutRequest
import org.polyfrost.polyplus.client.network.http.responses.CreateCheckoutResponse
import org.polyfrost.polyplus.client.network.http.responses.TransactionInfo
import org.polyfrost.polyplus.client.network.http.responses.TransactionsResponse
import org.polyfrost.polyplus.client.utils.ClientPlatform

object BillingService {
    private val LOGGER = LogManager.getLogger()

    class AlreadyOwnedException(message: String) : Exception(message)

    suspend fun createCheckout(priceIds: List<String>): Result<CreateCheckoutResponse> {
        val prices = priceIds.filter { it.isNotBlank() }
        if (prices.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nothing to check out"))
        }
        val player = ClientPlatform.localPlayerUuid().toString()
        val result = PolyPlusClient.HTTP.postBodyAuthorized<CreateCheckoutResponse>(
            "${PolyPlusConfig.apiUrl}/stripe/create",
        ) {
            contentType(ContentType.Application.Json)
            setBody(CreateCheckoutRequest(player = player, prices = prices))
        }

        val error = result.exceptionOrNull() ?: return result
        alreadyOwned(error)?.let { message ->
            LOGGER.info("Checkout refused by the backend: {}", message)
            return Result.failure(AlreadyOwnedException(message))
        }
        LOGGER.error("Failed to create Stripe checkout", error)
        org.polyfrost.polyplus.client.PolyPlusSentry.capture(error)
        return result
    }

    private fun alreadyOwned(error: Throwable): String? {
        val conflict = error as? ClientRequestException ?: return null
        if (conflict.response.status != HttpStatusCode.Conflict) return null
        return conflict.message.substringAfter("Text: \"", "").substringBeforeLast('"')
            .takeIf { it.isNotBlank() }
            ?: "You already own this item."
    }

    suspend fun checkoutAndOpen(priceIds: List<String>): Result<String> =
        createCheckout(priceIds).map { response ->
            ClientPlatform.openUri(response.url)
            response.url
        }

    suspend fun fetchTransactions(): Result<List<TransactionInfo>> =
        PolyPlusClient.HTTP
            .getBodyAuthorized<TransactionsResponse>("${PolyPlusConfig.apiUrl}/transactions/player")
            .map { it.transactions.sortedByDescending { tx -> tx.id } }
            .onFailure { LOGGER.error("Failed to fetch transactions", it); org.polyfrost.polyplus.client.PolyPlusSentry.capture(it) }
}
