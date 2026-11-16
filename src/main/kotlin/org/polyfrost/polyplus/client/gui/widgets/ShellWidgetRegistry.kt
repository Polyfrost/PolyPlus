package org.polyfrost.polyplus.client.gui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.gui.pausescreen.PauseEvergreenMedia
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.Mods
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

private const val AVAILABILITY_MILLIS = 2000L
private const val HIDE_AFTER_FAILURES = 3
private const val SEPARATOR = ","
private val WIDGET_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
private val LOGGER = LoggerFactory.getLogger("${PolyPlusConstants.ID}/widgets")

private class IdList(private val read: () -> String) {
    @Volatile
    private var cached: Pair<String, List<String>>? = null

    fun get(): List<String> {
        val raw = read()
        cached?.let { if (it.first == raw) return it.second }
        val ids = WidgetMigration.parse(raw)
        cached = raw to ids
        return ids
    }
}

object ShellWidgetRegistry {
    private val lock = Any()
    private val extras = LinkedHashMap<String, ShellWidget>()
    private val revisionState = mutableIntStateOf(0)
    private val pauseIds = IdList { PolyPlusPauseConfig.pauseWidgets }
    private val lobbyIds = IdList { PolyPlusLobbyConfig.lobbyWidgets }
    private val pauseOrder = IdList { PolyPlusPauseConfig.pauseWidgetOrder }
    private val lobbyOrder = IdList { PolyPlusLobbyConfig.lobbyWidgetOrder }

    @Volatile
    private var snapshot: List<ShellWidget> = emptyList()

    private val builtIns: List<ShellWidget> by lazy {
        listOf(MediaCardWidget, PerformanceShellWidget, RecordingShellWidget, SceneShellWidget, ClockWidget)
    }

    val revision: Int get() = revisionState.intValue

    fun invalidate() {
        revisionState.intValue++
    }

    fun registerBuiltIns() {
        if (Mods.evergreenHud) PauseEvergreenMedia.keepWarmInWorld(MediaCardWidget.id)
    }

    fun isBuiltIn(id: String): Boolean = builtIns.any { it.id == id }

    fun registered(): List<ShellWidget> = snapshot

    fun find(id: String): ShellWidget? = builtIns.firstOrNull { it.id == id } ?: snapshot.firstOrNull { it.id == id }

    fun register(w: ShellWidget): Boolean {
        val type = w.javaClass.name
        val id = runCatching { w.id }.getOrElse {
            LOGGER.warn("Rejected widget {}: reading its id threw", type, it)
            return false
        }
        if (!WIDGET_ID.matches(id)) {
            LOGGER.warn("Rejected widget {} ({}): the id must be a lowercase namespace:path", id, type)
            return false
        }
        if (isBuiltIn(id)) {
            LOGGER.warn("Rejected widget {} ({}): the id belongs to a built-in widget", id, type)
            return false
        }
        synchronized(lock) {
            val existing = extras[id]
            if (existing != null) {
                LOGGER.warn("Rejected widget {} ({}): the id is already registered by {}", id, type, existing.javaClass.name)
                return false
            }
            extras[id] = w
            snapshot = extras.values.toList()
        }
        ShellWidgetGuard.forget(id)
        invalidate()
        return true
    }

    fun registerLegacy(widget: PauseWidget): Boolean {
        val id = runCatching { widget.id }.getOrNull() ?: return false
        if (isBuiltIn(id)) return false
        val title = runCatching { widget.title }.getOrNull()?.takeIf { it.isNotBlank() } ?: id
        val icon = runCatching { widget.icon }.getOrNull().orEmpty()
        return register(LegacyPauseShellWidget(widget, id, title, icon))
    }

    fun unregister(id: String): Boolean {
        val removed = synchronized(lock) {
            val removed = extras.remove(id) != null
            if (removed) snapshot = extras.values.toList()
            removed
        }
        if (removed) invalidate()
        return removed
    }

    fun ids(s: WidgetSurface): List<String> = when (s) {
        WidgetSurface.PAUSE -> pauseIds.get()
        WidgetSurface.LOBBY -> lobbyIds.get()
    }

    fun contains(s: WidgetSurface, id: String): Boolean = id in ids(s)

    fun order(s: WidgetSurface): List<String> {
        val enabled = ids(s)
        val saved = when (s) {
            WidgetSurface.PAUSE -> pauseOrder.get()
            WidgetSurface.LOBBY -> lobbyOrder.get()
        }.ifEmpty { enabled + WidgetMigration.DEFAULT_ORDER.filter { it !in enabled } }
        return arrange(saved + enabled.filter { it !in saved }, enabled)
    }

    fun arranged(s: WidgetSurface): List<ShellWidget> {
        val available = all(s)
        val byId = available.associateBy { it.id }
        val full = available.fold(order(s)) { acc, w -> place(acc, w.id) }
        return full.mapNotNull { byId[it] }
    }

    fun add(s: WidgetSurface, id: String) {
        val enabled = ids(s)
        if (id in enabled) return
        store(s, place(order(s), id), enabled.toSet() + id)
    }

    fun remove(s: WidgetSurface, id: String) {
        val enabled = ids(s)
        if (id in enabled) store(s, order(s), enabled.toSet() - id)
    }

    fun set(s: WidgetSurface, id: String, shown: Boolean) {
        if (shown) add(s, id) else remove(s, id)
    }

    @OptIn(ExperimentalOneConfigUiApi::class)
    fun reorder(s: WidgetSurface, from: Int, to: Int) {
        ids(s).withItemMoved(from, to)?.let { reorderVisible(s, it) }
    }

    fun reorderVisible(s: WidgetSurface, visible: List<String>) {
        val full = visible.fold(order(s)) { acc, id -> place(acc, id) }
        store(s, arrange(full, visible), ids(s).toSet())
    }

