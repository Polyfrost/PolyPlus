//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.world.level.GameType
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.network.http.PlayersApi
import org.polyfrost.polyplus.client.network.http.responses.BlockedPlayer
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.FriendRequest
import org.polyfrost.polyplus.client.network.http.responses.GlobalChatMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.p2p.EosStatus
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GlobalChatRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.gui.clickableWithSound

// TODO: Redesign
class SocialOverlayScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    override fun shouldCloseOnEsc(): Boolean = true

    //? if <26.1 {
    /*override fun renderBackground(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    *///?} else {
    override fun extractBackground(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}

    override fun onClose() {
        Minecraft.getInstance().execute { SocialOverlay.close() }
    }

    @Composable
    override fun compose() {
        Theme {
            SocialOverlayContent(onClose = { Minecraft.getInstance().execute { SocialOverlay.close() } })
        }
    }
}

private enum class SocialTab(val label: String, val icon: String) {
    Friends("Friends", "users-01.svg"),
    Requests("Requests", "bell-01.svg"),
    Chats("Chats", "message-chat-circle.svg"),
    Global("Global", "globe-01.svg"),
    Sessions("Sessions", "key-01.svg"),
}

private const val ICONS = "assets/polyplus/mainmenu/"

private val FrameBg = Color(0xFF0A0F13)
private val RailBg = Color(0xFF05080A)
private val RailBgSelected = Color(0xFF0F161B)
private val ContentBg = Color(0xFF0D1418)
private val DividerColor = Color(0x1EFFFFFF)
private val TextDim = Color(0xFF7C8A93)
private val TextBright = Color(0xFFE6EDF0)
private val DangerColor = Color(0xFFFF4655)
private val PanelShapeValue = RoundedCornerShape(2.dp)
private val RailWidth = 78.dp
private val FrameWidth = 760.dp
private val FrameHeight = 540.dp
private val PopoutWidth = 260.dp

