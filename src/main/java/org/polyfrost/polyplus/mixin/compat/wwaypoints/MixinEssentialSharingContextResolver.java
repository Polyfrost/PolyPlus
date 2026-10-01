package org.polyfrost.polyplus.mixin.compat.wwaypoints;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded(value = "wwaypoints", minVersion = "0.8.0", maxVersion = "0.8.0")
@Mixin(targets = "com.wwaypoints.client.sharing.EssentialSharingContextResolver", remap = false)
public class MixinEssentialSharingContextResolver {
    @Unique
    private static final boolean polyplus$essentialPresent = polyplus$findEssential();

    @Unique
    private static boolean polyplus$findEssential() {
        try {
            Class.forName("gg.essential.Essential", false, Minecraft.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void polyplus$skipWithoutEssential(Minecraft minecraft, CallbackInfoReturnable<Object> cir) {
        if (!polyplus$essentialPresent) cir.setReturnValue(null);
    }
}