    fun view(s: WidgetSurface): List<ShellWidget> = ids(s)
        .mapNotNull { find(it) }
        .filter { it.on(s) && !ShellWidgetGuard.blocked(it.id) && it.available(s) }

    fun all(s: WidgetSurface): List<ShellWidget> = (builtIns + snapshot).filter { it.on(s) && it.available(s) }

    private fun place(order: List<String>, id: String): List<String> {
        if (id in order) return order
        if (isBuiltIn(id)) return order + id
        val top = order.indexOfFirst { isBuiltIn(it) }.takeIf { it >= 0 } ?: order.size
        return order.take(top) + id + order.drop(top)
    }

    private fun arrange(full: List<String>, visible: List<String>): List<String> {
        val moved = visible.toSet()
        val next = visible.iterator()
        return full.map { if (it in moved && next.hasNext()) next.next() else it }
    }

    private fun store(s: WidgetSurface, order: List<String>, enabled: Set<String>) {
        val full = WidgetMigration.join(order)
        val raw = WidgetMigration.join(order.filter { it in enabled })
        when (s) {
            WidgetSurface.PAUSE -> {
                PolyPlusPauseConfig.pauseWidgetOrder = full
                PolyPlusPauseConfig.pauseWidgets = raw
                runCatching { PolyPlusPauseConfig.save() }
            }
            WidgetSurface.LOBBY -> {
                PolyPlusLobbyConfig.lobbyWidgetOrder = full
                PolyPlusLobbyConfig.lobbyWidgets = raw
                runCatching { PolyPlusLobbyConfig.save() }
            }
        }
        invalidate()
    }

    private fun ShellWidget.on(s: WidgetSurface): Boolean = runCatching { s in surfaces }.getOrDefault(false)

    private fun ShellWidget.available(s: WidgetSurface): Boolean = runCatching { isAvailable(s) }.getOrDefault(false)
}

internal object WidgetMigration {
    const val MEDIA = "polyplus:media"
    const val PERFORMANCE = "polyplus:performance"
    const val RECORDING = "polyplus:recording"
    const val SCENE = "polyplus:scene"
    const val CLOCK = "polyplus:clock"

    val DEFAULT_ORDER = listOf(MEDIA, SCENE, PERFORMANCE, RECORDING, CLOCK)

    fun parse(raw: String): List<String> = raw.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun join(ids: List<String>): String = ids.distinct().joinToString(SEPARATOR)

    fun pause(saved: String, panels: String, recording: Boolean, performance: Boolean, scene: Boolean): String {
        val builtIns = buildList {
            add(MEDIA)
            if (scene) add(SCENE)
            if (performance) add(PERFORMANCE)
            if (recording) add(RECORDING)
        }
        val known = setOf(MEDIA, SCENE, PERFORMANCE, RECORDING)
        return join((parse(saved) + parse(panels)).filter { it !in known } + builtIns)
    }

    fun lobby(saved: String, media: Boolean, spark: Boolean): String {
        val builtIns = buildList {
            if (media) add(MEDIA)
            if (spark) add(PERFORMANCE)
        }
        val known = setOf(MEDIA, PERFORMANCE)
        return join(parse(saved).filter { it !in known } + builtIns)
    }
}

internal object ShellWidgetGuard {
    private var composing: String? = null
    private var session: WeakReference<Any>? = null
    private val sessionFailed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val hidden: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val failures = ConcurrentHashMap<String, Int>()

    fun blocked(id: String): Boolean = id in hidden || id in sessionFailed

    fun begin(owner: Any?) {
        if (owner == null || session?.get() === owner) return
        session = WeakReference(owner)
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
                LOGGER.error("Widget {} failed and is hidden until the menu reopens", id, cause)
            } else {
                LOGGER.error("Widget {} failed while composing and is hidden until the menu reopens", id)
            }
        }
        if (count >= HIDE_AFTER_FAILURES && hidden.add(id)) {
            LOGGER.error("Widget {} failed {} times and is hidden until the game restarts", id, count)
        }
        ShellWidgetRegistry.invalidate()
    }
}

@Composable
internal fun ShellWidgetGuarded(id: String, content: @Composable () -> Unit) {
    ShellWidgetGuard.settle()
    if (ShellWidgetGuard.blocked(id)) return
    ShellWidgetGuard.enter(id)
    content()
    ShellWidgetGuard.exit(id)
}

internal fun Modifier.shellWidgetGuard(id: String): Modifier = this
    .layout { measurable, constraints ->
        val placeable = ShellWidgetGuard.watch(id) { measurable.measure(constraints) }
        layout(placeable.width, placeable.height) {
            ShellWidgetGuard.watch(id) { placeable.place(0, 0) }
        }
    }
    .drawWithContent { ShellWidgetGuard.watch(id) { drawContent() } }

private class PolledWidgets(val revision: Int, val widgets: List<ShellWidget>)

@Composable
internal fun rememberShellWidgets(surface: WidgetSurface): List<ShellWidget> {
    ShellWidgetGuard.begin(ClientPlatform.currentScreen())
    ShellWidgetGuard.settle()
    val revision = ShellWidgetRegistry.revision
    val fresh = remember(surface, revision) { ShellWidgetRegistry.view(surface) }
    var polled by remember(surface) { mutableStateOf<PolledWidgets?>(null) }
    LaunchedEffect(surface, revision) {
        while (true) {
            delay(AVAILABILITY_MILLIS)
            polled = PolledWidgets(revision, ShellWidgetRegistry.view(surface))
        }
    }
    return polled?.takeIf { it.revision == revision }?.widgets ?: fresh
}
