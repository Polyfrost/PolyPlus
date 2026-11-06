@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialToggle
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
    LaunchedEffect(Unit) { LegalDocuments.load().onSuccess { document = it } }
    Column(modifier.socialSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle("Turn on online services")
        SocialText(reason, 14.sp, color = MutedText)
        SocialText("Declining keeps crash reporting and all online features off.", 12.sp, color = MutedText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialButton("Terms of Service", modifier = Modifier.weight(1f)) {
                ClientPlatform.openUri(document?.resolvedTermsUrl ?: LegalDocuments.TERMS_URL)
            }
            SocialButton("Privacy Policy", modifier = Modifier.weight(1f)) {
                ClientPlatform.openUri(document?.resolvedPrivacyUrl ?: LegalDocuments.PRIVACY_URL)
            }
        }
        SocialToggle("I agree to both", agreed) { agreed = !agreed }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialButton("Not now", modifier = Modifier.weight(1f), onClick = onDismiss)
            SocialButton("Agree", modifier = Modifier.weight(1f), filled = true, enabled = agreed) {
                PrivacyEnforcement.acceptDocuments(document?.version ?: 0, document?.resolvedPrivacyVersion ?: 0)
                onAccepted()
            }
        }
    }
}
