package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.render.Effect;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Effect.class)
public abstract class MixinEffect {
    @Shadow @Final
    private List<String> samplerNames;

    @Inject(method = "clear", at = @At("TAIL"))
    private void polyplus$disableExtraSamplerUnits(CallbackInfo ci) {
        int count = samplerNames.size();
        if (count <= 1) return;
        for (int unit = 1; unit < count; unit++) {
            GlStateManager.activeTexture(GLX.GL_TEXTURE0 + unit);
            GlStateManager.disableTexture();
        }
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }
}
*///?}
