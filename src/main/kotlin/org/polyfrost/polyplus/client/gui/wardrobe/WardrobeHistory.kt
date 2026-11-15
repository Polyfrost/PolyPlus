@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.border
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialWarnText
import org.polyfrost.polyplus.client.gui.SocialSuccessText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.PaddingValues
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.LoadState
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.BillingService
import org.polyfrost.polyplus.client.network.http.responses.TransactionInfo
import org.polyfrost.polyplus.client.network.http.responses.TransactionStatus
import org.polyfrost.oneconfig.api.ui.v1.components.Text

@Composable
internal fun HistoryScreen(ownedIds: Set<Int>, reloadKey: Int, framed: Boolean = true) {
    var transactions by remember { mutableStateOf<List<TransactionInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // keyed on ownership rather than the catalog poll so transactions only refetch when a purchase lands
    LaunchedEffect(ownedIds, reloadKey) {
        loading = true
        loadError = null
        BillingService.fetchTransactions()
            .onSuccess { transactions = it }
            .onFailure { loadError = wardrobeFailure(it, "load your purchase history") }
        loading = false
    }

    Box(
        modifier = Modifier.fillMaxSize().then(
            if (framed) {
                Modifier.themedSurface(SurfaceKind.Card, OneConfigTheme.shape(12.dp), glass = false)
                    .padding(18.dp)
            } else {
                Modifier
            },
        ),
    ) {
        when {
            loading && transactions.isEmpty() -> EmptyState("Loading transactions...", Modifier.fillMaxSize())
            loadError != null && transactions.isEmpty() -> EmptyState("Couldn't load your purchase history", Modifier.fillMaxSize(), state = LoadState.Error(loadError!!))
            transactions.isEmpty() -> EmptyState("No transactions yet.", Modifier.fillMaxSize())
            else -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                loadError?.let { Text(it, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small) }
                val list = rememberLazyListState()
                ScrollContainer(list, scrollbar = false) {
                    LazyColumn(Modifier, list, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(transactions, key = { it.id }) { tx -> TransactionRow(tx) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionInfo) {
    val shape = OneConfigTheme.shape(8.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .border(1.dp, OneConfigTheme.current.borderColor, shape)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Order #${tx.id}", color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium))
            Text(tx.provider.displayName, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
        }
        tx.amount?.let { Text(money(it, tx.currency), color = OneConfigTheme.current.textColor, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium)) }
        Badge(
            tx.status.displayName,
            tone = statusTone(tx.status),
            colors = BadgeColors(container = Color.Transparent, content = statusInk(tx.status)),
            labelStyle = TextStyle(fontSize = 12.sp),
            contentPadding = PaddingValues(0.dp),
        )
    }
}

@Composable
private fun statusInk(status: TransactionStatus): Color = when (status) {
    TransactionStatus.Completed -> SocialSuccessText
    TransactionStatus.Pending -> SocialWarnText
    TransactionStatus.Failed -> SocialDangerText
    TransactionStatus.Refunded, TransactionStatus.PartiallyRefunded, TransactionStatus.Chargeback -> OneConfigTheme.current.mutedText
    TransactionStatus.Unknown -> OneConfigTheme.current.mutedText
}

private fun statusTone(status: TransactionStatus): BadgeTone = when (status) {
    TransactionStatus.Completed -> BadgeTone.Success
    TransactionStatus.Pending -> BadgeTone.Warning
    TransactionStatus.Failed -> BadgeTone.Danger
    TransactionStatus.Refunded, TransactionStatus.PartiallyRefunded, TransactionStatus.Chargeback -> BadgeTone.Neutral
    TransactionStatus.Unknown -> BadgeTone.Neutral
}
