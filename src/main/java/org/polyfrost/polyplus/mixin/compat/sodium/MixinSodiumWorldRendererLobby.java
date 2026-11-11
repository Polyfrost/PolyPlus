package org.polyfrost.polyplus.mixin.compat.sodium;

//? if >= 1.21.10 && < 26.1 {
/*import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public class MixinSodiumWorldRendererLobby {
    @Inject(method = "extractBlockEntities", at = @At("TAIL"), remap = false, require = 0, expect = 0)
    private void polyplus$hideUnrevealedBlockEntities(Camera camera, float tickDelta, Long2ObjectMap<?> progression, LevelRenderState state, CallbackInfo ci) {
        if (LobbyScene.terrainActive()) LobbyScene.hideUnrevealedBlockEntities(state.blockEntityRenderStates);
    }
}
*///?}
