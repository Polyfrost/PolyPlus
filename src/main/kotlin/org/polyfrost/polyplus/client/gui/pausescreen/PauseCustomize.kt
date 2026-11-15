@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.Switch
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.popupMaxHeight
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

private val PANEL_WIDTH = 290.dp
private val PICKER_WIDTH = 170.dp
private val ROW_HEIGHT = 38.dp
private val ROW_PADDING = 10.dp
private val NESTED_INDENT = 14.dp
private const val STYLE_OPTION = "pauseScreenStyle"
private const val PREVIEW_OPTION = "flatPausePlayerPreview"
private const val UNAVAILABLE = "Not available right now."
//? if >= 26.3 {
private val PANEL_EXCLUDED = setOf("replacePauseLanButton")
//?} else {
/*private val PANEL_EXCLUDED = emptySet<String>()
*///?}

private class OptionSection(val title: String, val options: List<Property<*>>)

private class Choice(val value: Any, val label: String)

private fun choicesOf(prop: Property<*>): List<Choice>? {
    val type = prop.type ?: return null
    val enumType = if (type.isEnum) type else type.superclass?.takeIf { it.isEnum } ?: return null
    val labels = when (val raw = prop.getMetadata<Any>("options")) {
        is Array<*> -> raw.toList()
        is Iterable<*> -> raw.toList()
        else -> emptyList()
    }
    return enumType.enumConstants.orEmpty().filterNotNull().mapIndexed { index, value ->
        Choice(value, labels.getOrNull(index)?.toString()?.takeIf { it.isNotBlank() } ?: (value as Enum<*>).name)
    }
}

private fun isSwitch(prop: Property<*>): Boolean =
    prop.type == Boolean::class.javaPrimitiveType || prop.type == Boolean::class.javaObjectType

private fun pauseOptionSections(): List<OptionSection> {
    val sections = LinkedHashMap<String, MutableList<Property<*>>>()
    PolyPlusPauseConfig.getTree()?.onAllProps { _, prop ->
        if (prop.getMetadata<Any>("hidden") == true || prop.getMetadata<Any>("visualizer") == null || prop.getID() in PANEL_EXCLUDED) return@onAllProps
        sections.getOrPut(prop.getMetadata<String>("subcategory").orEmpty()) { ArrayList() } += prop
    }
    return sections.map { (title, options) -> OptionSection(title, options) }
}

private fun switchOption(id: String, title: String, get: () -> Boolean, set: (Boolean) -> Unit): Property<Boolean> =
    Properties.functional(Supplier(get), Consumer(set), id, title, null, Boolean::class.java)

@Composable
internal fun CustomizeButton(screen: OneClientPauseScreen, modifier: Modifier = Modifier) {
    IconButton(
        icon = "paintbrush",
        contentDescription = "Customize",
        onClick = screen::toggleCustomize,
        modifier = modifier,
        tooltip = if (screen.customizeOpen) null else "Customize",
    )
}

@Composable
internal fun BoxScope.CustomizeDismiss(screen: OneClientPauseScreen) {
    Box(Modifier.matchParentSize().notFocusable().oneConfigClickable(null, screen::closeCustomize, indication = false, pointerIcon = null))
}

