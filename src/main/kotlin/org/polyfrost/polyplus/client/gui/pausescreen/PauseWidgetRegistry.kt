@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.api.pause.PauseWidgetContext
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

private const val AVAILABILITY_MILLIS = 2000L
private const val HIDE_AFTER_FAILURES = 3
private const val SEPARATOR = ","
private val WIDGET_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
private val LOGGER = LoggerFactory.getLogger("${PolyPlusConstants.ID}/pause-widgets")

internal data class PauseWidgetsView(
    val builtIns: List<PauseWidget>,
    val added: List<PauseWidget>,
    val addable: List<PauseWidget>,
    val panels: List<PauseWidget> = emptyList(),
) {
    val empty: Boolean get() = builtIns.isEmpty() && added.isEmpty() && addable.isEmpty()
}

internal class PauseWidgetsHandle(val view: PauseWidgetsView, val refresh: () -> Unit)

internal interface PauseWidgetPresence {
    fun shown()

    fun hidden()
}

internal class RegisteredPauseWidget(
    val delegate: PauseWidget,
    override val id: String,
    override val title: String,
    override val icon: String,
) : PauseWidget {
    val fullBleed: Boolean get() = delegate is FullBleedPanel

    override fun isAvailable(): Boolean = runCatching { delegate.isAvailable() }.getOrDefault(false)

    override val supportsPanel: Boolean get() = runCatching { delegate.supportsPanel }.getOrDefault(false)

    @Composable
    override fun Bar(context: PauseWidgetContext) = delegate.Bar(context)

    @Composable
    override fun Card(context: PauseWidgetContext) = delegate.Card(context)

    @Composable
    override fun Panel(context: PauseWidgetContext) = delegate.Panel(context)
}

private class IdList(private val read: () -> String) {
    @Volatile
    private var cached: Pair<String, List<String>>? = null

    fun get(): List<String> {
        val raw = read()
        cached?.let { if (it.first == raw) return it.second }
        val ids = raw.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        cached = raw to ids
        return ids
    }
}

internal object PauseWidgetGuard {
    private var composing: String? = null
    private var session: WeakReference<Screen>? = null
    private val sessionFailed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val hidden: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failures = ConcurrentHashMap<String, Int>()

    fun blocked(id: String): Boolean = id in hidden || id in sessionFailed

    fun begin(screen: Screen?) {
        if (screen == null || session?.get() === screen) return
        session = WeakReference(screen)
        sessionFailed.clear()
    }

    fun settle() {
        val stuck = composing ?: return
        composing = null
        fail(stuck, null)
    }

    fun enter(id: String) {
        settle()
        composing = id
    }

    fun exit(id: String) {
        if (composing == id) composing = null
    }

    fun forget(id: String) {
        sessionFailed.remove(id)
        hidden.remove(id)
        failures.remove(id)
    }

    inline fun <T> watch(id: String, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        fail(id, t)
        throw t
    }

    fun fail(id: String, cause: Throwable?) {
        if (!sessionFailed.add(id)) return
        val count = failures.merge(id, 1) { a, b -> a + b } ?: 1
        if (count == 1) {
            if (cause != null) {
                LOGGER.error("Pause widget {} failed and is hidden until the pause menu reopens", id, cause)
            } else {
                LOGGER.error("Pause widget {} failed while composing and is hidden until the pause menu reopens", id)
            }
        }
        if (count >= HIDE_AFTER_FAILURES && hidden.add(id)) {
            LOGGER.error("Pause widget {} failed {} times and is hidden until the game restarts", id, count)
        }
        PauseWidgetRegistry.invalidate()
    }
}

@Composable
internal fun WidgetGuard(id: String, content: @Composable () -> Unit) {
    PauseWidgetGuard.settle()
    if (PauseWidgetGuard.blocked(id)) return
    PauseWidgetGuard.enter(id)
    content()
    PauseWidgetGuard.exit(id)
}

internal fun Modifier.widgetGuard(id: String): Modifier = this
    .layout { measurable, constraints ->
        val placeable = PauseWidgetGuard.watch(id) { measurable.measure(constraints) }
        layout(placeable.width, placeable.height) {
            PauseWidgetGuard.watch(id) { placeable.place(0, 0) }
        }
    }
    .drawWithContent { PauseWidgetGuard.watch(id) { drawContent() } }

