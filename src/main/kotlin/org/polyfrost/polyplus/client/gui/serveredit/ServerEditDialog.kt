@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.serveredit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.language.I18n
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialDropdown
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.SocialOffStateBackground
import org.polyfrost.polyplus.client.gui.SocialSwitch
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.serverapi.ServerApiProtocol
import androidx.compose.foundation.hoverable
import androidx.compose.ui.graphics.graphicsLayer

private const val NAME_MAX_LENGTH = 32
private const val ADDRESS_MAX_LENGTH = 128
private const val DEFAULT_NAME_KEY = "selectServer.defaultName"

private val DialogWidth = 720.dp
private val BodyHeight = 280.dp
private val PreviewWidth = 268.dp
private val ButtonWidth = 96.dp
private val ButtonHeight = 38.dp
private val CaptureRowSpace = 54.dp

private class ServerEditForm(server: ServerData?) {
    val initialAddress = server?.ip.orEmpty()
    val isNew = server == null
    var name by mutableStateOf(fieldValue(server?.name.orEmpty().take(NAME_MAX_LENGTH)))
    var address by mutableStateOf(fieldValue(initialAddress.take(ADDRESS_MAX_LENGTH)))
    var packStatus by mutableStateOf(server?.resourcePackStatus ?: ServerData.ServerPackStatus.PROMPT)
    var confirmingDelete by mutableStateOf(false)
    val packOpen = mutableStateOf(false)
    var finished = false
    val showCapture = server?.isLan != true && LobbySceneStore.captureEnabled
    var captureDraft by mutableStateOf<Boolean?>(null)

    val addressValid: Boolean get() = ServerAddress.isValidAddress(address.text)
    val displayName: String get() = name.text.ifEmpty { I18n.get(DEFAULT_NAME_KEY) }
    val captureBlocked: Boolean get() = LobbySceneStore.serverPolicy(address.text) == ServerApiProtocol.ScenePolicy.DENY
    val captureOn: Boolean get() = !captureBlocked && (captureDraft ?: (LobbySceneStore.serverChoice(if (isNew) address.text else initialAddress) != false))

    fun applyCapture() {
        val target = address.text
        val draft = captureDraft
        when {
            draft != null && !captureBlocked -> LobbySceneStore.setServerChoice(target, if (draft) null else false)
            !isNew && target != initialAddress -> LobbySceneStore.setServerChoice(target, LobbySceneStore.serverChoice(initialAddress))
        }
    }
}

private fun fieldValue(text: String) = TextFieldValue(text, TextRange(text.length))

private fun TextFieldValue.limited(maxLength: Int) = if (text.length > maxLength) copy(text = text.take(maxLength)) else this

@Composable
internal fun ServerEditDialog(
    server: ServerData?,
    onDone: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val form = remember(server) { ServerEditForm(server) }
    val probe = remember(server) { ServerStatusProbe(Minecraft.getInstance(), server) }
    val nameFocus = remember { FocusRequester() }

    val cancel: () -> Unit = {
        if (form.confirmingDelete) form.confirmingDelete = false else onDismiss()
    }
    val save: () -> Unit = save@{
        if (form.finished || form.confirmingDelete || !form.addressValid) return@save
        form.finished = true
        ServerEditStore.save(server, form.displayName, form.address.text, form.packStatus)
        if (form.showCapture) form.applyCapture()
        onDone()
    }
    val delete: () -> Unit = delete@{
        if (form.finished || server == null) return@delete
        form.finished = true
        ServerEditStore.delete(server)
        onDelete?.invoke()
    }

    if (form.packOpen.value) EscapeCloses { form.packOpen.value = false }
    DriveServerProbe(probe, form.address.text, form.addressValid, debounce = form.address.text != form.initialAddress)

    ModalDialog(cancel, DialogWidth, Dp.Unspecified, PaddingValues(0.dp), 0.dp, false) {
        LaunchedEffect(Unit) { nameFocus.requestFocus() }
        Column(Modifier.fillMaxWidth().onPreviewKeyEvent { event -> dialogKey(event, cancel, save) }) {
            DialogHeader(if (server == null) "Add server" else "Edit server", onDismiss)
            Hairline()
            Row(
                Modifier.fillMaxWidth().height(if (form.showCapture) BodyHeight + CaptureRowSpace else BodyHeight).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServerFields(form, nameFocus)
                }
                ServerPreviewCard(form.displayName, form.addressValid, probe, Modifier.width(PreviewWidth).fillMaxHeight())
            }
            Hairline()
            DialogFooter(form, server != null && onDelete != null, onDismiss, save)
        }
    }
    if (form.confirmingDelete) {
        ConfirmDialog(
            "Delete this server?",
            "It will be removed from your server list.",
            "Delete server",
            "Keep server",
            themedIcon(LobbyIcons.TRASH),
            true,
            true,
            delete,
        ) { form.confirmingDelete = false }
    }
}

