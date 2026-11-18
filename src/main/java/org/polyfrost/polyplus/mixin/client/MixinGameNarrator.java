package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.text2speech.Narrator;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameNarrator.class)
public class MixinGameNarrator {
    @Unique
    private static final Logger POLYPLUS_LOGGER = LoggerFactory.getLogger("polyplus/narrator");

    @Shadow
    @Final
    private Narrator narrator;

    @Unique
    private static boolean polyplus$isMuted() {
        if (!PolyPlusConfig.getDisableNarratorWhileMuted()) return false;
        Options options = Minecraft.getInstance().options;
        return options != null
            && (options.getSoundSourceVolume(SoundSource.MASTER) <= 0f || options.getSoundSourceVolume(SoundSource.VOICE) <= 0f);
    }

    @ModifyReturnValue(method = "getStatus", at = @At("RETURN"))
    private NarratorStatus polyplus$offWhileMuted(NarratorStatus original) {
        return polyplus$isMuted() ? NarratorStatus.OFF : original;
    }

    @ModifyReturnValue(method = "isActive", at = @At("RETURN"))
    private boolean polyplus$inactiveWhileMuted(boolean original) {
        return original && !polyplus$isMuted();
    }

    @Inject(method = "checkStatus", at = @At("HEAD"), cancellable = true)
    private void polyplus$skipNarratorFailureDialog(boolean requiredActive, CallbackInfo ci) {
        if (requiredActive && !narrator.active()) {
            POLYPLUS_LOGGER.warn("Text-to-speech library failed to initialize; narrator is unavailable");
        }
        ci.cancel();
    }
}
