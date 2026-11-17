package org.polyfrost.polyplus.client.gui.screens.connection

//? if > 1.8.9 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*import net.minecraft.client.gui.GuiGraphics
*///?}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ConnectionConnectScreenAccessor

object ConnectionTarget {
    @Volatile
    var server: ServerData? = null
        private set

    @Volatile
    var address: String? = null
        private set

    @JvmStatic
    fun note(server: ServerData?, address: ServerAddress?) {
        TerrainCard.forget()
        this.server = server
        this.address = server?.ip ?: address?.let { if (it.port == DEFAULT_PORT) it.host else "${it.host}:${it.port}" }
    }

    fun clear() {
        server = null
        address = null
    }

    fun title(): String? {
        val hide = Minecraft.getInstance().options.hideServerAddress
        val data = server ?: return if (hide) address?.let { HIDDEN } else address
        val name = data.name.trim()
        if (name.isEmpty() || name == I18n.get("selectServer.defaultName") || (hide && name.equals(data.ip, ignoreCase = true))) {
            return if (hide) HIDDEN else data.ip
        }
        return name
    }

    fun subtitle(): String? {
        if (Minecraft.getInstance().options.hideServerAddress) return null
        val data = server ?: return null
        return data.ip.takeIf { it != title() }
    }

    private const val HIDDEN = "Multiplayer server"

    private const val DEFAULT_PORT = 25565
}

