package org.polyfrost.polyplus.client.gui.screens.connection

//? if > 1.8.9 {
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import java.net.URI
import java.util.Optional
import java.util.function.Consumer
import net.minecraft.client.Minecraft
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*import net.minecraft.client.gui.GuiGraphics
*///?}
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
import org.polyfrost.polyplus.client.gui.screens.world.bindVanilla
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ConnectionDeathScreenAccessor

class DeathShellScreen(val vanilla: DeathScreen) : ShellScreen(ScreenShell.InGame, null) {
    private val access = vanilla as ConnectionDeathScreenAccessor

    init {
        bindVanilla(vanilla)
    }

    //? if >= 1.21.11 {
    private val player = access.polyplusPlayer()
    //?} else {
    /*private val player = Minecraft.getInstance().player!!
    *///?}
    private val hardcore = access.polyplusHardcore()
    private val causeParts = access.polyplusCauseOfDeath()?.let(::flatten).orEmpty()
    private var hoveredStyle: Style? = null

    private val details = run {
        val pos = player.blockPosition()
        val scoreValue = player.score.toString()
        DeathDetails(
            title = vanilla.title.string,
            cause = causeParts.map { (style, text) -> spanOf(style, text) },
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

    //? if >= 1.21.10
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

    private fun hoverCause(index: Int?) {
        hoveredStyle = index?.let { causeParts.getOrNull(it)?.first }?.takeIf { it.hoverEvent != null || urlOf(it) != null }
    }

    private fun openCauseLink(index: Int) {
        val style = causeParts.getOrNull(index)?.first ?: return
        val uri = urlOf(style) ?: return
        //? if >= 1.21.8 {
        clickUrlAction(Minecraft.getInstance(), this, uri)
        //?} else {
        /*handleComponentClicked(style)
        *///?}
    }

    //? if >= 26.1 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
        val style = hoveredStyle ?: return
        @Suppress("UNCHECKED_CAST")
        (ctx.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR) as? Consumer<Style>)?.accept(style)
    }
    //?} else {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        super.render(ctx, mouseX, mouseY, tickDelta)
        val style = hoveredStyle ?: return
        ctx.renderComponentHoverEffect(font, style, mouseX, mouseY)
    }
    *///?}

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
            onCauseHover = ::hoverCause,
            onCauseClick = ::openCauseLink,
        )
    }

    private companion object {
        const val DELAY_TICKS = 20

        fun flatten(text: Component): List<Pair<Style, String>> {
            val parts = ArrayList<Pair<Style, String>>()
            text.visit(FormattedText.StyledContentConsumer<Unit> { style: Style, content: String ->
                if (content.isNotEmpty()) parts += style to content
                Optional.empty()
            }, Style.EMPTY)
            return parts
        }

        fun urlOf(style: Style): URI? {
            //? if >= 1.21.5 {
            return (style.clickEvent as? ClickEvent.OpenUrl)?.uri()
            //?} else {
            /*val event = style.clickEvent ?: return null
            if (event.action != ClickEvent.Action.OPEN_URL) return null
            return runCatching { URI(event.value) }.getOrNull()
            *///?}
        }

        fun spanOf(style: Style, text: String) = DeathCauseSpan(
            text = text,
            color = style.color?.let { Color(0xFF000000.toInt() or it.value) } ?: Color.Unspecified,
            bold = style.isBold,
            italic = style.isItalic,
            underlined = style.isUnderlined,
            strikethrough = style.isStrikethrough,
            link = urlOf(style) != null,
        )
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
        cause = if (hardcore) listOf(DeathCauseSpan(LobbyI18n.get("deathScreen.hardcoreInfo"))) else emptyList(),
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
            onCauseHover = {},
            onCauseClick = {},
        )
    }

    private companion object {
        const val DELAY_TICKS = 20
    }
}
*///?}
