package org.polyfrost.polyplus.mixin.compat.soundfix;

//? if = 1.8.9 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "io.github.moehreag.soundfix.b3d_audio.SoundBuffer", remap = false)
public interface SoundBufferAccessor {
    @Accessor(value = "hasAlBuffer", remap = false)
    boolean polyplus$hasAlBuffer();
}
*///?}