class ConnectingScreen(shell: ScreenShell, val vanilla: ConnectScreen) :
    ShellScreen(shell, (vanilla as ConnectionConnectScreenAccessor).polyplusParent()) {
    private val access = vanilla as ConnectionConnectScreenAccessor
    private val hold = SceneHold(this)
    private val server = ConnectionTarget.server
    private val heading = ConnectionTarget.title() ?: "Joining server"
    private val address = ConnectionTarget.subtitle()

    private var step by mutableStateOf(0)
    private var label by mutableStateOf(FINDING)
    private var landing by mutableStateOf(false)
    private var lastNarration = -1L

    override val title: String get() = heading

    override val closeOnEscape: Boolean get() = false

    override val chrome: Boolean get() = false

    init {
        readStatus()
    }

    private fun narrate() {
        val now = System.currentTimeMillis()
        if (now - lastNarration <= NARRATION_DELAY_MS) return
        lastNarration = now
        //? if >= 1.21.8 {
        Minecraft.getInstance().narrator.saySystemNow(Component.translatable("narrator.joining"))
        //?} else {
        /*Minecraft.getInstance().narrator.sayNow(Component.translatable("narrator.joining"))
        *///?}
    }

    override fun init() {
        hold.attach(shell)
        super.init()
    }

    override fun removed() {
        hold.detach()
        super.removed()
    }

    override fun tick() {
        super.tick()
        if (hold.leaving) {
            hold.settle()
            return
        }
        vanilla.tick()
        hold.tick()
        readStatus()
    }

    //? if >= 26.1 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        hold.frame()
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
        hold.veil(ctx, width, height)
        narrate()
    }
    //?} else {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        hold.frame()
        super.render(ctx, mouseX, mouseY, tickDelta)
        hold.veil(ctx, width, height)
        narrate()
    }
    *///?}

    private fun readStatus() {
        val status = access.polyplusStatus()
        val connection = access.polyplusConnection()
        val reaching = connection != null || access.polyplusChannelFuture() != null
        when ((status.contents as? TranslatableContents)?.key) {
            "connect.connecting" -> show(if (reaching) 1 else 0, if (reaching) CONNECTING else FINDING)
            "connect.transferring" -> show(if (reaching) 1 else 0, "Moving to another server")
            "connect.authorizing" -> show(2, "Logging in")
            "connect.encrypting" -> show(2, "Securing the connection")
            "connect.negotiating" -> show(2, "Talking to the server")
            "connect.joining" ->
                if (connection?.packetListener is ClientConfigurationPacketListenerImpl) {
                    landing = true
                    show(3, "Getting the world ready")
                }
                else show(3, "Joining")
            else -> show(step, status.string.ifBlank { label })
        }
    }

    private fun show(next: Int, text: String) {
        step = next
        label = text
    }

    private fun cancel() {
        if (hold.leaving) return
        synchronized(vanilla) {
            access.polyplusSetAborted(true)
            access.polyplusChannelFuture()?.cancel(true)
            access.polyplusSetChannelFuture(null)
            access.polyplusConnection()?.disconnect(ConnectScreen.ABORT_CONNECTION)
        }
        hold.leave { ClientPlatform.setScreen(parent) }
    }

    @Composable
    override fun Content() {
        if (hold.kept) HeldSceneFrame(hold, landing) { Body() } else super.Content()
    }

    @Composable
    override fun Body() {
        ConnectionCard(
            shell,
            heading,
            server,
            address,
            content = { ConnectionSteps(label, step, CONNECTION_STEPS) },
        ) {
            ConnectionAction(CommonComponents.GUI_CANCEL.string, onClick = ::cancel)
        }
    }

    internal fun terrainClaim(): TerrainCard.Claim = TerrainCard.Claim(shell, heading, address, server, true)

    private companion object {
        const val FINDING = "Finding the server"
        const val CONNECTING = "Connecting"
        const val NARRATION_DELAY_MS = 2000L
    }
}
//?} elif = 1.8.9 {
/*import androidx.compose.runtime.Composable
import net.minecraft.client.Minecraft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen
import net.minecraft.client.network.handler.ClientPlayNetworkHandler
import net.minecraft.client.options.ServerListEntry
import net.minecraft.text.LiteralText
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.lobby.LobbyCommonComponents
import org.polyfrost.polyplus.client.lobby.LobbyI18n
import org.polyfrost.polyplus.client.lobby.LobbyServerData
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.LegacyConnectScreenAccessor

object ConnectionTarget {
    @Volatile
    var server: LobbyServerData? = null
        private set

    @Volatile
    var address: String? = null
        private set

    @JvmStatic
    fun note(server: ServerListEntry?, host: String?, port: Int) {
        this.server = server?.let(::LobbyServerData)
        this.address = server?.ip ?: host?.let { if (port == DEFAULT_PORT) it else "$it:$port" }
    }

    fun clear() {
        server = null
        address = null
    }

    fun title(): String? {
        val hide = Minecraft.getInstance().options.hideServerAddress
        val data = server ?: return if (hide) address?.let { HIDDEN } else address
        val name = data.name.trim()
        if (name.isEmpty() || name == LobbyI18n.get("selectServer.defaultName") || (hide && name.equals(data.ip, ignoreCase = true))) {
            return if (hide) HIDDEN else data.ip
        }
        return name
    }

    fun subtitle(): String? {
        if (Minecraft.getInstance().options.hideServerAddress) return null
        val data = server ?: return null
        return data.ip.takeIf { it != title() }
    }

    private const val HIDDEN = "Multiplayer server"

    private const val DEFAULT_PORT = 25565
}

class ConnectingScreen(shell: ScreenShell, val vanilla: ConnectScreen) :
    ShellScreen(shell, (vanilla as LegacyConnectScreenAccessor).polyplusParent()) {
    private val access = vanilla as LegacyConnectScreenAccessor
    private val hold = SceneHold(this)
    private val server = ConnectionTarget.server
    private val heading = ConnectionTarget.title() ?: "Joining server"
    private val address = ConnectionTarget.subtitle()

    private var step by mutableStateOf(0)
    private var label by mutableStateOf(CONNECTING)
    private var landing by mutableStateOf(false)

    override val title: String get() = heading

    override val closeOnEscape: Boolean get() = false

    override val chrome: Boolean get() = false

    init {
        readStatus()
    }

    override fun init() {
        hold.attach(shell)
        super.init()
    }

    override fun removed() {
        hold.detach()
        super.removed()
    }

    override fun tick() {
        super.tick()
        if (hold.leaving) {
            hold.settle()
            return
        }
        vanilla.tick()
        hold.tick()
        readStatus()
    }

    override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        hold.frame()
        super.render(mouseX, mouseY, tickDelta)
        hold.veil(width, height)
    }

    private fun readStatus() {
        val connection = access.polyplusConnection()
        when {
            connection == null -> show(0, CONNECTING)
            connection.listener is ClientPlayNetworkHandler -> {
                landing = true
                show(2, "Joining")
            }
            connection.isEncrypted -> show(1, "Securing the connection")
            else -> show(1, "Logging in")
        }
    }

    private fun show(next: Int, text: String) {
        step = next
        label = text
    }

    private fun cancel() {
        if (hold.leaving) return
        access.polyplusSetAborted(true)
        access.polyplusConnection()?.disconnect(LiteralText("Aborted"))
        hold.leave { ClientPlatform.setScreen(parent) }
    }

    @Composable
    override fun Content() {
        if (hold.kept) HeldSceneFrame(hold, landing) { Body() } else super.Content()
    }

    @Composable
    override fun Body() {
        ConnectionCard(
            shell,
            heading,
            server,
            address,
            content = { ConnectionSteps(label, step, CONNECTION_STEPS) },
        ) {
            ConnectionAction(LobbyCommonComponents.GUI_CANCEL.string, onClick = ::cancel)
        }
    }

    internal fun terrainClaim(): TerrainCard.Claim = TerrainCard.Claim(shell, heading, address, server, true)

    private companion object {
        const val CONNECTING = "Connecting"
    }
}
*///?}
