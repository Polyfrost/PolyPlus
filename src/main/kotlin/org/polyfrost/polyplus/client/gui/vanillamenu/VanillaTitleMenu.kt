@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.minecraft.canvas.InlinePolyCanvas
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.VanillaMenuLayout
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.MENU_PREVIEW_YAW
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.PauseMenuPanel
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewOffscreen
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.lobby.StartupOverlay
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * One piece of the vanilla menu: a widget with an id, where the template put it, and the widgets that follow it
 * (Quickplay's rows, the top right dropdown). The player's changes are a [MenuElement] in [MenuLayoutStore].
 */
internal class MenuPiece(
    val id: String,
    val label: String,
    val widget: AbstractWidget,
    val type: String,
    val templateGroup: String?,
    /** The key the old editor stored this piece's offset under. */
    val legacyKey: String?,
) {
    var extras: List<AbstractWidget> = emptyList()
    var layoutExtras: (MenuPiece) -> Unit = {}
    var tx = 0
    var ty = 0
    var tw = 0
    var th = 0
    var order = 0

    val builtin: Boolean get() = type == MenuElement.TYPE_BUILTIN
    val data: MenuElement? get() = MenuLayoutStore.element(MenuLayoutStore.VANILLA, id)
    val scale: Float get() = data?.scale ?: 1f
    val z: Int get() = data?.z ?: order
    val shown: Boolean get() = data?.visible ?: true
    val group: String? get() = (data?.group ?: templateGroup)?.takeIf { it.isNotEmpty() }

    // the size it takes on screen, with its scale
    val left: Int get() = widget.x
    val top: Int get() = widget.y
    val width: Int get() = (widget.width * scale).roundToInt()
    val height: Int get() = (widget.height * scale).roundToInt()
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/**
 * The vanilla title screen with OneClient's buttons in vanilla style. A template (Centered, Essential style or
 * OneClient style) places everything, then the player's layout moves, sizes, scales, groups and hides pieces and adds
 * their own (images, text, sliders, buttons, panels). The menu editor ([VanillaMenuEditor]) changes that layout.
 */
object VanillaTitleMenu {
    private val LOGGER = LogManager.getLogger("PolyPlus/VanillaTitleMenu")
    private val LATE_PHASE: Identifier = Identifier.fromNamespaceAndPath("polyplus", "vanilla_title_menu")

    internal const val ROW = 24
    internal const val SIZE = 20
    private const val GAP = 4
    internal const val MARGIN = 4
    private const val COLUMN = 200
    private const val DROPDOWN_WIDTH = 124
    private const val LOGO_BOTTOM = 80
    private const val PREVIEW_RATIO = 0.7f
    private const val INTRO_ROW_DELAY = 0.07f
    private const val INTRO_SLIDE_SECONDS = 0.5f
    private const val INTRO_DISTANCE = 30
    private const val SPIN_DEGREES_PER_PIXEL = 0.8f
    private const val HIDDEN_ALPHA = 0.3f

    /** The intro only plays the first time the title screen opens after the game starts. */
    private var introPending = true

    internal class State(val screen: TitleScreen) {
        val pieces = LinkedHashMap<String, MenuPiece>()
        var preview: PreviewSlot? = null
        var dropdown: List<Button> = emptyList()
        var dropdownToggle: AbstractWidget? = null
        var introStart = 0L
        var introRows: Map<AbstractWidget, Int> = emptyMap()
        var spinning = false
        var spinLastX = 0.0
        val editing: Boolean get() = MenuLayoutEditor.isEditing(MenuLayoutStore.VANILLA)
    }

    private val states = WeakHashMap<Screen, State>()

    internal fun state(screen: Screen): State? = states[screen]

    fun register() {
        ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE)
        ScreenEvents.AFTER_INIT.register(LATE_PHASE) { _, screen, _, _ ->
            if (screen !is TitleScreen || MainMenuReplacement.replacesTitleScreen()) return@register
            runCatching { build(screen) }.onFailure { LOGGER.error("Couldn't add the OneClient buttons to the title screen", it) }
            ScreenKeyboardEvents.allowKeyPress(screen).register { s, event -> !(states[s]?.let { VanillaMenuEditor.keyPressed(it, event) } ?: false) }
            ScreenMouseEvents.allowMouseScroll(screen).register { s, x, y, _, amount -> !(states[s]?.let { VanillaMenuEditor.scrolled(it, x, y, amount) } ?: false) }
        }
    }

    @JvmStatic
    fun scope(): String = MenuLayoutStore.VANILLA

    internal fun presetName(layout: VanillaMenuLayout): String = when (layout) {
        VanillaMenuLayout.CENTERED -> "Centered"
        VanillaMenuLayout.SIDES -> "Essential style"
        VanillaMenuLayout.ONECLIENT -> "OneClient style"
    }

    private fun key(widget: AbstractWidget): String =
        (widget.message.contents as? TranslatableContents)?.key ?: widget.message.string.ifEmpty { widget.javaClass.simpleName }

    /** Rebuilds the screen from the layout, after an undo, a template or an added piece. */
    internal fun rebuild(screen: Screen) {
        screen.resize(screen.width, screen.height)
    }

    // ---------------------------------------------------------------- layout

    private fun build(screen: TitleScreen) {
        val access = screen as ScreenAccessor
        val vanilla = screen.children().filterIsInstance<AbstractWidget>()
        val state = State(screen)
        states[screen] = state
        // vanilla's own pieces are pieces too, each on its own
        vanilla.filter { it.y < screen.height - 10 }.forEach { widget ->
            val key = key(widget)
            piece(state, widget, uniqueId(state, key), label(widget, key), legacyKey = key)
        }

        if (PolyPlusMainMenuConfig.vanillaMenuButtons) {
            val built = when (PolyPlusMainMenuConfig.vanillaMenuLayout) {
                VanillaMenuLayout.SIDES -> buildSides(screen, access, vanilla, state)
                VanillaMenuLayout.ONECLIENT -> buildOneClient(screen, access, vanilla, state)
                VanillaMenuLayout.CENTERED -> false
            }
            if (!built) buildCentered(screen, access, vanilla, state)
        }
        addTopRight(screen, access, state)

        // where the template put everything, before the player's layout moves it
        for (piece in state.pieces.values) {
            piece.tx = piece.widget.x
            piece.ty = piece.widget.y
            piece.tw = piece.widget.width
            piece.th = piece.widget.height
        }
        addPlayerPieces(screen, access, state)
        state.pieces.values.forEachIndexed { index, piece -> piece.order = index * 10 }
        migrateLegacy(state)
        applyLayout(state)

        if (introPending && PolyPlusMainMenuConfig.introAnimation && !state.editing) {
            introPending = false
            val widgets = state.pieces.values.filter { it.shown }.map { it.widget }
            val rows = widgets.map { it.y }.distinct().sorted()
            state.introRows = widgets.associateWith { rows.indexOf(it.y) }
            state.introStart = -1L
            applyIntro(state, 0f)
        }
        introPending = false
    }

    private fun label(widget: AbstractWidget, key: String): String = when {
        widget.message.string.isNotBlank() -> widget.message.string
        else -> key
    }

    private fun uniqueId(state: State, base: String): String {
        if (base !in state.pieces) return base
        var n = 2
        while ("$base#$n" in state.pieces) n++
        return "$base#$n"
    }

    internal fun piece(
        state: State,
        widget: AbstractWidget,
        id: String,
        label: String,
        type: String = MenuElement.TYPE_BUILTIN,
        group: String? = null,
        legacyKey: String? = id,
    ): MenuPiece = MenuPiece(id, label, widget, type, group, legacyKey).also { state.pieces[id] = it }

    private fun add(access: ScreenAccessor, state: State, widget: AbstractWidget, id: String, label: String, group: String? = null, legacyKey: String? = id): MenuPiece {
        access.polyplusAddRenderableWidget(widget)
        return piece(state, widget, id, label, group = group, legacyKey = legacyKey)
    }

    private fun find(widgets: List<AbstractWidget>, key: String) = widgets.firstOrNull { (it.message.contents as? TranslatableContents)?.key == key }

    private fun buildCentered(screen: TitleScreen, access: ScreenAccessor, vanilla: List<AbstractWidget>, state: State) {
        val singleplayer = find(vanilla, "menu.singleplayer") ?: return
        val anchor = find(vanilla, "menu.online") ?: find(vanilla, "menu.multiplayer") ?: return
        val center = screen.width / 2
        val left = center - COLUMN / 2
        val rowY = anchor.y + ROW
        // everything under Realms moves down a row, except the copyright line at the very bottom
        vanilla.filter { it.y >= rowY && it.y < screen.height - 10 }.forEach { it.y += ROW }

        val third = (COLUMN - 2 * GAP) / 3
        val row = listOf(
            Triple("social", "Social", textButton("Social", "Friends, chats and parties") { SocialOverlay.open(screen) }),
            Triple("wardrobe", "Wardrobe", textButton("Wardrobe", "Cosmetics and your Minecraft capes") { PolyPlusOneConfigIntegration.openCosmetics() }),
            Triple("host", "Host World", textButton("Host World", "Host a singleplayer world for your friends") { MenuPanelScreen.open(MenuPanelScreen.Panel.HostWorld) }),
        )
        row.forEachIndexed { index, (id, label, button) ->
            // the last one takes the rounding so the row ends exactly where the 200 wide buttons end
            val x = left + index * (third + GAP)
            val w = if (index == row.lastIndex) left + COLUMN - x else third
            button.setRectangle(w, SIZE, x, rowY)
            add(access, state, button, "oc-$id", label)
        }

        // vanilla's small icon buttons share a row with OneClient's, spread over exactly the 200 wide column
        val iconRowY = (find(vanilla, "menu.options")?.y ?: (rowY + 2 * ROW)) - ROW
        val vanillaIcons = vanilla.filter { it.y == iconRowY && it.width == SIZE && it.height == SIZE }
        val leftSpace = left - 2 * MARGIN
        val panelWidth = minOf(150, leftSpace - 8)
        val quickplayPanel = panelWidth >= 80
        val ours = buildList {
            add(Triple("oc-accounts", "Accounts", iconButton("accounts", "Accounts") { MenuPanelScreen.open(MenuPanelScreen.Panel.Accounts) }))
            add(Triple("oc-oneconfig", "OneConfig", iconButton("mods", "OneConfig mod settings") { PolyPlusOneConfigIntegration.openMods() }))
        }
        ours.forEach { (id, label, widget) -> add(access, state, widget, id, label) }
        val icons = ours.take(1).map { it.third } + vanillaIcons + ours.drop(1).map { it.third }
        spread(icons, left, COLUMN, iconRowY)

        // with the extra row the buttons may reach the copyright line, so the block moves up toward the logo
        val block = (vanilla.filter { it.y >= singleplayer.y && it.y < screen.height - 10 } + row.map { it.third } + ours.map { it.third }).distinct()
        val overflow = block.maxOf { it.y + it.height } - (screen.height - 14)
        val lift = minOf(overflow, singleplayer.y - LOGO_BOTTOM)
        if (lift > 0) block.forEach { it.y -= lift }

        if (quickplayPanel) addQuickplay(screen, access, state, (left - panelWidth) / 2, singleplayer.y, panelWidth)
    }

    /**
     * Like the OneClient menu: Quickplay on the left, your skin, account and the OneClient buttons on the right,
     * the vanilla buttons in the middle. False when the screen is too narrow for the side columns.
     */
    private fun buildOneClient(screen: TitleScreen, access: ScreenAccessor, vanilla: List<AbstractWidget>, state: State): Boolean {
        val singleplayer = find(vanilla, "menu.singleplayer") ?: return false
        val center = screen.width / 2
        val sideSpace = center - COLUMN / 2
        if (sideSpace < 96) return false
        val columnWidth = minOf(140, sideSpace - 16)
        val leftX = (sideSpace - columnWidth) / 2
        val rightX = center + COLUMN / 2 + (sideSpace - columnWidth) / 2
        addQuickplay(screen, access, state, leftX, singleplayer.y - 10, columnWidth)

        val previewHeight = 64
        val buttons = 4
        val top = (singleplayer.y - 40).coerceAtLeast(MARGIN + SIZE + GAP)
            .coerceAtMost(screen.height - 14 - previewHeight - buttons * (SIZE + 2))
        addPreview(access, state, rightX, top, columnWidth, previewHeight, group = "player")
        val font = Minecraft.getInstance().font
        val name = ClientPlatform.localPlayerName() + "  ▾"
        val column = listOf(
            Triple("player.account", "Account", Button.builder(Component.literal(font.plainSubstrByWidth(name, columnWidth - 8))) { MenuPanelScreen.open(MenuPanelScreen.Panel.Accounts) }
                .tooltip(Tooltip.create(Component.literal("Switch account"))).build()),
            Triple("player.host", "Host World", textButton("Host World", "Host a singleplayer world for your friends") { MenuPanelScreen.open(MenuPanelScreen.Panel.HostWorld) }),
            Triple("player.social", "Social", textButton("Social", "Friends, chats and parties") { SocialOverlay.open(screen) }),
            Triple("player.wardrobe", "Wardrobe", textButton("Wardrobe", "Cosmetics and your Minecraft capes") { PolyPlusOneConfigIntegration.openCosmetics() }),
        )
        column.forEachIndexed { index, (id, label, button) ->
            button.setRectangle(columnWidth, SIZE, rightX, top + previewHeight + 2 + index * (SIZE + 2))
            add(access, state, button, id, label, group = "player", legacyKey = "player")
        }

        // OneConfig next to vanilla's small icon buttons
        val iconRowY = (find(vanilla, "menu.options")?.y ?: (singleplayer.y + 4 * ROW)) - ROW
        val vanillaIcons = vanilla.filter { it.y == iconRowY && it.width == SIZE && it.height == SIZE }
        val oneconfig = iconButton("mods", "OneConfig mod settings") { PolyPlusOneConfigIntegration.openMods() }
        add(access, state, oneconfig, "oc-oneconfig", "OneConfig")
        spread(vanillaIcons + oneconfig, center - COLUMN / 2, COLUMN, iconRowY)
        return true
    }

    /** Essential's layout. False when the screen is too narrow for the side columns. */
    private fun buildSides(screen: TitleScreen, access: ScreenAccessor, vanilla: List<AbstractWidget>, state: State): Boolean {
        val singleplayer = find(vanilla, "menu.singleplayer") ?: return false
        val center = screen.width / 2
        val sideSpace = center - COLUMN / 2
        if (sideSpace < 96) return false
        val columnWidth = minOf(140, sideSpace - 16)
        val rightX = center + COLUMN / 2 + (sideSpace - columnWidth) / 2

        val buttons = listOf(
            Triple("host", "Host World", textButton("Host World", "Host a singleplayer world for your friends") { MenuPanelScreen.open(MenuPanelScreen.Panel.HostWorld) }),
            Triple("social", "Social", textButton("Social", "Friends, chats and parties") { SocialOverlay.open(screen) }),
            Triple("oneconfig", "OneConfig", textButton("OneConfig", "OneConfig mod settings") { PolyPlusOneConfigIntegration.openMods() }),
        )
        // the column moves up when the buttons and Quickplay wouldn't fit above the copyright line
        val quickplayHeight = SIZE + 2 + QuickplayPanel.entries().size * (QuickplayPanel.ROW_HEIGHT + 2)
        val columnTop = minOf(singleplayer.y, screen.height - 14 - buttons.size * ROW - 6 - quickplayHeight).coerceAtLeast(MARGIN + SIZE + GAP)
        buttons.forEachIndexed { index, (id, label, button) ->
            button.setRectangle(columnWidth, SIZE, rightX, columnTop + index * ROW)
            add(access, state, button, "oc-$id", label)
        }
        addQuickplay(screen, access, state, rightX, columnTop + buttons.size * ROW + 6, columnWidth)

        // your skin on the left, Wardrobe right under it
        val previewWidth = minOf(150, sideSpace - 16)
        val previewHeight = (previewWidth / PREVIEW_RATIO).roundToInt().coerceAtMost(screen.height - singleplayer.y - 40)
        val previewX = (sideSpace - previewWidth) / 2
        val previewY = singleplayer.y - 16
        addPreview(access, state, previewX, previewY, previewWidth, previewHeight, group = "player")
        val wardrobe = textButton("Wardrobe", "Cosmetics and your Minecraft capes") { PolyPlusOneConfigIntegration.openCosmetics() }
        wardrobe.setRectangle(minOf(previewWidth, 100), SIZE, previewX + (previewWidth - minOf(previewWidth, 100)) / 2, previewY + previewHeight + 4)
        add(access, state, wardrobe, "player.wardrobe", "Wardrobe", group = "player", legacyKey = "player")

        // accounts at the top left, like Essential's account switcher
        val font = Minecraft.getInstance().font
        val name = ClientPlatform.localPlayerName() + "  ▾"
        val accounts = Button.builder(Component.literal(name)) { MenuPanelScreen.open(MenuPanelScreen.Panel.Accounts) }
            .tooltip(Tooltip.create(Component.literal("Switch account")))
            .bounds(MARGIN, MARGIN, font.width(name) + 16, SIZE).build()
        add(access, state, accounts, "oc-accounts", "Account")
        return true
    }

    private fun addPreview(access: ScreenAccessor, state: State, x: Int, y: Int, width: Int, height: Int, group: String?, type: String = MenuElement.TYPE_BUILTIN, id: String = "player.preview"): MenuPiece {
        val preview = PreviewSlot(x, y, width, height)
        state.preview = preview
        access.polyplusAddRenderableWidget(preview)
        return piece(state, preview, id, "Player preview", type = type, group = group, legacyKey = "player")
    }

    internal fun addQuickplay(screen: Screen, access: ScreenAccessor, state: State, x: Int, y: Int, width: Int, type: String = MenuElement.TYPE_BUILTIN, id: String = "quickplay"): MenuPiece? {
        val entries = QuickplayPanel.entries()
        if (entries.isEmpty()) return null
        lateinit var piece: MenuPiece
        val header = QuickplayPanel.header(width) { showExtras(state, piece) }
        header.setPosition(x, y)
        access.polyplusAddRenderableWidget(header)
        piece = piece(state, header, id, "Quickplay", type = type)
        piece.extras = entries.map { server -> QuickplayPanel.Row(screen, server, width).also { access.polyplusAddRenderableWidget(it) } }
        piece.layoutExtras = { p ->
            val scale = p.scale
            p.extras.forEachIndexed { index, row ->
                row.setRectangle(p.widget.width, QuickplayPanel.ROW_HEIGHT, p.widget.x, p.widget.y + ((SIZE + 2 + index * (QuickplayPanel.ROW_HEIGHT + 2)) * scale).roundToInt())
                ElementTransforms.set(row, scale)
            }
        }
        return piece
    }

    private fun addTopRight(screen: TitleScreen, access: ScreenAccessor, state: State) {
        val entries = listOf<Pair<() -> Component, () -> Unit>>(
            { Component.literal("OneClient Lobby") } to { MainMenuReplacement.switchToLobby() },
            { Component.literal("PolyPlus Menu") } to { MainMenuReplacement.switchToPolyPlusMenu() },
            { Component.literal("Notifications") } to { MenuPanelScreen.open(MenuPanelScreen.Panel.Notifications) },
            { themeLabel() } to { toggleTheme() },
            { Component.literal("Edit Main Menu") } to { EditMenuChooser.open() },
            { Component.literal("OneClient Settings") } to { PolyPlusOneConfigIntegration.openSettings() },
        )
        val toggleX = screen.width - SIZE - MARGIN
        val bell = iconButton("bell", "Notifications") { MenuPanelScreen.open(MenuPanelScreen.Panel.Notifications) }
        if (PolyPlusMainMenuConfig.vanillaMenuDropdown) {
            val items = entries.map { (label, action) ->
                lateinit var button: Button
                button = Button.builder(label()) {
                    action()
                    button.message = label()
                }.size(DROPDOWN_WIDTH, SIZE).build()
                button.visible = false
                access.polyplusAddRenderableWidget(button)
                button
            }
            state.dropdown = items
            val toggle = iconButton("sliders", "OneClient", tooltip = false) { items.forEach { it.visible = !it.visible } }
            toggle.setPosition(toggleX, MARGIN)
            bell.setPosition(toggleX - SIZE - GAP, MARGIN)
            add(access, state, bell, "topright.bell", "Notifications", group = "topright", legacyKey = "topright")
            val menu = add(access, state, toggle, "topright.menu", "OneClient menu", group = "topright", legacyKey = "topright")
            state.dropdownToggle = toggle
            menu.extras = items
            // the dropdown opens under its button, toward the middle of the screen
            menu.layoutExtras = { p ->
                val opensLeft = p.widget.x + DROPDOWN_WIDTH > screen.width
                val x = if (opensLeft) p.right - DROPDOWN_WIDTH else p.left
                val below = p.bottom + GAP + items.size * SIZE <= screen.height
                items.forEachIndexed { index, item ->
                    item.setPosition(x, if (below) p.bottom + GAP + index * SIZE else p.top - GAP - (items.size - index) * SIZE)
                }
            }
        } else {
            // all of them in a row along the top, right aligned
            val font = Minecraft.getInstance().font
            var right = screen.width - MARGIN
            entries.reversed().forEachIndexed { index, (label, action) ->
                val width = font.width(label()) + 12
                lateinit var button: Button
                button = Button.builder(label()) {
                    action()
                    button.message = label()
                }.bounds(right - width, MARGIN, width, SIZE).build()
                add(access, state, button, "topright.${entries.size - 1 - index}", label().string, group = "topright", legacyKey = "topright")
                right -= width + 2
            }
        }
    }

    /** The pieces the player added in the editor. */
    private fun addPlayerPieces(screen: TitleScreen, access: ScreenAccessor, state: State) {
        for (element in MenuLayoutStore.added(MenuLayoutStore.VANILLA).sortedBy { it.z ?: 0 }) {
            val piece = runCatching { MenuAddedElements.create(screen, access, state, element) }
                .onFailure { LOGGER.error("Couldn't add menu piece {} ({})", element.id, element.type, it) }.getOrNull() ?: continue
            val w = element.w?.roundToInt() ?: piece.widget.width
            val h = element.h?.roundToInt() ?: piece.widget.height
            piece.tx = piece.widget.x
            piece.ty = piece.widget.y
            piece.tw = w
            piece.th = h
        }
    }

    private fun spread(icons: List<AbstractWidget>, left: Int, width: Int, y: Int) {
        if (icons.isEmpty()) return
        if (icons.size == 1) {
            icons[0].setPosition(left + (width - SIZE) / 2, y)
            return
        }
        val step = (width - SIZE).toFloat() / (icons.size - 1)
        // never further apart than a normal gap, so a short row stays together in the middle
        val used = if (step > SIZE + GAP) SIZE + GAP.toFloat() else step
        val start = left + (width - (used * (icons.size - 1) + SIZE)) / 2f
        icons.forEachIndexed { index, icon -> icon.setPosition((start + index * used).roundToInt(), y) }
    }

    // ---------------------------------------------------------------- the player's layout

    private val ANCHORS = mapOf(
        "top-left" to (0f to 0f), "top" to (0.5f to 0f), "top-right" to (1f to 0f),
        "left" to (0f to 0.5f), "center" to (0.5f to 0.5f), "right" to (1f to 0.5f),
        "bottom-left" to (0f to 1f), "bottom" to (0.5f to 1f), "bottom-right" to (1f to 1f),
    )

    private fun anchorOf(name: String?): Pair<Float, Float> = ANCHORS[name] ?: (0f to 0f)

    /** The nearest of the nine anchors, by which third of the screen the piece's middle is in. */
    private fun nearestAnchor(screen: Screen, x: Int, y: Int, w: Int, h: Int): String {
        val cx = (x + w / 2f) / screen.width
        val cy = (y + h / 2f) / screen.height
        val col = if (cx < 1f / 3) 0f else if (cx > 2f / 3) 1f else 0.5f
        val row = if (cy < 1f / 3) 0f else if (cy > 2f / 3) 1f else 0.5f
        return ANCHORS.entries.first { it.value == col to row }.key
    }

    /** Stores where a piece is now (its top left corner and unscaled size), anchored to the nearest screen point. */
    internal fun store(state: State, piece: MenuPiece, x: Int, y: Int, w: Int = piece.widget.width, h: Int = piece.widget.height) {
        val element = MenuLayoutStore.edit(MenuLayoutStore.VANILLA, piece.id)
        if (!piece.builtin) element.type = piece.type
        val scale = element.scale
        val sw = (w * scale).roundToInt()
        val sh = (h * scale).roundToInt()
        val anchor = nearestAnchor(state.screen, x, y, sw, sh)
        val (ax, ay) = anchorOf(anchor)
        element.anchor = anchor
        element.x = x + ax * sw - ax * state.screen.width
        element.y = y + ay * sh - ay * state.screen.height
        element.w = if (piece.builtin && w == piece.tw) null else w.toFloat()
        element.h = if (piece.builtin && h == piece.th) null else h.toFloat()
    }

    /** Puts every piece where the template and the player's layout say, with its size, scale, opacity and order. */
    internal fun applyLayout(state: State) {
        val screen = state.screen
        val editing = state.editing
        for (piece in state.pieces.values) {
            val element = piece.data
            val scale = (element?.scale ?: 1f).coerceIn(0.25f, 4f)
            val w = element?.w?.roundToInt()?.coerceAtLeast(2) ?: piece.tw
            val h = element?.h?.roundToInt()?.coerceAtLeast(2) ?: piece.th
            var x = piece.tx
            var y = piece.ty
            if (element?.anchor != null) {
                val (ax, ay) = anchorOf(element.anchor)
                x = (ax * screen.width + element.x - ax * w * scale).roundToInt()
                y = (ay * screen.height + element.y - ay * h * scale).roundToInt()
            }
            piece.widget.setRectangle(w, h, x, y)
            ElementTransforms.set(piece.widget, scale)
            val shown = element?.visible ?: true
            piece.widget.visible = shown || editing
            piece.widget.setAlpha((element?.opacity ?: 1f).coerceIn(0f, 1f) * if (shown) 1f else HIDDEN_ALPHA)
            piece.layoutExtras(piece)
            showExtras(state, piece)
        }
        sortByZ(state)
    }

    private fun showExtras(state: State, piece: MenuPiece) {
        when (piece.id) {
            // the dropdown only opens from its button, never in the editor
            "topright.menu" -> if (state.editing || !piece.shown) piece.extras.forEach { it.visible = false }
            else -> {
                val open = piece.widget is Button && piece.extras.isNotEmpty() && QuickplayPanel.expanded
                piece.extras.forEach {
                    it.visible = open && (piece.shown || state.editing)
                    it.setAlpha(piece.widget.alpha)
                }
            }
        }
    }

    /** Draw order (and which piece gets a click first) follows each piece's z. */
    private fun sortByZ(state: State) {
        val access = state.screen as ScreenAccessor
        val ordered = state.pieces.values.sortedBy { it.z }.flatMap { listOf(it.widget) + it.extras }
        val ours: Set<Any> = ordered.toHashSet()
        val renderables = access.polyplusRenderables()
        val renderSlots = renderables.indices.filter { renderables[it] in ours }
        val renderOrder = ordered.filter { it in renderables }
        if (renderSlots.size == renderOrder.size) renderSlots.forEachIndexed { index, slot -> renderables[slot] = renderOrder[index] }
        // the one drawn on top gets the click
        val children = access.polyplusChildren()
        val childSlots = children.indices.filter { children[it] in ours }
        val childOrder = ordered.reversed().filter { it in children }
        if (childSlots.size == childOrder.size) childSlots.forEachIndexed { index, slot -> children[slot] = childOrder[index] }
    }

    private fun migrateLegacy(state: State) {
        val layout = MenuLayoutStore.scope(MenuLayoutStore.VANILLA)
        if (layout.legacyOffsets.isEmpty() && layout.legacyHidden.isEmpty()) return
        for (piece in state.pieces.values) {
            val key = piece.legacyKey ?: continue
            if (MenuLayoutStore.element(MenuLayoutStore.VANILLA, piece.id)?.anchor != null) continue
            layout.legacyOffsets[key]?.let { offset ->
                store(state, piece, piece.tx + offset[0].roundToInt(), piece.ty + offset[1].roundToInt(), piece.tw, piece.th)
            }
            if (key in layout.legacyHidden) MenuLayoutStore.edit(MenuLayoutStore.VANILLA, piece.id).visible = false
        }
        layout.legacyOffsets.clear()
        layout.legacyHidden.clear()
        MenuLayoutStore.save()
    }

    // ---------------------------------------------------------------- intro

    private fun applyIntro(state: State, t: Float) {
        var done = true
        for ((widget, row) in state.introRows) {
            val piece = state.pieces.values.firstOrNull { it.widget === widget } ?: continue
            val p = easeOut(((t - row * INTRO_ROW_DELAY) / INTRO_SLIDE_SECONDS).coerceIn(0f, 1f))
            if (p < 1f) done = false
            val target = targetY.getOrPut(widget) { widget.y }
            widget.y = target + ((1f - p) * INTRO_DISTANCE).roundToInt()
            widget.setAlpha(p * (piece.data?.opacity ?: 1f))
            piece.layoutExtras(piece)
        }
        if (done && t > 0f) {
            state.introStart = 0L
            state.introRows = emptyMap()
            targetY.clear()
            applyLayout(state)
        }
    }

    private val targetY = WeakHashMap<AbstractWidget, Int>()

    private fun easeOut(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    // ---------------------------------------------------------------- frame hooks

    /** Called before the title screen draws: the intro, the editor's drags, spinning the skin, and Quickplay's pings. */
    @JvmStatic
    fun frameStart(screen: Screen, mouseX: Int, mouseY: Int) {
        val state = states[screen] ?: return
        QuickplayPanel.tick()
        if (state.introStart != 0L) {
            // the slide starts once the OneClient intro has opened the curtain
            if (state.introStart == -1L) {
                if (StartupOverlay.active) {
                    applyIntro(state, 0f)
                    return
                }
                state.introStart = System.nanoTime()
            }
            applyIntro(state, (System.nanoTime() - state.introStart) / 1_000_000_000f)
        }
        if (state.spinning) {
            if (PauseMenuPanel.leftDown()) {
                preview.dragBy(-(mouseX - state.spinLastX).toFloat() * SPIN_DEGREES_PER_PIXEL)
                state.spinLastX = mouseX.toDouble()
            } else {
                state.spinning = false
                preview.endDrag()
            }
        }
        if (state.editing) VanillaMenuEditor.frameStart(state, mouseX, mouseY)
    }

    /** Called after the title screen draws: your skin, the open dropdown, and the editor on top. */
    @JvmStatic
    fun frameEnd(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val state = states[screen] ?: return
        state.preview?.let { slot ->
            val piece = state.pieces.values.firstOrNull { it.widget === slot }
            if (slot.visible && (piece?.shown != false || state.editing)) drawPreview(screen, graphics, slot, piece?.scale ?: 1f, slot.alpha)
            if (!state.editing && slot.visible && (state.spinning || overPreview(state, mouseX.toDouble(), mouseY.toDouble()))) {
                graphics.requestCursor(if (state.spinning) CursorTypes.RESIZE_EW else CursorTypes.POINTING_HAND)
            }
        }
        // drawn again last, so the splash text and anything else can't end up on top of the open dropdown
        state.dropdown.filter { it.visible }.forEach { it.extractRenderState(graphics, mouseX, mouseY, a) }
        if (state.editing) VanillaMenuEditor.draw(state, graphics, mouseX, mouseY)
    }

    private fun overPreview(state: State, x: Double, y: Double): Boolean {
        val slot = state.preview ?: return false
        val piece = state.pieces.values.firstOrNull { it.widget === slot } ?: return false
        return slot.visible && piece.shown && x >= piece.left && x < piece.right && y >= piece.top && y < piece.bottom
    }

    /** True when the click was used here (the editor, spinning the skin), so the screen shouldn't handle it. */
    @JvmStatic
    fun mouseClicked(screen: Screen, event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val state = states[screen] ?: return false
        if (state.editing) return VanillaMenuEditor.mouseClicked(state, event, doubleClick)
        // the open dropdown is on top, so it gets the click before any button under it
        state.dropdown.firstOrNull { it.visible && it.isMouseOver(mouseX, mouseY) }?.let { return it.mouseClicked(event, doubleClick) }
        // a click outside the open dropdown closes it
        val open = state.dropdown.any { it.visible }
        if (open && state.dropdown.none { it.isMouseOver(mouseX, mouseY) } && state.dropdownToggle?.isMouseOver(mouseX, mouseY) != true) {
            state.dropdown.forEach { it.visible = false }
        }
        if (event.button() == 0 && overPreview(state, mouseX, mouseY)) {
            if (doubleClick) {
                preview.resetYaw()
                return true
            }
            state.spinning = true
            state.spinLastX = mouseX
            preview.dragging = true
            preview.spinVelocity = 0f
            return true
        }
        return false
    }

    // ---------------------------------------------------------------- your skin

    /** Holds the place of the player preview so it lays out and moves like the buttons; drawn in [frameEnd]. */
    internal class PreviewSlot(x: Int, y: Int, width: Int, height: Int) : AbstractWidget(x, y, width, height, Component.literal("Player")) {
        init {
            active = false
        }

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) = Unit

        override fun updateWidgetNarration(output: NarrationElementOutput) = Unit
    }

    internal fun addPreviewPiece(access: ScreenAccessor, state: State, element: MenuElement): MenuPiece? {
        if (state.preview != null) return null
        return addPreview(access, state, 0, 0, element.w?.roundToInt() ?: 90, element.h?.roundToInt() ?: 128, group = null, type = element.type, id = element.id)
    }

    private val preview by lazy {
        PlayerPreviewOffscreen.register().apply {
            vanillaHost = true
            source = PlayerPreviewSource.LocalLive
            autoSpin = false
            // centered in its box, which the model (1.8 blocks tall) fills to about 85%
            verticalAnchor = 0.5f
            initialYaw = MENU_PREVIEW_YAW
        }
    }

    private fun drawPreview(screen: Screen, graphics: GuiGraphicsExtractor, slot: PreviewSlot, scale: Float, alpha: Float) {
        if (screen.width <= 0 || screen.height <= 0 || alpha <= 0.01f) return
        val entry = preview
        val w = slot.width * scale
        val h = slot.height * scale
        // the model fills the box the same way at any size, so resizing the box resizes the model
        entry.modelScale = 0.47f
        entry.fw = w / screen.width
        entry.fh = h / screen.height
        // the canvas draws in framebuffer pixels
        val px = Minecraft.getInstance().window.width / screen.width.toFloat()
        InlinePolyCanvas.draw(graphics) { canvas ->
            PlayerPreviewOffscreen.drawFaded(entry, canvas, slot.x * px, slot.y * px, w * px, h * px, 0f)
        }
    }

    // ---------------------------------------------------------------- theme and helpers

    private fun dark(): Boolean = PolyPlusMainMenuConfig.vanillaDarkTheme

    private fun themeLabel(): Component = Component.literal(if (dark()) "Light Theme" else "Dark Theme")

    /** One switch for both: the OneConfig theme and the dark vanilla buttons. */
    fun toggleTheme() {
        val goingDark = !dark()
        PolyPlusMainMenuConfig.vanillaDarkTheme = goingDark
        PolyPlusMainMenuConfig.save()
        // the OneConfig theme follows, so the lobby, PolyPlus menu and social menu match the vanilla buttons
        val target = OnboardingFeatures.themeVariant(!goingDark) ?: return
        PolyPlusConfig.onboardingLightTheme = !goingDark
        OneConfigTheme.activate(target)
        PolyPlusConfig.save()
    }

    internal fun textButton(label: String, tooltip: String, onPress: () -> Unit): Button =
        Button.builder(Component.literal(label)) { onPress() }.tooltip(Tooltip.create(Component.literal(tooltip))).build()

    private fun iconButton(sprite: String, label: String, tooltip: Boolean = true, onPress: () -> Unit): SpriteIconButton {
        val button = SpriteIconButton.builder(Component.literal(label), { onPress() }, true)
            .sprite(Identifier.fromNamespaceAndPath("polyplus", "oneclient/$sprite"), 15, 15)
            .size(SIZE, SIZE)
            .build()
        if (tooltip) button.setTooltip(Tooltip.create(Component.literal(label)))
        return button
    }

    internal fun toast(title: String, body: String) {
        val mc = Minecraft.getInstance()
        SystemToast.add(
            //? if >= 26.2 {
            mc.gui.toastManager(),
            //?} else {
            /*mc.toastManager,
            *///?}
            SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(body))
    }
}
//?}
