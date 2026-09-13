package org.polyfrost.polyplus.mixin.compat.mountopacity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.microcontrollers.mountopacity.hook.EntityHook", remap = false)
public class MixinEntityHook {
    @ModifyExpressionValue(
            method = {"setRenderLayer", "setOpacity(I)I"},
            at = @At(
                    value = "FIELD",
                    target = "Ldev/microcontrollers/mountopacity/hook/EntityHook;player:Lnet/minecraft/client/player/LocalPlayer;",
                    opcode = Opcodes.GETSTATIC
            ),
            remap = true,
            require = 0,
            expect = 0
    )
    private static LocalPlayer polyplus$livePlayer(LocalPlayer cached) {
        return Minecraft.getInstance().player;
    }
}
