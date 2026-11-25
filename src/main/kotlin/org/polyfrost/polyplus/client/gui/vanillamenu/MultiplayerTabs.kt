package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.featured.normalizeServerAddress
import org.polyfrost.polyplus.client.party.PartyJoin
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
import org.polyfrost.polyplus.privacy.PrivacyConsent

/** Servers, Friends and Discover tabs along the top of the vanilla multiplayer screen, like Essential's. */
object MultiplayerTabs {
    enum class Tab(val label: String) { Servers("Servers"), Friends("Friends"), Discover("Discover") }

    private const val TAB_WIDTH = 72
    private const val TAB_GAP = 4
    private const val TAB_Y = 6

    fun enabled(): Boolean = PolyPlusMainMenuConfig.multiplayerTabs

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen.javaClass != JoinMultiplayerScreen::class.java || !enabled()) return@register
            // the tabs take the title's place in the header
            screen.children().filterIsInstance<StringWidget>().filter { it.y < 33 }.forEach { it.visible = false }
            addTabs(screen, Tab.Servers, (screen as JoinMultiplayerScreenParent).`polyplus$lastScreen`())
        }
    }

    fun addTabs(screen: Screen, current: Tab, parent: Screen?) {
        val total = Tab.entries.size * TAB_WIDTH + (Tab.entries.size - 1) * TAB_GAP
        Tab.entries.forEachIndexed { index, tab ->
            val selected = tab == current
            val label = Component.literal(tab.label).let { if (selected) it.withStyle(ChatFormatting.UNDERLINE, ChatFormatting.YELLOW) else it }
            val button = Button.builder(label) { if (!selected) open(tab, parent) }
                .bounds(screen.width / 2 - total / 2 + index * (TAB_WIDTH + TAB_GAP), TAB_Y, TAB_WIDTH, 20)
                .build()
            (screen as ScreenAccessor).polyplusAddRenderableWidget(button)
        }
    }

    fun open(tab: Tab, parent: Screen?) = ClientPlatform.setScreen(
        when (tab) {
            Tab.Servers -> JoinMultiplayerScreen(parent ?: ClientPlatform.currentScreen()!!)
            Tab.Friends -> FriendsWorldsScreen(parent)
            Tab.Discover -> DiscoverServersScreen(parent)
        },
    )

    fun connect(from: Screen, name: String, address: String) {
        val data = ServerData(name, address, ServerData.Type.OTHER)
        ConnectScreen.startConnecting(from, Minecraft.getInstance(), ServerAddress.parseString(address), data, false, null)
    }
}

/**
 * A vanilla style list made of plain buttons: one row per entry, with an optional small button on the right,
 * paged instead of scrolled so it works the same on every version.
 */
abstract class RowListScreen(title: Component, protected val parent: Screen?) : Screen(title) {
    class Row(val label: Component, val tooltip: Component? = null, val side: Pair<Component, () -> Unit>? = null, val onPress: (() -> Unit)?)

    private var page = 0
    protected open val tab: MultiplayerTabs.Tab? = null

    protected abstract fun rows(): List<Row>

    protected open fun emptyText(): Component = Component.literal("Nothing here yet.")

    protected open fun footerButtons(): List<Button> = emptyList()

