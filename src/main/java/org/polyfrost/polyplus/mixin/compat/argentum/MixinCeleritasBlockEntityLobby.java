package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.polyfrost.polyplus.client.lobby.LobbyScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer", remap = false)
public class MixinCeleritasBlockEntityLobby {
    @WrapWithCondition(
            method = "renderBlockEntityList(Ljava/util/List;Ljava/lang/Float;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;FI)V",
                    remap = true
            ),
            remap = false,
            require = 0,
            expect = 0
    )
    private boolean polyplus$hideUnrevealed(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float tickDelta, int progress) {
        return !LobbyScene.unrevealed(blockEntity.getPos().getX(), blockEntity.getPos().getZ());
    }
}
*///?}
