package org.polyfrost.polyplus.mixin.compat.confirmdisconnect;

//? if < 1.21.8 && > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.Screen;
import org.objectweb.asm.Opcodes;
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen", remap = false)
public class MixinConfirmDisconnectScreen {
    @ModifyExpressionValue(
            method = "lambda$init$1",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/microcontrollers/confirmdisconnect/ConfirmDisconnectScreen;parent:Lnet/minecraft/client/gui/screens/Screen;",
                    opcode = Opcodes.GETFIELD
            ),
            remap = true,
            require = 0,
            expect = 0
    )
    private Screen polyplus$vanillaParent(Screen parent) {
        return parent instanceof OneClientPauseScreen pause ? pause.getVanilla() : parent;
    }
}
*///?}
