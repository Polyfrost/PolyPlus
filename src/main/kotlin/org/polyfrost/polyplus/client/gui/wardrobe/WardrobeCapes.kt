@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.Switch
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CapeResolver

@Composable
internal fun CapeGroupHeading(title: String, note: String, forceToggle: Boolean) {
    val theme = OneConfigTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, Modifier, theme.textColor, theme.typography.label)
        InfoTip(note)
        Spacer(Modifier.weight(1f))
        if (forceToggle) ForceCapesToggle()
    }
}

@Composable
internal fun ForceCapesToggle() {
    var forced by remember { mutableStateOf(PolyPlusCosmeticsConfig.forcePolyPlusCapes) }
    val modName = CapeResolver.localOverrideModName
    val theme = OneConfigTheme.current
    val toggle = {
        forced = !forced
        PolyPlusCosmeticsConfig.forcePolyPlusCapes = forced
        PolyPlusCosmeticsConfig.save()
    }
    val label = if (modName != null) "Show over $modName" else "Show over other mods"
    val hover = rememberHoverState()
    TooltipBox(
        text = if (modName != null) {
            "$modName sets your cape. Turn this on to show your PolyPlus cape instead, or your Minecraft cape if you have none."
        } else {
            "When another mod sets your cape, its cape is shown. Turn this on to show your PolyPlus cape instead, or your Minecraft cape if you have none."
        },
        placement = TooltipPlacement.AboveStart,
    ) {
        Row(
            modifier = Modifier
                .hoverable(hover.interactionSource)
                .oneConfigClickable(label, toggle, role = A11yRole.Switch, checked = forced, indication = false, interactionSource = hover.interactionSource),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, Modifier.widthIn(max = 220.dp), if (hover.hovered) theme.textColor else theme.textColorSecondary, theme.typography.small, 1, TextOverflow.Ellipsis)
            Switch(
                forced,
                { toggle() },
                Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {},
                stateLabel = true,
            )
        }
    }
}

@Composable
private fun InfoTip(text: String) {
    val theme = OneConfigTheme.current
    TooltipBox(text, Modifier.size(16.dp), TooltipPlacement.AboveStart) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Icon(name = "info", contentDescription = null, modifier = Modifier.size(14.dp), tint = theme.textColorSecondary)
        }
    }
}

@Composable
internal fun CapeSectionNote(text: String) {
    val shape = OneConfigTheme.shape(16.dp)
    Box(
        modifier = Modifier.fillMaxWidth().height(57.dp)
            .clip(shape)
            .border(1.dp, OneConfigTheme.current.borderColor, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
    }
}
