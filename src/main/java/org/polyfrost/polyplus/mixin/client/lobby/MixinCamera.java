package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >= 26.1 {
import net.minecraft.client.Camera;
//?} else {
/*import net.minecraft.client.renderer.GameRenderer;
*///?}
import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >= 26.1 {
@Mixin(value = Camera.class, priority = 2000)
//?} else {
/*@Mixin(value = GameRenderer.class, priority = 2000)
*///?}
public class MixinCamera {
    //? if >= 26.1 {
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    //?} else {
    /*@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    *///?}
    //? if >= 1.21.4 {
    private float polyplus$lobbyFov(float original) {
        return PauseStage.fov(LobbyScene.cameraFov(original));
    }
    //?} else {
    /*private double polyplus$lobbyFov(double original) {
        return PauseStage.fov(LobbyScene.cameraFov((float) original));
    }
    *///?}

    //? if >= 26.1 {
    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    *///?}
    //? if >= 1.21.4 {
    private void polyplus$holdLobbyFov(CallbackInfoReturnable<Float> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue(PauseStage.fov(LobbyScene.cameraFov(0f)));
    }
    //?} else {
    /*private void polyplus$holdLobbyFov(CallbackInfoReturnable<Double> cir) {
        if (LobbyScene.terrainActive()) cir.setReturnValue((double) PauseStage.fov(LobbyScene.cameraFov(0f)));
    }
    *///?}

    //? if >= 26.1 {
    @ModifyExpressionValue(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    //?} elif >= 1.21.10 {
    /*@ModifyExpressionValue(method = "getProjectionMatrixForCulling", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    *///?} elif >= 1.21.5 {
    /*@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 1))
    *///?} elif >= 1.21.4 {
    /*@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    *///?} else {
    /*@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    *///?}
    //? if >= 1.21.4 {
    private float polyplus$lobbyCullingFov(float original) {
        return LobbyScene.cullingFov(original);
    }
    //?} else {
    /*private double polyplus$lobbyCullingFov(double original) {
        return LobbyScene.cullingFov((float) original);
    }
    *///?}
}
//?}
