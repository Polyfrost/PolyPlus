package org.polyfrost.polyplus.mixin.client.cosmetics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticSoundLibrary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if > 1.8.9 {
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;

import java.util.concurrent.CompletableFuture;
//?} else {
/*import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.system.SoundEngine;
import net.minecraft.resources.Identifier;

import java.net.URL;
*///?}

//? if >= 1.21.11 {
import net.minecraft.resources.Identifier;
//?} elif > 1.8.9 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

//? if > 1.8.9 {
@Mixin(SoundBufferLibrary.class)
//?} else {
/*@Mixin(SoundEngine.class)
*///?}
public class MixinCosmeticSounds {
    //? if > 1.8.9 {
    @WrapMethod(method = "getCompleteBuffer")
    //? if >= 1.21.11 {
    private CompletableFuture<SoundBuffer> polyplus$cosmeticSound(Identifier path, Operation<CompletableFuture<SoundBuffer>> original) {
    //?} else {
    /*private CompletableFuture<SoundBuffer> polyplus$cosmeticSound(ResourceLocation path, Operation<CompletableFuture<SoundBuffer>> original) {
    *///?}
        CompletableFuture<SoundBuffer> own = CosmeticSoundLibrary.buffer(path);
        return own != null ? own : original.call(path);
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void polyplus$clearCosmeticSounds(CallbackInfo ci) {
        CosmeticSoundLibrary.clearBuffers();
    }
    //?} else {
    /*@Inject(method = "play", at = @At("HEAD"))
    private void polyplus$loadMonoCosmeticSound(SoundInstance sound, CallbackInfo ci) {
        CosmeticSoundLibrary.loadMono((SoundEngine) (Object) this, sound.getLocation());
    }

    @WrapMethod(method = "getSoundUrl")
    private static URL polyplus$cosmeticSoundUrl(Identifier path, Operation<URL> original) {
        URL own = CosmeticSoundLibrary.url(path);
        return own != null ? own : original.call(path);
    }
    *///?}
}