@Composable
internal fun PauseCustomizePanel(screen: OneClientPauseScreen, modifier: Modifier = Modifier) {
    val close = screen::closeCustomize
    EscapeCloses(close)
    val theme = OneConfigTheme.current
    val sections = remember { pauseOptionSections() }
    val initialStyle = remember { PolyPlusPauseConfig.pauseScreenStyle }
    var pickedStyle by remember { mutableStateOf<PauseScreenStyle?>(null) }
    var menuChanges by remember { mutableStateOf(0) }
    LiveOptions(sections) { prop, value ->
        runCatching { PolyPlusPauseConfig.save() }
        when {
            prop.getID() == STYLE_OPTION -> if (value is PauseScreenStyle) pickedStyle = value
            prop.getID() == PREVIEW_OPTION -> screen.playerPreview = value == true
            PauseWidgetRegistry.builtInToggledBy(prop.getID()) != null -> PauseWidgetRegistry.invalidate()
            else -> menuChanges++
        }
    }
    LaunchedEffect(pickedStyle) { pickedStyle?.let(screen::restyle) }
    LaunchedEffect(menuChanges) { if (menuChanges > 0) screen.resyncMenu() }
    val chosen = pickedStyle ?: initialStyle
    val widgetRevision = PauseWidgetRegistry.revision
    val widgets = remember(widgetRevision) { PauseWidgetRegistry.view() }
    val optional = widgets.added + widgets.addable
    val scroll = rememberScrollState()
    ScrollContainer(scroll, modifier.blockPointer().width(PANEL_WIDTH).heightIn(max = popupMaxHeight()).pauseGlass(16.dp), scrollbar = false) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = ROW_PADDING), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Customize", modifier = Modifier.weight(1f), color = theme.textColor, style = theme.typography.heading)
                IconButton(icon = "close", contentDescription = "Close", onClick = close, tooltip = null)
            }
            sections.forEach { section ->
                SectionTitle(section.title)
                section.options.forEach { prop ->
                    ConfigOptionRow(prop)
                    if (prop.getID() == STYLE_OPTION && chosen == PauseScreenStyle.VANILLA) {
                        Text(
                            text = if (screen.fromVanilla) "Close to go back to the vanilla menu." else "Shows the next time you pause.",
                            modifier = Modifier.padding(horizontal = ROW_PADDING).padding(bottom = 4.dp),
                            color = theme.textColorSecondary,
                            style = theme.typography.small,
                        )
                    }
                }
                if (section.options.any { PauseWidgetRegistry.builtInToggledBy(it.getID()) != null }) {
                    optional.forEach { widget -> WidgetRows(screen, widget, widget in widgets.added) }
                }
            }
            TextButton(
                text = "All settings",
                onClick = {
                    screen.customizeOpen = false
                    screen.openSettings()
                },
                modifier = Modifier.align(Alignment.End).padding(top = 6.dp),
                style = ControlStyle.Secondary,
            )
        }
    }
}

@Composable
private fun LiveOptions(sections: List<OptionSection>, onChange: (Property<*>, Any?) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    DisposableEffect(sections) {
        val hooks = sections.flatMap { it.options }.map { prop ->
            prop.revaluateDisplay()
            @Suppress("UNCHECKED_CAST")
            val typed = prop as Property<Any?>
            val hook = Predicate<Any?> { value ->
                latest(prop, value)
                false
            }
            typed.addCallback(hook)
            typed to hook
        }
        onDispose { hooks.forEach { (prop, hook) -> prop.removeCallback(hook) } }
    }
}

@Composable
private fun rememberDisplay(prop: Property<*>): Property.Display {
    var display by remember(prop) { mutableStateOf(prop.display) }
    DisposableEffect(prop) {
        val listener = Consumer<Property.Display> { display = it }
        prop.addDisplayListener(listener)
        display = prop.display
        onDispose { prop.removeDisplayListener(listener) }
    }
    return display
}

@Composable
private fun rememberValue(prop: Property<*>): MutableState<Any?> {
    val value = remember(prop) { mutableStateOf(prop.get()) }
    DisposableEffect(prop) {
        @Suppress("UNCHECKED_CAST")
        val typed = prop as Property<Any?>
        val hook = Predicate<Any?> {
            value.value = it
            false
        }
        typed.addCallback(hook)
        value.value = prop.get()
        onDispose { typed.removeCallback(hook) }
    }
    return value
}

private fun Property<*>.assign(value: Any?, state: MutableState<Any?>) {
    @Suppress("UNCHECKED_CAST")
    (this as Property<Any?>).set(value)
    state.value = get()
}

