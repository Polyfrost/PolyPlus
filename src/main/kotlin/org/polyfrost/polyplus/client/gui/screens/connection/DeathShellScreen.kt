package org.polyfrost.polyplus.client.gui.screens.connection

//? if >= 26.3 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import java.net.URI
import java.util.Optional
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyDarkChrome
import org.polyfrost.polyplus.client.gui.scaledDensity
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellModal
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ConnectionDeathScreenAccessor

class DeathShellScreen(val vanilla: DeathScreen) : ShellScreen(ScreenShell.InGame, null) {
    private val access = vanilla as ConnectionDeathScreenAccessor
    private val player = access.polyplusPlayer()
    private val hardcore = access.polyplusHardcore()
    private val cause = access.polyplusCauseOfDeath()
    private val causeUrl = cause?.let(::findUrl)

    private val details = run {
        val pos = player.blockPosition()
        val scoreValue = player.score.toString()
        DeathDetails(
            title = vanilla.title.string,
            cause = cause?.string,
            causeLink = causeUrl != null,
            scoreLabel = Component.translatable("deathScreen.score.value", "").string,
            scoreValue = scoreValue,
            coordinates = "${pos.x} / ${pos.y} / ${pos.z}",
            primaryLabel = Component.translatable(if (hardcore) "deathScreen.spectate" else "deathScreen.respawn").string,
            secondaryLabel = Component.translatable("deathScreen.titleScreen").string,
        )
    }

    private var ticks by mutableIntStateOf(0)
    private var respawned by mutableStateOf(false)

    override val title: String get() = details.title

    override val closeOnEscape: Boolean get() = false

    override val chrome: Boolean get() = false

    override val pausesGame: Boolean get() = false

    override val blurBackground: Boolean get() = false

    override val dimBackground: Boolean get() = false

    override fun isAllowedInPortal(): Boolean = true

    override fun tick() {
        super.tick()
        val current = Minecraft.getInstance().player
        if (current != null && current !== player) {
            ClientPlatform.setScreen(null)
            return
        }
        if (ticks < DELAY_TICKS) ticks++
    }

    private fun respawn() {
        player.respawn()
        respawned = true
    }

    private fun exitToTitle() {
        val mc = Minecraft.getInstance()
        mc.reportingContext.draftReportHandled(mc, this, { access.polyplusHandleExitToTitleScreen() }, true)
    }

    private fun openCauseLink() {
        val uri = causeUrl ?: return
        clickUrlAction(Minecraft.getInstance(), this, uri)
    }

    @Composable
    override fun Content() {
        CompositionLocalProvider(LocalDensity provides scaledDensity(ShellModal.UI_SCALE)) {
            LobbyDarkChrome { Body() }
        }
    }

    @Composable
    override fun Body() {
        DeathOverlay(
            details,
            ready = ticks >= DELAY_TICKS,
            respawned = respawned,
            onPrimary = ::respawn,
            onSecondary = ::exitToTitle,
            onCause = ::openCauseLink,
        )
    }

    private companion object {
        const val DELAY_TICKS = 20

        fun findUrl(text: Component): URI? =
            text.visit(FormattedText.StyledContentConsumer<URI> { style: Style, _: String ->
                when (val event = style.clickEvent) {
                    is ClickEvent.OpenUrl -> Optional.of(event.uri())
                    else -> Optional.empty()
                }
            }, Style.EMPTY).orElse(null)
    }
}
//?} elif = 1.8.9 {
/*import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.ConfirmScreen
import net.minecraft.client.gui.screen.ConfirmationListener
import net.minecraft.client.gui.screen.game.DeathScreen
import net.minecraft.client.gui.screen.menu.TitleScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyDarkChrome
import org.polyfrost.polyplus.client.gui.scaledDensity
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellModal
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.polyplus.client.lobby.LobbyI18n
import org.polyfrost.polyplus.client.utils.ClientPlatform

class DeathShellScreen(val vanilla: DeathScreen) : ShellScreen(ScreenShell.InGame, null) {
    private val mc = Minecraft.getInstance()
    private val player = mc.player
    private val hardcore = mc.world.data.isHardcore
    private val integrated = mc.isIntegratedServerRunning

    private val details = DeathDetails(
        title = LobbyI18n.get(if (hardcore) "deathScreen.title.hardcore" else "deathScreen.title"),
        cause = if (hardcore) LobbyI18n.get("deathScreen.hardcoreInfo") else null,
        causeLink = false,
        scoreLabel = LobbyI18n.get("deathScreen.score") + ": ",
        scoreValue = player.score.toString(),
        coordinates = "${floor(player.x).toInt()} / ${floor(player.y).toInt()} / ${floor(player.z).toInt()}",
        primaryLabel = LobbyI18n.get(
            when {
                !hardcore -> "deathScreen.respawn"
                integrated -> "deathScreen.deleteWorld"
                else -> "deathScreen.leaveServer"
            },
        ),
        secondaryLabel = if (hardcore) null else LobbyI18n.get("deathScreen.titleScreen"),
    )

    private var ticks by mutableIntStateOf(0)
    private var respawned by mutableStateOf(false)

    override val title: String get() = details.title

    override val closeOnEscape: Boolean get() = false

    override val chrome: Boolean get() = false

    override val pausesGame: Boolean get() = false

    override val blurBackground: Boolean get() = false

    override val dimBackground: Boolean get() = false

    override fun tick() {
        super.tick()
        val current = mc.player
        if (current != null && current !== player) {
            ClientPlatform.setScreen(null)
            return
        }
        if (ticks < DELAY_TICKS) ticks++
    }

    private fun primary() {
        if (hardcore) ClientPlatform.setScreen(TitleScreen()) else respawn()
    }

    private fun respawn() {
        player.respawn()
        respawned = true
    }

    private fun exitToTitle() {
        val confirm = ConfirmScreen(
            ConfirmationListener { confirmed, _ ->
                if (confirmed) {
                    mc.world.disconnect()
                    mc.setWorld(null)
                    ClientPlatform.setScreen(TitleScreen())
                } else {
                    respawn()
                    ClientPlatform.setScreen(this)
                }
            },
            LobbyI18n.get("deathScreen.quit.confirm"),
            "",
            LobbyI18n.get("deathScreen.titleScreen"),
            LobbyI18n.get("deathScreen.respawn"),
            0,
        )
        ClientPlatform.setScreen(confirm)
        confirm.disableButtons(DELAY_TICKS)
    }

    @Composable
    override fun Content() {
        CompositionLocalProvider(LocalDensity provides scaledDensity(ShellModal.UI_SCALE)) {
            LobbyDarkChrome { Body() }
        }
    }

    @Composable
    override fun Body() {
        DeathOverlay(
            details,
            ready = ticks >= DELAY_TICKS,
            respawned = respawned,
            onPrimary = ::primary,
            onSecondary = ::exitToTitle,
            onCause = {},
        )
    }

    private companion object {
        const val DELAY_TICKS = 20
    }
}
*///?}
