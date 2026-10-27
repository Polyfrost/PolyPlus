@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.BillingService
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.network.http.responses.TransactionInfo
import org.polyfrost.polyplus.client.network.http.responses.TransactionStatus

@Composable
internal fun HistoryScreen(ownedIds: Set<Int>, reloadKey: Int) {
    var transactions by remember { mutableStateOf<List<TransactionInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // keyed on ownership rather than the catalog poll so transactions only refetch when a purchase lands
    LaunchedEffect(ownedIds, reloadKey) {
        loading = true
        loadError = null
        BillingService.fetchTransactions()
            .onSuccess { transactions = it }
            .onFailure { loadError = "Couldn't load transactions: ${it.message}" }
        loading = false
    }

    Box(
        modifier = Modifier.fillMaxSize().clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(12.dp))
            .padding(18.dp),
    ) {
        when {
            loading && transactions.isEmpty() -> CenteredNote("Loading transactions...")
            loadError != null && transactions.isEmpty() -> CenteredNote(loadError!!)
            transactions.isEmpty() -> CenteredNote("No transactions yet.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                loadError?.let { SocialText(it, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(transactions, key = { it.id }) { tx -> TransactionRow(tx) }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionInfo) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(ppShape(8.dp))
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(8.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SocialText("Order #${tx.id}", color = OneConfigTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            SocialText(tx.provider.displayName, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
        }
        tx.amount?.let { SocialText(money(it, tx.currency), color = OneConfigTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        SocialText(
            tx.status.displayName,
            color = statusColor(tx.status),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusColor(status: TransactionStatus): Color = when (status) {
    TransactionStatus.Completed -> Color(0xFF239A60)
    TransactionStatus.Pending -> Color(0xFFE0A030)
    TransactionStatus.Failed -> Color(0xFFFF4444)
    TransactionStatus.Refunded, TransactionStatus.PartiallyRefunded, TransactionStatus.Chargeback -> Color(0xFF8A9296)
    TransactionStatus.Unknown -> Color(0xFF8A9296)
}
