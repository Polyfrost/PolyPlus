package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 && < 26.1 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.screens.LoadingOverlay
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ReloadInstance
import net.minecraft.world.inventory.InventoryMenu
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier
import kotlin.math.cos
import kotlin.math.sin
//? if >= 1.21.8 {
import net.minecraft.client.renderer.RenderPipelines
//?} elif >= 1.21.4 {
/^import net.minecraft.client.renderer.RenderType
^///?}

private const val FULL_WIDTH = 204
private const val HALF_WIDTH = 98
private const val GAME_MENU_PADDING_TOP = 50
private const val GAME_MENU_TITLE_Y = 40
private const val LINE_HEIGHT = 9
private const val VERTICAL_ANCHOR = 0.25f

private const val INVENTORY_TITLE_X = 97
private const val LABEL_COLOR = -12566464
private const val ATLAS_SIZE = 256
private const val RECIPE_BUTTON_X = 104
private const val RECIPE_BUTTON_Y = 22
private const val RECIPE_BUTTON_WIDTH = 20
private const val RECIPE_BUTTON_HEIGHT = 18
private const val PLAYER_X0 = 26
private const val PLAYER_Y0 = 8
private const val PLAYER_X1 = 75
private const val PLAYER_Y1 = 78
private const val PLAYER_SIZE = 30
private const val PLAYER_OFFSET_Y = 0.0625f
private const val LOOK_DISTANCE = 40.0
private const val SWAY_SPEED = 0.9
private const val SWAY_REACH = 1.2
private const val NOD_SPEED = 0.6
private const val NOD_BASE = -0.35
private const val NOD_REACH = 0.25
private const val NANOS_PER_SECOND = 1_000_000_000.0

private val INWORLD_MENU_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/inworld_menu_background.png")

@Suppress("UNCHECKED_CAST")
private fun <T> absent(): T = null as T

internal class RenderScreenPause : PauseScreen(true) {
    override fun init() {
        val grid = GridLayout()
        grid.defaultCellSetting().padding(4, 4, 4, 0)
        val helper = grid.createRowHelper(2)
        helper.addChild(button("menu.returnToGame", FULL_WIDTH), 2, grid.newCellSettings().paddingTop(GAME_MENU_PADDING_TOP))
        helper.addChild(button("gui.advancements", HALF_WIDTH))
        helper.addChild(button("gui.stats", HALF_WIDTH))
        helper.addChild(button("menu.sendFeedback", HALF_WIDTH))
        helper.addChild(button("menu.reportBugs", HALF_WIDTH))
        helper.addChild(button("menu.options", HALF_WIDTH))
        helper.addChild(button("menu.shareToLan", HALF_WIDTH))
        //? if >= 1.21.8 {
        val leave = CommonComponents.disconnectButtonLabel(Minecraft.getInstance().isLocalServer)
        //?} else {
        /^val leave = if (Minecraft.getInstance().isLocalServer) Component.translatable("menu.returnToMenu") else CommonComponents.GUI_DISCONNECT
        ^///?}
        helper.addChild(Button.builder(leave) {}.width(FULL_WIDTH).build(), 2)
        grid.arrangeElements()
        FrameLayout.alignInRectangle(grid, 0, 0, width, height, 0.5f, VERTICAL_ANCHOR)
        grid.visitWidgets { addRenderableWidget(it) }
        //? if >= 1.21.10 {
        val textWidth = font.width(title)
        addRenderableWidget(StringWidget(width / 2 - textWidth / 2, GAME_MENU_TITLE_Y, textWidth, LINE_HEIGHT, title, font))
        //?} else {
        /^addRenderableWidget(StringWidget(0, GAME_MENU_TITLE_Y, width, LINE_HEIGHT, title, font))
        ^///?}
    }

