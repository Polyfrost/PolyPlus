//? if >= 1.21.1 {
package org.polyfrost.polyplus.mixin.client.cosmetics;

import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin implements PlayerEmotesAccess, PlayerCosmeticsAccess {
    @Unique
    private EmoteController polyplus$emoteController;

    @Unique
    private CosmeticEquipment polyplus$cosmeticEquipment;

    @Override
    public EmoteController polyplus$emoteController() {
        EmoteController controller = polyplus$emoteController;
        if (controller == null) {
            controller = new EmoteController();
            polyplus$emoteController = controller;
        }
        return controller;
    }

    @Override
    public CosmeticEquipment polyplus$cosmeticEquipment() {
        CosmeticEquipment equipment = polyplus$cosmeticEquipment;
        if (equipment == null) {
            equipment = new CosmeticEquipment();
            polyplus$cosmeticEquipment = equipment;
        }
        return equipment;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$tickEmote(CallbackInfo ci) {
        polyplus$emoteController().tick((AbstractClientPlayer) (Object) this);
    }
}
//?}
