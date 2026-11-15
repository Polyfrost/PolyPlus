@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ProgressBar
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.CosmeticLoadProgress
import org.polyfrost.oneconfig.api.ui.v1.components.Text

@Composable
internal fun CosmeticsLoadingScreen(
    state: CosmeticLoadProgress.Snapshot,
    onRetry: () -> Unit,
) {
    val failed = state.failure != null
    val outdatedRuntime = state.failure == CosmeticLoadProgress.OUTDATED_RUNTIME
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (failed) "Couldn't load cosmetics" else "Loading cosmetics",
            color = OneConfigTheme.current.textColor,
            style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
        )
        Text(
            when {
                outdatedRuntime -> "PolyPlus needs a newer Fabric Language Kotlin. Update it and restart the game."
                failed -> "PolyPlus couldn't reach its servers (${state.failure}). Check your connection and try again."
                else -> state.label
            },
            modifier = Modifier.width(360.dp),
            color = OneConfigTheme.current.textColorSecondary,
            style = OneConfigTheme.current.typography.small,
            textAlign = TextAlign.Center,
        )
        if (failed) {
            if (!outdatedRuntime) {
                Spacer(Modifier.height(2.dp))
                TextButton("Retry", "refresh", onRetry, style = ControlStyle.Primary, sound = null)
            }
        } else {
            ProgressBar(state.fraction, Modifier.width(360.dp), ControlSize.Large)
        }
    }
}
