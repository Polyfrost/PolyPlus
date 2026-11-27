package org.polyfrost.polyplus.mixin.compat.soundfix;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.moehreag.soundfix.b3d_audio.SoundBuffer;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticSoundCache;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticSoundLibrary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

@Pseudo
@Mixin(targets = "io.github.moehreag.soundfix.sounds.SoundBufferLibrary", remap = false)
public class MixinSoundBufferLibrary implements CosmeticSoundCache {
    @Shadow
    @Final
    private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void polyplus$trackCache(CallbackInfo ci) {
        CosmeticSoundLibrary.trackCache(this);
    }

    @Inject(method = "clear", at = @At("HEAD"), remap = false, require = 0)
    private void polyplus$discardRetired(CallbackInfo ci) {
        CosmeticSoundLibrary.cacheCleared(this);
    }

    @Override
    public BooleanSupplier polyplus$detach(Identifier path) {
        CompletableFuture<SoundBuffer> buffer = this.cache.remove(path);
        if (buffer == null) return null;
        return () -> {
            if (!buffer.isDone()) {
                buffer.thenAccept(SoundBuffer::discardAlBuffer);
                return true;
            }
            if (buffer.isCompletedExceptionally()) return true;
            SoundBuffer done = buffer.join();
            done.discardAlBuffer();
            return !((SoundBufferAccessor) (Object) done).polyplus$hasAlBuffer();
        };
    }

    @WrapOperation(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resource/manager/ResourceManager;getResource(Lnet/minecraft/resource/Identifier;)Lnet/minecraft/client/resource/Resource;",
                    remap = true
            ),
            remap = false,
            require = 0
    )
    private Resource polyplus$cosmeticSound(ResourceManager manager, Identifier path, Operation<Resource> original) {
        Resource own = CosmeticSoundLibrary.resource(path);
        return own != null ? own : original.call(manager, path);
    }

    @WrapOperation(
            method = "*",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/nio/ByteBuffer;Ljavax/sound/sampled/AudioFormat;)Lio/github/moehreag/soundfix/b3d_audio/SoundBuffer;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private SoundBuffer polyplus$monoCosmeticSound(ByteBuffer data, AudioFormat format, Operation<SoundBuffer> original, @Local(argsOnly = true) Identifier path) {
        if (!CosmeticSoundLibrary.owns(path) || !CosmeticSoundLibrary.downmixes(format)) return original.call(data, format);
        return original.call(CosmeticSoundLibrary.downmix(data, format), CosmeticSoundLibrary.monoFormat(format));
    }
}
*///?}