    override fun init() {
        val access = this as ScreenAccessor
        val tab = tab
        if (tab != null && MultiplayerTabs.enabled()) {
            MultiplayerTabs.addTabs(this, tab, parent)
        } else {
            access.polyplusAddRenderableWidget(centered(title, 14))
        }
        val rows = rows()
        val top = 40
        val bottom = height - 56
        val perPage = ((bottom - top) / 24).coerceAtLeast(1)
        val pages = (rows.size + perPage - 1) / perPage
        page = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
        val rowWidth = 260.coerceAtMost(width - 20)
        val left = width / 2 - rowWidth / 2
        if (rows.isEmpty()) {
            access.polyplusAddRenderableWidget(centered(emptyText().copy().withStyle(ChatFormatting.GRAY), top + 20))
        }
        rows.drop(page * perPage).take(perPage).forEachIndexed { index, row ->
            val y = top + index * 24
            val sideWidth = if (row.side != null) 50 else 0
            val main = Button.builder(row.label) { row.onPress?.invoke() }
                .bounds(left, y, rowWidth - if (sideWidth > 0) sideWidth + 4 else 0, 20)
                .build()
            main.active = row.onPress != null
            row.tooltip?.let { main.setTooltip(Tooltip.create(it)) }
            access.polyplusAddRenderableWidget(main)
            row.side?.let { (label, action) ->
                access.polyplusAddRenderableWidget(Button.builder(label) { action() }.bounds(left + rowWidth - sideWidth, y, sideWidth, 20).build())
            }
        }
        val footerY = height - 28
        val footer = footerButtons() + Button.builder(CommonComponents.GUI_BACK) { onClose() }.build()
        val footerWidth = 74
        val totalWidth = footer.size * footerWidth + (footer.size - 1) * 4
        footer.forEachIndexed { index, button ->
            button.setRectangle(footerWidth, 20, width / 2 - totalWidth / 2 + index * (footerWidth + 4), footerY)
            access.polyplusAddRenderableWidget(button)
        }
        if (pages > 1) {
            val pageY = footerY - 24
            val previous = Button.builder(Component.literal("<")) { page--; rebuildWidgets() }.bounds(width / 2 - 70, pageY, 20, 20).build()
            val next = Button.builder(Component.literal(">")) { page++; rebuildWidgets() }.bounds(width / 2 + 50, pageY, 20, 20).build()
            previous.active = page > 0
            next.active = page < pages - 1
            access.polyplusAddRenderableWidget(previous)
            access.polyplusAddRenderableWidget(StringWidget(width / 2 - 45, pageY + 6, 90, 9, Component.literal("Page ${page + 1} of $pages"), font))
            access.polyplusAddRenderableWidget(next)
        }
    }

    private fun centered(text: Component, y: Int): StringWidget {
        val textWidth = font.width(text)
        return StringWidget(width / 2 - textWidth / 2, y, textWidth, 9, text, font)
    }

    protected fun refresh() = rebuildWidgets()

    override fun onClose() = ClientPlatform.setScreen(parent)
}

/** Worlds your friends are hosting and invited you to, and which friends are online. */
class FriendsWorldsScreen(parent: Screen?) : RowListScreen(Component.literal("Friends"), parent) {
    override val tab = MultiplayerTabs.Tab.Friends
    private var lastInvites: Any? = null
    private var lastFriends: Any? = null

    override fun init() {
        if (PrivacyConsent.allowsOnlineServices()) {
            SessionsRepository.refreshIncoming()
            FriendsRepository.refreshAll()
        }
        super.init()
    }

    override fun tick() {
        super.tick()
        // the lists arrive from the backend after the screen opens
        val invites = SessionsRepository.incomingInvites.value
        val friends = FriendsRepository.friends.value
        if (invites !== lastInvites || friends !== lastFriends) {
            lastInvites = invites
            lastFriends = friends
            refresh()
        }
    }

    override fun emptyText(): Component = Component.literal(
        if (PrivacyConsent.allowsOnlineServices()) "No friends are hosting a world for you right now."
        else "Online services are off. Turn them on in Social to see your friends' worlds.",
    )

