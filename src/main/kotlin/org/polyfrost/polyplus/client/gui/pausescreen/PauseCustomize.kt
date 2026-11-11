@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.a11y
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ContextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.onClick
import org.polyfrost.oneconfig.api.ui.v1.components.rememberInteractionSource
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.gui.SocialSwitch
import org.polyfrost.polyplus.client.gui.clickableTextWithSound
import org.polyfrost.polyplus.client.gui.themedIcon
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

private val PANEL_WIDTH = 290.dp
private val PICKER_WIDTH = 170.dp
private val PICKER_HEIGHT = 32.dp
private val PICKER_GAP = 6.dp
private val SWITCH_WIDTH = 42.dp
private val SWITCH_HEIGHT = 21.dp
private val ROW_HEIGHT = 38.dp
private val ROW_PADDING = 10.dp
private val NESTED_INDENT = 14.dp
private const val DISABLED_ALPHA = 0.5f
private const val STYLE_OPTION = "pauseScreenStyle"
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
    IconButton(themedIcon("paintbrush"), if (screen.customizeOpen) null else "Customize", modifier, screen::toggleCustomize)
}

@Composable
internal fun BoxScope.CustomizeDismiss(screen: OneClientPauseScreen) {
    Box(Modifier.matchParentSize().clickableTextWithSound(screen::closeCustomize))
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
    Column(
        modifier
            .blockPointer()
            .width(PANEL_WIDTH)
            .heightIn(max = popupMaxHeight())
            .pauseGlass(16.dp)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = ROW_PADDING), verticalAlignment = Alignment.CenterVertically) {
            Text("Customize", Modifier.weight(1f), theme.textColor, 15.sp, FontWeight.Medium)
            IconButton(themedIcon("close"), null, Modifier, close)
        }
        sections.forEach { section ->
            SectionTitle(section.title)
            section.options.forEach { prop ->
                ConfigOptionRow(prop)
                if (prop.getID() == STYLE_OPTION && chosen == PauseScreenStyle.VANILLA) {
                    Text(
                        if (screen.fromVanilla) "Close to go back to the vanilla menu." else "Shows the next time you pause.",
                        Modifier.padding(horizontal = ROW_PADDING).padding(bottom = 4.dp),
                        theme.textColorSecondary,
                        12.sp,
                    )
                }
            }
            if (section.options.any { PauseWidgetRegistry.builtInToggledBy(it.getID()) != null }) {
                optional.forEach { widget -> WidgetRows(screen, widget, widget in widgets.added) }
            }
        }
        TextButton("All settings", Modifier.align(Alignment.End).padding(top = 6.dp), ButtonStyle.Secondary) {
            screen.customizeOpen = false
            screen.openSettings()
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
    Text(
        title.uppercase(),
        Modifier.padding(start = ROW_PADDING, top = 8.dp, bottom = 2.dp),
        OneConfigTheme.current.textColorSecondary,
        10.sp,
        FontWeight.Medium,
    )
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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .padding(start = ROW_PADDING + indent, end = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TooltipBox(tooltip, Modifier.weight(1f)) {
            Text(title, Modifier, theme.textColor, 13.sp, null, BaselineShift.None, FontWeight.Medium, TextAlign.Start, 1, TextOverflow.Ellipsis)
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
        else -> Text(rememberValue(prop).value?.toString().orEmpty(), Modifier, OneConfigTheme.current.textColorSecondary, 12.sp)
    }
}

@Composable
private fun OptionSwitch(prop: Property<*>, title: String, enabled: Boolean) {
    val theme = OneConfigTheme.current
    val value = rememberValue(prop)
    val checked = value.value == true
    val source = rememberInteractionSource()
    Row(
        Modifier
            .a11y(title, A11yRole.Switch, null, checked, theme.circleShape, true)
            .onClick(source, enabled) { prop.assign(!checked, value) }
            .pointerHoverIcon(PointerIcon.Hand),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (checked) "On" else "Off", Modifier, theme.textColorSecondary, 12.sp)
        SocialSwitch(checked, SWITCH_WIDTH, SWITCH_HEIGHT, theme.textColorSecondary.copy(alpha = DISABLED_ALPHA))
    }
}

@Composable
private fun ChoicePicker(prop: Property<*>, title: String, choices: List<Choice>, enabled: Boolean) {
    val theme = OneConfigTheme.current
    val accent = OneConfigTheme.accent
    val value = rememberValue(prop)
    var expanded by remember { mutableStateOf(false) }
    val source = rememberInteractionSource()
    val hovered by source.collectIsHoveredAsState()
    val label = choices.firstOrNull { it.value == value.value }?.label ?: value.value?.toString().orEmpty()
    val border by animateColorAsState(
        when {
            expanded -> accent
            hovered -> theme.textColorSecondary
            else -> theme.borderColor
        },
    )
    val content by animateColorAsState(if (hovered || expanded) theme.textColor else theme.textColorSecondary)
    val chevron by animateFloatAsState(if (expanded) 0f else 180f)
    val shape = theme.sideBarNavigationEntryShape
    val drop = with(LocalDensity.current) { (PICKER_HEIGHT + PICKER_GAP).roundToPx() }
    Box {
        Row(
            Modifier
                .width(PICKER_WIDTH)
                .height(PICKER_HEIGHT)
                .clip(shape)
                .background(theme.componentBackground, shape)
                .border(1.dp, border, shape)
                .hoverable(source, enabled)
                .a11y("$title, $label", A11yRole.Option, null, null, shape, true)
                .onClick(source, enabled) { expanded = !expanded }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, Modifier.weight(1f), content, 13.sp, null, BaselineShift.None, FontWeight.Normal, TextAlign.Start, 1, TextOverflow.Ellipsis)
            Icon(themedIcon("up"), content, Modifier.size(14.dp).graphicsLayer { rotationZ = chevron })
        }
        ContextMenu(expanded, IntOffset(0, drop), { expanded = false }) {
            choices.forEach { choice ->
                MenuItem(choice.label, null, null, MenuItemSize.Compact, false, choice.value == value.value, true, null, Modifier, null) {
                    expanded = false
                    prop.assign(choice.value, value)
                }
            }
        }
    }
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
