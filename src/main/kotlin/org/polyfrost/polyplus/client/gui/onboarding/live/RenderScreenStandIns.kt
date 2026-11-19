package org.polyfrost.polyplus.client.gui.onboarding.live

//? if >= 26.1 {
import net.minecraft.client.Options
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CommonButtons
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.Panorama
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityEquipment
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.Slot
import org.joml.Quaternionf
import org.joml.Vector3f
import org.polyfrost.polyplus.client.cosmetics.LocalAccountSkin
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess
import org.polyfrost.polyplus.client.emotes.playback.EmoteController
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

private const val FULL_WIDTH = 204
private const val HALF_WIDTH = 98
private const val ICON_SIZE = 20
private const val SPRITE_SIZE = 15
private const val GAME_MENU_PADDING_TOP = 50
private const val GAME_MENU_TITLE_Y = 40
private const val LINE_HEIGHT = 9
private const val VERTICAL_ANCHOR = 0.25f

private const val INVENTORY_WIDTH = 176
private const val INVENTORY_HEIGHT = 166
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
private const val PLAYER_SIZE = 30f
private const val PLAYER_OFFSET_Y = 0.0625f
private const val PLAYER_BB_WIDTH = 0.6f
private const val PLAYER_BB_HEIGHT = 1.8f
private const val FULL_BRIGHT = 0xF000F0
private const val STAND_IN_ENTITY_ID = Int.MIN_VALUE + 7
private const val LOOK_SCALE = 20f
private const val SWAY_SPEED = 0.9
private const val SWAY_REACH = 1.2
private const val NOD_SPEED = 0.6
private const val NOD_BASE = -0.35
private const val NOD_REACH = 0.25
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

private val INWORLD_MENU_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/inworld_menu_background.png")

@Suppress("UNCHECKED_CAST")
private fun <T> absent(): T = null as T

internal fun Panorama.extractInto(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
    //? if >= 26.2 {
    extractRenderState(graphics, width, height)
    //?} else
    /*extractRenderState(graphics, width, height, true)*/
}

internal class RenderScreenPause(private val panorama: Panorama) : PauseScreen(true) {
    override fun init() {
        val grid = GridLayout()
        grid.defaultCellSetting().padding(4, 4, 4, 0)
        val helper = grid.createRowHelper(2)
        helper.addChild(button("menu.returnToGame", FULL_WIDTH), 2, grid.newCellSettings().paddingTop(GAME_MENU_PADDING_TOP))
        helper.addChild(button("gui.advancements", HALF_WIDTH))
        helper.addChild(button("gui.stats", HALF_WIDTH))
        //? if >= 26.2 {
        val icons = LinearLayout.horizontal().spacing(4)
        icons.addChild(icon("menu.reportBugs", "pause_menu/bug"))
        icons.addChild(icon("menu.sendFeedback", "pause_menu/social_interactions"))
        icons.addChild(CommonButtons.friends(ICON_SIZE, {}, !minecraft.isDemo && !minecraft.isOfflineDeveloperMode))
        icons.addChild(icon("menu.playerReporting", "pause_menu/player_reporting"))
        helper.addChild(icons, 2, grid.newCellSettings().alignHorizontallyCenter())
        //?} else {
        /*helper.addChild(button("menu.sendFeedback", HALF_WIDTH))
        helper.addChild(button("menu.reportBugs", HALF_WIDTH))
        *///?}
        //? if >= 26.3 {
        helper.addChild(button("menu.options", HALF_WIDTH))
        helper.addChild(button("options.worldOptions.button", HALF_WIDTH))
        //?} elif >= 26.2 {
        /*helper.addChild(button("menu.options", HALF_WIDTH))
        helper.addChild(button("menu.multiplayerOptions.button", HALF_WIDTH))
        *///?} else {
        /*helper.addChild(button("menu.options", HALF_WIDTH))
        helper.addChild(button("menu.shareToLan", HALF_WIDTH))
        *///?}
        helper.addChild(Button.builder(CommonComponents.disconnectButtonLabel(minecraft.isLocalServer)) {}.width(FULL_WIDTH).build(), 2)
        grid.arrangeElements()
        FrameLayout.alignInRectangle(grid, 0, 0, width, height, 0.5f, VERTICAL_ANCHOR)
        grid.visitWidgets { addRenderableWidget(it) }
        val textWidth = font.width(title)
        addRenderableWidget(StringWidget(width / 2 - textWidth / 2, GAME_MENU_TITLE_Y, textWidth, LINE_HEIGHT, title, font))
    }

    private fun button(key: String, buttonWidth: Int): Button =
        Button.builder(Component.translatable(key)) {}.width(buttonWidth).build()

    private fun icon(key: String, sprite: String): SpriteIconButton =
        SpriteIconButton.builder(Component.translatable(key), {}, true)
            .width(ICON_SIZE)
            .sprite(Identifier.withDefaultNamespace(sprite), SPRITE_SIZE, SPRITE_SIZE)
            .withTootip()
            .build()

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        extractPanorama(graphics, a)
        Screen.extractMenuBackgroundTexture(graphics, INWORLD_MENU_BACKGROUND, 0, 0, 0f, 0f, width, height)
    }

    override fun extractPanorama(graphics: GuiGraphicsExtractor, a: Float) {
        panorama.extractInto(graphics, width, height)
    }

    override fun extractBlurredBackground(graphics: GuiGraphicsExtractor) = Unit

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit
}

