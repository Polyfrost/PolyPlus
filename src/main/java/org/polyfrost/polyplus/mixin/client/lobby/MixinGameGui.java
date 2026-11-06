package org.polyfrost.polyplus.mixin.client.lobby;

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public class MixinGameGui {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideLobbyHud(CallbackInfo ci) {
        if (LobbyScene.terrainActive() || PauseStage.hidesHud()) {
            Minecraft.getInstance().gameRenderer.setupGuiState();
            ci.cancel();
        }
    }
}
*///?}
