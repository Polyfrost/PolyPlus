@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.height
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
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
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialText
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
        SocialText(reason, 14.sp, color = MutedText)
        SocialText("Declining keeps crash reporting and all online features off.", 12.sp, color = MutedText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("Terms of Service", Modifier.weight(1f).height(38.dp)) {
                ClientPlatform.openUri(document?.resolvedTermsUrl ?: LegalDocuments.TERMS_URL)
            }
            TextButton("Privacy Policy", Modifier.weight(1f).height(38.dp)) {
                ClientPlatform.openUri(document?.resolvedPrivacyUrl ?: LegalDocuments.PRIVACY_URL)
            }
        }
        ToggleRow("I agree to both", agreed, Modifier) { agreed = !agreed }
        val loaded = document
        when {
            loaded != null -> Unit
            failed -> SocialText("Couldn't load the current terms. Check your connection and try again.", 12.sp, color = SocialDangerText)
            else -> SocialText("Loading the current terms...", 12.sp, color = MutedText)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("Not now", Modifier.weight(1f).height(38.dp), onDismiss)
            if (loaded == null && failed) {
                TextButton("Retry", Modifier.weight(1f).height(38.dp), ButtonStyle.Primary) { attempt++ }
            } else {
                TextButton(if (loaded == null) "Loading..." else "Agree", null, Modifier.weight(1f).height(38.dp), ButtonStyle.Primary, agreed && loaded != null) {
                    val accepted = document ?: return@TextButton
                    PrivacyEnforcement.acceptDocuments(accepted.version, accepted.resolvedPrivacyVersion)
                    onAccepted()
                }
            }
        }
    }
}
