@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
//? if = 1.8.9 {
/*import net.minecraft.client.gui.screens.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogButtonLayout
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.utils.ClientPlatform

private val PopupPadding = PaddingValues(horizontal = 22.dp, vertical = 24.dp)
private val PopupButtonHeight = 44.dp

@Composable
internal fun ColumnScope.AccountStatusText(busy: String?, error: String?, errorSteps: List<String>?) {
    if (error != null) {
        Text(error, modifier = Modifier.align(Alignment.CenterHorizontally), color = SocialDangerText, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
    }
    if (!errorSteps.isNullOrEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "What you can do:",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
            errorSteps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("•", color = SocialTextSecondary, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
                    Text(step, color = SocialTextSecondary, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
                }
            }
        }
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
    val theme = OneConfigTheme.current
    ModalDialog(
        onCancel,
        width = 380.dp,
        title = { CenteredTitle("Sign in to Microsoft") },
        padding = PopupPadding,
        spacing = 14.dp,
        scrimColor = SocialScrim,
    ) {
        Text(
            "We opened the Microsoft sign-in page in your browser. Finish there and you'll be brought back automatically.",
            modifier = Modifier.fillMaxWidth(),
            color = SocialTextSecondary,
            style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Center,
        )
        TextButton(
            text = "Open in browser again",
            icon = ASSETS + "link-external-01.svg",
            onClick = { ClientPlatform.openUri(browserAuthUrl) },
            modifier = Modifier.fillMaxWidth().height(PopupButtonHeight),
            style = ControlStyle.Primary,
            size = ControlSize.Large,
        )
        OrDivider()
        Text(
            "Or enter this code at the Microsoft sign-in page:",
            modifier = Modifier.fillMaxWidth(),
            color = SocialTextSecondary,
            style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Center,
        )
        val codeShape = OneConfigTheme.shape(8.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(codeShape)
                .background(theme.componentBackground.copy(alpha = 0.5f))
                .border(SocialPanelBorderWidth, OneConfigTheme.accent, codeShape)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(code, style = OneConfigTheme.current.typography.body.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp), textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = "Copy code",
                icon = ASSETS + "copy-01.svg",
                onClick = { copyToClipboard(code) },
                modifier = Modifier.weight(1f).height(PopupButtonHeight),
                size = ControlSize.Large,
            )
            TextButton(
                text = "Open in browser",
                icon = ASSETS + "link-external-01.svg",
                onClick = { ClientPlatform.openUri(verificationUri) },
                modifier = Modifier.weight(1f).height(PopupButtonHeight),
                style = ControlStyle.Primary,
                size = ControlSize.Large,
            )
        }
        Text(
            "If your browser doesn't open, go to this link and enter the code:",
            modifier = Modifier.fillMaxWidth(),
            color = SocialTextSecondary,
            style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal),
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(verificationUri, modifier = Modifier.fillMaxWidth(), color = OneConfigTheme.accent, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center)
            }
            TextButton(
                text = "Copy link",
                icon = ASSETS + "copy-01.svg",
                onClick = { copyToClipboard(verificationUri) },
                modifier = Modifier.height(PopupButtonHeight),
                size = ControlSize.Large,
            )
        }
        if (status != null) {
            BusyRow(status)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(text = "Cancel", onClick = onCancel, style = ControlStyle.Ghost)
        }
    }
}

@Composable
private fun CenteredTitle(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DialogTitle(text) }
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

    ModalDialog(
        { if (idle) onCancel() },
        width = 380.dp,
        title = { CenteredTitle("Add offline account") },
        padding = PopupPadding,
        spacing = 14.dp,
        scrimColor = SocialScrim,
        buttonLayout = DialogButtonLayout.Fill,
        buttons = {
            TextButton(
                text = "Cancel",
                icon = ASSETS + "x-close.svg",
                onClick = { if (idle) onCancel() },
                modifier = Modifier.weight(1f).height(PopupButtonHeight),
                enabled = idle,
            )
            TextButton(
                text = "Add account",
                icon = ASSETS + "plus.svg",
                onClick = submit,
                modifier = Modifier.weight(1f).height(PopupButtonHeight),
                style = ControlStyle.Primary,
                enabled = valid && idle,
            )
        },
    ) {
        TextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            size = ControlSize.Large,
            placeholder = "Username",
            leadingIcon = ASSETS + "user-01.svg",
            maxLength = 16,
            fontSize = 14.sp,
            onSubmit = submit,
        )
        val hint = when {
            error != null && submitted == trimmed -> error
            trimmed.isNotEmpty() && !validChars -> "Only letters, digits, and underscores"
            trimmed.isNotEmpty() && trimmed.length < 3 -> "At least 3 characters"
            else -> null
        }
        if (hint != null) {
            Text(hint, modifier = Modifier.fillMaxWidth(), color = SocialDangerText, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
        }
        if (busy != null) {
            BusyRow(busy)
        }
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        ThemedIcon(ASSETS + "loading-02.svg", OneConfigTheme.accent, 14.dp)
        Text(text, color = SocialTextSecondary, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center)
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
        Divider(Modifier.weight(1f))
        Text("or", color = SocialTextSecondary, style = OneConfigTheme.current.typography.small, textAlign = TextAlign.Center)
        Divider(Modifier.weight(1f))
    }
}
