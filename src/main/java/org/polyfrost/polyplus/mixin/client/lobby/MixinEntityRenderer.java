package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
//? if >= 1.21.10
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseAvatar;
import org.polyfrost.polyplus.client.lobby.LobbyHologram;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    //? if >= 1.21.10 {
    @ModifyExpressionValue(method = "extractShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object polyplus$forceLobbyShadows(Object original) {
        if (LobbyHologram.extracting) return Boolean.FALSE;
        return LobbyScene.terrainActive() ? Boolean.TRUE : original;
    }

    @ModifyExpressionValue(method = "extractShadow", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;distanceToCameraSq:D"))
    private double polyplus$extendLobbyShadows(double original) {
        return LobbyScene.shadowDistance(original);
    }
    //?}

    //? if = 1.21.10 {
    /*@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/debug/DebugScreenEntryList;isCurrentlyEnabled(Lnet/minecraft/resources/ResourceLocation;)Z"))
    private boolean polyplus$hideLobbyHitboxes(boolean original) {
        return original && !LobbyScene.terrainActive();
    }
    *///?}

    @ModifyReturnValue(method = "getPackedLightCoords", at = @At("RETURN"))
    private int polyplus$keyLightLobbyCast(int original, Entity entity, float partialTickTime) {
        return PauseAvatar.light(entity, LobbyScene.lightFor(entity, original));
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.entity.EntityRenderer;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @ModifyExpressionValue(method = "postRender", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;entityShadows:Z"))
    private boolean polyplus$forceLobbyShadows(boolean original) {
        return LobbyScene.terrainActive() || original;
    }

    @ModifyExpressionValue(method = "postRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;squaredDistanceToCamera(DDD)D"))
    private double polyplus$extendLobbyShadows(double original) {
        return LobbyScene.shadowDistance(original);
    }
}
*///?}
