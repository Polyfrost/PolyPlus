@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CapeResolver
import org.polyfrost.polyplus.client.gui.SocialOffStateBackground
import org.polyfrost.polyplus.client.gui.SocialSwitch
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.ppShape

@Composable
internal fun CapeGroupHeading(title: String, note: String, forceToggle: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SocialText(title, color = OneConfigTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        InfoTip(note)
        Spacer(Modifier.weight(1f))
        if (forceToggle) ForceCapesToggle()
    }
}

@Composable
internal fun ForceCapesToggle() {
    var forced by remember { mutableStateOf(PolyPlusCosmeticsConfig.forcePolyPlusCapes) }
    val modName = CapeResolver.localOverrideModName
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = OneConfigTheme.current
    val toggle = {
        forced = !forced
        PolyPlusCosmeticsConfig.forcePolyPlusCapes = forced
        PolyPlusCosmeticsConfig.save()
    }
    Row(
        modifier = Modifier.hoverable(interaction).clickableWithSound(toggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            SocialText(
                if (modName != null) "Show over $modName" else "Show over other mods",
                color = if (hovered) theme.textColor else theme.textColorSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            if (hovered) {
                TooltipPopup(
                    if (modName != null) {
                        "$modName sets your cape. Turn this on to show your PolyPlus cape instead, or your Minecraft cape if you have none."
                    } else {
                        "When another mod sets your cape, its cape is shown. Turn this on to show your PolyPlus cape instead, or your Minecraft cape if you have none."
                    },
                )
            }
        }
        SocialText(if (forced) "On" else "Off", color = SocialTextSecondary, fontSize = 12.sp)
        SocialSwitch(forced, 42.dp, 21.dp, SocialOffStateBackground)
    }
}

@Composable
private fun InfoTip(text: String) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = OneConfigTheme.current
    Box(Modifier.size(16.dp).hoverable(interaction), contentAlignment = Alignment.Center) {
        Icon("info", color = if (hovered) theme.textColor else theme.textColorSecondary, modifier = Modifier.size(14.dp))
        if (hovered) TooltipPopup(text)
    }
}

@Composable
internal fun CapeSectionNote(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(57.dp)
            .clip(ppShape(16.dp))
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(16.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SocialText(text, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp)
    }
}
