@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigDestination
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
import java.lang.management.ManagementFactory
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Things the player adds to the lobby with the editor, like a HUD editor: a clock, RAM, CPU, FPS, any text, and
 * buttons that open a mod's settings, a screen, a server or a website. They're stored in [MenuLayoutStore] (scope
 * "lobby") with the element type as kind, so moving, scaling, hiding, grouping and undo work like for every piece.
 */
internal object LobbyHud {
    const val SCOPE = "lobby"

    val kinds = listOf(
        "clock" to "Clock",
        "ram" to "RAM usage",
        "cpu" to "CPU usage",
        "fps" to "FPS",
        "text" to "Text",
        "button" to "Button (mod settings, screen, server, link)",
    )

    val icons = listOf(
        "alert-triangle", "bell-01", "check-circle", "check", "clock", "copy-01", "diamond-01", "discord", "edit-02",
        "globe-01", "headphones-02", "image-01", "key-01", "link-external-01", "log-in-04", "log-out-04", "logo",
        "message-chat-circle", "microphone-01", "minecraft-block", "moon-star", "oneconfig-icon", "package-01",
        "pause-circle", "play-circle", "plus", "refresh-cw-01", "search-lg", "settings-02", "trash-01", "user-01",
        "user-plus-01", "users-01", "video-recorder", "volume-max",
    )

    val buttonActions = listOf("mod" to "Open mod settings") + CustomAction.actions

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
    private val hhmmss = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val os = runCatching { ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean }.getOrNull()

    fun isHud(element: MenuElement?) = element != null && !element.builtin && kinds.any { it.first == element.type }

    fun label(element: MenuElement): String = kinds.firstOrNull { it.first == element.type }?.second?.substringBefore(" (") ?: element.type

    private fun defaults(kind: String): Map<String, String> = when (kind) {
        "clock" -> mapOf("icon" to "clock", "seconds" to "false")
        "ram" -> mapOf("icon" to "package-01")
        "cpu" -> mapOf("icon" to "diamond-01")
        "fps" -> mapOf("icon" to "play-circle")
        "text" -> mapOf("text" to "Your text", "icon" to "")
        "button" -> mapOf("label" to "Mod settings", "icon" to "settings-02", "action" to "screen", "value" to "mods")
        else -> emptyMap()
    } + mapOf("color" to "FFFFFF", "bg" to "true")

    /** What the element shows right now. */
    fun text(element: MenuElement): String = when (element.type) {
        "clock" -> LocalTime.now().format(if (element.prop("seconds") == "true") hhmmss else hhmm)
        "ram" -> {
            val runtime = Runtime.getRuntime()
            val used = (runtime.totalMemory() - runtime.freeMemory()) / MB
            "RAM $used / ${runtime.maxMemory() / MB} MB"
        }
        "cpu" -> {
            val load = os?.processCpuLoad ?: -1.0
            if (load < 0) "CPU --" else "CPU ${(load * 100).roundToInt()}%"
        }
        "fps" -> "${Minecraft.getInstance().fps} FPS"
        "text" -> element.prop("text").replace("\\n", "\n")
        "button" -> element.prop("label", "Button")
        else -> element.type
    }

    fun add(kind: String) {
        MenuLayoutStore.checkpoint(SCOPE)
        val id = MenuLayoutStore.newId(SCOPE, kind)
        val element = MenuLayoutStore.edit(SCOPE, id)
        element.type = kind
        element.y = -150f
        element.props.putAll(defaults(kind))
        MenuLayoutStore.save()
        MenuLayoutEditor.select(SCOPE, id, false)
    }

    fun duplicate(ids: Collection<String>) {
        MenuLayoutStore.checkpoint(SCOPE)
        MenuLayoutEditor.selection.clear()
        for (id in ids) {
            val from = MenuLayoutStore.element(SCOPE, id)?.takeIf { isHud(it) } ?: continue
            val copy = MenuLayoutStore.edit(SCOPE, MenuLayoutStore.newId(SCOPE, from.type))
            copy.type = from.type
            copy.x = from.x + 12f
            copy.y = from.y + 12f
            copy.scale = from.scale
            copy.opacity = from.opacity
            copy.visible = from.visible
            copy.group = from.group
            copy.props.putAll(from.props)
            MenuLayoutEditor.select(SCOPE, copy.id, true)
        }
        MenuLayoutStore.save()
    }

