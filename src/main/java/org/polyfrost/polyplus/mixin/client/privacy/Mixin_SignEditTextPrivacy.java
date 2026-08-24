package org.polyfrost.polyplus.mixin.client.privacy;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.polyfrost.polyplus.client.privacy.RichTextPrivacy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignEditScreen.class)
public class Mixin_SignEditTextPrivacy {
    @Shadow
    @Final
    private String[] messages;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;ZZLnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN")
    )
    private void polyplus$dontResolveSignText(SignBlockEntity sign, boolean frontText, boolean filtered, Component title, CallbackInfo ci) {
        SignText text = sign.getText(frontText);
        for (int i = 0; i < this.messages.length; i++) {
            this.messages[i] = RichTextPrivacy.unresolved(text.getMessage(i, filtered));
        }
    }
}
