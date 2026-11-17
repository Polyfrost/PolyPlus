package org.polyfrost.polyplus.mixin.client;

//? if < 1.21.9 && > 1.8.9 {
/*import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.polyfrost.polyplus.client.gui.screens.connection.TerrainCard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingProgress {
    @Shadow @Final private StoringChunkProgressListener progressListener;

    @Shadow private long lastNarration;

    @Unique
    private TerrainCard.Claim polyplus$card;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$claimCard(StoringChunkProgressListener listener, CallbackInfo ci) {
        polyplus$card = TerrainCard.claim(true);
        TerrainCard.carry(polyplus$card);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void polyplus$drawCard(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        TerrainCard.Claim card = polyplus$card;
        if (card != null && TerrainCard.draw(graphics, card, progressListener.getProgress() / 100f)) {
            long now = Util.getMillis();
            if (now - lastNarration > 2000L) {
                lastNarration = now;
                ((Screen) (Object) this).triggerImmediateNarration(true);
            }
            ci.cancel();
        }
    }
}
*///?} else {
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public class MixinLevelLoadingProgress {
}
//?}
