@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.text.Font
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.Outfit
import org.polyfrost.polyplus.client.gui.lobbyshell.DockedPanel
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPlayerMenu
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelHint
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelSection
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelSlider
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelToggle
import org.polyfrost.polyplus.client.gui.lobbyshell.ResetRow
import org.polyfrost.polyplus.client.gui.lobbyshell.Segmented
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.math.roundToInt

private val MinecraftFont = FontFamily(Font("assets/oneconfig/fonts/minecraft/Minecraft-Regular.otf"))

/**
 * The side panel (OneClient "Customize your lobby" style) that edits one added lobby element live: what it shows
 * or does, its text color, font, size, background, icon, a picture, and its size/opacity in the layout.
 */
internal object LobbyElementPanel {
    /** The element being edited, or null when the panel is closed. */
    var editing by mutableStateOf<String?>(null)

    /** The last thing changed, so typing in a field is one undo step and not one per key. */
    private var lastChange: String? = null

    val fonts = listOf("default" to "Default", "outfit" to "Outfit", "minecraft" to "Minecraft")

    val swatches = listOf("FFFFFF", "BFC6D1", "FFD54A", "FF8A3D", "FF4D5E", "FF6FD8", "B07CFF", "5AA9FF", "3DDC97", "1E1E1E")

    fun open(id: String) {
        LobbyPlayerMenu.closePoses()
        LobbyPlayerMenu.close()
        lastChange = null
        editing = id
    }

    fun close() {
        editing = null
        lastChange = null
    }

    fun font(id: String): FontFamily? = when (id) {
        "outfit" -> Outfit
        "minecraft" -> MinecraftFont
        else -> null
    }

    /** Checkpoint (once per run of edits to the same thing), change the element, and store it. */
    fun change(id: String, what: String, persist: Boolean = true, block: (MenuElement) -> Unit) {
        MenuLayoutStore.element(LobbyHud.SCOPE, id) ?: return
        val key = "$id/$what"
        if (key != lastChange) {
            MenuLayoutStore.checkpoint(LobbyHud.SCOPE)
            lastChange = key
        }
        block(MenuLayoutStore.edit(LobbyHud.SCOPE, id))
        if (persist) MenuLayoutStore.save() else MenuLayoutStore.changed()
    }

    fun set(id: String, key: String, value: String) {
        if (MenuLayoutStore.element(LobbyHud.SCOPE, id)?.props?.get(key) == value) return
        change(id, key) { it.props[key] = value }
    }

    /** Ends a run of edits, so the next change gets its own undo step. */
    fun settle() {
        lastChange = null
        MenuLayoutStore.save()
    }
}

/** Pictures from the menu-images folder as Compose images (the lobby is Compose, the vanilla menu uses textures). */
internal object LobbyImages {
    private val cache = HashMap<String, ImageBitmap?>()

    fun get(file: String): ImageBitmap? {
        if (file.isEmpty()) return null
        return cache.getOrPut(file) {
            runCatching {
                val path = MenuLayoutStore.imageDir.resolve(file)
                if (path.parent != MenuLayoutStore.imageDir || !path.isRegularFile()) return@runCatching null
                PolyBitmap.decode(Files.readAllBytes(path))?.asComposeImageBitmap()
            }.getOrNull()
        }
    }

    fun reload() {
        cache.clear()
        MenuImages.reload()
        MenuLayoutStore.changed()
    }
}

@Composable
internal fun BoxScope.LobbyElementPanelHost() {
    MenuLayoutStore.revision.intValue
    val id = LobbyElementPanel.editing
    val element = id?.let { MenuLayoutStore.element(LobbyHud.SCOPE, it) }?.takeIf { LobbyHud.isHud(it) }
    if (id != null && element == null) LobbyElementPanel.close()
    DockedPanel(element != null, "Edit ${element?.let { LobbyHud.label(it) } ?: "element"}", LobbyElementPanel::close) {
        if (element != null) ElementPage(element)
    }
}

