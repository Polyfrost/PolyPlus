package org.polyfrost.polyplus.mixin.client.lobby;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.world.WorldRenderer;
import org.polyfrost.polyplus.client.lobby.LobbyLegacyRender;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Inject(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"))
    private void polyplus$bindLobbyTerrain(CallbackInfoReturnable<Integer> cir) {
        if (LobbyScene.terrainActive()) LobbyLegacyRender.bindTerrain();
    }

    @Inject(method = "render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
    private void polyplus$unbindLobbyTerrain(CallbackInfoReturnable<Integer> cir) {
        if (LobbyScene.terrainActive()) LobbyLegacyRender.unbindTerrain();
    }

    @WrapWithCondition(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;FI)V"))
    private boolean polyplus$hideUnrevealed(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float tickDelta, int progress) {
        return !LobbyScene.unrevealed(blockEntity.getPos().getX(), blockEntity.getPos().getZ());
    }
}
*///?}
