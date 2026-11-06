package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.8 && > 1.8.9 {
/*import com.llamalad7.mixinextras.sugar.Local;
//? if >= 1.21.5 {
/^import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.RenderType;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
^///?} elif < 1.21.4 {
/^import net.minecraft.client.renderer.ShaderInstance;
^///?} else {
/^import net.minecraft.client.renderer.CompiledShaderProgram;
^///?}
import com.moulberry.mixinconstraints.annotations.IfModAbsent;
import net.minecraft.client.renderer.LevelRenderer;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfModAbsent("sodium")
@Mixin(LevelRenderer.class)
public class MixinLevelRendererSections {
    //? if >= 1.21.5 {
    /^@ModifyExpressionValue(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;getRenderPipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
    private RenderPipeline polyplus$lobbyTerrain(RenderPipeline original, @Local(argsOnly = true) RenderType layer) {
        return LobbyPipelines.select(layer, original);
    }

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V", shift = At.Shift.AFTER))
    private void polyplus$lobbyAim(CallbackInfo ci, @Local RenderPass pass) {
        if (LobbyScene.terrainActive()) pass.setUniform("LobbyAim", LobbyScene.aimDistance());
    }
    ^///?} elif < 1.21.4 {
    /^@Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V"))
    private void polyplus$lobbyAim(CallbackInfo ci, @Local ShaderInstance shader) {
        if (LobbyScene.terrainActive()) shader.safeGetUniform("LobbyAim").set(LobbyScene.aimDistance());
    }
    ^///?} else {
    /^@Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CompiledShaderProgram;apply()V"))
    private void polyplus$lobbyAim(CallbackInfo ci, @Local CompiledShaderProgram shader) {
        if (LobbyScene.terrainActive()) shader.safeGetUniform("LobbyAim").set(LobbyScene.aimDistance());
    }
    ^///?}
}
*///?}
