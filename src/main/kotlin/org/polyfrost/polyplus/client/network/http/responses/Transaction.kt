package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = TransactionProvider.Serializer::class)
enum class TransactionProvider(val serializedName: String, val displayName: String) {
    Stripe("stripe", "Stripe"),
    Paynow("paynow", "PayNow"),
    Ingame("ingame", "In-game"),
    AdminGrant("admin_grant", "Admin grant"),
    Unknown("unknown", "Unknown");

    internal object Serializer : KSerializer<TransactionProvider> by ApiEnumSerializer(
        "TransactionProvider",
        TransactionProvider.entries,
        Unknown,
        TransactionProvider::serializedName,
    )
}

@Serializable(with = TransactionStatus.Serializer::class)
enum class TransactionStatus(val serializedName: String, val displayName: String) {
    Pending("pending", "Pending"),
    Completed("completed", "Completed"),
    Failed("failed", "Failed"),
    Refunded("refunded", "Refunded"),
    PartiallyRefunded("partially_refunded", "Partially refunded"),
    Chargeback("chargeback", "Chargeback"),
    Unknown("unknown", "Unknown");

    internal object Serializer : KSerializer<TransactionStatus> by ApiEnumSerializer(
        "TransactionStatus",
        TransactionStatus.entries,
        Unknown,
        TransactionStatus::serializedName,
    )
}

@Serializable
data class TransactionInfo(
    val id: Int,
    val provider: TransactionProvider,
    val status: TransactionStatus,
    @SerialName("amount_minor") val amountMinor: Long? = null,
    val currency: String? = null,
    val buyer: String? = null,
    @SerialName("discount_rate") val discountRate: Int? = null,
) {
    val amount: Float? get() = amountMinor?.let { it / 100f }
}

@Serializable
data class TransactionsResponse(
    val transactions: List<TransactionInfo> = emptyList(),
)
