package org.polyfrost.polyplus.client.emoji

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

//? if >= 1.21.10 {
import net.minecraft.network.chat.FontDescription
//?}

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
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
        Minecraft.getInstance().textureManager.bind(ATLAS)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, colorBuffer)
        GL11.glColor4f(1f, 1f, 1f, alpha)
        val u = (index % 32) / 32f
        val v = (index / 32) / 24f
        val du = 1 / 32f
        val dv = 1 / 24f
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP)
        GL11.glTexCoord2f(u, v); GL11.glVertex3f(x, y - 1, 0f)
        GL11.glTexCoord2f(u, v + dv); GL11.glVertex3f(x, y + 8, 0f)
        GL11.glTexCoord2f(u + du, v); GL11.glVertex3f(x + 9, y - 1, 0f)
        GL11.glTexCoord2f(u + du, v + dv); GL11.glVertex3f(x + 9, y + 8, 0f)
        GL11.glEnd()
        GL11.glColor4f(colorBuffer.get(0), colorBuffer.get(1), colorBuffer.get(2), colorBuffer.get(3))
    }
    *///?}
}
