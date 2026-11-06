package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.11 && > 1.8.9 {
/*//? if < 1.21.10 && >= 1.21.5 {
/^import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
^///?}
import net.minecraft.client.renderer.LightTexture;
//? if >= 1.21.8 {
/^import org.joml.Vector3fc;
import org.polyfrost.polyplus.client.lobby.LobbySky;
^///?} elif >= 1.21.5 {
/^import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
^///?} elif < 1.21.4 {
/^import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.joml.Vector3f;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
^///?} else {
/^import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.polyfrost.polyplus.client.lobby.LobbySky;
^///?}
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LightTexture.class)
public class MixinLightTexture {
    //? if < 1.21.10 && >= 1.21.5 {
    /^@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private RenderPipeline polyplus$lobbyLightmap(RenderPipeline original) {
        return LobbyPipelines.lightmap(original);
    }

    ^///?}
    //? if >= 1.21.8 {
    /^@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 1))
    private float polyplus$lobbySkyFactor(float factor) {
        return LobbyScene.terrainActive() ? LobbySky.skyLightFactor(factor) : factor;
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;"))
    private Vector3fc polyplus$lobbySkyLight(Vector3fc color) {
        return LobbyScene.terrainActive() ? LobbySky.skyLightColor(color) : color;
    }
    ^///?} elif >= 1.21.5 {
    /^@Inject(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V", shift = At.Shift.AFTER))
    private void polyplus$lobbyNight(CallbackInfo ci, @Local RenderPass pass) {
        if (LobbyScene.terrainActive()) LobbySky.nightUniform(pass);
    }
    ^///?} elif < 1.21.4 {
    /^@ModifyVariable(method = "updateLightTexture", at = @At("STORE"), ordinal = 2)
    private float polyplus$lobbySkyFactor(float factor) {
        return LobbyScene.terrainActive() ? LobbySky.skyLightFactor(factor) : factor;
    }

    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;lerp(Lorg/joml/Vector3fc;F)Lorg/joml/Vector3f;", ordinal = 0, remap = false))
    private Vector3f polyplus$lobbySkyLight(Vector3f color) {
        return LobbyScene.terrainActive() ? new Vector3f(LobbySky.skyLightColor(color)) : color;
    }

    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;notGamma(F)F", ordinal = 0))
    private float polyplus$notGammaRed(float original, @Local(ordinal = 1) Vector3f color) {
        return LobbyScene.terrainActive() ? LobbySky.notGamma(color.x, color) : original;
    }

    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;notGamma(F)F", ordinal = 1))
    private float polyplus$notGammaGreen(float original, @Local(ordinal = 1) Vector3f color) {
        return LobbyScene.terrainActive() ? LobbySky.notGamma(color.y, color) : original;
    }

    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;notGamma(F)F", ordinal = 2))
    private float polyplus$notGammaBlue(float original, @Local(ordinal = 1) Vector3f color) {
        return LobbyScene.terrainActive() ? LobbySky.notGamma(color.z, color) : original;
    }
    ^///?} else {
    /^@ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Lnet/minecraft/client/renderer/ShaderProgram;)Lnet/minecraft/client/renderer/CompiledShaderProgram;"))
    private CompiledShaderProgram polyplus$lobbyNight(CompiledShaderProgram shader) {
        return LobbyScene.terrainActive() ? LobbySky.nightUniform(shader) : shader;
    }
    ^///?}
}
*///?}
