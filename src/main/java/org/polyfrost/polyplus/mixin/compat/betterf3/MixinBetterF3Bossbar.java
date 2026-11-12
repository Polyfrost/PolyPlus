package org.polyfrost.polyplus.mixin.compat.betterf3;

//? if >= 1.21.10 {
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BossHealthOverlay.class, priority = 1500)
public class MixinBetterF3Bossbar {
    @TargetHandler(mixin = "me.cominixo.betterf3.mixin.bossbar.BossbarMixin", name = "init")
    @ModifyExpressionValue(
            method = "@MixinSquared:Handler",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showDebugScreen()Z"
            ),
            require = 0,
            expect = 0
    )
    private boolean polyplus$onlyWhileF3Open(boolean shown) {
        //? if >= 1.21.11 {
        return shown && Minecraft.getInstance().debugEntries.isOverlayVisible();
        //?} else
        /*return shown && Minecraft.getInstance().debugEntries.isF3Visible();*/
    }
}
//?}
