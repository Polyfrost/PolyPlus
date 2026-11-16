@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.UiSound
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.playUiSound
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.RecentServer
import org.polyfrost.polyplus.client.gui.ThemedIcon
import kotlin.math.max
import kotlin.math.roundToInt

internal const val PIN_ICON = "star"
internal const val PINNED_ICON = "star-filled"

private val StripHeight = 32.dp
private val ChipIcon = 22.dp
private val MarkSize = 14.dp
private val ChipGap = 6.dp
private val ChipRadius = 16.dp
private val LiftElevation = 10.dp

private fun pinKey(entry: ServerEntry): String = entry.data.ip.trim().lowercase()

private class ChipReorder(private val scope: CoroutineScope) {
    var base: List<String> = emptyList()
    var commit: (List<String>) -> Unit = {}
    var strip: LayoutCoordinates? = null
    val handles = HashMap<String, LayoutCoordinates>()

    private var override by mutableStateOf<List<String>?>(null)
    var dragging by mutableStateOf<String?>(null)
        private set
    var dragged: String? = null

    private var pointer by mutableFloatStateOf(0f)
    private var startPointer = 0f
    private var startLeft = 0f
    private var waiting = false
    private var total = 0f
    private val lefts = HashMap<String, Float>()
    private val widths = HashMap<String, Float>()
    private val slides = HashMap<String, Animatable<Float, AnimationVector1D>>()

    val shown: List<String> get() = override?.takeIf { it.toSet() == base.toSet() } ?: base

    fun measured(ids: List<String>, newLefts: List<Float>, newWidths: List<Float>, width: Float) {
        waiting = false
        total = width
        val live = ids.toSet()
        slides.keys.retainAll(live)
        lefts.keys.retainAll(live)
        widths.keys.retainAll(live)
        handles.keys.retainAll(live)
        val held = dragging
        ids.forEachIndexed { i, id ->
            val left = newLefts[i]
            widths[id] = newWidths[i]
            val old = lefts.put(id, left)
            val slide = slides[id]
            when {
                slide == null -> slides[id] = Animatable(left)
                old != left && id != held -> scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (Motion.reduced) slide.snapTo(left) else slide.animateTo(left, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }

    fun x(id: String, left: Float): Float = if (id == dragging) dragLeft() else slides[id]?.value ?: left

    fun local(id: String, at: Offset): Float? {
        val strip = strip?.takeIf { it.isAttached } ?: return null
        val handle = handles[id]?.takeIf { it.isAttached } ?: return null
        return strip.localPositionOf(handle, at).x
    }

    fun start(id: String, at: Float): Boolean {
        if (dragging != null || base.size < 2) return false
        val slide = slides[id] ?: return false
        startLeft = slide.value
        startPointer = at
        pointer = at
        override = shown
        dragging = id
        dragged = id
        playUiSound(UiSound.HudDragStart)
        return true
    }

    fun move(at: Float) {
        pointer = at
        val id = dragging ?: return
        if (waiting) return
        val ids = shown
        val index = ids.indexOf(id)
        if (index < 0) return
        val left = dragLeft()
        val right = left + (widths[id] ?: 0f)
        val target = when {
            index + 1 < ids.size && right > middle(ids[index + 1]) -> index + 1
            index > 0 && left < middle(ids[index - 1]) -> index - 1
            else -> return
        }
        ids.withItemMoved(index, target)?.let {
            override = it
            waiting = true
        }
    }

    fun end() {
        val id = dragging ?: return
        val order = override
        val slide = slides[id]
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            slide?.snapTo(dragLeft())
            dragging = null
            override = null
            if (order != null && order != base) commit(order)
            playUiSound(UiSound.HudDragEnd)
            val left = lefts[id] ?: return@launch
            if (slide == null) return@launch
            if (Motion.reduced) slide.snapTo(left) else slide.animateTo(left, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    private fun middle(id: String): Float = (lefts[id] ?: 0f) + (widths[id] ?: 0f) / 2f

    private fun dragLeft(): Float {
        val width = widths[dragging] ?: 0f
        return (startLeft + pointer - startPointer).coerceIn(0f, max(0f, total - width))
    }
}

@Composable
private fun rememberChipReorder(ids: List<String>, commit: (List<String>) -> Unit): ChipReorder {
    val scope = rememberCoroutineScope()
    val reorder = remember { ChipReorder(scope) }
    reorder.base = ids
    reorder.commit = commit
    return reorder
}

@Composable
private fun ChipLayout(reorder: ChipReorder, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.onPlaced { reorder.strip = it }) { measurables, constraints ->
        val gap = ChipGap.roundToPx()
        val count = measurables.size
        val room = if (constraints.hasBoundedWidth) constraints.maxWidth - gap * (count - 1).coerceAtLeast(0) else Constraints.Infinity
        val slot = if (count == 0 || room == Constraints.Infinity) Constraints.Infinity else (room / count).coerceAtLeast(0)
        val child = Constraints(maxWidth = slot, maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity)
        val placeables = measurables.map { it.measure(child) }
        val ids = measurables.map { it.layoutId as? String ?: "" }
        val lefts = ArrayList<Float>(count)
        var x = 0
        placeables.forEach {
            lefts += x.toFloat()
            x += it.width + gap
        }
        val used = (x - gap).coerceAtLeast(0)
        reorder.measured(ids, lefts, placeables.map { it.width.toFloat() }, used.toFloat())
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else used
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val id = ids[i]
                placeable.place(reorder.x(id, lefts[i]).roundToInt(), (height - placeable.height) / 2, if (id == reorder.dragging) 1f else 0f)
            }
        }
    }
}

private fun Modifier.chipDrag(reorder: ChipReorder, id: String): Modifier = this
    .onGloballyPositioned { reorder.handles[id] = it }
    .pointerInput(reorder, id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            reorder.dragged = null
            if (down.isConsumed || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val crossed = awaitSlop(down.id, down.position) ?: return@awaitEachGesture
            val origin = reorder.local(id, down.position) ?: return@awaitEachGesture
            if (!reorder.start(id, origin)) return@awaitEachGesture
            try {
                reorder.local(id, crossed)?.let(reorder::move)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.type == PointerEventType.Scroll) continue
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    change.consume()
                    if (change.position != change.previousPosition) reorder.local(id, change.position)?.let(reorder::move)
                }
            } finally {
                reorder.end()
            }
        }
    }