@Composable
private fun SocialOverlayContent(onClose: () -> Unit) {
    var tab by remember { mutableStateOf(SocialTab.Friends) }
    val openChats = remember { mutableStateListOf<Int>() }
    val friends by FriendsRepository.friends.collectAsState()
    val groups by GroupsRepository.groups.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier
                    .width(FrameWidth)
                    .heightIn(max = FrameHeight)
                    .clip(PanelShapeValue)
                    .background(FrameBg)
                    .border(1.dp, LocalTheme.current.borderColor, PanelShapeValue)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                TopBar(onClose)
                HairlineDivider(color = Accent, thickness = 2.dp)
                Row(modifier = Modifier.fillMaxWidth().heightIn(max = FrameHeight - 44.dp)) {
                    NavRail(
                        selected = tab,
                        onlineFriends = friends.count { it.online },
                        unreadChats = groups.count { it.unread },
                        onSelect = { tab = it },
                    )
                    HairlineDivider(vertical = true)
                    Column(modifier = Modifier.weight(1f).padding(20.dp)) {
                        TabHeader(tab)
                        Spacer(Modifier.height(14.dp))
                        when (tab) {
                            SocialTab.Friends -> FriendsTab()
                            SocialTab.Requests -> RequestsTab()
                            SocialTab.Chats -> ChatsTab(onOpen = { id -> if (id !in openChats) openChats.add(id) })
                            SocialTab.Global -> GlobalTab()
                            SocialTab.Sessions -> SessionsTab()
                        }
                    }
                }
            }

            if (openChats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    openChats.forEach { groupId ->
                        ChatPopout(groupId = groupId, onClose = { openChats.remove(groupId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialText("SOCIAL", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        SocialText("SHIFT+P TO CLOSE", fontSize = 10.sp, color = TextDim, letterSpacing = 1.sp)
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x1AFFFFFF))
                .clickableWithSound(onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ICONS + "x-close.svg", TextBright, Modifier.size(13.dp))
        }
    }
}

@Composable
private fun NavRail(selected: SocialTab, onlineFriends: Int, unreadChats: Int, onSelect: (SocialTab) -> Unit) {
    Column(modifier = Modifier.width(RailWidth).fillMaxHeight().background(RailBg)) {
        SocialTab.entries.forEach { entry ->
            val isSelected = entry == selected
            val badge = when (entry) {
                SocialTab.Friends -> onlineFriends.takeIf { it > 0 }
                SocialTab.Chats -> unreadChats.takeIf { it > 0 }
                else -> null
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(64.dp)
                        .background(if (isSelected) Accent else Color.Transparent),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .background(if (isSelected) RailBgSelected else Color.Transparent)
                        .clickableWithSound { onSelect(entry) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(ICONS + entry.icon, if (isSelected) Accent else TextDim, Modifier.size(18.dp))
                        if (badge != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 7.dp, y = (-5).dp)
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                SocialText(
                                    if (badge > 9) "9+" else badge.toString(),
                                    fontSize = 7.sp,
                                    color = Color(0xFF0A0F13),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SocialText(
                        entry.label.uppercase(),
                        fontSize = 8.sp,
                        letterSpacing = 0.6.sp,
                        color = if (isSelected) TextBright else TextDim,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabHeader(tab: SocialTab) {
    Column {
        SocialText(tab.label.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.width(32.dp).height(2.dp).background(Accent))
    }
}

@Composable
private fun HairlineDivider(vertical: Boolean = false, color: Color = DividerColor, thickness: androidx.compose.ui.unit.Dp = 1.dp) {
    if (vertical) {
        Box(modifier = Modifier.width(thickness).fillMaxHeight().background(color))
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(thickness).background(color))
    }
}

@Composable
private fun EmptyRow(text: String) {
    SocialText(text.uppercase(), fontSize = 11.sp, letterSpacing = 0.5.sp, color = TextDim, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun FlatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        HairlineDivider()
    }
}

@Composable
private fun TacticalButton(label: String, color: Color = Accent, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .then(
                if (filled) {
                    Modifier.background(color)
                } else {
                    Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                },
            )
            .clickableWithSound(onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        SocialText(
            label.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            color = if (filled) Color(0xFF0A0F13) else color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PlayerLookupInputRow(placeholder: String, buttonLabel: String, onResolved: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val username = value.trim()
        if (username.isEmpty() || loading) return
        loading = true
        notFound = false
        scope.launch {
            val result = PlayersApi.lookupByUsername(username)
            loading = false
            result.onSuccess { id ->
                if (id != null) {
                    onResolved(id)
                    value = ""
                } else {
                    notFound = true
                }
            }.onFailure { notFound = true }
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it; notFound = false },
                    singleLine = true,
                    textStyle = TextStyle(color = TextBright, fontSize = 12.sp, fontFamily = LocalTheme.current.typography.family),
                    cursorBrush = SolidColor(Accent),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            SocialText(placeholder.uppercase(), fontSize = 11.sp, letterSpacing = 0.5.sp, color = TextDim)
                        }
                        inner()
                    },
                )
            }
            TacticalButton(
                if (loading) "..." else buttonLabel,
                enabledColor(value.isNotBlank() && !loading),
                filled = value.isNotBlank() && !loading,
            ) { submit() }
        }
        if (notFound) {
            SocialText("PLAYER NOT FOUND", fontSize = 10.sp, letterSpacing = 0.5.sp, color = DangerColor)
        }
        HairlineDivider(color = if (value.isEmpty()) DividerColor else Accent.copy(alpha = 0.6f))
    }
}

private fun enabledColor(enabled: Boolean): Color = if (enabled) Accent else Color(0xFF4A5459)

@Composable
private fun FriendsTab() {
    val friends by FriendsRepository.friends.collectAsState()
    val blocked by FriendsRepository.blocked.collectAsState()

    LaunchedEffect(friends) { PlayerNamesRepository.resolve(friends.map { it.player }) }

    Column {
        PlayerLookupInputRow("Username to friend...", "Add") { FriendsRepository.sendRequest(it) }
        Spacer(Modifier.height(12.dp))
        SocialText("FRIENDS - ${friends.size}", fontSize = 11.sp, letterSpacing = 0.8.sp, color = TextDim)
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 210.dp)) {
            if (friends.isEmpty()) item { EmptyRow("No friends yet") }
            items(friends, key = { it.player }) { friend -> FriendRow(friend) }
        }
        if (blocked.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SocialText("BLOCKED - ${blocked.size}", fontSize = 11.sp, letterSpacing = 0.8.sp, color = TextDim)
            Spacer(Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 130.dp)) {
                items(blocked, key = { it.player }) { entry -> BlockedRow(entry) }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend) {
    var inviteStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        FlatRow {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (friend.online) Accent else TextDim),
            )
            SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 12.sp, modifier = Modifier.weight(1f))
            SocialText(friend.kind.name.uppercase(), fontSize = 10.sp, letterSpacing = 0.5.sp, color = TextDim)
            TacticalButton("DM") { GroupsRepository.openDirectMessage(friend.player) }
            TacticalButton("Invite to World") {
                inviteStatus = null
                inviteToWorld(scope, friend.player) { inviteStatus = it }
            }
            TacticalButton("Remove", DangerColor) { FriendsRepository.removeFriend(friend.player) }
            TacticalButton("Block", DangerColor) { FriendsRepository.block(friend.player) }
        }
        inviteStatus?.let {
            SocialText(it.uppercase(), fontSize = 9.sp, letterSpacing = 0.4.sp, color = TextDim, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

private fun inviteToWorld(scope: kotlinx.coroutines.CoroutineScope, player: String, onStatus: (String) -> Unit) {
    val existingSession = P2PSessionManager.currentSessionId
    if (existingSession != null) {
        SessionsRepository.invite(existingSession, player)
        onStatus("Invite sent")
        return
    }

    val mc = Minecraft.getInstance()
    if (mc.singleplayerServer != null) {
        HostWorldManager.hostCurrentWorldViaP2P(
            GameType.SURVIVAL,
            false,
            onFailure = { onStatus("Couldn't start hosting: ${it.message}") },
            onHosted = { sessionId -> SessionsRepository.invite(sessionId, player); onStatus("Hosting - invite sent") },
        )
    } else {
        scope.launch {
            val entry = HostWorldManager.loadWorlds().firstOrNull()
            if (entry == null) {
                onStatus("No worlds to host")
                return@launch
            }
            //? if >= 26.2 {
            /*val currentScreen = mc.gui.screen()
            *///?} else {
            val currentScreen = mc.screen
            //?}
            val returnScreen = currentScreen ?: net.minecraft.client.gui.screens.TitleScreen()
            HostWorldManager.hostViaP2P(
                returnScreen,
                entry,
                entry.gameMode,
                false,
                onFailure = { onStatus("Couldn't start hosting: ${it.message}") },
                onHosted = { sessionId -> SessionsRepository.invite(sessionId, player); onStatus("Hosting ${entry.name} - invite sent") },
            )
        }
    }
}

@Composable
private fun BlockedRow(entry: BlockedPlayer) {
    FlatRow {
        SocialText(PlayerNamesRepository.displayName(entry.player), fontSize = 12.sp, modifier = Modifier.weight(1f))
        TacticalButton("Unblock") { FriendsRepository.unblock(entry.player) }
    }
}

@Composable
private fun RequestsTab() {
    val incoming by FriendsRepository.incomingRequests.collectAsState()
    val outgoing by FriendsRepository.outgoingRequests.collectAsState()

    LaunchedEffect(incoming, outgoing) {
        PlayerNamesRepository.resolve((incoming + outgoing).map { it.player })
    }

    Column {
        SocialText("INCOMING - ${incoming.size}", fontSize = 11.sp, letterSpacing = 0.8.sp, color = TextDim)
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 195.dp)) {
            if (incoming.isEmpty()) item { EmptyRow("No incoming requests") }
            items(incoming, key = { it.id }) { request ->
                FlatRow {
                    SocialText(PlayerNamesRepository.displayName(request.player), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TacticalButton("Accept", filled = true) { FriendsRepository.acceptRequest(request.id) }
                    TacticalButton("Decline", DangerColor) { FriendsRepository.declineRequest(request.id) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SocialText("OUTGOING - ${outgoing.size}", fontSize = 11.sp, letterSpacing = 0.8.sp, color = TextDim)
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 195.dp)) {
            if (outgoing.isEmpty()) item { EmptyRow("No outgoing requests") }
            items(outgoing, key = { it.id }) { request ->
                FlatRow {
                    SocialText(PlayerNamesRepository.displayName(request.player), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TacticalButton("Cancel", DangerColor) { FriendsRepository.cancelRequest(request.id) }
                }
            }
        }
    }
}

@Composable
private fun ChatsTab(onOpen: (Int) -> Unit) {
    val groups by GroupsRepository.groups.collectAsState()

    LaunchedEffect(groups) {
        PlayerNamesRepository.resolve(groups.flatMap { it.members })
    }

    Column {
        PlayerLookupInputRow("Username for a new group...", "New group") {
            GroupsRepository.createGroup("Untitled group", listOf(it))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 370.dp)) {
            if (groups.isEmpty()) item { EmptyRow("No chats yet - add a friend and hit DM") }
            items(groups, key = { it.id }) { group ->
                FlatRow(modifier = Modifier.clickableWithSound { onOpen(group.id) }) {
                    if (group.unread) {
                        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(Accent))
                    }
                    Icon(
                        ICONS + if (group.kind == GroupKind.Dm) "message-chat-circle.svg" else "users-01.svg",
                        TextDim,
                        Modifier.size(14.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        SocialText(chatTitle(group), fontSize = 12.sp, fontWeight = if (group.unread) FontWeight.SemiBold else FontWeight.Normal)
                        group.lastMessage?.let {
                            SocialText(it.content, fontSize = 11.sp, color = TextDim, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun chatTitle(group: GroupSummary): String {
    if (group.name != null) return group.name
    val selfUuid = ClientPlatform.localPlayerUuid().toString()
    val others = group.members.filter { it != selfUuid }.ifEmpty { group.members }
    return others.map { PlayerNamesRepository.displayName(it) }.joinToString(", ")
}

@Composable
private fun ChatPopout(groupId: Int, onClose: () -> Unit) {
    val groups by GroupsRepository.groups.collectAsState()
    val group = groups.firstOrNull { it.id == groupId }
    val messages by GroupsRepository.messagesFlow(groupId).collectAsState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(groupId) { GroupsRepository.loadMessages(groupId) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        messages.lastOrNull()?.let { GroupsRepository.markRead(groupId, it.id) }
    }

    Column(
        modifier = Modifier
            .width(PopoutWidth)
            .heightIn(max = 340.dp)
            .clip(PanelShapeValue)
            .background(FrameBg)
            .border(1.dp, LocalTheme.current.borderColor, PanelShapeValue)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialText(
                (group?.let { chatTitle(it) } ?: "CHAT").uppercase(),
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x1AFFFFFF))
                    .clickableWithSound(onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ICONS + "x-close.svg", TextBright, Modifier.size(9.dp))
            }
        }
        HairlineDivider()
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp).padding(horizontal = 12.dp)) {
            if (messages.isEmpty()) item { EmptyRow("No messages yet") }
            items(messages, key = { it.id }) { message -> MessageRow(message) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextBright, fontSize = 11.sp, fontFamily = LocalTheme.current.typography.family),
                    cursorBrush = SolidColor(Accent),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) SocialText("MESSAGE...", fontSize = 10.sp, letterSpacing = 0.5.sp, color = TextDim)
                        inner()
                    },
                )
            }
            TacticalButton("Send", filled = draft.isNotBlank()) {
                if (draft.isNotBlank()) {
                    GroupsRepository.sendMessage(groupId, draft.trim())
                    draft = ""
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: GroupMessage) {
    FlatRow {
        SocialText(PlayerNamesRepository.displayName(message.sender), fontSize = 10.sp, letterSpacing = 0.5.sp, color = TextDim)
        SocialText(message.content, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GlobalTab() {
    val messages by GlobalChatRepository.messages.collectAsState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(messages) { PlayerNamesRepository.resolve(messages.map { it.sender }) }

    Column {
        LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
            if (messages.isEmpty()) item { EmptyRow("No global chat messages yet") }
            items(messages, key = { it.id }) { message -> GlobalMessageRow(message) }
        }
        Spacer(Modifier.height(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextBright, fontSize = 12.sp, fontFamily = LocalTheme.current.typography.family),
                        cursorBrush = SolidColor(Accent),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) SocialText("SAY SOMETHING...", fontSize = 11.sp, letterSpacing = 0.5.sp, color = TextDim)
                            inner()
                        },
                    )
                }
                TacticalButton("Send", filled = draft.isNotBlank()) {
                    if (draft.isNotBlank()) {
                        GlobalChatRepository.send(draft.trim())
                        draft = ""
                    }
                }
            }
            HairlineDivider(color = if (draft.isEmpty()) DividerColor else Accent.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun GlobalMessageRow(message: GlobalChatMessage) {
    FlatRow {
        SocialText(PlayerNamesRepository.displayName(message.sender), fontSize = 10.sp, letterSpacing = 0.5.sp, color = TextDim)
        SocialText(message.content, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SessionsTab() {
    val invites by SessionsRepository.incomingInvites.collectAsState()
    val hostingId = P2PSessionManager.currentSessionId
    val eosStatus by P2PSessionManager.status.collectAsState()
    var joinFailure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(invites) { PlayerNamesRepository.resolve(invites.map { it.sender }) }
    LaunchedEffect(Unit) {
        P2PSessionManager.joinFailures.collect { joinFailure = it }
    }

    Column {
        FlatRow {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (hostingId != null) Accent else TextDim),
            )
            SocialText(
                when {
                    hostingId != null -> "HOSTING A P2P SESSION"
                    eosStatus is EosStatus.Connecting -> "CONNECTING TO MULTIPLAYER SERVICES..."
                    eosStatus is EosStatus.Failed -> "MULTIPLAYER SERVICES UNAVAILABLE"
                    else -> "NOT HOSTING A P2P SESSION"
                },
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = if (eosStatus is EosStatus.Failed) DangerColor else TextBright,
                modifier = Modifier.weight(1f),
            )
        }
        if (hostingId != null) {
            Spacer(Modifier.height(10.dp))
            PlayerLookupInputRow("Username to invite...", "Invite") { SessionsRepository.invite(hostingId, it) }
        }
        joinFailure?.let {
            Spacer(Modifier.height(8.dp))
            SocialText(it.uppercase(), fontSize = 10.sp, letterSpacing = 0.5.sp, color = DangerColor)
        }
        Spacer(Modifier.height(14.dp))
        SocialText("INCOMING INVITES - ${invites.size}", fontSize = 11.sp, letterSpacing = 0.8.sp, color = TextDim)
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            if (invites.isEmpty()) item { EmptyRow("No pending invites") }
            items(invites, key = { it.id }) { invite -> SessionInviteRow(invite) }
        }
    }
}

@Composable
private fun SessionInviteRow(invite: SessionInvite) {
    FlatRow {
        Column(modifier = Modifier.weight(1f)) {
            SocialText("FROM ${PlayerNamesRepository.displayName(invite.sender).uppercase()}", fontSize = 12.sp)
        }
        TacticalButton("Accept", filled = true) { SessionsRepository.accept(invite) }
        TacticalButton("Decline", DangerColor) { SessionsRepository.decline(invite) }
    }
}

@Composable
private fun SocialText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = TextBright,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = maxLines != 1,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            fontFamily = LocalTheme.current.typography.family,
        ),
    )
}
//?}
