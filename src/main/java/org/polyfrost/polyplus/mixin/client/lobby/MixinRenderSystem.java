package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.4 && > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class MixinRenderSystem {
    @ModifyReturnValue(method = "getRendertypeSolidShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$solid(ShaderInstance original) {
        return LobbyPipelines.solid(original);
    }

    @ModifyReturnValue(method = "getRendertypeCutoutMippedShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$cutoutMipped(ShaderInstance original) {
        return LobbyPipelines.cutoutMipped(original);
    }

    @ModifyReturnValue(method = "getRendertypeCutoutShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$cutout(ShaderInstance original) {
        return LobbyPipelines.cutout(original);
    }

    @ModifyReturnValue(method = "getRendertypeTranslucentShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$translucent(ShaderInstance original) {
        return LobbyPipelines.translucent(original);
    }

    @ModifyReturnValue(method = "getRendertypeTripwireShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$tripwire(ShaderInstance original) {
        return LobbyPipelines.tripwire(original);
    }

    @ModifyReturnValue(method = "getRendertypeEntityCutoutNoCullShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$entity(ShaderInstance original) {
        return LobbyPipelines.entity(original);
    }

    @ModifyReturnValue(method = "getRendertypeEntitySolidShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$hologramSolid(ShaderInstance original) {
        return LobbyPipelines.hologram(original);
    }

    @ModifyReturnValue(method = "getRendertypeEntityCutoutShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$hologramCutout(ShaderInstance original) {
        return LobbyPipelines.hologram(original);
    }

    @ModifyReturnValue(method = "getRendertypeEntityTranslucentShader", at = @At("RETURN"))
    private static ShaderInstance polyplus$hologramTranslucent(ShaderInstance original) {
        return LobbyPipelines.hologram(original);
    }
}
*///?} elif < 1.21.5 && > 1.8.9 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderProgram;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {
    @ModifyVariable(method = "setShader(Lnet/minecraft/client/renderer/ShaderProgram;)Lnet/minecraft/client/renderer/CompiledShaderProgram;", at = @At("HEAD"), argsOnly = true)
    private static ShaderProgram polyplus$lobbyShader(ShaderProgram original) {
        return LobbyPipelines.shader(original);
    }
}
*///?}