    override fun rows(): List<Row> {
        if (!PrivacyConsent.allowsOnlineServices()) return emptyList()
        val invites = SessionsRepository.incomingInvites.value
        val worlds = invites.map { invite ->
            val host = PlayerNamesRepository.nameOr(invite.sender)
            val version = listOfNotNull(invite.minecraftVersion, invite.loader?.replaceFirstChar { it.uppercase() }).joinToString(" ")
            val label = Component.literal("$host's world").append(
                if (version.isEmpty()) Component.empty() else Component.literal("  $version").withStyle(ChatFormatting.GRAY),
            )
            Row(
                label,
                Component.literal("Join the world $host is hosting"),
                side = Component.literal("Decline") to { SessionsRepository.decline(invite); refresh() },
            ) { PartyJoin.world("$host's world") { SessionsRepository.accept(invite) } }
        }
        val online = FriendsRepository.friends.value.filter { it.online }.map { friend ->
            val name = PlayerNamesRepository.nameOr(friend.player)
            Row(
                Component.literal(name).append(Component.literal("  online, not hosting").withStyle(ChatFormatting.GRAY)),
                Component.literal("Open Social to message $name or invite them"),
                onPress = { SocialOverlay.open(this) },
            )
        }
        return worlds + online
    }

    override fun footerButtons(): List<Button> = listOf(
        Button.builder(Component.literal("Social")) { SocialOverlay.open(this) }.build(),
        Button.builder(Component.literal("Host World")) { MenuPanelScreen.open(MenuPanelScreen.Panel.HostWorld) }.build(),
    )
}

/** Browse the servers in OneClient's server catalog, join one or add it to your list. */
class DiscoverServersScreen(parent: Screen?) : RowListScreen(Component.literal("Discover"), parent) {
    override val tab = MultiplayerTabs.Tab.Discover
    private var revision = Long.MIN_VALUE

    override fun init() {
        FeaturedServers.warmUp()
        revision = FeaturedServers.revision()
        super.init()
    }

    override fun tick() {
        super.tick()
        if (FeaturedServers.revision() != revision) {
            revision = FeaturedServers.revision()
            refresh()
        }
    }

    override fun emptyText(): Component = Component.literal(
        if (PrivacyConsent.allowsOnlineServices()) "Loading servers..." else "Online services are off, so the server list can't load.",
    )

    override fun rows(): List<Row> {
        val saved = savedAddresses()
        return FeaturedServers.snapshot().visibleServers().map { server ->
            val isSaved = normalizeServerAddress(server.address) in saved
            Row(
                Component.literal(server.name).append(Component.literal("  ${server.address}").withStyle(ChatFormatting.GRAY)),
                server.featured?.description?.let { Component.literal(it) },
                side = if (isSaved) null else Component.literal("Add") to { save(server.name, server.address); refresh() },
            ) { MultiplayerTabs.connect(this, server.name, server.address) }
        }
    }

    private fun savedAddresses(): Set<String> {
        val list = ServerList(Minecraft.getInstance())
        list.load()
        return (0 until list.size()).map { normalizeServerAddress(list.get(it).ip) }.toSet()
    }

    private fun save(name: String, address: String) {
        val list = ServerList(Minecraft.getInstance())
        list.load()
        list.add(ServerData(name, address, ServerData.Type.OTHER), false)
        list.save()
        VanillaTitleMenu.toast("Server added", "$name is in your server list now.")
    }
}

/** The servers you played on last, one click to join again. */
class QuickplayScreen(parent: Screen?) : RowListScreen(Component.literal("Quickplay"), parent) {
    override fun emptyText(): Component = Component.literal("Servers you join show up here.")

    override fun rows(): List<Row> = PolyPlusRecentServers.recent().take(12).map { server ->
        val named = !server.name.equals(server.ip, ignoreCase = true) && !PolyPlusRecentServers.isDefaultName(server.name)
        val label = if (named) Component.literal(server.name).append(Component.literal("  ${server.ip}").withStyle(ChatFormatting.GRAY)) else Component.literal(server.ip)
        Row(label, Component.literal("Join ${server.ip}")) { MultiplayerTabs.connect(this, server.name, server.ip) }
    }

    override fun footerButtons(): List<Button> = listOf(
        Button.builder(Component.translatable("menu.multiplayer")) { ClientPlatform.setScreen(JoinMultiplayerScreen(parent ?: this)) }.build(),
    )
}
//?}