internal object PauseWidgetRegistry {
    private val lock = Any()
    private val optional = LinkedHashMap<String, RegisteredPauseWidget>()
    private val revisionState = mutableIntStateOf(0)
    private val savedIds = IdList { PolyPlusPauseConfig.pauseWidgets }
    private val savedPanelIds = IdList { PolyPlusPauseConfig.pauseWidgetPanels }

    @Volatile
    private var snapshot: List<RegisteredPauseWidget> = emptyList()

    private val builtIns: List<PauseWidget> = listOf(RecordingWidget, PerformanceWidget, SceneWidget)

    val revision: Int get() = revisionState.intValue

    fun invalidate() {
        revisionState.intValue++
    }

    fun isBuiltIn(id: String): Boolean = builtIns.any { it.id == id }

    fun register(widget: PauseWidget): Boolean {
        val type = widget.javaClass.name
        val id = runCatching { widget.id }.getOrElse {
            LOGGER.warn("Rejected pause widget {}: reading its id threw", type, it)
            return false
        }
        if (!WIDGET_ID.matches(id)) {
            LOGGER.warn("Rejected pause widget {} ({}): the id must be a lowercase namespace:path", id, type)
            return false
        }
        if (isBuiltIn(id)) {
            LOGGER.warn("Rejected pause widget {} ({}): the id belongs to a built-in widget", id, type)
            return false
        }
        val title = runCatching { widget.title }.getOrNull()?.takeIf { it.isNotBlank() } ?: id
        val icon = runCatching { widget.icon }.getOrNull().orEmpty()
        synchronized(lock) {
            val existing = optional[id]
            if (existing != null) {
                LOGGER.warn("Rejected pause widget {} ({}): the id is already registered by {}", id, type, existing.delegate.javaClass.name)
                return false
            }
            optional[id] = RegisteredPauseWidget(widget, id, title, icon)
            snapshot = optional.values.toList()
        }
        PauseWidgetGuard.forget(id)
        invalidate()
        return true
    }

    fun unregister(id: String): Boolean {
        val removed = synchronized(lock) {
            val removed = optional.remove(id) != null
            if (removed) snapshot = optional.values.toList()
            removed
        }
        if (removed) invalidate()
        return removed
    }

    fun find(id: String): PauseWidget? = builtIns.firstOrNull { it.id == id } ?: snapshot.firstOrNull { it.id == id }?.delegate

    fun all(): List<PauseWidget> = builtIns + snapshot.map { it.delegate }

    fun isAdded(id: String): Boolean = isBuiltIn(id) || id in saved()

    fun view(): PauseWidgetsView {
        val saved = saved()
        val registered = snapshot.filter { !PauseWidgetGuard.blocked(it.id) }
        val byId = registered.associateBy { it.id }
        val added = saved.mapNotNull { byId[it] }.filter { it.isAvailable() }
        val addedById = added.associateBy { it.id }
        return PauseWidgetsView(
            builtIns.filter { it.available() },
            added,
            registered.filter { it.id !in saved && it.isAvailable() },
            savedPanels().mapNotNull { addedById[it] }.filter { it.panelCapable() },
        )
    }

    fun isPanel(id: String): Boolean = id in savedPanels()

    fun setPanel(id: String, panel: Boolean) {
        val panels = savedPanels()
        if (panel && id !in panels) storePanels(panels + id)
        if (!panel && id in panels) storePanels(panels - id)
    }

    fun reorderPanels(visible: List<String>) {
        val moved = visible.toSet()
        val next = visible.iterator()
        storePanels(savedPanels().map { if (it in moved && next.hasNext()) next.next() else it })
    }

    fun add(id: String) {
        val saved = saved()
        if (id !in saved) store(saved + id)
    }

    fun remove(id: String) {
        store(saved() - id)
        val panels = savedPanels()
        if (id in panels) storePanels(panels - id)
    }

    fun reorder(visible: List<String>) {
        val moved = visible.toSet()
        val next = visible.iterator()
        store(saved().map { if (it in moved && next.hasNext()) next.next() else it })
    }

