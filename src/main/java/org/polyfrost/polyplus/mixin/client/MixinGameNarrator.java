package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameNarrator.class)
public class MixinGameNarrator {
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
}
