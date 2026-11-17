package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.polyplus.client.gui.screens.ScreenShell;
import org.polyfrost.polyplus.client.gui.screens.ShellScreen;
import org.polyfrost.polyplus.client.gui.screens.connection.TerrainCard;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRendererShellScene {
    @Inject(method = "extract", at = @At("RETURN"))
    private void polyplus$extractLobbyBehindShell(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Screen screen = ClientPlatform.INSTANCE.currentScreen();
        TerrainCard.sweep(screen);
        if (advanceGameTime) return;
        if (screen instanceof ShellScreen shellScreen && shellScreen.getShell() == ScreenShell.Lobby) {
            LobbyScene.onExtractEnd((GameRenderer) (Object) this, deltaTracker);
        }
    }
}
//?} elif = 1.8.9 {
/*import net.minecraft.client.render.GameRenderer;
import org.polyfrost.polyplus.client.gui.screens.connection.TerrainCard;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRendererShellScene {
    @Inject(method = "render(FJ)V", at = @At("HEAD"))
    private void polyplus$sweepTerrainCard(float tickDelta, long startTime, CallbackInfo ci) {
        TerrainCard.sweep(ClientPlatform.INSTANCE.currentScreen());
    }
}
*///?} else {
/*import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.polyplus.client.gui.screens.connection.TerrainCard;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRendererShellScene {
    @Inject(method = "render", at = @At("HEAD"))
    private void polyplus$sweepTerrainCard(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        TerrainCard.sweep(ClientPlatform.INSTANCE.currentScreen());
    }
}
*///?}
