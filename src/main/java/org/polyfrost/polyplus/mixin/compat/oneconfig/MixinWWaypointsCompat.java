package org.polyfrost.polyplus.mixin.compat.oneconfig;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.polyplus.compat.WWaypointsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.compat.WWaypointsCompat", remap = false)
public class MixinWWaypointsCompat {
    @ModifyReturnValue(method = "buildTree", at = @At("RETURN"), remap = false)
    private Tree polyplus$lockStaySneaked(Tree tree) {
        WWaypointsCompat.lockStaySneaked(tree);
        return tree;
    }
}
