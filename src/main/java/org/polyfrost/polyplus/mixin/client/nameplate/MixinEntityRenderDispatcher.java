package org.polyfrost.polyplus.mixin.client.nameplate;

//? if = 1.8.9 {
/*import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Shadow
    public float cameraPitch;

    @Inject(
        method = "prepare",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;perspective:I", opcode = Opcodes.GETFIELD)
    )
    private void polyplus$recordNameTagPitch(World world, TextRenderer textRenderer, Entity camera, Entity targetEntity, GameOptions options, float tickDelta, CallbackInfo ci) {
        LegacyNameplateFacing.record(this.cameraPitch, options.perspective);
    }

    @Inject(method = "setCameraYaw", at = @At("HEAD"))
    private void polyplus$leaveWorldView(float yaw, CallbackInfo ci) {
        LegacyNameplateFacing.leaveWorldView();
    }
}
*///?}
