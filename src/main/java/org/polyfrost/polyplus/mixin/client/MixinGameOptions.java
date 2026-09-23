package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GameOptions.class)
public abstract class MixinGameOptions {
    @Unique
    private static final String polyplus$STREAM_CATEGORY = "key.categories.stream";

    @Shadow
    public KeyBinding[] keyBindings;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V", at = @At("RETURN"))
    private void polyplus$removeStreamKeys(CallbackInfo ci) {
        List<KeyBinding> kept = new ArrayList<>(this.keyBindings.length);
        for (KeyBinding binding : this.keyBindings) {
            if (polyplus$STREAM_CATEGORY.equals(binding.getCategory())) {
                binding.setKeyCode(0);
            } else {
                kept.add(binding);
            }
        }
        this.keyBindings = kept.toArray(new KeyBinding[0]);
        KeyBinding.getCategories().remove(polyplus$STREAM_CATEGORY);
    }
}
*///?}
