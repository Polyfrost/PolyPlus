package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
import kotlin.math.roundToInt

/** The editor's Properties: exact position, size, scale, opacity, group, and what a piece shows or does. */
internal class ElementPropertiesScreen(
    private val state: VanillaTitleMenu.State,
    private val piece: MenuPiece,
) : Screen(Component.literal("Properties: ${piece.label}")) {
    private interface Field {
        val label: String
    }

    private class Text(override val label: String, val initial: String, val apply: (String) -> Unit) : Field {
        var value = initial
    }

    private class Choice(override val label: String, val options: List<Pair<String, String>>, initial: String, val apply: (String) -> Unit) : Field {
        var value = options.firstOrNull { it.first == initial }?.first ?: options.firstOrNull()?.first.orEmpty()
        fun shown() = options.firstOrNull { it.first == value }?.second ?: value
    }

    private class Action(override val label: String, val run: () -> Unit) : Field

    private val element = MenuLayoutStore.element(MenuLayoutStore.VANILLA, piece.id)
    private var x = piece.left
    private var y = piece.top
    private var w = piece.widget.width
    private var h = piece.widget.height
    private var scale = piece.scale
    private var opacity = element?.opacity ?: 1f
    private var visible = piece.shown
    private var group = piece.group.orEmpty()
    private val props = LinkedHashMap(element?.props ?: emptyMap<String, String>())

    private val fields: List<Field> = buildList {
        add(Text("X", x.toString()) { v -> v.trim().toIntOrNull()?.let { x = it } })
        add(Text("Y", y.toString()) { v -> v.trim().toIntOrNull()?.let { y = it } })
        add(Text("Width", w.toString()) { v -> v.trim().toIntOrNull()?.let { w = it.coerceIn(2, 4000) } })
        add(Text("Height", h.toString()) { v -> v.trim().toIntOrNull()?.let { h = it.coerceIn(2, 4000) } })
        add(Text("Scale", "%.2f".format(scale)) { v -> v.trim().replace(',', '.').toFloatOrNull()?.let { scale = it.coerceIn(0.25f, 4f) } })
        add(Text("Opacity %", (opacity * 100).roundToInt().toString()) { v -> v.trim().toFloatOrNull()?.let { opacity = (it / 100f).coerceIn(0f, 1f) } })
        add(Choice("Visible", listOf("true" to "Shown", "false" to "Hidden"), visible.toString()) { visible = it == "true" })
        add(Text("Group", group) { v -> group = v.trim() })
        when (piece.type) {
            "text" -> {
                add(Text("Text", props["text"].orEmpty()) { props["text"] = it })
                addTextStyle()
            }
            "clock", "fps" -> addTextStyle()
            "image" -> {
                val files = MenuImages.files()
                add(Choice("Image", listOf("" to "(none)") + files.map { it to it }, props["file"].orEmpty()) { props["file"] = it })
                add(Action("Open images folder") { MenuImages.openFolder() })
                add(Action("Reload images") {
                    MenuImages.reload()
                    rebuildWidgets()
                })
            }
            "button" -> {
                add(Text("Label", props["label"].orEmpty()) { props["label"] = it })
                add(Choice("Action", CustomAction.actions, props["action"] ?: "url") { props["action"] = it })
                add(Text("Server / URL", if (props["action"] == "screen") "" else props["value"].orEmpty()) { v -> if (props["action"] != "screen") props["value"] = v.trim() })
                add(Choice("Screen", CustomAction.screens, props["value"] ?: "options") { v -> if (props["action"] == "screen") props["value"] = v })
            }
            "panel" -> {
                add(Text("Color (hex)", props["color"] ?: "000000") { props["color"] = it.trim().removePrefix("#") })
                add(Text("Corner radius", props["radius"] ?: "4") { v -> v.trim().toIntOrNull()?.let { props["radius"] = it.coerceIn(0, 64).toString() } })
            }
        }
    }

    private fun MutableList<Field>.addTextStyle() {
        add(Text("Color (hex)", props["color"] ?: "FFFFFF") { props["color"] = it.trim().removePrefix("#") })
        add(Choice("Shadow", listOf("true" to "On", "false" to "Off"), props["shadow"] ?: "true") { props["shadow"] = it })
        add(Choice("Align", listOf("left" to "Left", "center" to "Center", "right" to "Right"), props["align"] ?: "left") { props["align"] = it })
    }

    override fun init() {
        val access = this as ScreenAccessor
        val rowHeight = 22
        val perColumn = ((height - 70) / rowHeight).coerceAtLeast(4)
        val columns = (fields.size + perColumn - 1) / perColumn
        val columnWidth = 230
        val left = width / 2 - columns * columnWidth / 2
        val top = 30
        val heading = Component.literal("Properties: ${piece.label}")
        access.polyplusAddRenderableWidget(StringWidget(width / 2 - font.width(heading) / 2, 12, font.width(heading), 9, heading, font))
        fields.forEachIndexed { index, field ->
            val cx = left + (index / perColumn) * columnWidth
            val cy = top + (index % perColumn) * rowHeight
            when (field) {
                is Text -> {
                    access.polyplusAddRenderableWidget(StringWidget(cx, cy + 6, 80, 9, Component.literal(field.label), font))
                    val box = EditBox(font, cx + 84, cy, 140, 18, Component.literal(field.label))
                    box.setMaxLength(256)
                    box.value = field.value
                    box.setResponder { field.value = it }
                    access.polyplusAddRenderableWidget(box)
                }
                is Choice -> {
                    access.polyplusAddRenderableWidget(StringWidget(cx, cy + 6, 80, 9, Component.literal(field.label), font))
                    lateinit var button: Button
                    button = Button.builder(Component.literal(field.shown())) {
                        val index2 = field.options.indexOfFirst { it.first == field.value }
                        field.value = field.options[(index2 + 1) % field.options.size].first
                        button.message = Component.literal(field.shown())
                    }.bounds(cx + 84, cy, 140, 18).build()
                    access.polyplusAddRenderableWidget(button)
                }
                is Action -> access.polyplusAddRenderableWidget(Button.builder(Component.literal(field.label)) { field.run() }.bounds(cx, cy, 224, 18).build())
            }
        }
        val bottom = height - 28
        access.polyplusAddRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { done() }.bounds(width / 2 - 104, bottom, 100, 20).build())
        access.polyplusAddRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }.bounds(width / 2 + 4, bottom, 100, 20).build())
    }

    private fun done() {
        MenuLayoutStore.checkpoint(MenuLayoutStore.VANILLA)
        for (field in fields) when (field) {
            is Text -> field.apply(field.value)
            is Choice -> field.apply(field.value)
        }
        // the action decides which of the two value fields counts, so they're applied again in that order
        if (piece.type == "button") {
            fields.filterIsInstance<Choice>().firstOrNull { it.label == "Action" }?.let { props["action"] = it.value }
            if (props["action"] == "screen") {
                fields.filterIsInstance<Choice>().firstOrNull { it.label == "Screen" }?.let { props["value"] = it.value }
            } else {
                fields.filterIsInstance<Text>().firstOrNull { it.label == "Server / URL" }?.let { props["value"] = it.value.trim() }
            }
        }
        val stored = MenuLayoutStore.edit(MenuLayoutStore.VANILLA, piece.id)
        stored.scale = scale
        stored.opacity = opacity
        stored.visible = visible
        if (group != piece.group.orEmpty()) stored.group = group
        if (!piece.builtin) {
            stored.type = piece.type
            stored.props.clear()
            stored.props.putAll(props)
        }
        VanillaTitleMenu.store(state, piece, x, y, w, h)
        MenuLayoutStore.save()
        onClose()
    }

    override fun onClose() = ClientPlatform.setScreen(state.screen)
}
//?}
