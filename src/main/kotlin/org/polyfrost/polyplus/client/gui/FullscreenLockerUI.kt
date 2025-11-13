package org.polyfrost.polyplus.client.gui

import net.minecraft.client.gui.GuiScreen
import org.polyfrost.oneconfig.api.ui.v1.OCPolyUIBuilder
import org.polyfrost.oneconfig.api.ui.v1.UIManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyui.animate.SetAnimation
import org.polyfrost.polyui.color.rgba
import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.disable
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.component.extensions.onChange
import org.polyfrost.polyui.component.extensions.onClick
import org.polyfrost.polyui.component.extensions.onRightClick
import org.polyfrost.polyui.component.extensions.setFont
import org.polyfrost.polyui.component.extensions.withBorder
import org.polyfrost.polyui.component.impl.Block
import org.polyfrost.polyui.component.impl.Group
import org.polyfrost.polyui.component.impl.Image
import org.polyfrost.polyui.component.impl.Text
import org.polyfrost.polyui.component.impl.TextInput
import org.polyfrost.polyui.event.State
import org.polyfrost.polyui.unit.Align
import org.polyfrost.polyui.unit.Vec2
import org.polyfrost.polyui.utils.image
import kotlin.reflect.KMutableProperty0

object FullscreenLockerUI {
    private const val DESIGNED_WIDTH = 1920f
    private const val DESIGNED_HEIGHT = 1080f

    private var backArrow: Drawable? = null
    private var forwardArrow: Drawable? = null

    fun create(): GuiScreen {
        val uiManager = UIManager.INSTANCE
        val builder = OCPolyUIBuilder.create()
            .blurs()
            .atResolution(DESIGNED_WIDTH, DESIGNED_HEIGHT)
            .backgroundColor(rgba(21, 21, 21))
            .size(1499f, 1080f)
            .translatorDelegate("assets/${PolyPlusConstants.ID}/lang")
                as OCPolyUIBuilder

        val cartCount = State(0)
        val polyUI = builder.make(
            Group(
                // Header
                Group(
                    // Left
                    Group(
                        // TODO: Implement navigation history
                        Image("/assets/polyplus/ico/left-arrow.svg".image()).disable().onClick {
//                            val prev = previous.removeLastOrNull() ?: return@onClick false
//                            if (previous.isEmpty()) prevArrow?.disable()
//                            val current = current
//                            openPage(prev, SetAnimation.SlideRight, addToPrev = false, clearNext = false)
//                            next.add(current ?: return@onClick false)
//                            nextArrow?.disable(false)
                            false
                        }.named("Back").bindTo(::backArrow),
                        Image("/assets/polyplus/ico/right-arrow.svg".image()).disable().onClick {
//                            val nextDrawable = next.removeLastOrNull() ?: return@onClick false
//                            if (next.isEmpty()) nextArrow?.disable()
//                            openPage(nextDrawable, clearNext = false)
                            false
                        }.named("Forward").bindTo(::forwardArrow),
                        Text("polyplus.locker.title", fontSize = 24f).setFont { semiBold }.named("Title"),
                        alignment = Align(pad = Vec2(16f, 8f), wrap = Align.Wrap.NEVER),
                    ).named("Controls"),

                    // Right
                    Block(
                        Image("/assets/polyplus/ico/search.svg".image()).named("SearchIcon"),
                        TextInput(
                            placeholder = "polyplus.search.placeholder",
                            visibleSize = Vec2(210f, 12f)
                        ).onChange { text: String ->
                            // TODO: Filter cosmetics list based on search input
                            false
                        }.named("SearchInput"),

                        size = Vec2(256f, 32f),
                        alignment = Align(pad = Vec2(10f, 7f))
                    ).onRightClick {
                        (this[1] as TextInput).text = ""
                    }.withBorder(1f) { page.border5 }.named("SearchField"),

                    size = Vec2(1482f, 78f),
                    alignment = Align(main = Align.Content.SpaceBetween, line = Align.Line.Center),
                ),

                // Content
                Group(
                    // Cosmetic list
                    CosmeticList(
                        size = Vec2(972f, 802f),
                    ),

                    // Sidebar
                    Group(
                        // Purchasing options
                        CartControls(cartCount),

                        // Player preview
                        PlayerPreview(),
                    ),
                ),

                size = Vec2(1499f, 1080f),
            ),
        )

        polyUI.window = uiManager.createWindow()
        return uiManager.createPolyUIScreen(polyUI, DESIGNED_WIDTH, DESIGNED_HEIGHT, false, true) { }
    }

    private fun Drawable.bindTo(ref: KMutableProperty0<Drawable?>): Drawable {
        ref.set(this)
        return this
    }
}
