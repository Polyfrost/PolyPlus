@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape

@Composable
internal fun SmallButton(
    label: String,
    iconPath: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.height(33.dp)
            .clip(ppShape(8.dp))
            .background(if (primary) OneConfigTheme.accent else OneConfigTheme.current.chipBackground)
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconPath, color = if (primary) OneConfigTheme.current.accentTextColor else OneConfigTheme.current.textColor, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        SocialText(label, color = if (primary) OneConfigTheme.current.accentTextColor else OneConfigTheme.current.textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun WardrobeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 64,
    onSubmit: (() -> Unit)? = null,
) {
    val theme = OneConfigTheme.current
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        singleLine = true,
        textStyle = TextStyle(
            color = theme.textColor,
            fontSize = 12.sp,
            fontFamily = theme.fontFamily,
        ),
        cursorBrush = SolidColor(theme.textColor),
        keyboardActions = onSubmit?.let { submit -> KeyboardActions(onDone = { submit() }) } ?: KeyboardActions.Default,
        keyboardOptions = if (onSubmit != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
        modifier = modifier
            .clip(ppShape(5.dp))
            .background(theme.chipBackground)
            .border(1.dp, theme.borderColor, ppShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .trackTextInputFocus(),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    SocialText(placeholder, color = theme.textColorSecondary, fontSize = 12.sp, maxLines = 1)
                }
                inner()
            }
        },
    )
}

@Composable
internal fun CenteredNote(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SocialText(text, color = OneConfigTheme.current.textColorSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun CardLabel(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    var truncated by remember(text) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(modifier.hoverable(interaction)) {
        SocialText(
            text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { truncated = it.hasVisualOverflow },
        )
        if (truncated && hovered) {
            TooltipPopup(text)
        }
    }
}

@Composable
internal fun TooltipPopup(text: String) {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val positionProvider = remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val above = anchorBounds.top - gap - popupContentSize.height
                val y = if (above >= 0) above else anchorBounds.bottom + gap
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(
            modifier = Modifier.widthIn(max = 240.dp)
                .clip(ppShape(6.dp))
                .background(OneConfigTheme.current.popupBackground)
                .border(1.dp, OneConfigTheme.current.borderColor, ppShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            SocialText(text, color = OneConfigTheme.current.textColor, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun cardBrush(): Brush = SolidColor(OneConfigTheme.current.modCardBackground)

@Composable
internal fun Modifier.wardrobePanel(): Modifier =
    clip(ppShape(16.dp))
        .background(OneConfigTheme.current.modCardBackground)
        .border(1.dp, OneConfigTheme.current.borderColor, ppShape(16.dp))