private suspend fun AwaitPointerEventScope.awaitSlop(pointer: PointerId, origin: Offset): Offset? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (event.type == PointerEventType.Scroll) continue
        if (!change.pressed || change.isConsumed) return null
        if ((change.position - origin).getDistance() > viewConfiguration.touchSlop) {
            change.consume()
            return change.position
        }
    }
}

@Composable
private fun Modifier.chipLift(reorder: ChipReorder, id: String): Modifier {
    val theme = OneConfigTheme.current
    val elevation by animateDpAsState(if (reorder.dragging == id) LiftElevation else 0.dp)
    if (!theme.shadowEnabled || elevation <= 0.dp) return this
    return shadow(elevation, OneConfigTheme.shape(ChipRadius), clip = false, ambientColor = theme.shadowColor, spotColor = theme.shadowColor)
}

private sealed interface PinChip {
    val key: String
}

private class ServerPinChip(val entry: ServerEntry) : PinChip {
    override val key: String get() = pinKey(entry)
}

private class RealmPinChip(val pin: RecentServer, val realm: RealmEntry?) : PinChip {
    override val key: String get() = pin.ip.trim().lowercase()
}

@Composable
internal fun PinnedServersStrip(lobby: LobbyShell, modifier: Modifier = Modifier) {
    val servers = lobby.servers
    val byAddress = servers.pinned.associateBy(::pinKey)
    val realms = LobbyRealms.available()
    val shown = servers.pins.take(PolyPlusRecentServers.PINNED_MAX).mapNotNull { pin ->
        when {
            !pin.isRealm -> byAddress[pin.ip.trim().lowercase()]?.let(::ServerPinChip)
            realms -> RealmPinChip(pin, LobbyRealms.find(pin.realm))
            else -> null
        }
    }
    if (shown.isEmpty()) return
    val byKey = shown.associateBy { it.key }
    val order = shown.map { it.key }
    val reorder = rememberChipReorder(order) { servers.setPinnedOrder(it) }
    Row(
        modifier.fillMaxWidth().height(StripHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        PinnedMark()
        ChipLayout(reorder, Modifier.weight(1f).height(StripHeight)) {
            reorder.shown.forEach { id ->
                val entry = byKey[id] ?: return@forEach
                key(id) {
                    Box(Modifier.layoutId(id).chipLift(reorder, id)) {
                        val index = servers.pins.indexOfFirst { it.ip.trim().lowercase() == id }
                        when (entry) {
                            is ServerPinChip -> PinnedChip(lobby, entry.entry, index, servers.pins.size, reorder, Modifier.chipDrag(reorder, id))
                            is RealmPinChip -> RealmChip(lobby, entry, index, servers.pins.size, reorder, Modifier.chipDrag(reorder, id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedChip(lobby: LobbyShell, entry: ServerEntry, index: Int, count: Int, reorder: ChipReorder, drag: Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val name = data.shownName
    val hover = rememberHoverState()
    val id = pinKey(entry)
    LaunchedEffect(data) { servers.ping(data) }
    @Suppress("UNUSED_EXPRESSION") servers.revision
    ServerRevisions.of(data)
    PlayerSampleTooltip(data, title = name) {
        Row(
            Modifier
                .height(StripHeight)
                .cardFrame(false, hover.hovered || reorder.dragging == id)
                .contextMenu { pinnedMenuItems(lobby, entry, index, count) }
                .hoverable(hover.interactionSource)
                .oneConfigClickable("Join $name", {
                    if (reorder.dragged == id) reorder.dragged = null else lobby.join(data)
                })
                .then(drag)
                .padding(start = 5.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ServerIcon(data, servers.revision, ChipIcon)
            Text(name, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

private fun pinnedMenuItems(lobby: LobbyShell, entry: ServerEntry, index: Int, count: Int): List<MenuAction> {
    val items = serverMenuItems(lobby, entry)
    return items.take(1) + pinMoves(lobby, index, count) + items.drop(1)
}

private fun pinMoves(lobby: LobbyShell, index: Int, count: Int): List<MenuAction> = listOf(
    MenuAction("Move left", { lobby.servers.movePinned(index, index - 1) }, enabled = index > 0),
    MenuAction("Move right", { lobby.servers.movePinned(index, index + 1) }, enabled = index in 0 until count - 1),
)

@Composable
private fun RealmChip(lobby: LobbyShell, chip: RealmPinChip, index: Int, count: Int, reorder: ChipReorder, drag: Modifier) {
    val realm = chip.realm
    val name = realm?.name ?: chip.pin.name
    val hover = rememberHoverState()
    val id = chip.key
    TooltipBox(realm?.let { "$name · ${it.status}" } ?: name, Modifier, TooltipPlacement.AboveCenter, Dp.Unspecified) {
        Row(
            Modifier
                .height(StripHeight)
                .cardFrame(false, hover.hovered || reorder.dragging == id)
                .contextMenu { realmPinMenuItems(lobby, chip, index, count) }
                .hoverable(hover.interactionSource)
                .oneConfigClickable("Join $name", {
                    if (reorder.dragged == id) reorder.dragged = null else lobby.playRealm(chip.pin.realm)
                })
                .then(drag)
                .padding(start = 5.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (realm != null) RealmHead(realm, ChipIcon) else ThemedIcon(REALM_ICON, OneConfigTheme.current.mutedText, ChipIcon)
            Text(name, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

private fun realmPinMenuItems(lobby: LobbyShell, chip: RealmPinChip, index: Int, count: Int): List<MenuAction> {
    val realm = chip.realm
    val unpin = MenuAction("Unpin from home", {
        PolyPlusRecentServers.setRealmPinned(chip.pin.realm, chip.pin.name, false)
        lobby.servers.rebuild()
    }, icon = PINNED_ICON)
    if (realm == null) {
        return listOf(MenuAction("Open in Realms", lobby::openRealms, icon = REALM_ICON)) + pinMoves(lobby, index, count) + unpin
    }
    val items = realmMenuItems(lobby, realm)
    return items.take(1) + pinMoves(lobby, index, count) + items.drop(1)
}

@Composable
internal fun PinnedMark() {
    ThemedIcon(PINNED_ICON, OneConfigTheme.accent, MarkSize)
}
