@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.world

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

abstract class WorldDialogShell(shell: ScreenShell) : WorldShellScreen(shell) {
    @Composable
    override fun Content() {
        ProvidePopupSounds(false) { Body() }
    }
}

class ConfirmDialogShell(shell: ScreenShell, private val spec: DialogSpec) : WorldDialogShell(shell) {
    private var answered = false

    override val title: String get() = spec.title

    override fun back() = answer(false)

    private fun answer(result: Boolean) {
        if (answered) return
        answered = true
        spec.onResult(result)
    }

    @Composable
    override fun Body() {
        ConfirmDialog(spec, ::answer)
    }
}

class DirectJoinShell(shell: ScreenShell, private val draft: DirectJoinDraft) : WorldDialogShell(shell) {
    override val title: String get() = "Direct connect"

    override fun back() = draft.cancel()

    @Composable
    override fun Body() {
        DirectJoinDialog(draft)
    }
}

@Composable
fun ConfirmDialog(spec: DialogSpec, onResult: (Boolean) -> Unit) {
    val theme = OneConfigTheme.current
    ModalDialog(
        onDismiss = { onResult(false) },
        width = 440.dp,
        dismissOnScrim = false,
        title = { DialogTitle(spec.title) },
        buttons = {
            spec.dismissLabel?.let { TextButton(it, { onResult(false) }) }
            TextButton(spec.confirmLabel, { onResult(true) }, style = if (spec.danger) ControlStyle.Danger else ControlStyle.Primary)
        },
    ) {
        if (spec.message.isNotBlank()) Text(spec.message, color = theme.textColorSecondary, style = theme.typography.body)
    }
}

@Composable
fun DirectJoinDialog(draft: DirectJoinDraft) {
    var address by remember(draft) { mutableStateOf(draft.lastAddress) }
    val focus = remember { FocusRequester() }
    val target = address.trim()
    val valid = draft.isValid(target)
    val theme = OneConfigTheme.current
    ModalDialog(
        onDismiss = draft::cancel,
        width = 440.dp,
        dismissOnScrim = false,
        title = { DialogTitle("Direct connect") },
        buttons = {
            TextButton("Cancel", draft::cancel)
            TextButton("Join", { draft.join(target) }, style = ControlStyle.Primary, enabled = valid)
        },
    ) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        TextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Server address",
            maxLength = 128,
            invalid = target.isNotEmpty() && !valid,
            focusRequester = focus,
            onSubmit = { if (valid) draft.join(target) },
        )
        if (target.isNotEmpty() && !valid) Text("That address doesn't look right.", color = theme.textColorSecondary, style = theme.typography.small)
    }
}