@Composable
private fun ElementPage(element: MenuElement) {
    MenuLayoutStore.revision.intValue // the element is changed in place: redraw on every change
    val id = element.id
    PanelPageColumn {
        when (element.type) {
            "text" -> PanelSection("Text", divider = false) {
                PropField(id, "text", element.prop("text"), "Your text")
                PanelHint("Type \\n for a new line.")
            }
            "button" -> {
                PanelSection("Button", divider = false) {
                    PropField(id, "label", element.prop("label"), "Label")
                }
                ButtonAction(element)
            }
            "image" -> PanelSection(null, divider = false) {
                PanelHint("Pick a picture below. Size sets how big it is.")
                val size = element.prop("size", "48").toFloatOrNull() ?: 48f
                PanelSlider(
                    "Picture size",
                    size,
                    { v -> LobbyElementPanel.change(id, "size", persist = false) { it.props["size"] = v.roundToInt().toString() } },
                    LobbyElementPanel::settle,
                    range = 8f..64f,
                    step = 1f,
                    valueText = { "${it.roundToInt()}" },
                )
                PanelToggle("Background", element.prop("bg", "true") == "true", { LobbyElementPanel.set(id, "bg", it.toString()) })
            }
            "clock" -> PanelSection(null, divider = false) {
                PanelToggle("Show seconds", element.prop("seconds") == "true", { LobbyElementPanel.set(id, "seconds", it.toString()) })
            }
            else -> PanelSection(null, divider = false) {
                PanelHint("Shows ${LobbyHud.label(element)} live. Style it below.")
            }
        }
        if (element.type != "image") {
            TextStyleSection(element)
            IconSection(element)
        }
        ImageSection(element)
        LayoutSection(element)
        PanelSection(null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("Duplicate", modifier = Modifier.weight(1f), style = ControlStyle.Secondary, size = ControlSize.Compact, onClick = {
                    LobbyHud.duplicate(listOf(id))
                })
                TextButton("Delete", modifier = Modifier.weight(1f), style = ControlStyle.Secondary, size = ControlSize.Compact, onClick = {
                    LobbyHud.delete(listOf(id))
                    LobbyElementPanel.close()
                })
            }
        }
    }
}

@Composable
private fun PropField(id: String, key: String, value: String, placeholder: String) {
    TextField(
        value = value,
        onValueChange = { LobbyElementPanel.set(id, key, it) },
        modifier = Modifier.fillMaxWidth(),
        size = ControlSize.Large,
        placeholder = placeholder,
        maxLength = 256,
        fontSize = 14.sp,
    )
}

@Composable
private fun ButtonAction(element: MenuElement) {
    MenuLayoutStore.revision.intValue // the element is changed in place: redraw on every change
    val id = element.id
    val action = element.prop("action", "screen")
    PanelSection("When clicked") {
        Segmented(
            LobbyHud.buttonActions.map { it.first },
            action,
            { picked ->
                LobbyElementPanel.change(id, "action") {
                    it.props["action"] = picked
                    it.props["value"] = if (picked == "screen") "mods" else ""
                }
            },
            label = { key -> LobbyHud.buttonActions.firstOrNull { it.first == key }?.second?.removePrefix("Open ") ?: key },
            columns = 2,
        )
        val value = element.prop("value")
        when (action) {
            "mod" -> PickList(LobbyHud.mods(), value, "No mods with settings found.") { LobbyElementPanel.set(id, "value", it) }
            "screen" -> PickList(CustomAction.screens, value.ifEmpty { "mods" }, "") { LobbyElementPanel.set(id, "value", it) }
            else -> PropField(id, "value", value, if (action == "url") "https://..." else "play.example.net")
        }
    }
}

@Composable
private fun PickList(options: List<Pair<String, String>>, selected: String, empty: String, onPick: (String) -> Unit) {
    if (options.isEmpty()) {
        PanelHint(empty)
        return
    }
    Column(Modifier.fillMaxWidth().card(11.dp).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (key, name) -> ChoiceRow(name, key == selected) { onPick(key) } }
    }
}

@Composable
private fun ChoiceRow(label: String, on: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .selectableRow(on, onClick, label, OneConfigTheme.shape(8.dp), true, ItemSelectionStyle.Accent, A11yRole.RadioButton)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            color = SelectionDefaults.itemContentColor(on, ItemSelectionStyle.Accent),
            style = theme.typography.small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun TextStyleSection(element: MenuElement) {
    MenuLayoutStore.revision.intValue // the element is changed in place: redraw on every change
    val id = element.id
    val current = element.prop("color", "FFFFFF").uppercase()
    PanelSection("Text style") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LobbyElementPanel.swatches.forEach { hex ->
                val on = hex == current
                Box(
                    Modifier
                        .weight(1f)
                        .height(24.dp)
                        .background(LobbyHud.color(hex), RoundedCornerShape(6.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) OneConfigTheme.current.textColor else Color(0x40FFFFFF), RoundedCornerShape(6.dp))
                        .clickable { LobbyElementPanel.set(id, "color", hex) },
                )
            }
        }
        var hex by remember(id) { mutableStateOf(current) }
        if (hex.length == 6 && hex.uppercase() != current) hex = current
        TextField(
            value = hex,
            onValueChange = { typed ->
                hex = typed.removePrefix("#").take(6)
                if (hex.length == 6 && hex.toIntOrNull(16) != null) LobbyElementPanel.set(id, "color", hex.uppercase())
            },
            modifier = Modifier.fillMaxWidth(),
            size = ControlSize.Large,
            placeholder = "Hex color, like FFD54A",
            maxLength = 7,
            fontSize = 14.sp,
        )
        Segmented(
            LobbyElementPanel.fonts.map { it.first },
            element.prop("font", "default"),
            { LobbyElementPanel.set(id, "font", it) },
            label = { key -> LobbyElementPanel.fonts.first { it.first == key }.second },
        )
        val size = element.prop("size", "14").toFloatOrNull() ?: 14f
        PanelSlider(
            "Font size",
            size,
            { v -> LobbyElementPanel.change(id, "size", persist = false) { it.props["size"] = v.roundToInt().toString() } },
            LobbyElementPanel::settle,
            range = 8f..40f,
            step = 1f,
            valueText = { "${it.roundToInt()}" },
        )
        PanelToggle("Bold", element.prop("bold") == "true", { LobbyElementPanel.set(id, "bold", it.toString()) })
        PanelToggle("Text shadow", element.prop("shadow") == "true", { LobbyElementPanel.set(id, "shadow", it.toString()) })
        PanelToggle("Background", element.prop("bg", "true") == "true", { LobbyElementPanel.set(id, "bg", it.toString()) })
    }
}

