package org.polyfrost.polyplus.client.emoji

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

//? if >= 1.21.10 {
import net.minecraft.network.chat.FontDescription
//?}

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.platform.GlStateManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
*///?}

object EmojiFont {
    private val FONT: Identifier = Identifier.fromNamespaceAndPath("polyplus", "emoji")

    //? if >= 1.21.10 {
    fun apply(base: Style): Style = base.withFont(FontDescription.Resource(FONT))
    //?} else if > 1.8.9 {
    /*fun apply(base: Style): Style = base.withFont(FONT)
    *///?} else {
    /*fun apply(base: Style): Style = base
    *///?}

    //? if > 1.8.9 {
    fun glyph(codepoint: String, base: Style): Component =
        Component.literal(codepoint).setStyle(apply(base))
    //?} else {
    /*private val ATLAS: Identifier = Identifier.fromNamespaceAndPath("polyplus", "textures/emoji/emoji_0.png")
    private const val LEGACY_BASE = 0xF100
    private const val LEGACY_COUNT = 32 * 24
    const val LEGACY_ADVANCE = 10f
    private val colorBuffer = BufferUtils.createFloatBuffer(16)

    fun glyph(codepoint: String, base: Style): Component =
        Component.literal(legacyChar(codepoint)).setStyle(base)

    @JvmStatic
    fun legacyChar(glyph: String): String {
        val index = EmojiRegistry.atlasIndex(glyph)
        return if (index in 0 until LEGACY_COUNT) (LEGACY_BASE + index).toChar().toString() else glyph
    }

    @JvmStatic
    fun legacyIndex(chr: Char): Int = (chr.code - LEGACY_BASE).takeIf { it in 0 until LEGACY_COUNT } ?: -1

    @JvmStatic
    fun drawLegacy(index: Int, x: Float, y: Float, alpha: Float) {
        val u = (index % 32) / 32f
        val v = (index / 32) / 24f
        drawLegacyQuad(ATLAS, x, y - 1, x + 9, y + 8, u, v, u + 1 / 32f, v + 1 / 24f, alpha)
    }

    @JvmStatic
    fun drawLegacyQuad(texture: Identifier, x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float, alpha: Float) {
        // restored afterwards so Argentum don't flush their glyphs with the atlas bound
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        Minecraft.getInstance().textureManager.bind(texture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, colorBuffer)
        GL11.glColor4f(1f, 1f, 1f, alpha)
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP)
        GL11.glTexCoord2f(u0, v0); GL11.glVertex3f(x0, y0, 0f)
        GL11.glTexCoord2f(u0, v1); GL11.glVertex3f(x0, y1, 0f)
        GL11.glTexCoord2f(u1, v0); GL11.glVertex3f(x1, y0, 0f)
        GL11.glTexCoord2f(u1, v1); GL11.glVertex3f(x1, y1, 0f)
        GL11.glEnd()
        GL11.glColor4f(colorBuffer.get(0), colorBuffer.get(1), colorBuffer.get(2), colorBuffer.get(3))
        GlStateManager.bindTexture(previousTexture)
    }
    *///?}
}
