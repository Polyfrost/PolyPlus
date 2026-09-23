package org.polyfrost.polyplus.mixin.client;

import net.minecraft.network.chat.FormattedText;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

//? if >= 26.1 {
import net.minecraft.client.multiplayer.chat.GuiMessage;
//?} else {
/*import net.minecraft.client.GuiMessage;
*///?}

@Mixin(GuiMessage.class)
public class MixinGuiMessage {
    // emoji are applied to the display lines only, so the stored message stays comparable for chat compacting mods
    @ModifyArg(
        method = "splitLines",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
        index = 0
    )
    private FormattedText emojiLines(FormattedText text) {
        return EmojiRegistry.transformForViewer(text);
    }
}
