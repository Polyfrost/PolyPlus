@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.reorderAutoScroll
import org.polyfrost.oneconfig.api.ui.v1.components.reorderContainer
import org.polyfrost.oneconfig.api.ui.v1.components.reorderOverlay
import org.polyfrost.oneconfig.api.ui.v1.components.reorderableItem
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.UiSound
import org.polyfrost.oneconfig.api.ui.v1.components.playUiSound
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val RowHeight = 64.dp
private val MORE_MENU_GAP = 6.dp
private val RowGap = 6.dp
private val RowIcon = 36.dp
private val PlayersWidth = 104.dp
private val PingWidth = 56.dp

@Composable
internal fun ServerList(lobby: LobbyShell, shown: List<ServerEntry>, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val reorder = rememberServerReorder(lobby, shown, canReorder, listState)
    ScrollContainer(listState, modifier.fillMaxSize(), scrollbar = false) {
        Box(Modifier.fillMaxSize().reorderContainer(reorder.state).reorderAutoScroll(reorder.state)) {
            LazyColumn(Modifier.fillMaxSize(), listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
                itemsIndexed(reorder.entries, key = { _, entry -> entry.key }) { index, entry ->
                    val drag = Modifier.reorderableItem(reorder.state, entry.key, index, canReorder && entry.kind == ServerKind.SAVED)
                    ServerRow(lobby, entry, canReorder, onDelete, Modifier.animateItem(placementSpec = Motion.itemPlacementSpec()), drag)
                }
            }
            reorder.overlayEntry?.let { entry -> ServerRow(lobby, entry, canReorder, onDelete, Modifier.reorderOverlay(reorder.state)) }
        }
    }
}

