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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.components.settings.Option
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.gui.clickableTextWithSound
import org.polyfrost.polyplus.client.gui.themedIcon
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

private val PANEL_WIDTH = 290.dp
private val DROPDOWN_WIDTH = 140.dp
private val ROW_HEIGHT = 38.dp
private val ROW_PADDING = 10.dp
private val NESTED_INDENT = 14.dp
private const val STYLE_OPTION = "pauseScreenStyle"
private const val UNAVAILABLE = "Not available right now."

private class OptionSection(val title: String, val options: List<Property<*>>)

private fun pauseOptionSections(): List<OptionSection> {
    val sections = LinkedHashMap<String, MutableList<Property<*>>>()
    PolyPlusPauseConfig.getTree()?.onAllProps { _, prop ->
        if (prop.getMetadata<Any>("hidden") == true || prop.getMetadata<Any>("visualizer") == null) return@onAllProps
        sections.getOrPut(prop.getMetadata<String>("subcategory").orEmpty()) { ArrayList() } += prop
    }
    return sections.map { (title, options) -> OptionSection(title, options) }
}

private fun switchOption(id: String, title: String, get: () -> Boolean, set: (Boolean) -> Unit): Property<Boolean> =
    Properties.functional(Supplier(get), Consumer(set), id, title, null, Boolean::class.java).apply {
        addMetadata("visualizer", Visualizer.SwitchVisualizer::class.java)
    }

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
        CompositionLocalProvider(LocalOptionWidth provides DROPDOWN_WIDTH) {
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
private fun rememberShown(prop: Property<*>): Boolean {
    var shown by remember(prop) { mutableStateOf(prop.display != Property.Display.HIDDEN) }
    DisposableEffect(prop) {
        val listener = Consumer<Property.Display> { shown = it != Property.Display.HIDDEN }
        prop.addDisplayListener(listener)
        onDispose { prop.removeDisplayListener(listener) }
    }
    return shown
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
    if (!rememberShown(prop)) return
    val widget = PauseWidgetRegistry.builtInToggledBy(prop.getID())
    val ready = widget == null || runCatching { widget.isAvailable() }.getOrDefault(false)
    val description = prop.description?.toString()?.takeIf { it.isNotBlank() }
    OptionLine(
        prop,
        prop.getTitle()?.toString() ?: prop.getID(),
        if (ready) description else UNAVAILABLE,
        if (prop.getMetadata<Any>("dependencyNames") != null) NESTED_INDENT else 0.dp,
    )
}

@Composable
private fun OptionLine(prop: Property<*>, title: String, tooltip: String?, indent: Dp) {
    val theme = OneConfigTheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(start = ROW_PADDING + indent, end = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TooltipBox(tooltip, Modifier.weight(1f)) {
            Text(title, Modifier, theme.textColor, 13.sp, null, BaselineShift.None, FontWeight.Medium, TextAlign.Start, 1, TextOverflow.Ellipsis)
        }
        Option(prop)
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