@Composable
private fun IconSection(element: MenuElement) {
    MenuLayoutStore.revision.intValue // the element is changed in place: redraw on every change
    val id = element.id
    val current = element.prop("icon")
    val theme = OneConfigTheme.current
    PanelSection("Icon") {
        val options = listOf("") + LobbyHud.icons
        Column(Modifier.fillMaxWidth().card(11.dp).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.chunked(ICONS_PER_ROW).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { icon ->
                        val on = icon == current
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .selectableRow(on, { LobbyElementPanel.set(id, "icon", icon) }, icon.ifEmpty { "No icon" }, OneConfigTheme.shape(8.dp), true, ItemSelectionStyle.Accent, A11yRole.RadioButton),
                            contentAlignment = Alignment.Center,
                        ) {
                            val tint = SelectionDefaults.itemContentColor(on, ItemSelectionStyle.Accent)
                            if (icon.isEmpty()) Text("-", color = tint, style = theme.typography.small) else LobbyIcon(icon, tint, 16.dp)
                        }
                    }
                    repeat(ICONS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ImageSection(element: MenuElement) {
    MenuLayoutStore.revision.intValue // the element is changed in place: redraw on every change
    val id = element.id
    val current = element.prop("image")
    val files = remember(MenuLayoutStore.revision.intValue) { MenuImages.files() }
    PanelSection("Picture") {
        PanelHint(if (element.type == "image") "Put .png or .jpg files in the menu-images folder, then pick one." else "Put .png or .jpg files in the menu-images folder, then pick one. It shows next to the text.")
        Column(Modifier.fillMaxWidth().card(11.dp).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ChoiceRow("No picture", current.isEmpty()) { LobbyElementPanel.set(id, "image", "") }
            files.forEach { file ->
                val on = file == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .selectableRow(on, { LobbyElementPanel.set(id, "image", file) }, file, OneConfigTheme.shape(8.dp), true, ItemSelectionStyle.Accent, A11yRole.RadioButton)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val bitmap = LobbyImages.get(file)
                    if (bitmap != null) Image(bitmap, null, Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
                    Text(
                        file,
                        Modifier.weight(1f),
                        color = SelectionDefaults.itemContentColor(on, ItemSelectionStyle.Accent),
                        style = OneConfigTheme.current.typography.small,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("Open folder", { MenuImages.openFolder() }, Modifier.weight(1f), ControlStyle.Secondary, ControlSize.Compact)
            TextButton("Reload", { LobbyImages.reload() }, Modifier.weight(1f), ControlStyle.Secondary, ControlSize.Compact)
        }
    }
}

@Composable
private fun LayoutSection(element: MenuElement) {
    MenuLayoutStore.revision.intValue // the element is changed in place: redraw on every change
    val id = element.id
    PanelSection("Layout") {
        PanelSlider(
            "Size",
            element.scale,
            { v -> LobbyElementPanel.change(id, "scale", persist = false) { it.scale = (v * 20f).roundToInt() / 20f } },
            LobbyElementPanel::settle,
            range = 0.25f..4f,
            valueText = { "${(it * 100).roundToInt()}%" },
        )
        PanelHint("Tip: in the editor you can also scroll on an element to resize it.")
        PanelSlider(
            "Opacity",
            element.opacity * 100f,
            { v -> LobbyElementPanel.change(id, "opacity", persist = false) { it.opacity = (v / 100f).coerceIn(0f, 1f) } },
            LobbyElementPanel::settle,
            range = 5f..100f,
            step = 1f,
            valueText = { "${it.roundToInt()}%" },
        )
        PanelToggle("Visible", element.visible, { on -> LobbyElementPanel.change(id, "visible") { it.visible = on } })
        ResetRow({
            LobbyElementPanel.change(id, "reset-style") {
                listOf("size", "bold", "shadow", "font", "image").forEach(it.props::remove)
                it.props["color"] = "FFFFFF"
                it.props["bg"] = "true"
                it.scale = 1f
                it.opacity = 1f
            }
        }, label = "Reset style")
    }
}

private const val ICONS_PER_ROW = 8
//?}