@Composable
internal fun ServerRow(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier, drag: Modifier = Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val revision = servers.revision
    ServerRevisions.of(data)
    val hover = rememberHoverState()
    val selected = lobby.isSelected(data)
    val doubleClick = rememberDoubleClick { lobby.join(data) }
    LaunchedEffect(data) { servers.ping(data) }
    val failed = LobbyServers.failed(data)
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .contextMenu { serverMenuItems(lobby, entry, canReorder, vertical = true, onDelete = onDelete, folders = true) }
            .hoverable(hover.interactionSource)
            .oneConfigClickable(
                "${data.shownName}, ${serverPlayers(data)}",
                {
                    doubleClick()
                    lobby.select(data)
                },
                selected = selected.takeIf { it },
                shape = OneConfigTheme.shape(16.dp),
            )
            .then(drag)
            .cardFrame(selected, hover.hovered)
            .serverWash(data, 0.22f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerIcon(data, revision, RowIcon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(data.shownName, Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                if (servers.isPinned(data)) PinnedMark()
                if (data.state() == ServerData.State.INCOMPATIBLE) DangerTag("Incompatible".uppercase())
            }
            MotdText(entry, revision)
        }
        PlayerSampleTooltip(data, Modifier.width(PlayersWidth)) {
            Text(serverPlayers(data), color = if (failed) SocialDangerText else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        Text(serverPing(data), Modifier.width(PingWidth), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        TextButton(text = "Join", onClick = { lobby.join(data) }, size = ControlSize.Large, style = ControlStyle.Primary)
        ServerMoreMenu(lobby, entry, canReorder, onDelete)
    }
}

@Composable
internal fun ServerMoreMenu(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, vertical: Boolean = true) {
    NavPopupButton(LobbyIcons.MORE, "More", gap = MORE_MENU_GAP) { close ->
        LobbyUserTheme { MenuList(serverMenuItems(lobby, entry, canReorder, vertical = vertical, onDelete = onDelete, folders = true), close) }
    }
}

@Composable
internal fun GroupedServerList(lobby: LobbyShell, slots: List<ServerSlot>, canMove: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    ScrollContainer(listState, modifier.fillMaxSize(), scrollbar = false) {
        LazyColumn(Modifier.fillMaxSize(), listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
            itemsIndexed(slots, key = { _, slot -> slot.key }) { index, slot ->
                val placed = Modifier.animateItem(placementSpec = Motion.itemPlacementSpec()).groupGap(slot, index)
                when (slot) {
                    is EntrySlot -> ServerRow(lobby, slot.entry, canMove, onDelete, if (slot.nested) placed.padding(start = NestedIndent) else placed)
                    is FolderSlot -> FolderHeader(lobby.servers, slot, placed)
                    is SectionSlot -> SectionLabel(slot.label, placed)
                    is GroupSlot -> GroupHeader(slot, placed)
                }
            }
        }
    }
}

@Composable
internal fun FolderHeader(servers: LobbyServers, slot: FolderSlot, modifier: Modifier = Modifier) {
    val hover = rememberHoverState()
    val shape = OneConfigTheme.shape(12.dp)
    val count = if (slot.count == 1) "1 server" else "${slot.count} servers"
    Row(
        modifier
            .fillMaxWidth()
            .height(FolderHeaderHeight)
            .contextMenu { folderMenuItems(servers, slot) }
            .hoverable(hover.interactionSource)
            .oneConfigClickable(
                "${slot.folder}, $count, ${if (slot.collapsed) "collapsed" else "expanded"}",
                { servers.toggleFolder(slot.folder) },
                shape = shape,
            )
            .rowFrame(false, hover.hovered, shape)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LobbyIcon(CHEVRON, OneConfigTheme.current.mutedText, 16.dp, Modifier.rotate(if (slot.collapsed) 0f else 90f))
        ThemedIcon(FOLDER_ICON, SocialTextPrimary, 18.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(slot.folder, Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(slot.count.toString(), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
        }
        NavPopupButton(LobbyIcons.MORE, "Folder options", gap = MORE_MENU_GAP) { close ->
            LobbyUserTheme { MenuList(folderMenuItems(servers, slot), close) }
        }
    }
}

@Composable
internal fun GroupHeader(slot: GroupSlot, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(FolderHeaderHeight)
            .rowFrame(false, false, OneConfigTheme.shape(12.dp))
            .padding(start = GroupHeaderInset, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemedIcon(SERVERS_GROUP_ICON, SocialTextPrimary, 18.dp)
        Text(slot.label, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
        Text(slot.count.toString(), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
    }
}

internal fun Modifier.groupGap(slot: ServerSlot, index: Int): Modifier =
    if (index > 0 && (slot is FolderSlot || slot is SectionSlot)) padding(top = GroupGap) else this

internal const val SERVERS_GROUP = "Servers"
private const val SERVERS_GROUP_ICON = SOCIAL_ASSETS + "globe-01.svg"
internal val GroupGap = 12.dp
private val GroupHeaderInset = 36.dp

@Composable
internal fun RealmsHeader(lobby: LobbyShell, count: Int, modifier: Modifier = Modifier) {
    val hover = rememberHoverState()
    val shape = OneConfigTheme.shape(12.dp)
    val collapsed = LobbyRealms.collapsed
    val invites = LobbyRealms.invites
    val problem = LobbyRealms.problem
    val realms = if (count == 1) "1 realm" else "$count realms"
    val inlineStatus = collapsed || LobbyRealms.realms.isNotEmpty()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(FolderHeaderHeight)
                .contextMenu { realmsGroupMenuItems(lobby) }
                .hoverable(hover.interactionSource)
                .oneConfigClickable("Realms, $realms, ${if (collapsed) "collapsed" else "expanded"}", LobbyRealms::toggleCollapsed, shape = shape)
                .rowFrame(false, hover.hovered, shape)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LobbyIcon(CHEVRON, OneConfigTheme.current.mutedText, 16.dp, Modifier.rotate(if (collapsed) 0f else 90f))
            ThemedIcon(REALM_ICON, SocialTextPrimary, 18.dp)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Realms", style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
                Text(count.toString(), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
                if (invites > 0) {
                    val label = if (invites == 1) "1 invite" else "$invites invites"
                    Badge(label, Modifier.height(18.dp).oneConfigClickable("Open $label", lobby::openRealms), tone = BadgeTone.Accent)
                }
                when {
                    !inlineStatus -> Unit
                    problem != null -> Text(problem, Modifier.weight(1f, fill = false), color = SocialDangerText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    LobbyRealms.loading -> Text("Refreshing...", Modifier.weight(1f, fill = false), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
            }
            NavPopupButton(LobbyIcons.MORE, "Realms options", gap = MORE_MENU_GAP) { close ->
                LobbyUserTheme { MenuList(realmsGroupMenuItems(lobby), close) }
            }
        }
        RealmsGroupBody(lobby)
    }
}

@Composable
internal fun RealmRow(lobby: LobbyShell, realm: RealmEntry, modifier: Modifier = Modifier) {
    val hover = rememberHoverState()
    val selected = lobby.isSelected(realm)
    val doubleClick = rememberDoubleClick { lobby.playRealm(realm) }
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .contextMenu { realmMenuItems(lobby, realm) }
            .hoverable(hover.interactionSource)
            .oneConfigClickable(
                "${realm.name}, ${realm.status}",
                {
                    doubleClick()
                    lobby.select(realm)
                },
                selected = selected.takeIf { it },
                shape = OneConfigTheme.shape(16.dp),
            )
            .cardFrame(selected, hover.hovered)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RealmHead(realm, RowIcon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(realm.name, Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                if (lobby.servers.isRealmPinned(realm.id)) PinnedMark()
                RealmStatusBadge(realm)
            }
            val warning = realm.warning
            Text(
                warning ?: realm.motd,
                color = if (warning != null) SocialDangerText else OneConfigTheme.current.mutedText,
                style = OneConfigTheme.current.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        Text(realm.detail, Modifier.width(PlayersWidth + PingWidth), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        TextButton(text = if (realm.joinable) "Join" else "Open", onClick = { lobby.playRealm(realm) }, size = ControlSize.Large, style = ControlStyle.Primary)
        RealmMoreMenu(lobby, realm)
    }
}

@Composable
internal fun RealmMoreMenu(lobby: LobbyShell, realm: RealmEntry) {
    NavPopupButton(LobbyIcons.MORE, "More", gap = MORE_MENU_GAP) { close ->
        LobbyUserTheme { MenuList(realmMenuItems(lobby, realm), close) }
    }
}

@Composable
internal fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp),
        color = OneConfigTheme.current.mutedText,
        style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

private const val CHEVRON = "chevron-right"
private val FolderHeaderHeight = 44.dp
private val NestedIndent = 20.dp

@Composable
internal fun PlayerSampleTooltip(data: ServerData, modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    val sample = playerSample(data)
    if (sample == null && title == null) {
        Box(modifier) { content() }
        return
    }
    TooltipBox(
        tooltip = { PlayerSampleBody(title, sample) },
        modifier = modifier,
        placement = TooltipPlacement.AboveCenter,
        maxWidth = SampleMaxWidth,
        content = content,
    )
}

@Composable
private fun PlayerSampleBody(title: String?, sample: PlayerSample?) {
    val theme = OneConfigTheme.current
    val style = theme.typography.labelRegular
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) Text(title, color = theme.textColor, style = style.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        if (sample == null) return@Column
        if (title != null) Text("%,d online".format(sample.online), color = theme.mutedText, style = style, maxLines = 1, softWrap = false)
        when {
            sample.names.isEmpty() && sample.online == 0 -> Text("No one online", color = theme.mutedText, style = style, maxLines = 1, softWrap = false)
            sample.names.isEmpty() -> Text("No player list shared", color = theme.mutedText, style = style, maxLines = 1, softWrap = false)
            else -> {
                sample.names.forEach { Text(it, color = theme.textColor, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
                if (sample.more > 0) Text("and %,d more".format(sample.more), color = theme.mutedText, style = style, maxLines = 1, softWrap = false)
            }
        }
    }
}

private val SampleMaxWidth = 240.dp

private fun answered(data: ServerData): Boolean =
    data.state() == ServerData.State.SUCCESSFUL || data.state() == ServerData.State.INCOMPATIBLE

private fun serverPlayers(data: ServerData): String = when {
    data.isLan -> "Local network"
    LobbyServers.failed(data) -> "Offline"
    answered(data) -> data.players?.let { "%,d online".format(it.online()) }.orEmpty()
    else -> "Checking..."
}

private fun serverPing(data: ServerData): String = when {
    data.isLan || LobbyServers.failed(data) -> ""
    answered(data) -> "${data.ping} ms"
    else -> ""
}

private class SlotId(val key: String, val full: Boolean)

private class SlotDrag(private val scope: CoroutineScope) {
    var base: List<ServerSlot> = emptyList()
    var grid = false
    var commit: (ServerEntry, List<ServerSlot>, String?) -> Unit = { _, _, _ -> }
    var content: LayoutCoordinates? = null
    val handles = HashMap<String, LayoutCoordinates>()

    private var override by mutableStateOf<List<ServerSlot>?>(null)
    var dragging by mutableStateOf<String?>(null)
        private set
    var dropFolder by mutableStateOf<String?>(null)
        private set

    var pointer by mutableStateOf(Offset.Zero)
        private set
    private var startPointer = Offset.Zero
    private var startPos = Offset.Zero
    private var waiting = false
    private var total = Size.Zero
    private val rects = HashMap<String, Rect>()
    private val slides = HashMap<String, Animatable<Offset, AnimationVector2D>>()

    val shown: List<ServerSlot> get() = override ?: base

    fun measured(keys: List<String>, newRects: List<Rect>, size: Size) {
        waiting = false
        total = size
        val live = keys.toSet()
        slides.keys.retainAll(live)
        rects.keys.retainAll(live)
        handles.keys.retainAll(live)
        val held = dragging
        keys.forEachIndexed { i, key ->
            val rect = newRects[i]
            val old = rects.put(key, rect)
            val slide = slides[key]
            when {
                slide == null -> slides[key] = Animatable(rect.topLeft, Offset.VectorConverter)
                old?.topLeft != rect.topLeft && key != held -> scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (Motion.reduced) slide.snapTo(rect.topLeft) else slide.animateTo(rect.topLeft, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }

    fun position(key: String, natural: Offset): Offset = if (key == dragging) dragPos() else slides[key]?.value ?: natural

    fun local(key: String, at: Offset): Offset? {
        val content = content?.takeIf { it.isAttached } ?: return null
        val handle = handles[key]?.takeIf { it.isAttached } ?: return null
        return content.localPositionOf(handle, at)
    }

    fun start(key: String, at: Offset): Boolean {
        if (dragging != null) return false
        val slide = slides[key] ?: return false
        startPos = slide.value
        startPointer = at
        pointer = at
        override = shown
        dragging = key
        playUiSound(UiSound.HudDragStart)
        return true
    }

    fun shift(by: Float) {
        if (dragging == null) return
        move(pointer + Offset(0f, by))
    }

    fun move(at: Offset) {
        pointer = at
        val key = dragging ?: return
        val slots = shown
        val index = slots.indexOfFirst { it.key == key }
        val me = rects[key] ?: return
        if (index < 0) return
        val center = dragPos() + Offset(me.width / 2f, me.height / 2f)
        val hit = slots.firstOrNull { it.key != key && rects[it.key]?.contains(center) == true }
        if (hit is FolderSlot && hit.collapsed) {
            dropFolder = hit.folder
            return
        }
        dropFolder = null
        if (waiting) return
        val target = (if (grid) gridTarget(slots, index, center, hit) else listTarget(slots, index, center.y - me.height / 2f, center.y + me.height / 2f))?.takeIf { it != index } ?: return
        val movable = slots.indices.filter { slots[it].movable() }
        if (movable.isEmpty() || target !in movable.first()..movable.last()) return
        slots.withItemMoved(index, target)?.let {
            override = it
            waiting = true
        }
    }

    fun end() {
        val key = dragging ?: return
        val order = override
        val folder = dropFolder
        val slide = slides[key]
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            slide?.snapTo(dragPos())
            val moved = order != null && order.map { it.key } != base.map { it.key }
            val entry = (base.firstOrNull { it.key == key } as? EntrySlot)?.entry
            dragging = null
            override = null
            dropFolder = null
            if (entry != null && (folder != null || moved)) commit(entry, order ?: base, folder)
            playUiSound(UiSound.HudDragEnd)
            val target = rects[key]?.topLeft ?: return@launch
            if (slide == null) return@launch
            if (Motion.reduced) slide.snapTo(target) else slide.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    private fun ServerSlot.movable(): Boolean = this is FolderSlot || (this is EntrySlot && entry.kind == ServerKind.SAVED)

    private fun middle(slot: ServerSlot): Float = rects[slot.key]?.center?.y ?: 0f

    private fun listTarget(slots: List<ServerSlot>, index: Int, top: Float, bottom: Float): Int? = when {
        index + 1 < slots.size && bottom > middle(slots[index + 1]) -> index + 1
        index > 0 && top < middle(slots[index - 1]) -> index - 1
        else -> null
    }

    private fun gridTarget(slots: List<ServerSlot>, index: Int, center: Offset, hit: ServerSlot?): Int? {
        if (hit != null) {
            val at = slots.indexOf(hit)
            val rect = rects[hit.key] ?: return null
            return when (hit) {
                is EntrySlot -> at
                is FolderSlot, is GroupSlot -> if (center.y < rect.center.y) (if (at > index) at - 1 else at) else (if (at > index) at else at + 1)
                is SectionSlot -> null
            }
        }
        val row = slots.filter { slot -> rects[slot.key]?.let { center.y >= it.top && center.y <= it.bottom } == true }
        val last = row.maxByOrNull { rects[it.key]?.right ?: 0f } ?: return null
        if (last !is EntrySlot || center.x <= (rects[last.key]?.right ?: Float.MAX_VALUE)) return null
        val at = slots.indexOf(last)
        return if (at > index) at else at + 1
    }

    private fun dragPos(): Offset {
        val size = rects[dragging]?.size ?: Size.Zero
        val raw = startPos + (pointer - startPointer)
        val x = if (grid) raw.x.coerceIn(0f, max(0f, total.width - size.width)) else startPos.x
        return Offset(x, raw.y.coerceIn(0f, max(0f, total.height - size.height)))
    }
}

@Composable
private fun SlotLayout(drag: SlotDrag, minCell: Dp, gap: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.onPlaced { drag.content = it }) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val columns = if (drag.grid) ((width + gapPx) / (minCell.roundToPx() + gapPx)).coerceAtLeast(1) else 1
        val cell = ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
        val ids = measurables.map { it.layoutId as? SlotId ?: SlotId("", true) }
        val placeables = ArrayList<Placeable>(measurables.size)
        val rects = ArrayList<Rect>(measurables.size)
        var top = 0
        var column = 0
        var rowHeight = 0
        fun closeRow() {
            if (column == 0) return
            top += rowHeight + gapPx
            column = 0
            rowHeight = 0
        }
        measurables.forEachIndexed { i, measurable ->
            val full = ids[i].full || columns == 1
            if (full) {
                closeRow()
                val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
                placeables += placeable
                rects += Rect(0f, top.toFloat(), width.toFloat(), (top + placeable.height).toFloat())
                top += placeable.height + gapPx
            } else {
                val placeable = measurable.measure(Constraints(minWidth = cell, maxWidth = cell))
                val x = column * (cell + gapPx)
                placeables += placeable
                rects += Rect(x.toFloat(), top.toFloat(), (x + placeable.width).toFloat(), (top + placeable.height).toFloat())
                rowHeight = max(rowHeight, placeable.height)
                column++
                if (column == columns) closeRow()
            }
        }
        closeRow()
        val height = (top - gapPx).coerceAtLeast(0)
        drag.measured(ids.map { it.key }, rects, Size(width.toFloat(), height.toFloat()))
        layout(width, constraints.constrainHeight(height)) {
            placeables.forEachIndexed { i, placeable ->
                val key = ids[i].key
                val at = drag.position(key, rects[i].topLeft)
                placeable.place(at.x.roundToInt(), at.y.roundToInt(), if (key == drag.dragging) 1f else 0f)
            }
        }
    }
}

private fun Modifier.slotDragSource(drag: SlotDrag, key: String): Modifier = this
    .onGloballyPositioned { drag.handles[key] = it }
    .pointerInput(drag, key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val crossed = awaitDragSlop(down.id, down.position) ?: return@awaitEachGesture
            val origin = drag.local(key, down.position) ?: return@awaitEachGesture
            if (!drag.start(key, origin)) return@awaitEachGesture
            try {
                drag.local(key, crossed)?.let(drag::move)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.type == PointerEventType.Scroll) continue
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    change.consume()
                    if (change.position != change.previousPosition) drag.local(key, change.position)?.let(drag::move)
                }
            } finally {
                drag.end()
            }
        }
    }

private suspend fun AwaitPointerEventScope.awaitDragSlop(pointer: PointerId, origin: Offset): Offset? {
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
private fun Modifier.slotLift(drag: SlotDrag, key: String): Modifier {
    val theme = OneConfigTheme.current
    val elevation by animateDpAsState(if (drag.dragging == key) SlotLiftElevation else 0.dp)
    if (!theme.shadowEnabled || elevation <= 0.dp) return this
    return shadow(elevation, OneConfigTheme.shape(16.dp), clip = false, ambientColor = theme.shadowColor, spotColor = theme.shadowColor)
}

@Composable
internal fun DraggableServerSlots(
    servers: LobbyServers,
    slots: List<ServerSlot>,
    grid: Boolean,
    minCell: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    entry: @Composable (EntrySlot, Modifier, Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drag = remember { SlotDrag(scope) }
    drag.base = slots
    drag.grid = grid
    drag.commit = { moved, order, folder -> dropServer(servers, moved, order, folder) }
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    val dragging = drag.dragging != null
    val density = LocalDensity.current
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        val edge = with(density) { AutoScrollEdge.toPx() }
        val speed = with(density) { AutoScrollSpeed.toPx() }
        var last = 0L
        while (drag.dragging != null) {
            val now = withFrameNanos { it }
            val seconds = if (last == 0L) 0f else (now - last) / 1_000_000_000f
            last = now
            val y = drag.pointer.y - scroll.value
            val factor = when {
                y < edge -> -((edge - y) / edge).coerceIn(0f, 1f)
                y > viewport - edge -> ((y - (viewport - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (factor != 0f && seconds > 0f) {
                val consumed = scroll.scrollBy(factor * speed * seconds)
                if (consumed != 0f) drag.shift(consumed)
            }
        }
    }
    ScrollContainer(scroll, modifier.fillMaxSize().onSizeChanged { viewport = it.height }, scrollbar = false) {
        SlotLayout(drag, minCell, gap, Modifier.fillMaxWidth()) {
            drag.shown.forEachIndexed { index, slot ->
                key(slot.key) {
                    val id = Modifier.layoutId(SlotId(slot.key, slot !is EntrySlot)).groupGap(slot, index)
                    when (slot) {
                        is EntrySlot -> {
                            val movable = slot.entry.kind == ServerKind.SAVED
                            val placed = if (movable) id.slotLift(drag, slot.key) else id
                            val handle = if (movable) Modifier.slotDragSource(drag, slot.key) else Modifier
                            entry(slot, if (!grid && slot.nested) placed.padding(start = NestedIndent) else placed, handle)
                        }
                        is FolderSlot -> {
                            val target = drag.dropFolder == slot.folder
                            FolderHeader(servers, slot, if (target) id.border(2.dp, OneConfigTheme.accent, OneConfigTheme.shape(12.dp)) else id)
                        }
                        is SectionSlot -> SectionLabel(slot.label, id)
                        is GroupSlot -> GroupHeader(slot, id)
                    }
                }
            }
        }
    }
}

private fun dropServer(servers: LobbyServers, moved: ServerEntry, order: List<ServerSlot>, dropFolder: String?) {
    val index = order.indexOfFirst { it.key == moved.key }
    val header = if (dropFolder != null) null else order.subList(0, index.coerceAtLeast(0)).lastOrNull { it is FolderSlot || it is SectionSlot || it is GroupSlot } as? FolderSlot
    val folder = dropFolder ?: header?.folder
    val into = dropFolder != null || header?.collapsed == true
    fun saved(slot: ServerSlot?): ServerEntry? = (slot as? EntrySlot)?.entry?.takeIf { it.kind == ServerKind.SAVED }
    val after = if (into) null else saved(order.getOrNull(index - 1))
    val before = if (into || after != null) null else saved(order.getOrNull(index + 1))
    servers.drop(moved, folder, after, before, toEnd = into)
}

private val SlotLiftElevation = 14.dp
private val AutoScrollEdge = 56.dp
private val AutoScrollSpeed = 900.dp
