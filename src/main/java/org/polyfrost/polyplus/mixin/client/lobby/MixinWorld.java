package org.polyfrost.polyplus.mixin.client.lobby;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public class MixinWorld {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3d polyplus$lobbySkyColor(Vec3d color) {
        return LobbyScene.terrainActive() ? LobbySky.skyColor(color) : color;
    }
}
*///?}
