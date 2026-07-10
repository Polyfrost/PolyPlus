package org.polyfrost.polyplus.client

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
//? if >= 1.21.10 {
import net.minecraft.network.chat.FontDescription
//?}
//? if >= 1.21.1 {
import com.mojang.authlib.GameProfile
//?}
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}
//? if >= 1.21.1 && < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}
//? if >= 1.21.8 {
import net.minecraft.client.renderer.RenderPipelines
//?}
//? if >= 1.21.4 && < 1.21.8 {
/*import net.minecraft.client.renderer.RenderType
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

    @JvmField
    val badgeGlyph: Component = Component.literal(GLYPH).setStyle(BADGE_STYLE)

    @JvmStatic
    fun decorate(name: Component, uuid: UUID): Component {
        if (!shouldBadge(uuid)) return name

        return Component.empty()
            .append(badgeGlyph)
            .append(name)
    }

    //? if >= 1.21.1 {
    private val BADGE_TEXTURE: Identifier = Identifier.fromNamespaceAndPath("polyplus", "textures/badge.png")

    private const val TEX_W = 48
    private const val TEX_H = 45

    private const val TEXT_H = 8

    private const val BADGE_H = 8
    private const val BADGE_W = (BADGE_H * TEX_W + TEX_H / 2) / TEX_H

    private const val BADGE_Y_OFFSET = (TEXT_H - BADGE_H) / 2

    private const val PAD_LEFT = 1
    private const val PAD_RIGHT = PAD_LEFT + 1

    const val BADGE_ADVANCE = PAD_LEFT + BADGE_W + PAD_RIGHT

    @JvmStatic
    fun tabUuid(profile: GameProfile): UUID =
        //? if >= 1.21.10 {
        profile.id()
        //?} else {
        /*profile.getId()
        *///?}

    @JvmStatic
    //? if >= 26.1 {
    fun blitTab(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
    //?} else {
    /*fun blitTab(graphics: GuiGraphics, x: Int, y: Int) {
    *///?}
        val bx = x + PAD_LEFT
        val by = y + BADGE_Y_OFFSET
        //? if >= 1.21.8 {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            BADGE_TEXTURE,
            bx, by,
            0f, 0f,
            BADGE_W, BADGE_H,
            TEX_W, TEX_H,
            TEX_W, TEX_H,
        )
        //?} else if >= 1.21.4 {
        /*graphics.blit(
            RenderType::guiTextured,
            BADGE_TEXTURE,
            bx, by,
            0f, 0f,
            BADGE_W, BADGE_H,
            TEX_W, TEX_H,
            TEX_W, TEX_H,
        )
        *///?} else {
        /*graphics.blit(
            BADGE_TEXTURE,
            bx, by,
            BADGE_W, BADGE_H,
            0f, 0f,
            TEX_W, TEX_H,
            TEX_W, TEX_H,
        )
        *///?}
    }
    //?}
}
