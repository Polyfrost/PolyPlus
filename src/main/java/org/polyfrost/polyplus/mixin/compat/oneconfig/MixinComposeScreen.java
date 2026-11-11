package org.polyfrost.polyplus.mixin.compat.oneconfig;

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ComposeScreen.class, remap = false)
public abstract class MixinComposeScreen {
    @Shadow
    private TitleScreen panorama;

    @Inject(method = "render", at = @At("HEAD"), remap = true)
    private void polyplus$drawBackdrop(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (LobbyScene.showing() || self instanceof PolyPlusLobbyScreen) {
            panorama = null;
            return;
        }
        if (MenuPanorama.drawComposeBackdrop(self)) {
            panorama = null;
        } else if (panorama == null && Minecraft.getInstance().world == null) {
            panorama = MenuPanorama.legacyPanorama();
        }
    }
}
*///?}
