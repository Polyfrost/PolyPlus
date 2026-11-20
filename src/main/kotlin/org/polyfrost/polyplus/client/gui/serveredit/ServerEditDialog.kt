@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.serveredit

import androidx.compose.foundation.layout.Arrangement
import org.polyfrost.polyplus.client.gui.KitDangerColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.language.I18n
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleControl
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.PanelHeader
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.serverapi.ServerApiProtocol
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.polyplus.client.featured.ServerFolders
import org.polyfrost.polyplus.client.gui.lobbyshell.FOLDER_ICON
import org.polyfrost.polyplus.client.gui.lobbyshell.FOLDER_NAME_MAX

private const val NAME_MAX_LENGTH = 32
private const val ADDRESS_MAX_LENGTH = 128
private const val DEFAULT_NAME_KEY = "selectServer.defaultName"

private val DialogWidth = 720.dp
private val BodyHeight = 244.dp
private val PreviewWidth = 268.dp
private val BodyGap = 18.dp
private val ButtonWidth = 96.dp
private val CaptureRowSpace = 54.dp

private class ServerEditForm(server: ServerData?) {
    val initialAddress = server?.ip.orEmpty()
    val isNew = server == null
    var name by mutableStateOf(fieldValue(server?.name.orEmpty().take(NAME_MAX_LENGTH)))
    var address by mutableStateOf(fieldValue(initialAddress.take(ADDRESS_MAX_LENGTH)))
    var packStatus by mutableStateOf(server?.resourcePackStatus ?: ServerData.ServerPackStatus.PROMPT)
    var confirmingDelete by mutableStateOf(false)
    var finished = false
    val showCapture = server?.isLan != true && LobbySceneStore.captureEnabled
    var captureDraft by mutableStateOf<Boolean?>(null)
    val showFolder = server?.isLan != true
    var folder by mutableStateOf(server?.ip?.let(ServerFolders::folderOf).orEmpty())
    val folderNames = ServerFolders.names()

    val target: String get() = address.text.trim()
    val addressValid: Boolean get() = ServerAddress.isValidAddress(target)
    val displayName: String get() = name.text.ifEmpty { I18n.get(DEFAULT_NAME_KEY) }
    val captureBlocked: Boolean get() = LobbySceneStore.serverPolicy(target) == ServerApiProtocol.ScenePolicy.DENY
    val captureOn: Boolean get() = !captureBlocked && (captureDraft ?: (LobbySceneStore.serverChoice(if (isNew) target else initialAddress) != false))

    fun applyCapture() {
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
    serverIndex: Int,
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
        ServerEditStore.save(server, serverIndex, form.displayName, form.target, form.packStatus, form.folder.takeIf { form.showFolder })
        if (form.showCapture) form.applyCapture()
        onDone()
    }
    val delete: () -> Unit = delete@{
        if (form.finished || server == null) return@delete
        form.finished = true
        ServerEditStore.delete(server, serverIndex)
        onDelete?.invoke()
    }

    DriveServerProbe(probe, form.target, form.addressValid, debounce = form.target != form.initialAddress)

    ModalDialog(
        onDismiss = cancel,
        width = DialogWidth,
        dismissOnScrim = false,
        title = { DialogHeader(if (server == null) "Add server" else "Edit server", onDismiss) },
        buttons = { DialogFooter(form, server != null && onDelete != null, onDismiss, save) },
    ) {
        LaunchedEffect(Unit) { nameFocus.requestFocus() }
        val bodyPolicy = remember(form.showCapture) { DialogBodyPolicy(if (form.showCapture) BodyHeight + CaptureRowSpace else BodyHeight) }
        val fields: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ServerFields(form, nameFocus)
            }
        }
        val preview: @Composable () -> Unit = { ServerPreviewCard(form.displayName, form.addressValid, probe) }
        Layout(
            contents = listOf(fields, preview),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event -> dialogKey(event, save) },
            measurePolicy = bodyPolicy,
        )
    }
    if (form.confirmingDelete) {
        ConfirmDialog(
            title = "Delete this server?",
            text = "It will be removed from your server list.",
            confirmLabel = "Delete server",
            onConfirm = delete,
            onDismiss = { form.confirmingDelete = false },
            dismissLabel = "Keep server",
            danger = true,
            icon = LobbyIcons.TRASH,
        )
    }
}

private class DialogBodyPolicy(private val minHeight: Dp) : MultiContentMeasurePolicy {
    private fun IntrinsicMeasureScope.fieldsWidth(width: Int): Int =
        if (width == Constraints.Infinity) width else (width - PreviewWidth.roundToPx() - BodyGap.roundToPx()).coerceAtLeast(0)

    private fun IntrinsicMeasureScope.sideWidth(): Int = PreviewWidth.roundToPx() + BodyGap.roundToPx()

    override fun MeasureScope.measure(measurables: List<List<Measurable>>, constraints: Constraints): MeasureResult {
        val previewWidth = PreviewWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val fieldsWidth = fieldsWidth(constraints.maxWidth)
        val fields = measurables[0].map { it.measure(Constraints(minWidth = fieldsWidth, maxWidth = fieldsWidth)) }
        val height = maxOf(fields.maxOfOrNull { it.height } ?: 0, minHeight.roundToPx())
        val preview = measurables[1].map { it.measure(Constraints.fixed(previewWidth, height)) }
        return layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            fields.forEach { it.place(0, 0) }
            preview.forEach { it.place(constraints.maxWidth - previewWidth, 0) }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        maxOf(measurables[0].maxOfOrNull { it.minIntrinsicHeight(fieldsWidth(width)) } ?: 0, minHeight.roundToPx())

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<List<IntrinsicMeasurable>>, width: Int): Int =
        maxOf(measurables[0].maxOfOrNull { it.maxIntrinsicHeight(fieldsWidth(width)) } ?: 0, minHeight.roundToPx())

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int =
        (measurables[0].maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0) + sideWidth()

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<List<IntrinsicMeasurable>>, height: Int): Int =
        (measurables[0].maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0) + sideWidth()
}