    fun delete(ids: Collection<String>) {
        MenuLayoutStore.checkpoint(SCOPE)
        ids.filter { isHud(MenuLayoutStore.element(SCOPE, it)) }.forEach {
            MenuLayoutStore.remove(SCOPE, it)
            MenuLayoutEditor.selection.remove(MenuLayoutEditor.key(SCOPE, it))
        }
        MenuLayoutStore.save()
    }

    fun edit(id: String) {
        if (isHud(MenuLayoutStore.element(SCOPE, id))) ClientPlatform.setScreen(LobbyElementScreen(ClientPlatform.currentScreen(), id))
    }

    /** Every mod with a OneConfig config, id to name. */
    fun mods(): List<Pair<String, String>> {
        val trees = runCatching { ConfigManager.active().trees().toList() }.getOrDefault(emptyList())
        return trees.mapNotNull { tree ->
            val id = tree.id ?: return@mapNotNull null
            val title = tree.title?.toString()?.takeIf { it.isNotBlank() } ?: id.removeSuffix(".json")
            id to title
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }

    fun click(element: MenuElement, screen: Screen) {
        if (element.prop("action") == "mod") {
            val id = element.prop("value").trim()
            if (id.isNotEmpty()) runCatching { OneConfigUI.open(OneConfigDestination.ModConfig(OneConfigPages.modConfig(id))) }
        } else {
            CustomAction.run(element, screen)
        }
    }

    fun color(hex: String): Color = Color(0xFF000000.toInt() or (hex.trim().removePrefix("#").toIntOrNull(16)?.and(0xFFFFFF) ?: 0xFFFFFF))

    private const val MB = 1024L * 1024L
}

/** The added lobby elements, drawn on top of the lobby (home page). */
@Composable
internal fun BoxScope.LobbyAddedElements(screen: Screen) {
    MenuLayoutStore.revision.intValue
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val editing = MenuLayoutEditor.isEditing(LobbyHud.SCOPE)
    for (element in MenuLayoutStore.added(LobbyHud.SCOPE)) {
        if (!LobbyHud.isHud(element)) continue
        key(element.id) {
            EditableBlock(LobbyHud.SCOPE, element.id, LobbyHud.label(element), Modifier.align(Alignment.Center)) {
                tick.let { LobbyHudPill(element, editing, screen) }
            }
        }
    }
}

@Composable
private fun LobbyHudPill(element: MenuElement, editing: Boolean, screen: Screen) {
    val color = LobbyHud.color(element.prop("color", "FFFFFF"))
    val icon = element.prop("icon")
    val shape = RoundedCornerShape(8.dp)
    var modifier: Modifier = Modifier
    if (element.prop("bg", "true") == "true") modifier = modifier.background(Color(0xB0101216), shape)
    if (element.type == "button" && !editing) modifier = modifier.clickable { LobbyHud.click(element, screen) }
    Row(
        modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isNotEmpty() && icon in LobbyHud.icons) LobbyIcon(icon, color, 16.dp)
        val text = LobbyHud.text(element)
        if (text.isNotEmpty()) MenuText(text, 14.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

/** Edit an added lobby element: what it shows or does, color, icon, background, size and group. */
internal class LobbyElementScreen(private val parent: Screen?, private val id: String) : Screen(Component.literal("Edit element")) {
    private interface Field {
        val label: String
    }

    private class Text(override val label: String, initial: String) : Field {
        var value = initial
    }

    private class Choice(override val label: String, val options: List<Pair<String, String>>, initial: String) : Field {
        var value = options.firstOrNull { it.first == initial }?.first ?: options.firstOrNull()?.first.orEmpty()
        fun shown() = options.firstOrNull { it.first == value }?.second ?: value
    }

    private val element = MenuLayoutStore.element(LobbyHud.SCOPE, id)
    private val type = element?.type.orEmpty()
    private val props = LinkedHashMap(element?.props ?: emptyMap<String, String>())
    private val mods = LobbyHud.mods()

    private val content = when (type) {
        "text" -> Text("Text (\\n = new line)", props["text"].orEmpty())
        "button" -> Text("Label", props["label"].orEmpty())
        else -> null
    }
    private val seconds = if (type == "clock") Choice("Seconds", listOf("false" to "Off", "true" to "On"), props["seconds"] ?: "false") else null
    private val action = if (type == "button") Choice("Action", LobbyHud.buttonActions, props["action"] ?: "screen") else null
    private val mod = if (type == "button") Choice("Mod", listOf("" to "(pick a mod)") + mods, if (props["action"] == "mod") props["value"].orEmpty() else "") else null
    private val screenChoice = if (type == "button") Choice("Screen", CustomAction.screens, if (props["action"] == "screen") props["value"].orEmpty() else "mods") else null
    private val address = if (type == "button") Text("Server / URL", if (props["action"] == "server" || props["action"] == "url") props["value"].orEmpty() else "") else null
    private val color = Text("Color (hex)", props["color"] ?: "FFFFFF")
    private val icon = Choice("Icon", listOf("" to "(none)") + LobbyHud.icons.map { it to it }, props["icon"].orEmpty())
    private val background = Choice("Background", listOf("true" to "On", "false" to "Off"), props["bg"] ?: "true")
    private val scale = Text("Size", "%.2f".format(element?.scale ?: 1f))
    private val opacity = Text("Opacity %", ((element?.opacity ?: 1f) * 100).roundToInt().toString())
    private val visible = Choice("Visible", listOf("true" to "Shown", "false" to "Hidden"), (element?.visible ?: true).toString())
    private val group = Text("Group", element?.group.orEmpty())

    private val fields: List<Field> = listOfNotNull(content, seconds, action, mod, screenChoice, address, color, icon, background, scale, opacity, visible, group)

    override fun init() {
        val access = this as ScreenAccessor
        val rowHeight = 22
        val perColumn = ((height - 70) / rowHeight).coerceAtLeast(4)
        val columns = (fields.size + perColumn - 1) / perColumn
        val columnWidth = 250
        val left = width / 2 - columns * columnWidth / 2
        val heading = Component.literal("Edit: ${element?.let { LobbyHud.label(it) } ?: id}")
        access.polyplusAddRenderableWidget(StringWidget(width / 2 - font.width(heading) / 2, 12, font.width(heading), 9, heading, font))
        fields.forEachIndexed { index, field ->
            val cx = left + (index / perColumn) * columnWidth
            val cy = 30 + (index % perColumn) * rowHeight
            access.polyplusAddRenderableWidget(StringWidget(cx, cy + 6, 90, 9, Component.literal(field.label), font))
            when (field) {
                is Text -> {
                    val box = EditBox(font, cx + 94, cy, 150, 18, Component.literal(field.label))
                    box.setMaxLength(256)
                    box.value = field.value
                    box.setResponder { field.value = it }
                    access.polyplusAddRenderableWidget(box)
                }
                is Choice -> {
                    lateinit var button: Button
                    button = Button.builder(Component.literal(field.shown())) {
                        val at = field.options.indexOfFirst { it.first == field.value }
                        val step = if (Minecraft.getInstance().hasShiftDown()) -1 else 1
                        field.value = field.options[(at + step + field.options.size) % field.options.size].first
                        button.message = Component.literal(field.shown())
                    }.bounds(cx + 94, cy, 150, 18).build()
                    access.polyplusAddRenderableWidget(button)
                }
            }
        }
        val hint = Component.literal("Shift+click a choice to go back")
        access.polyplusAddRenderableWidget(StringWidget(width / 2 - font.width(hint) / 2, height - 42, font.width(hint), 9, hint, font))
        val bottom = height - 28
        access.polyplusAddRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { done() }.bounds(width / 2 - 104, bottom, 100, 20).build())
        access.polyplusAddRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.bounds(width / 2 + 4, bottom, 100, 20).build())
    }

    private fun done() {
        if (element == null) return onClose()
        MenuLayoutStore.checkpoint(LobbyHud.SCOPE)
        when (type) {
            "text" -> props["text"] = content?.value.orEmpty()
            "button" -> {
                props["label"] = content?.value.orEmpty()
                val chosen = action?.value ?: "screen"
                props["action"] = chosen
                props["value"] = when (chosen) {
                    "mod" -> mod?.value.orEmpty()
                    "screen" -> screenChoice?.value ?: "mods"
                    else -> address?.value?.trim().orEmpty()
                }
            }
            "clock" -> props["seconds"] = seconds?.value ?: "false"
        }
        props["color"] = color.value.trim().removePrefix("#").ifEmpty { "FFFFFF" }
        props["icon"] = icon.value
        props["bg"] = background.value
        val stored = MenuLayoutStore.edit(LobbyHud.SCOPE, id)
        stored.props.clear()
        stored.props.putAll(props)
        scale.value.trim().replace(',', '.').toFloatOrNull()?.let { stored.scale = it.coerceIn(0.25f, 4f) }
        opacity.value.trim().toFloatOrNull()?.let { stored.opacity = (it / 100f).coerceIn(0f, 1f) }
        stored.visible = visible.value == "true"
        stored.group = group.value.trim().ifEmpty { null }
        MenuLayoutStore.save()
        onClose()
    }

    override fun onClose() = ClientPlatform.setScreen(parent)
}
//?}
