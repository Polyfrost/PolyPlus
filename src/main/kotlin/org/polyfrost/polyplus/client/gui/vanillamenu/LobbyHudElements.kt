@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigDestination
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.MenuRow
import org.polyfrost.polyplus.client.gui.lobbyshell.POPUP_MENU_WIDTH
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
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
        if (isHud(MenuLayoutStore.element(SCOPE, id))) LobbyElementPanel.open(id)
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

/** Right-click on a lobby widget outside the editor: a small Edit / Hide menu. */
internal object LobbyHudMenu {
    var id by mutableStateOf<String?>(null)
    var at by mutableStateOf(Offset.Zero)

    fun open(id: String, at: Offset) {
        this.id = id
        this.at = at
    }

    fun close() {
        id = null
    }

    fun hide(id: String) {
        MenuLayoutStore.checkpoint(LobbyHud.SCOPE)
        MenuLayoutStore.edit(LobbyHud.SCOPE, id).visible = false
        MenuLayoutStore.save()
    }
}

@Composable
internal fun BoxScope.LobbyHudMenuHost() {
    val id = LobbyHudMenu.id ?: return
    val at = LobbyHudMenu.at
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press) {
                        event.changes.forEach { it.consume() }
                        LobbyHudMenu.close()
                    }
                }
            }
        },
    )
    Column(
        Modifier
            .align(Alignment.TopStart)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(0, 0) {
                    val x = at.x.roundToInt().coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                    val y = at.y.roundToInt().coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
                    placeable.place(x, y)
                }
            }
            .width(POPUP_MENU_WIDTH)
            .panel(12.dp)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MenuRow("Edit") {
            LobbyHudMenu.close()
            LobbyElementPanel.open(id)
        }
        MenuRow("Hide") {
            LobbyHudMenu.close()
            LobbyHudMenu.hide(id)
        }
        MenuRow("Duplicate") {
            LobbyHudMenu.close()
            LobbyHud.duplicate(listOf(id))
            MenuLayoutEditor.selection.clear()
        }
        MenuRow("Delete") {
            LobbyHudMenu.close()
            LobbyHud.delete(listOf(id))
        }
    }
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
    val size = (element.prop("size", "14").toFloatOrNull() ?: 14f).coerceIn(6f, 64f)
    val image = element.prop("image").takeIf { it.isNotEmpty() }?.let { LobbyImages.get(it) }
    val shape = RoundedCornerShape(8.dp)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var modifier: Modifier = Modifier.onGloballyPositioned { coords = it }
    if (element.prop("bg", "true") == "true") modifier = modifier.background(Color(0xB0101216), shape)
    if (element.type == "button" && !editing) modifier = modifier.clickable { LobbyHud.click(element, screen) }
    if (!editing) modifier = modifier.pointerInput(element.id) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    val change = event.changes.firstOrNull() ?: continue
                    change.consume()
                    val at = coords?.takeIf { it.isAttached }?.localToRoot(change.position) ?: change.position
                    LobbyHudMenu.open(element.id, at)
                }
            }
        }
    }
    Row(
        modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (image != null) {
            val height = (size * 1.3f).dp
            Image(image, null, Modifier.height(height).width(height * (image.width.toFloat() / image.height.coerceAtLeast(1))))
        }
        if (icon.isNotEmpty() && icon in LobbyHud.icons) LobbyIcon(icon, color, (size * 1.15f).dp)
        val text = LobbyHud.text(element)
        if (text.isNotEmpty()) {
            val shadow = if (element.prop("shadow") == "true") Shadow(Color.Black.copy(alpha = 0.6f), Offset(1f, 1f), 2f) else null
            BasicText(
                text,
                style = TextStyle(
                    color = color,
                    fontSize = size.sp,
                    fontWeight = if (element.prop("bold") == "true") FontWeight.Bold else FontWeight.Medium,
                    fontFamily = LobbyElementPanel.font(element.prop("font", "default")) ?: OneConfigTheme.current.fontFamily,
                    shadow = shadow,
                ),
            )
        }
    }
}
//?}