private fun dialogKey(event: KeyEvent, save: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> {
            save()
            true
        }
        else -> false
    }
}

@Composable
private fun DialogHeader(title: String, onDismiss: () -> Unit) {
    Announce(title)
    PanelHeader(
        title,
        titleStyle = OneConfigTheme.current.typography.title,
        actions = {
            IconButton(
                icon = LobbyIcons.CLOSE,
                contentDescription = "Close",
                onClick = onDismiss,
                modifier = Modifier.focusProperties { canFocus = false },
                tooltip = null,
                tooltipPlacement = TooltipPlacement.BelowStart,
                tint = SocialTextPrimary,
            )
        },
    )
}

@Composable
private fun ServerFields(form: ServerEditForm, nameFocus: FocusRequester) {
    val invalid = form.address.text.isNotEmpty() && !form.addressValid
    FormField("Server name") {
        TextField(
            value = form.name,
            onValueChange = { form.name = it.limited(NAME_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            size = ControlSize.Large,
            placeholder = Platform.i18n().getUnformattedText(I18n.get(DEFAULT_NAME_KEY)),
            fontSize = 14.sp,
            label = "Server name",
            focusRequester = nameFocus,
        )
    }
    FormField("Server address", helper = "Enter a valid server address.".takeIf { invalid }, helperColor = SocialDangerText) {
        TextField(
            value = form.address,
            onValueChange = { form.address = it.limited(ADDRESS_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            size = ControlSize.Large,
            placeholder = "play.example.net",
            invalid = invalid,
            fontSize = 14.sp,
            label = "Server address",
        )
    }
    FormField("Server resource packs", helper = packHelp(form.packStatus)) {
        Select(
            selected = form.packStatus,
            options = ServerData.ServerPackStatus.entries,
            onSelect = { form.packStatus = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Platform.i18n().getUnformattedText(it.getName()) },
            size = ControlSize.Large,
            contentDescription = "Server resource packs",
            leadingLabel = "Use",
        )
    }
    if (form.showFolder) FolderField(form)
    if (form.showCapture) CaptureRow(form)
}

@Composable
private fun FolderField(form: ServerEditForm) {
    FormField("Folder", helper = "Leave empty to keep it out of folders.") {
        TextField(
            value = form.folder,
            onValueChange = { form.folder = it },
            modifier = Modifier.fillMaxWidth(),
            size = ControlSize.Large,
            placeholder = "No folder",
            fontSize = 14.sp,
            label = "Folder",
            leadingIcon = FOLDER_ICON,
            maxLength = FOLDER_NAME_MAX,
            trailing = if (form.folderNames.isEmpty()) null else {
                {
                    AnchoredPopupButton(
                        icon = "down",
                        contentDescription = "Pick a folder",
                        modifier = Modifier.focusProperties { canFocus = false },
                        placement = Placement.BelowEnd,
                    ) { close ->
                        MenuList(form.folderNames.map { name -> MenuAction(name, { form.folder = name }, checked = name.equals(form.folder.trim(), ignoreCase = true)) }, close)
                    }
                }
            },
        )
    }
}

@Composable
private fun CaptureRow(form: ServerEditForm) {
    val blocked = form.captureBlocked
    val on = form.captureOn
    ToggleRow(
        label = "Capture scenes here",
        checked = on,
        onCheckedChange = { form.captureDraft = it },
        modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
        description = when {
            blocked -> "Blocked by this server"
            on -> "Saves good spots while you play here"
            else -> "Not saving spots on this server"
        },
        enabled = !blocked,
        control = ToggleControl.Switch,
        framed = false,
    )
}

private fun packHelp(status: ServerData.ServerPackStatus) = when (status) {
    ServerData.ServerPackStatus.ENABLED -> "Use the server's resource pack without asking."
    ServerData.ServerPackStatus.DISABLED -> "Never use the server's resource pack."
    ServerData.ServerPackStatus.PROMPT -> "Ask each time the server offers a resource pack."
}

@Composable
private fun FormField(label: String, helper: String? = null, helperColor: Color = OneConfigTheme.current.mutedText, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
        control()
        Box(Modifier.height(14.dp)) {
            helper?.let { Text(it, color = helperColor, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        }
    }
}

@Composable
private fun RowScope.DialogFooter(
    form: ServerEditForm,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    save: () -> Unit,
) {
    val unfocusable = Modifier.focusProperties { canFocus = false }
    if (canDelete) TextButton(text = "Delete", icon = LobbyIcons.TRASH, onClick = { form.confirmingDelete = true }, modifier = unfocusable, style = ControlStyle.Danger, size = ControlSize.Large, dangerColor = KitDangerColor)
    Spacer(Modifier.weight(1f))
    TextButton(text = "Cancel", onClick = onDismiss, modifier = unfocusable.widthIn(min = ButtonWidth), style = ControlStyle.Secondary, size = ControlSize.Large)
    TextButton(text = "Save", onClick = save, modifier = unfocusable.widthIn(min = ButtonWidth), style = ControlStyle.Primary, size = ControlSize.Large, enabled = form.addressValid)
}
