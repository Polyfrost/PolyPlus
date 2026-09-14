package org.polyfrost.polyplus.mixin.client.privacy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.privacy.RichTextPrivacy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilScreen.class)
public class MixinAnvilScreen {

    @WrapOperation(
            method = {"slotChanged", "onNameChanged"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"),
            require = 2
    )
    private String polyplus$dontResolveItemName(Component hoverName, Operation<String> original) {
        return RichTextPrivacy.unresolved(hoverName);
    }
}
