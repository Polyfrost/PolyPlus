@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.lobbysocial.socialSurface
import org.polyfrost.polyplus.client.legal.LegalDocument
import org.polyfrost.polyplus.client.legal.LegalDocuments
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent

internal val ConsentWidth = 440.dp

@Composable
internal fun rememberOnlineConsent(): MutableState<Boolean> = remember { mutableStateOf(PrivacyConsent.allowsOnlineServices()) }

@Composable
internal fun ConsentStep(reason: String, modifier: Modifier, onDismiss: () -> Unit, onAccepted: () -> Unit) {
    val theme = OneConfigTheme.current
    var document by remember { mutableStateOf<LegalDocument?>(null) }
    var agreed by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        LegalDocuments.load().onSuccess { document = it }.onFailure { failed = true }
    }
    Column(modifier.socialSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("Turn on online services")
        Text(reason, Modifier, theme.mutedText, theme.typography.body)
        Text("Declining keeps crash reporting and all online features off.", Modifier, theme.mutedText, theme.typography.small)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "Terms of Service",
                onClick = { ClientPlatform.openUri(document?.resolvedTermsUrl ?: LegalDocuments.TERMS_URL) },
                modifier = Modifier.weight(1f), size = ControlSize.Large,
            )
            TextButton(
                text = "Privacy Policy",
                onClick = { ClientPlatform.openUri(document?.resolvedPrivacyUrl ?: LegalDocuments.PRIVACY_URL) },
                modifier = Modifier.weight(1f), size = ControlSize.Large,
            )
        }
        ToggleRow(label = "I agree to both", checked = agreed, onCheckedChange = { agreed = it }, modifier = Modifier)
        val loaded = document
        when {
            loaded != null -> Unit
            failed -> Text("Couldn't load the current terms. Check your connection and try again.", Modifier, SocialDangerText, theme.typography.small)
            else -> Text("Loading the current terms...", Modifier, theme.mutedText, theme.typography.small)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = "Not now", onClick = onDismiss, modifier = Modifier.weight(1f), size = ControlSize.Large)
            if (loaded == null && failed) {
                TextButton(text = "Retry", onClick = { attempt++ }, modifier = Modifier.weight(1f), size = ControlSize.Large, style = ControlStyle.Primary)
            } else {
                TextButton(
                    text = if (loaded == null) "Loading..." else "Agree",
                    onClick = {
                        val accepted = document ?: return@TextButton
                        PrivacyEnforcement.acceptDocuments(accepted.version, accepted.resolvedPrivacyVersion)
                        onAccepted()
                    },
                    modifier = Modifier.weight(1f), size = ControlSize.Large,
                    style = ControlStyle.Primary,
                    enabled = agreed && loaded != null,
                )
            }
        }
    }
}
