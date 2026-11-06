package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.10 && > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Unique
    //? if >= 1.21.5 {
    /^private static final String POLYPLUS_RENDER = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V";
    ^///?} elif >= 1.21.4 {
    /^private static final String POLYPLUS_RENDER = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V";
    ^///?} else {
    /^private static final String POLYPLUS_RENDER = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    ^///?}

    @ModifyExpressionValue(method = POLYPLUS_RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object polyplus$forceLobbyShadows(Object original) {
        return LobbyScene.terrainActive() ? Boolean.TRUE : original;
    }

    //? if >= 1.21.4 {
    /^@ModifyExpressionValue(method = POLYPLUS_RENDER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;distanceToCameraSq:D"))
    ^///?} else {
    /^@ModifyExpressionValue(method = POLYPLUS_RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;distanceToSqr(DDD)D"))
    ^///?}
    private double polyplus$extendLobbyShadows(double original) {
        return LobbyScene.shadowDistance(original);
    }

    @ModifyReturnValue(method = "shouldRenderHitBoxes", at = @At("RETURN"))
    private boolean polyplus$hideLobbyHitboxes(boolean original) {
        return original && !LobbyScene.terrainActive();
    }

    //? if < 1.21.5 {
    /^@ModifyExpressionValue(method = POLYPLUS_RENDER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderHitBoxes:Z"))
    private boolean polyplus$hideLobbyHitboxLines(boolean original) {
        return original && !LobbyScene.terrainActive();
    }
    ^///?}
}
*///?} elif = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseAvatar;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/entity/Entity;FZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getLightLevel(F)I"))
    private int polyplus$keyLightPauseAvatar(int original, @Local(argsOnly = true) Entity entity) {
        return PauseAvatar.light(entity, original);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitboxes:Z"))
    private boolean polyplus$hideLobbyHitboxes(boolean original) {
        return original && !LobbyScene.terrainActive();
    }
}
*///?}
