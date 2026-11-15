package org.polyfrost.polyplus.mixin.client;

import net.minecraft.client.Minecraft;
import org.polyfrost.polyplus.client.OneClientWindowTitle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//?} else {
/*import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}

@Mixin(Minecraft.class)
public class MixinMinecraftTitle {
    //? if > 1.8.9 {
    @ModifyReturnValue(method = "createTitle", at = @At("RETURN"))
    private String polyplus$oneClientTitle(String title) {
        return OneClientWindowTitle.apply(title);
    }
    //?} else {
    /*@ModifyArg(method = "initDisplay", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setTitle(Ljava/lang/String;)V", remap = false))
    private String polyplus$oneClientTitle(String title) {
        return OneClientWindowTitle.apply(title);
    }
    *///?}
}
