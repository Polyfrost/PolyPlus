package org.polyfrost.polyplus.client.gui.screens.connection

//? if > 1.8.9 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
//? if >= 26.3 {
import com.mojang.blaze3d.Blaze3D
//?} elif >= 1.21.11 {
/*import net.minecraft.util.Util
*///?} else {
/*import net.minecraft.Util
*///?}
import net.minecraft.client.Minecraft
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*import net.minecraft.client.gui.GuiGraphics
*///?}
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.launcher.SessionRefresh
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ConnectionDisconnectedScreenAccessor

class DisconnectedShellScreen(shell: ScreenShell, val vanilla: DisconnectedScreen) :
    ShellScreen(shell, (vanilla as ConnectionDisconnectedScreenAccessor).polyplusParent()) {
    private val access = vanilla as ConnectionDisconnectedScreenAccessor
    private val details = access.polyplusDetails()
    private val prompt = SessionRefresh.createPrompt()
    private val promptButton: Button? = prompt?.let { p -> Button.builder(p.label()) { p.onPress() }.width(200).build() }
    private val hold = SceneHold(this)
    private val server = ConnectionTarget.server?.takeIf { it.ip != P2PSessionManager.P2P_PLACEHOLDER_IP }
    private val serverName = server?.let { ConnectionTarget.title() }

    private var refreshLabel by mutableStateOf<String?>(null)
    private var refreshEnabled by mutableStateOf(false)
    private var canRejoin by mutableStateOf(P2PSessionManager.canRejoin())

    init {
        if (prompt != null && promptButton != null) prompt.attach(promptButton, this)
        syncPrompt()
    }

    override val title: String get() = vanilla.title.string

    override val closeOnEscape: Boolean get() = false

    override val chrome: Boolean get() = false

    override fun init() {
        hold.attach(shell)
        super.init()
    }

    override fun removed() {
        hold.detach()
        super.removed()
    }

    //? if >= 26.1 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        hold.frame()
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
        hold.veil(ctx, width, height)
    }
    //?} else {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        hold.frame()
        super.render(ctx, mouseX, mouseY, tickDelta)
        hold.veil(ctx, width, height)
    }
    *///?}

    override fun tick() {
        super.tick()
        hold.settle()
        hold.tick()
        syncPrompt()
        canRejoin = P2PSessionManager.canRejoin()
    }

    private fun syncPrompt() {
        val button = promptButton ?: return
        refreshLabel = button.message.string
        refreshEnabled = button.active
    }

    private fun leave() {
        hold.leave {
            if (Minecraft.getInstance().allowsMultiplayer()) ClientPlatform.setScreen(parent)
            else ClientPlatform.setScreen(TitleScreen())
        }
    }

    private fun leaveLabel(): String =
        if (Minecraft.getInstance().allowsMultiplayer()) access.polyplusButtonText().string
        else Component.translatable("gui.toTitle").string

    @Composable
    override fun Content() {
        if (hold.kept) HeldSceneFrame(hold) { Body() } else super.Content()
    }

    @Composable
    override fun Body() {
        ConnectionCard(
            shell,
            title,
            server,
            serverName,
            content = { ConnectionMessage(details.reason().string) },
        ) {
            details.bugReportLink().orElse(null)?.let { uri ->
                ConnectionAction(Component.translatable("gui.report_to_server").string) {
                    ConfirmLinkScreen.confirmLinkNow(this@DisconnectedShellScreen, uri, false)
                }
            }
            details.report().orElse(null)?.let { report ->
                ConnectionAction(Component.translatable("gui.open_report_dir").string) {
                    //? if >= 26.3 {
                    Blaze3D.openPath(report.parent)
                    //?} else {
                    /*Util.getPlatform().openPath(report.parent)
                    *///?}
                }
            }
            refreshLabel?.let { label ->
                ConnectionAction(label, enabled = refreshEnabled) { prompt?.onPress() }
            }
            if (canRejoin) ConnectionAction("Rejoin world", primary = true) { P2PSessionManager.rejoin() }
            ConnectionAction(leaveLabel(), primary = !canRejoin, onClick = ::leave)
        }
    }
}
//?} elif = 1.8.9 {
/*import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.gui.screen.menu.multiplayer.DisconnectedScreen
import net.minecraft.client.gui.widget.ButtonWidget
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.launcher.SessionRefresh
import org.polyfrost.polyplus.client.lobby.LobbyI18n
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.LegacyDisconnectedScreenAccessor

class DisconnectedShellScreen(shell: ScreenShell, val vanilla: DisconnectedScreen) :
    ShellScreen(shell, (vanilla as LegacyDisconnectedScreenAccessor).polyplusParent()) {
    private val access = vanilla as LegacyDisconnectedScreenAccessor
    private val heading = access.polyplusTitle()
    private val reason = access.polyplusReason().formattedString
    private val prompt = SessionRefresh.createPrompt()
    private val promptButton: ButtonWidget? = prompt?.let { p -> ButtonWidget(0, 0, 0, p.label().string) }
    private val hold = SceneHold(this)
    private val server = ConnectionTarget.server?.takeIf { it.ip != P2PSessionManager.P2P_PLACEHOLDER_IP }
    private val serverName = server?.let { ConnectionTarget.title() }

    private var refreshLabel by mutableStateOf<String?>(null)
    private var refreshEnabled by mutableStateOf(false)
    private var canRejoin by mutableStateOf(P2PSessionManager.canRejoin())

    init {
        if (prompt != null && promptButton != null) prompt.attach(promptButton, this)
        syncPrompt()
    }

    override val title: String get() = heading

    override val closeOnEscape: Boolean get() = false

    override val chrome: Boolean get() = false

    override fun init() {
        hold.attach(shell)
        super.init()
    }

    override fun removed() {
        hold.detach()
        super.removed()
    }

    override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        hold.frame()
        super.render(mouseX, mouseY, tickDelta)
        hold.veil(width, height)
    }

    override fun tick() {
        super.tick()
        hold.settle()
        hold.tick()
        syncPrompt()
        canRejoin = P2PSessionManager.canRejoin()
    }

    private fun syncPrompt() {
        val button = promptButton ?: return
        refreshLabel = button.message
        refreshEnabled = button.active
    }

    private fun leave() {
        hold.leave { ClientPlatform.setScreen(parent) }
    }

    @Composable
    override fun Content() {
        if (hold.kept) HeldSceneFrame(hold) { Body() } else super.Content()
    }

    @Composable
    override fun Body() {
        ConnectionCard(
            shell,
            title,
            server,
            serverName,
            content = { ConnectionMessage(reason) },
        ) {
            refreshLabel?.let { label ->
                ConnectionAction(label, enabled = refreshEnabled) { prompt?.onPress() }
            }
            if (canRejoin) ConnectionAction("Rejoin world", primary = true) { P2PSessionManager.rejoin() }
            ConnectionAction(LobbyI18n.get("gui.toMenu"), primary = !canRejoin, onClick = ::leave)
        }
    }
}
*///?}