//? if >= 26.1 && < 26.3 {
/*internal class RenderScreenOptions(options: Options, private val panorama: Panorama) : OptionsScreen(absent(), options, false) {
*///?} else {
internal class RenderScreenOptions(options: Options, private val panorama: Panorama) : OptionsScreen(absent(), options) {
//?}
    override fun extractPanorama(graphics: GuiGraphicsExtractor, a: Float) {
        panorama.extractInto(graphics, width, height)
    }

    override fun extractBlurredBackground(graphics: GuiGraphicsExtractor) = Unit

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit
}

internal class RenderScreenInventory private constructor(
    menu: InventoryMenu,
    inventory: Inventory,
    private val panorama: Panorama,
) : AbstractContainerScreen<InventoryMenu>(menu, inventory, Component.translatable("container.crafting"), INVENTORY_WIDTH, INVENTORY_HEIGHT) {
    private val emotes = EmoteController()
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

    override fun extractBlurredBackground(graphics: GuiGraphicsExtractor) = Unit

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {
        graphics.text(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        panorama.extractInto(graphics, width, height)
        extractTransparentBackground(graphics)
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_LOCATION, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, ATLAS_SIZE, ATLAS_SIZE)
        extractAvatar(graphics)
    }

    private fun extractAvatar(graphics: GuiGraphicsExtractor) {
        val state = avatarState() ?: return
        if (minecraft.entityRenderDispatcher.getRenderer(state) == null) return
        val seconds = (System.nanoTime() - startNanos) / NANOS_PER_SECOND
        val xAngle = atan(sin(seconds * SWAY_SPEED) * SWAY_REACH).toFloat()
        val yAngle = atan(NOD_BASE + cos(seconds * NOD_SPEED) * NOD_REACH).toFloat()
        val rotation = Quaternionf().rotateZ(PI.toFloat())
        val xRotation = Quaternionf().rotateX(yAngle * LOOK_SCALE * DEGREES_TO_RADIANS)
        rotation.mul(xRotation)
        state.bodyRot = 180f + xAngle * LOOK_SCALE
        state.yRot = xAngle * LOOK_SCALE
        state.xRot = -yAngle * LOOK_SCALE
        val translation = Vector3f(0f, state.boundingBoxHeight / 2f + PLAYER_OFFSET_Y, 0f)
        graphics.entity(
            state,
            PLAYER_SIZE,
            translation,
            rotation,
            xRotation,
            leftPos + PLAYER_X0,
            topPos + PLAYER_Y0,
            leftPos + PLAYER_X1,
            topPos + PLAYER_Y1,
        )
    }

    private fun avatarState(): AvatarRenderState? {
        val skin = localSkin() ?: return null
        return AvatarRenderState().apply {
            (this as? AvatarEmoteRenderAccess)?.`polyplus$bindEmoteController`(emotes)
            this.skin = skin
            id = STAND_IN_ENTITY_ID
            mainArm = minecraft.options.mainHand().get()
            leftArmPose = HumanoidModel.ArmPose.EMPTY
            rightArmPose = HumanoidModel.ArmPose.EMPTY
            showHat = true
            showJacket = true
            showLeftSleeve = true
            showRightSleeve = true
            showLeftPants = true
            showRightPants = true
            showCape = true
            lightCoords = FULL_BRIGHT
            boundingBoxWidth = PLAYER_BB_WIDTH
            boundingBoxHeight = PLAYER_BB_HEIGHT
            scale = 1f
            ageInTicks = ((System.nanoTime() - startNanos) / NANOS_PER_SECOND * TICKS_PER_SECOND).toFloat()
        }
    }

    private fun localSkin(): PlayerSkin? =
        runCatching { LocalAccountSkin.skin() }.getOrNull()
            ?: runCatching { DefaultPlayerSkin.get(LocalAccountSkin.profile()) }.getOrNull()

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit

    companion object {
        private const val TICKS_PER_SECOND = 20.0

        fun create(panorama: Panorama): RenderScreenInventory {
            val inventory = Inventory(absent<Player>(), EntityEquipment())
            val menu = InventoryMenu(inventory, true, absent())
            menu.slots.replaceAll { slot ->
                if (slot.index in InventoryMenu.ARMOR_SLOT_START until InventoryMenu.ARMOR_SLOT_END) RenderScreenArmorSlot(slot) else slot
            }
            return RenderScreenInventory(menu, inventory, panorama)
        }
    }
}

private class RenderScreenArmorSlot(private val source: Slot) :
    Slot(source.container, source.containerSlot, source.x, source.y) {
    init {
        index = source.index
    }

    override fun isActive(): Boolean = true

    override fun getNoItemIcon(): Identifier? = source.noItemIcon
}
//?}
