package org.polyfrost.polyplus.mixin.compat.oneconfig;

import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.Event;
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent;
//? if > 1.8.9
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer;
import org.polyfrost.polyplus.api.lobby.LobbyApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EventManager.class, remap = false)
public class MixinEventManager {
    @Inject(method = "post", at = @At("HEAD"), cancellable = true, remap = false)
    private void polyplus$hideSceneLevels(Event event, CallbackInfo ci) {
        if (!(event instanceof WorldEvent.Load)) return;
        //? if > 1.8.9 {
        boolean building = LobbyApi.isBuilding() || PlayerPreviewRenderer.isBuildingLevel();
        //?} else {
        /*boolean building = LobbyApi.isBuilding();
        *///?}
        if (building) ci.cancel();
    }
}
