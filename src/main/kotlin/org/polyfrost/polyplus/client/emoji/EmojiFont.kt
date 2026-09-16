package org.polyfrost.polyplus.client.emoji

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

//? if >= 1.21.10 {
import net.minecraft.network.chat.FontDescription
//?}

object EmojiFont {
    private val FONT: Identifier = Identifier.fromNamespaceAndPath("polyplus", "emoji")

    fun apply(base: Style): Style =
        //? if >= 1.21.10 {
        base.withFont(FontDescription.Resource(FONT))
        //?} else {
        /*base.withFont(FONT)
        *///?}

    fun glyph(codepoint: String, base: Style): Component =
        Component.literal(codepoint).setStyle(apply(base))
}