    private fun saved(): List<String> = savedIds.get()

    private fun store(ids: List<String>) {
        PolyPlusPauseConfig.pauseWidgets = ids.distinct().joinToString(SEPARATOR)
        runCatching { PolyPlusPauseConfig.save() }
        invalidate()
    }

    private fun savedPanels(): List<String> = savedPanelIds.get()

    private fun storePanels(ids: List<String>) {
        PolyPlusPauseConfig.pauseWidgetPanels = ids.distinct().joinToString(SEPARATOR)
        runCatching { PolyPlusPauseConfig.save() }
        invalidate()
    }

    fun PauseWidget.panelCapable(): Boolean = runCatching { supportsPanel }.getOrDefault(false)

    private fun PauseWidget.available(): Boolean = runCatching { isAvailable() }.getOrDefault(false)
}

private data class PolledView(val revision: Int, val view: PauseWidgetsView)

@Composable
internal fun rememberPauseWidgets(): PauseWidgetsHandle {
    PauseWidgetGuard.begin(ClientPlatform.currentScreen())
    PauseWidgetGuard.settle()
    val revision = PauseWidgetRegistry.revision
    val fresh = remember(revision) { PauseWidgetRegistry.view() }
    var polled by remember { mutableStateOf<PolledView?>(null) }
    LaunchedEffect(revision) {
        while (true) {
            delay(AVAILABILITY_MILLIS)
            polled = PolledView(revision, PauseWidgetRegistry.view())
        }
    }
    val view = polled?.takeIf { it.revision == revision }?.view ?: fresh
    WidgetPresence(view.added)
    return PauseWidgetsHandle(view, PauseWidgetRegistry::invalidate)
}

@Composable
private fun WidgetPresence(widgets: List<PauseWidget>) {
    widgets.forEach { widget ->
        val presence = (widget as? RegisteredPauseWidget)?.delegate as? PauseWidgetPresence ?: return@forEach
        key(widget.id) {
            DisposableEffect(presence) {
                runCatching { presence.shown() }
                onDispose { runCatching { presence.hidden() } }
            }
        }
    }
}

internal class PauseScreenWidgetContext(val screen: OneClientPauseScreen) : PauseWidgetContext {
    override val pauseScreen: Screen get() = screen

    override val reducedMotion: Boolean get() = Motion.reduced

    override fun openScreen(factory: Function<Screen, Screen?>) {
        val next = try {
            factory.apply(screen)
        } catch (t: Throwable) {
            LOGGER.error("A pause widget failed to build the screen it asked to open", t)
            return
        }
        ClientPlatform.setScreen(next)
    }

    override fun closePauseScreen() = screen.closeIfCurrent()
}

private val PauseWidgetContext.pause: OneClientPauseScreen get() = (this as PauseScreenWidgetContext).screen

internal object RecordingWidget : PauseWidget {
    override val id = "polyplus:recording"
    override val title = "Recording"
    override val icon = asset("video-recorder")

    override fun isAvailable(): Boolean = recordingShown()

    @Composable
    override fun Bar(context: PauseWidgetContext) = RecordingBarTool(context.pause, glass = false)

    @Composable
    override fun Card(context: PauseWidgetContext) = RecordingCardTool(context.pause)
}

internal object PerformanceWidget : PauseWidget {
    override val id = "polyplus:performance"
    override val title = "Performance"
    override val icon = asset("loading-02")

    override fun isAvailable(): Boolean = performanceShown()

    @Composable
    override fun Bar(context: PauseWidgetContext) = PerformanceBarTool(context.pause, glass = false)

    @Composable
    override fun Card(context: PauseWidgetContext) = PerformanceCardTool(context.pause)
}

internal object SceneWidget : PauseWidget {
    override val id = "polyplus:scene"
    override val title = "Scene check"
    override val icon = asset("image-01")

    override fun isAvailable(): Boolean = sceneShown()

    @Composable
    override fun Bar(context: PauseWidgetContext) = SceneBarTool(glass = false)

    @Composable
    override fun Card(context: PauseWidgetContext) = SceneCardTool()
}
