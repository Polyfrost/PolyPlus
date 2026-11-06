package org.polyfrost.polyplus.mixin.client.lobby;

//? if < 1.21.11 && > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
//? if < 1.21.4
/^import net.minecraft.world.phys.Vec3;^/
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public class MixinClientLevel {
    //? if >= 1.21.4 {
    /^@ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private int polyplus$lobbySkyColor(int color) {
        return LobbyScene.terrainActive() ? LobbySky.skyColor(color) : color;
    }
    ^///?} else {
    /^@ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 polyplus$lobbySkyColor(Vec3 color) {
        return LobbyScene.terrainActive() ? LobbySky.skyColor(color) : color;
    }
    ^///?}
}
*///?}
