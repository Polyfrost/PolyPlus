package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.api.pause.PauseWidgetContext
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.function.Function

private const val WIDGETS_TICK_MILLIS = 500L
private const val SEPARATOR = ","
private val WIDGET_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

internal data class PauseWidgetsView(
    val builtIns: List<PauseWidget>,
    val added: List<PauseWidget>,
    val addable: List<PauseWidget>,
    val panels: List<PauseWidget> = emptyList(),
) {
    val empty: Boolean get() = builtIns.isEmpty() && added.isEmpty() && addable.isEmpty()
}

internal class PauseWidgetsHandle(val view: PauseWidgetsView, val refresh: () -> Unit)

internal object PauseWidgetRegistry {
    private val lock = Any()
    private val optional = LinkedHashMap<String, PauseWidget>()

    @Volatile
    private var snapshot: List<PauseWidget> = emptyList()

    private val builtIns: List<PauseWidget> = listOf(RecordingWidget, PerformanceWidget, SceneWidget)

    fun isBuiltIn(id: String): Boolean = builtIns.any { it.id == id }

    fun register(widget: PauseWidget): Boolean {
        val id = runCatching { widget.id }.getOrNull() ?: return false
        if (!WIDGET_ID.matches(id) || isBuiltIn(id)) return false
        synchronized(lock) {
            if (optional.containsKey(id)) return false
            optional[id] = widget
            snapshot = optional.values.toList()
        }
        return true
    }

    fun unregister(id: String): Boolean = synchronized(lock) {
        val removed = optional.remove(id) != null
        if (removed) snapshot = optional.values.toList()
        removed
    }

    fun find(id: String): PauseWidget? = builtIns.firstOrNull { it.id == id } ?: snapshot.firstOrNull { it.id == id }

    fun all(): List<PauseWidget> = builtIns + snapshot

    fun isAdded(id: String): Boolean = isBuiltIn(id) || id in saved()

    fun view(): PauseWidgetsView {
        val saved = saved()
        val registered = snapshot
        val byId = registered.associateBy { it.id }
        val added = saved.mapNotNull { byId[it] }.filter { it.available() }
        val addedById = added.associateBy { it.id }
        return PauseWidgetsView(
            builtIns.filter { it.available() },
            added,
            registered.filter { it.id !in saved && it.available() },
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

    private fun saved(): List<String> =
        PolyPlusPauseConfig.pauseWidgets.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun store(ids: List<String>) {
        PolyPlusPauseConfig.pauseWidgets = ids.distinct().joinToString(SEPARATOR)
        runCatching { PolyPlusPauseConfig.save() }
    }

    private fun savedPanels(): List<String> =
        PolyPlusPauseConfig.pauseWidgetPanels.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun storePanels(ids: List<String>) {
        PolyPlusPauseConfig.pauseWidgetPanels = ids.distinct().joinToString(SEPARATOR)
        runCatching { PolyPlusPauseConfig.save() }
    }

    fun PauseWidget.panelCapable(): Boolean = runCatching { supportsPanel }.getOrDefault(false)

    private fun PauseWidget.available(): Boolean = runCatching { isAvailable() }.getOrDefault(false)
}

@Composable
internal fun rememberPauseWidgets(): PauseWidgetsHandle {
    var view by remember { mutableStateOf(PauseWidgetRegistry.view()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(WIDGETS_TICK_MILLIS)
            view = PauseWidgetRegistry.view()
        }
    }
    return PauseWidgetsHandle(view) { view = PauseWidgetRegistry.view() }
}

internal class PauseScreenWidgetContext(val screen: OneClientPauseScreen) : PauseWidgetContext {
    override val pauseScreen: Screen get() = screen

    override val reducedMotion: Boolean get() = PolyPlusConfig.reducedMotion

    override fun openScreen(factory: Function<Screen, Screen?>) {
        ClientPlatform.setScreen(factory.apply(screen))
    }

    override fun closePauseScreen() {
        if (ClientPlatform.currentScreen() === screen) ClientPlatform.setScreen(null)
    }
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
