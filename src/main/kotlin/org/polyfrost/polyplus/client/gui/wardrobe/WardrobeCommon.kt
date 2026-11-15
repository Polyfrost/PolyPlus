@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

@Composable
internal fun CardLabel(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    var truncated by remember(text) { mutableStateOf(false) }
    TooltipBox(if (truncated) text else null, modifier, TooltipPlacement.AboveStart) {
        Text(
            text,
            color = color,
            style = OneConfigTheme.current.typography.body.copy(fontSize = fontSize, fontWeight = fontWeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            onTextLayout = { truncated = it.hasVisualOverflow },
        )
    }
}