private fun dialogKey(event: KeyEvent, cancel: () -> Unit, save: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Escape -> {
            cancel()
            true
        }
        Key.Enter, Key.NumPadEnter -> {
            save()
            true
        }
        else -> false
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
}

@Composable
private fun DialogHeader(title: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { DialogTitle(title) }
        IconButton(themedIcon(LobbyIcons.CLOSE), null, "Close", Modifier.focusProperties { canFocus = false }, null, SocialTextPrimary, TooltipPlacement.BelowStart, onDismiss)
    }
}

@Composable
private fun ServerFields(form: ServerEditForm, nameFocus: FocusRequester) {
    val invalid = form.address.text.isNotEmpty() && !form.addressValid
    FormField("Server name") {
        TextField(
            form.name,
            { form.name = it.limited(NAME_MAX_LENGTH) },
            Modifier.fillMaxWidth().height(38.dp),
            Platform.i18n().getUnformattedText(I18n.get(DEFAULT_NAME_KEY)),
            null,
            Int.MAX_VALUE,
            false,
            true,
            true,
            true,
            14.sp,
            "Server name",
            false,
            nameFocus,
            null,
            null,
            null,
        )
    }
    FormField("Server address", helper = "Enter a valid server address.".takeIf { invalid }, helperColor = SocialDangerText) {
        TextField(
            form.address,
            { form.address = it.limited(ADDRESS_MAX_LENGTH) },
            Modifier.fillMaxWidth().height(38.dp),
            "play.example.net",
            null,
            Int.MAX_VALUE,
            invalid,
            true,
            true,
            true,
            14.sp,
            "Server address",
            false,
            null,
            null,
            null,
            null,
        )
    }
    FormField("Server resource packs", helper = packHelp(form.packStatus)) {
        SocialDropdown(
            "Use",
            ServerData.ServerPackStatus.entries,
            form.packStatus,
            { Platform.i18n().getUnformattedText(it.getName()) },
            Modifier.fillMaxWidth(),
            form.packOpen,
        ) { form.packStatus = it }
    }
    if (form.showCapture) CaptureRow(form)
}

@Composable
private fun CaptureRow(form: ServerEditForm) {
    val blocked = form.captureBlocked
    val on = form.captureOn
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer { alpha = if (blocked) 0.5f else 1f }
            .rowFrame(false, hovered && !blocked)
            .hoverable(interaction, !blocked)
            .focusProperties { canFocus = false }
            .then(if (blocked) Modifier else Modifier.clickableWithSound("Capture scenes here", A11yRole.Switch, checked = on) { form.captureDraft = !on })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label("Capture scenes here", 14.sp)
            Label(
                when {
                    blocked -> "Blocked by this server"
                    on -> "Saves good spots while you play here"
                    else -> "Not saving spots on this server"
                },
                12.sp,
                color = SocialTextSecondary,
            )
        }
        Label(if (on) "On" else "Off", 12.sp, color = SocialTextSecondary)
        SocialSwitch(on, 42.dp, 21.dp, SocialOffStateBackground)
    }
}

private fun packHelp(status: ServerData.ServerPackStatus) = when (status) {
    ServerData.ServerPackStatus.ENABLED -> "Use the server's resource pack without asking."
    ServerData.ServerPackStatus.DISABLED -> "Never use the server's resource pack."
    ServerData.ServerPackStatus.PROMPT -> "Ask each time the server offers a resource pack."
}

@Composable
private fun FormField(label: String, helper: String? = null, helperColor: Color = MutedText, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SocialText(label, 12.sp, color = MutedText, fontWeight = FontWeight.Medium, maxLines = 1)
        control()
        Box(Modifier.height(14.dp)) {
            helper?.let { SocialText(it, 11.sp, color = helperColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun DialogFooter(
    form: ServerEditForm,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    save: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 18.dp).focusProperties { canFocus = false },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (canDelete) TextButton("Delete", themedIcon(LobbyIcons.TRASH), Modifier.height(ButtonHeight), ButtonStyle.Danger, true) { form.confirmingDelete = true }
        Spacer(Modifier.weight(1f))
        TextButton("Cancel", null, Modifier.widthIn(min = ButtonWidth).height(ButtonHeight), ButtonStyle.Secondary, true, onDismiss)
        TextButton("Save", null, Modifier.widthIn(min = ButtonWidth).height(ButtonHeight), ButtonStyle.Primary, form.addressValid, save)
    }
}