    private fun button(key: String, buttonWidth: Int): Button =
        Button.builder(Component.translatable(key)) {}.width(buttonWidth).build()

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderPanorama(graphics, partialTick)
        Screen.renderMenuBackgroundTexture(graphics, INWORLD_MENU_BACKGROUND, 0, 0, 0f, 0f, width, height)
    }

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit
}

internal class RenderScreenOptions(options: Options) : OptionsScreen(absent(), options) {
    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit
}

internal class RenderScreenBackdrop : Screen(Component.empty()) {
    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderPanorama(graphics, partialTick)
    }

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit
}

internal class RenderScreenLoading : Screen(Component.empty()) {
    private val overlay = LoadingOverlay(Minecraft.getInstance(), StuckReload, {}, false).also {
        LobbyScene.noteReload(BooleanSupplier { true })
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) = Unit

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        overlay.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit

    private object StuckReload : ReloadInstance {
        private val never = CompletableFuture<Unit>()

        override fun done(): CompletableFuture<*> = never

        override fun getActualProgress(): Float = PROGRESS
    }

    private companion object {
        const val PROGRESS = 0.6f
    }
}

internal class RenderScreenInventory private constructor(private val avatar: LivePlayerAvatar) :
    AbstractContainerScreen<InventoryMenu>(avatar.inventoryMenu, avatar.inventory, Component.translatable("container.crafting")) {
    private val startNanos = System.nanoTime()

    init {
        titleLabelX = INVENTORY_TITLE_X
    }

    override fun init() {
        super.init()
        addRenderableWidget(
            ImageButton(
                leftPos + RECIPE_BUTTON_X,
                height / 2 - RECIPE_BUTTON_Y,
                RECIPE_BUTTON_WIDTH,
                RECIPE_BUTTON_HEIGHT,
                RecipeBookComponent.RECIPE_BUTTON_SPRITES,
            ) {},
        )
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderPanorama(graphics, partialTick)
        renderTransparentBackground(graphics)
        renderBg(graphics, partialTick, mouseX, mouseY)
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        //? if >= 1.21.8 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, ATLAS_SIZE, ATLAS_SIZE)
        //?} elif >= 1.21.4 {
        /^graphics.blit(RenderType::guiTextured, INVENTORY_LOCATION, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, ATLAS_SIZE, ATLAS_SIZE)
        ^///?} else {
        /^graphics.blit(INVENTORY_LOCATION, leftPos, topPos, 0, 0, imageWidth, imageHeight)
        ^///?}
        val seconds = (System.nanoTime() - startNanos) / NANOS_PER_SECOND
        val centerX = leftPos + (PLAYER_X0 + PLAYER_X1) / 2.0
        val centerY = topPos + (PLAYER_Y0 + PLAYER_Y1) / 2.0
        val lookX = centerX - sin(seconds * SWAY_SPEED) * SWAY_REACH * LOOK_DISTANCE
        val lookY = centerY - (NOD_BASE + cos(seconds * NOD_SPEED) * NOD_REACH) * LOOK_DISTANCE
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            graphics,
            leftPos + PLAYER_X0,
            topPos + PLAYER_Y0,
            leftPos + PLAYER_X1,
            topPos + PLAYER_Y1,
            PLAYER_SIZE,
            PLAYER_OFFSET_Y,
            lookX.toFloat(),
            lookY.toFloat(),
            avatar,
        )
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false)
    }

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit

    companion object {
        private val LOG = LoggerFactory.getLogger("polyplus/render-screen")
        private val noted = HashSet<String>()

        private fun note(reason: String): RenderScreenInventory? {
            if (noted.add(reason)) LOG.info("[render-screen] inventory capture skipped: {}", reason)
            return null
        }

        fun create(): RenderScreenInventory? {
            val level = LivePlayerAvatars.level() ?: return note("no lobby level")
            val avatar = LivePlayerAvatars.player(level) ?: return note("no avatar")
            avatar.shownSkin = LivePlayerSkins.base()
            return RenderScreenInventory(avatar)
        }
    }
}
*///?}
