package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CapeResolver
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialToggle
import org.polyfrost.polyplus.client.gui.ppShape

@Composable
internal fun ForceCapesControl() {
    var forced by remember { mutableStateOf(PolyPlusCosmeticsConfig.forcePolyPlusCapes) }
    val modName = CapeResolver.localOverrideModName
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SocialToggle("Force PolyPlus capes", checked = forced) {
            forced = !forced
            PolyPlusCosmeticsConfig.forcePolyPlusCapes = forced
            PolyPlusCosmeticsConfig.save()
        }
        val note = when {
            modName == null -> "When another mod sets its own cape, that cape is shown unless this is on."
            forced -> "$modName sets its own cape, but the capes picked here are shown instead."
            else -> "Your cape is set by $modName. Turn this on to show the capes picked here."
        }
        SocialText(note, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
    }
}

@Composable
internal fun CapeSectionHeader(
    title: String,
    note: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SocialText(title, color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            SocialText(note, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
        }
        if (action != null) {
            Box(
                modifier = Modifier.height(28.dp)
                    .clip(ppShape(8.dp))
                    .background(LocalTheme.current.chipBackground)
                    .border(1.dp, LocalTheme.current.borderColor, ppShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                SocialText(action, color = LocalTheme.current.textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun CapeSectionNote(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(57.dp)
            .clip(ppShape(16.dp))
            .border(1.dp, LocalTheme.current.borderColor, ppShape(16.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SocialText(text, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
    }
}
