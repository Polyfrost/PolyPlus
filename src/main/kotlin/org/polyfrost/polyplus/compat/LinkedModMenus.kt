package org.polyfrost.polyplus.compat

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.lang.reflect.Method
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

internal object LinkedModMenus {
    enum class Entry { Friends, Wardrobe, Screenshots, Host, FriendsWorlds, Accounts, Settings }

    private const val ROOT = "gg.essential."
    private const val MOD_ID = "essential"
    private const val FRIENDS_TAB = 1
    private const val LOGO = "assets/essential/textures/menu/logo.png"

    private val logger = LogManager.getLogger("PolyPlus/LinkedModMenus")
    private val noticesShown = AtomicBoolean(false)

    private val actions: Map<Entry, () -> Unit> by lazy {
        runCatching(::resolve).onFailure { logger.warn("Couldn't read the linked mod's menus", it) }.getOrDefault(emptyMap())
    }

    val entries: List<Entry> get() = Entry.entries.filter { it in actions }

    val displayName: String? by lazy {
        runCatching { FabricLoader.getInstance().getModContainer(MOD_ID).map { it.metadata.name }.orElse(null) }.getOrNull()
    }

    val logo: ByteArray? by lazy {
        runCatching {
            val path = FabricLoader.getInstance().getModContainer(MOD_ID).flatMap { it.findPath(LOGO) }.orElse(null)
            path?.let(Files::readAllBytes) ?: LinkedModMenus::class.java.classLoader.getResourceAsStream(LOGO)?.use { it.readBytes() }
        }.onFailure { logger.warn("Couldn't read the linked mod's logo", it) }.getOrNull()
    }

    fun has(entry: Entry): Boolean = entry in actions

    fun open(entry: Entry) {
        val action = actions[entry] ?: return
        runCatching(action).onFailure { logger.warn("Couldn't open the linked mod's {} menu", entry, it) }
    }

    fun showStartupNotices(screen: Screen) {
        if (actions.isEmpty() || !noticesShown.compareAndSet(false, true)) return
        runCatching {
            val display = find("handlers.PauseMenuDisplay") ?: return
            val show = display.getDeclaredMethod("initModals", Screen::class.java).apply { isAccessible = true }
            show.invoke(display.getConstructor().newInstance(), screen)
        }.onFailure { logger.warn("Couldn't show the linked mod's startup notices", it) }
    }

    private fun resolve(): Map<Entry, () -> Unit> {
        if (find("Essential") == null) return emptyMap()
        val out = LinkedHashMap<Entry, () -> Unit>()
        fun put(entry: Entry, action: (() -> Unit)?) {
            if (action != null) out[entry] = action
        }
        put(Entry.Friends, screenOf("gui.friends.SocialMenu"))
        put(Entry.Wardrobe, screenOf("gui.wardrobe.Wardrobe"))
        put(Entry.Screenshots, screenOf("gui.screenshot.components.ScreenshotBrowser"))
        put(Entry.Host, staticCall("gui.sps.InviteOrHostModalFlowKt", "launchInviteOrHostModalFlow"))
        put(Entry.FriendsWorlds, serverList())
        put(Entry.Accounts, accounts())
        put(Entry.Settings, settings())
        return out
    }

    private fun find(name: String): Class<*>? = Reflect.find(ROOT + name)

    private fun instance(owner: Class<*>): Any? = runCatching { owner.getField("INSTANCE").get(null) }.getOrNull()

    private val openScreen: Method? by lazy {
        find("util.GuiUtil")?.let { Reflect.method(it, "openScreen", Class::class.java, Function0::class.java) }
    }

    private fun opener(screen: Class<*>, factory: () -> Any): (() -> Unit)? {
        val open = openScreen ?: return null
        return { open.invoke(null, screen, factory) }
    }

    private fun screenOf(name: String): (() -> Unit)? {
        val screen = find(name) ?: return null
        val create = runCatching { screen.getConstructor() }.getOrNull() ?: return null
        return opener(screen) { create.newInstance() }
    }

    private fun staticCall(owner: String, name: String): (() -> Unit)? {
        val call = find(owner)?.let { Reflect.method(it, name) } ?: return null
        return { call.invoke(null) }
    }

    private fun settings(): (() -> Unit)? {
        val config = find("config.McEssentialConfig") ?: return null
        val holder = instance(config) ?: return null
        val gui = Reflect.method(config, "gui") ?: return null
        return opener(gui.returnType) { gui.invoke(holder) }
    }

    private fun accounts(): (() -> Unit)? {
        val util = find("util.GuiUtil") ?: return null
        val holder = instance(util) ?: return null
        val push = Reflect.method(util, "pushModal", Function1::class.java) ?: return null
        val manager = find("gui.menu.AccountManager") ?: return null
        val create = runCatching { manager.getConstructor() }.getOrNull() ?: return null
        val all = Reflect.method(manager, "getAllAccounts") ?: return null
        val modal = find("gui.menu.AccountManagerModal")?.constructors?.firstOrNull { it.parameterCount == 3 } ?: return null
        return {
            val accounts = create.newInstance()
            val build: (Any?) -> Any = { modals -> modal.newInstance(modals, accounts, all.invoke(accounts)) }
            push.invoke(holder, build)
        }
    }

    private fun serverList(): (() -> Unit)? {
        if (find("gui.multiplayer.EssentialMultiplayerGui") == null) return null
        val config = find("config.EssentialConfig")
        val holder = config?.let(::instance)
        val tab = config?.let { Reflect.method(it, "setCurrentMultiplayerTab", Integer.TYPE) }
        return open@{
            val parent = ClientPlatform.currentScreen() ?: return@open
            if (holder != null && tab != null) runCatching { tab.invoke(holder, FRIENDS_TAB) }
            ClientPlatform.setScreen(object : JoinMultiplayerScreen(parent) {})
        }
    }
}
