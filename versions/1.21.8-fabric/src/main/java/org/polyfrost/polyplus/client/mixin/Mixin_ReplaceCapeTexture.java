package org.polyfrost.polyplus.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.polyfrost.polyplus.PolyPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class Mixin_ReplaceCapeTexture {
    @Shadow private PlayerListEntry playerListEntry;

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    void polyplus$onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        if (playerListEntry == null) return;
        var oldskinTexures = playerListEntry.getSkinTextures();
        var newSkinTextures = new SkinTextures(
                oldskinTexures.comp_1626(),
                oldskinTexures.comp_1911(),
                Identifier.of(PolyPlus.ID, "randomcape2.png"),
                oldskinTexures.comp_1628(),
                oldskinTexures.comp_1629(),
                oldskinTexures.comp_1630()
        );

        cir.setReturnValue(newSkinTextures);
    }
}
