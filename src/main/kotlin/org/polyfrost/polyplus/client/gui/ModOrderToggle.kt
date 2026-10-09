package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.internal.ui.api.Tooltip
import org.polyfrost.oneconfig.internal.ui.components.Chip
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.onClick
import org.polyfrost.oneconfig.internal.ui.components.rememberInteractionSource
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.concentric
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.features.DefaultModOrder

object ModOrderToggle {
    @Composable
    @JvmStatic
    fun Overlay(content: @Composable () -> Unit) {
        Box {
            content()
            Box(Modifier.align(Alignment.TopEnd)) {
                var alphabetical by remember { mutableStateOf(PolyPlusConfig.alphabeticalModOrder) }
                var confirming by remember { mutableStateOf(false) }
                val theme = LocalTheme.current
                Row(
                    Modifier
                        .height(36.dp)
                        .background(theme.componentBackground, theme.buttonShape)
                        .border(1.dp, theme.borderColor, theme.buttonShape)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Segment("star", "Recommended order", !alphabetical) { confirming = true }
                    Segment("capitalized", "A to Z", alphabetical) { confirming = true }
                }
                if (confirming) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, with(LocalDensity.current) { POPUP_DROP.dp.roundToPx() }),
                        onDismissRequest = { confirming = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        val theme = LocalTheme.current
                        Column(
                            Modifier
                                .width(280.dp)
                                .background(theme.popupBackground, theme.popupShape)
                                .border(1.dp, theme.borderColor, theme.popupShape)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Sort mods ${if (alphabetical) "in the recommended order" else "from A to Z"}? " +
                                    "This wipes your existing mod order, including any mods you dragged around.",
                                color = theme.textColor,
                                fontSize = 13.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("Cancel", selected = false) { confirming = false }
                                Chip("Wipe and switch", selected = true) {
                                    confirming = false
                                    alphabetical = !alphabetical
                                    PolyPlusConfig.alphabeticalModOrder = alphabetical
                                    PolyPlusConfig.save()
                                    DefaultModOrder.apply()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Segment(icon: String, hint: String, selected: Boolean, onSwitch: () -> Unit) {
        val theme = LocalTheme.current
        val interactionSource = rememberInteractionSource()
        Tooltip(
            text = { Text(hint, color = theme.textColor, fontSize = 12.sp) },
            modifier = Modifier,
            anchor = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .background(
                        if (selected) theme.textColor.copy(alpha = 0.08f) else Color.Transparent,
                        theme.buttonShape.concentric(3.dp),
                    )
                    .onClick(interactionSource, !selected, onSwitch)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, if (selected) theme.textColor else theme.textColorSecondary, Modifier.size(16.dp))
            }
        }
    }

    private const val POPUP_DROP = 42
}
