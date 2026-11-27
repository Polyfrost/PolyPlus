package org.polyfrost.polyplus.mixin.client.pets;

//? if >= 1.21.10 {
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import org.polyfrost.polyplus.client.pets.PetRenderHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 26.2 {
import net.minecraft.client.renderer.extract.LevelExtractor;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}
//? if >= 26.1 {
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} else {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}

//? if >= 26.2 {
@Mixin(LevelExtractor.class)
//?} else {
/*@Mixin(LevelRenderer.class)
*///?}
public class MixinPetExtract {
    @Shadow
    private ClientLevel level;

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void polyplus$extractPets(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState state, CallbackInfo ci) {
        PetRenderHook.extract(this.level, camera, frustum, deltaTracker);
    }
}
//?}