@Composable
private fun SectionTitle(title: String) {
    if (title.isEmpty()) return
    SectionHeader(title, Modifier.padding(start = ROW_PADDING, top = 4.dp))
}

@Composable
private fun ConfigOptionRow(prop: Property<*>) {
    val display = rememberDisplay(prop)
    if (display == Property.Display.HIDDEN) return
    val widget = PauseWidgetRegistry.builtInToggledBy(prop.getID())
    val ready = widget == null || runCatching { widget.isAvailable() }.getOrDefault(false)
    val description = prop.description?.toString()?.takeIf { it.isNotBlank() }
    OptionLine(
        prop,
        prop.getTitle()?.toString() ?: prop.getID(),
        if (ready) description else UNAVAILABLE,
        if (prop.getMetadata<Any>("dependencyNames") != null) NESTED_INDENT else 0.dp,
        display != Property.Display.DISABLED,
    )
}

@Composable
private fun OptionLine(prop: Property<*>, title: String, tooltip: String?, indent: Dp, enabled: Boolean = true) {
    val theme = OneConfigTheme.current
    val disabledAlpha = theme.disabledAlpha
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .padding(start = ROW_PADDING + indent, end = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TooltipBox(tooltip, Modifier.weight(1f).graphicsLayer { alpha = if (enabled) 1f else disabledAlpha }) {
            Text(
                text = title,
                color = theme.textColor,
                style = theme.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
        OptionControl(prop, title, enabled)
    }
}

@Composable
private fun OptionControl(prop: Property<*>, title: String, enabled: Boolean) {
    val choices = remember(prop) { choicesOf(prop) }
    when {
        choices != null -> ChoicePicker(prop, title, choices, enabled)
        isSwitch(prop) -> OptionSwitch(prop, title, enabled)
        else -> {
            val disabledAlpha = OneConfigTheme.current.disabledAlpha
            Text(
                text = rememberValue(prop).value?.toString().orEmpty(),
                modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else disabledAlpha },
                color = OneConfigTheme.current.textColorSecondary,
                style = OneConfigTheme.current.typography.small,
            )
        }
    }
}

@Composable
private fun OptionSwitch(prop: Property<*>, title: String, enabled: Boolean) {
    val value = rememberValue(prop)
    Switch(value.value == true, { prop.assign(it, value) }, enabled = enabled, label = title, stateLabel = true)
}

@Composable
private fun ChoicePicker(prop: Property<*>, title: String, choices: List<Choice>, enabled: Boolean) {
    val value = rememberValue(prop)
    Select(
        selected = choices.firstOrNull { it.value == value.value },
        options = choices,
        onSelect = { choice -> choice?.let { prop.assign(it.value, value) } },
        modifier = Modifier.width(PICKER_WIDTH),
        label = { it?.label.orEmpty() },
        enabled = enabled,
        placeholder = value.value?.toString(),
        contentDescription = title,
    )
}

@Composable
private fun WidgetRows(screen: OneClientPauseScreen, widget: PauseWidget, shown: Boolean) {
    val added = remember(widget.id) {
        switchOption(widget.id, widget.title, { PauseWidgetRegistry.isAdded(widget.id) }) {
            if (it) PauseWidgetRegistry.add(widget.id) else PauseWidgetRegistry.remove(widget.id)
        }
    }
    OptionLine(added, widget.title, null, 0.dp)
    val panelCapable = runCatching { widget.supportsPanel }.getOrDefault(false)
    if (shown && screen.stepBack && panelCapable) {
        val panel = remember(widget.id) {
            switchOption("${widget.id}.panel", "Show as panel", { PauseWidgetRegistry.isPanel(widget.id) }) {
                PauseWidgetRegistry.setPanel(widget.id, it)
            }
        }
        OptionLine(panel, "Show as panel", null, NESTED_INDENT)
    }
}
