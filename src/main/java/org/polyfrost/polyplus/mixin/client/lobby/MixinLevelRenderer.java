package org.polyfrost.polyplus.mixin.client.lobby;

//? if >= 1.21.10 && < 26.1 {
/*import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "extractVisibleBlockEntities", at = @At("RETURN"))
    private void polyplus$hideLobbyBlockEntities(Camera camera, float partialTick, LevelRenderState state, CallbackInfo ci) {
        LobbyScene.hideUnrevealedBlockEntities(state.blockEntityRenderStates);
    }
}
*///?} elif < 1.21.10 && > 1.8.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinLevelRenderer {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void polyplus$hideLobbyBlockEntity(BlockEntity blockEntity, float partialTick, PoseStack poses, MultiBufferSource buffers, CallbackInfo ci) {
        if (LobbyScene.unrevealed(blockEntity.getBlockPos().getX(), blockEntity.getBlockPos().getZ())) ci.cancel();
    }
}
*///?}
