@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
//? if = 1.8.9 {
/*import net.minecraft.client.gui.screens.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.utils.ClientPlatform

@Composable
internal fun ColumnScope.AccountStatusText(busy: String?, error: String?, errorSteps: List<String>?) {
    if (error != null) {
        SocialText(error, fontSize = 12.sp, color = SocialDangerColor, modifier = Modifier.align(Alignment.CenterHorizontally), textAlign = TextAlign.Center)
    }
    if (!errorSteps.isNullOrEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SocialText(
                "What you can do:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center,
            )
            errorSteps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    SocialText("•", fontSize = 12.sp, color = SocialTextSecondary,  textAlign = TextAlign.Center)
                    SocialText(step, fontSize = 12.sp, color = SocialTextSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun AddAccountButton(
    icon: String,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .clip(ppShape(8.dp))
            .background(if (hovered && enabled) OneConfigTheme.current.componentBackground.copy(alpha = 0.4f) else Color.Transparent)
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickableWithSound(text, onClick = onClick) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .border(1.dp, OneConfigTheme.current.borderColor, ppShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemedIcon(icon, SocialTextPrimary, 18.dp)
        SocialText(text, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically), textAlign = TextAlign.Center)
    }
}

@Composable
internal fun MicrosoftLoginPopup(
    code: String,
    verificationUri: String,
    browserAuthUrl: String,
    status: String?,
    onCancel: () -> Unit,
) {
    SocialModalScrim(onCancel) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(PanelShape)
                .background(OneConfigTheme.current.pageBackground.copy(alpha = 0.8f))
                .border(SocialPanelBorderWidth, SocialPanelBorderBrush, PanelShape)
                .swallowClicks()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Announce("Sign in to Microsoft")
            SocialText("Sign in to Microsoft", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            SocialText(
                "We opened the Microsoft sign-in page in your browser. Finish there and you'll be brought back automatically.",
                fontSize = 13.sp,
                color = SocialTextSecondary,
                textAlign = TextAlign.Center,
            )
            LoginModalButton(
                label = "Open in browser again",
                icon = ASSETS + "link-external-01.svg",
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { ClientPlatform.openUri(browserAuthUrl) },
            )
            OrDivider()
            SocialText(
                "Or enter this code at the Microsoft sign-in page:",
                fontSize = 13.sp,
                color = SocialTextSecondary,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ppShape(8.dp))
                    .background(OneConfigTheme.current.componentBackground.copy(alpha = 0.5f))
                    .border(SocialPanelBorderWidth, OneConfigTheme.accent, ppShape(8.dp))
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                SocialText(code, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoginModalButton(
                    label = "Copy code",
                    icon = ASSETS + "copy-01.svg",
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = { copyToClipboard(code) },
                )
                LoginModalButton(
                    label = "Open in browser",
                    icon = ASSETS + "link-external-01.svg",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = { ClientPlatform.openUri(verificationUri) },
                )
            }
            SocialText(
                "If your browser doesn't open, go to this link and enter the code:",
                fontSize = 13.sp,
                color = SocialTextSecondary,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectionContainer(Modifier.weight(1f)) {
                    SocialText(verificationUri, fontSize = 13.sp, color = OneConfigTheme.accent, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                LoginModalButton(
                    label = "Copy link",
                    icon = ASSETS + "copy-01.svg",
                    filled = false,
                    onClick = { copyToClipboard(verificationUri) },
                )
            }
            if (status != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingSpinner(14.dp)
                    SocialText(status, fontSize = 13.sp, color = SocialTextSecondary, textAlign = TextAlign.Center)
                }
            }
            Box(
                modifier = Modifier
                    .clip(ppShape(6.dp))
                    .clickableWithSound("Cancel", onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                SocialText("Cancel", fontSize = 14.sp, color = SocialTextPrimary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun OfflineAccountPopup(
    busy: String?,
    error: String?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    val trimmed = username.trim()
    val validChars = trimmed.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }
    val valid = trimmed.length in 3..16 && validChars
    val idle = busy == null
    var submitted by remember { mutableStateOf<String?>(null) }
    val submit = {
        if (valid && idle) {
            submitted = trimmed
            onSubmit(trimmed)
        }
    }

    SocialModalScrim({ if (idle) onCancel() }) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(PanelShape)
                .background(OneConfigTheme.current.pageBackground.copy(alpha = 0.8f))
                .border(SocialPanelBorderWidth, SocialPanelBorderBrush, PanelShape)
                .swallowClicks()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Announce("Add offline account")
            SocialText("Add offline account", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            TextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                placeholder = "Username",
                leadingIcon = themedIcon(ASSETS + "user-01.svg"),
                maxLength = 16,
                invalid = false,
                enabled = true,
                singleLine = true,
                framed = true,
                fontSize = 14.sp,
                label = null,
                masked = false,
                focusRequester = null,
                interactionSource = null,
                trailing = null,
                onSubmit = submit,
            )
            val hint = when {
                error != null && submitted == trimmed -> error
                trimmed.isNotEmpty() && !validChars -> "Only letters, digits, and underscores"
                trimmed.isNotEmpty() && trimmed.length < 3 -> "At least 3 characters"
                else -> null
            }
            if (hint != null) {
                SocialText(hint, fontSize = 12.sp, color = SocialDangerColor, textAlign = TextAlign.Center)
            }
            if (busy != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingSpinner(14.dp)
                    SocialText(busy, fontSize = 13.sp, color = SocialTextSecondary, textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoginModalButton(
                    label = "Cancel",
                    icon = ASSETS + "x-close.svg",
                    filled = false,
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = if (idle) 1f else 0.5f },
                    onClick = { if (idle) onCancel() },
                )
                LoginModalButton(
                    label = "Add account",
                    icon = ASSETS + "plus.svg",
                    filled = true,
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = if (valid && idle) 1f else 0.5f },
                    onClick = submit,
                )
            }
        }
    }
}

@Composable
private fun LoginModalButton(
    label: String,
    icon: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (filled) Color.White else SocialTextPrimary
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(PanelShape)
            .background(if (filled) OneConfigTheme.accent else PanelBackground)
            .border(SocialPanelBorderWidth, if (filled) SolidColor(OneConfigTheme.accent) else SocialPanelBorderBrush, PanelShape)
            .clickableWithSound(label, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedIcon(icon, contentColor, 16.dp)
        SocialText(label, fontSize = 14.sp, color = contentColor, maxLines = 1, textAlign = TextAlign.Center)
    }
}

private fun copyToClipboard(text: String) {
    runCatching {
        //? if > 1.8.9 {
        Minecraft.getInstance().keyboardHandler.setClipboard(text)
        //?} else {
        /*Screen.setClipboard(text)
        *///?}
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(SocialPanelBorderBrush))
        SocialText("or", fontSize = 12.sp, color = SocialTextSecondary, textAlign = TextAlign.Center)
        Box(Modifier.weight(1f).height(1.dp).background(SocialPanelBorderBrush))
    }
}

@Composable
private fun LoadingSpinner(size: Dp) {
    ThemedIcon(ASSETS + "loading-02.svg", OneConfigTheme.accent, size)
}

@Composable
internal fun AccountActionIcon(icon: String, color: Color, enabled: Boolean, label: String? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(ppShape(6.dp))
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .then(if (enabled) Modifier.clickableWithSound(label, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        ThemedIcon(icon, color, 18.dp)
    }
}
