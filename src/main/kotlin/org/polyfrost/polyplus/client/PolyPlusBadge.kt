package org.polyfrost.polyplus.client

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
//? if >= 1.21.10 {
import net.minecraft.network.chat.FontDescription
//?}
//? if >= 1.21.11 && < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
*///?}
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import java.util.UUID

object PolyPlusBadge {
    private val FONT: Identifier = Identifier.fromNamespaceAndPath("polyplus", "badge")

    private const val GLYPH = "\uE000\uE001"

    private val DEBUG_FORCE = java.lang.Boolean.getBoolean("polyplus.badge.debug")

    @JvmStatic
    fun shouldBadge(uuid: UUID): Boolean =
        PolyPlusConfig.showPolyPlusIndicator && (DEBUG_FORCE || CosmeticCatalog.isPolyPlusUser(uuid))

    private val BADGE_STYLE: Style =
        //? if >= 1.21.10 {
        Style.EMPTY.withFont(FontDescription.Resource(FONT))
        //?} else {
        /*Style.EMPTY.withFont(FONT)
        *///?}

    @JvmStatic
    fun decorate(name: Component, uuid: UUID): Component {
        if (!shouldBadge(uuid)) return name

        val badge = Component.literal(GLYPH).setStyle(BADGE_STYLE)
        return Component.empty()
            .append(badge)
            .append(name)
    }

    //? if >= 1.21.11 && < 26.1 {
    /*private val BADGE_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("polyplus", "textures/badge.png")

    private const val TEX_W = 788
    private const val TEX_H = 748

    private const val TEXT_H = 8

    private const val BADGE_H = 6
    private const val BADGE_W = (BADGE_H * TEX_W + TEX_H / 2) / TEX_H

    private const val BADGE_Y_OFFSET = (TEXT_H - BADGE_H) / 2

    private const val PAD_LEFT = 1
    private const val PAD_RIGHT = PAD_LEFT + 1

    const val BADGE_ADVANCE = PAD_LEFT + BADGE_W + PAD_RIGHT

    @JvmStatic
    fun blitTab(graphics: GuiGraphics, x: Int, y: Int) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            BADGE_TEXTURE,
            x + PAD_LEFT, y + BADGE_Y_OFFSET,
            0f, 0f,
            BADGE_W, BADGE_H,
            TEX_W, TEX_H,
            TEX_W, TEX_H,
        )
    }
    *///?}
}
