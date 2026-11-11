package org.polyfrost.polyplus.mixin.compat.confirmdisconnect;

//? if > 1.8.9 {
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PauseScreen.class, priority = 1500)
public class MixinConfirmDisconnectPauseScreen {
    @TargetHandler(mixin = "dev.microcontrollers.confirmdisconnect.mixin.PauseScreenMixin", name = "onDisconnect")
    @ModifyExpressionValue(
            method = "@MixinSquared:Handler",
            at = @At(
                    //? if >= 26.2 {
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;"
                    //?} else {
                    /*value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"
                    *///?}
            ),
            require = 0,
            expect = 0
    )
    private static Screen polyplus$vanillaPauseScreen(Screen shown) {
        return shown instanceof OneClientPauseScreen pause ? pause.getVanilla() : shown;
    }

    @TargetHandler(mixin = "dev.microcontrollers.confirmdisconnect.mixin.PauseScreenMixin", name = "onDisconnect")
    @ModifyArg(
            method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Ldev/microcontrollers/confirmdisconnect/ConfirmDisconnectScreen;<init>(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/screens/Screen;)V"),
            index = 1,
            require = 0,
            expect = 0
    )
    private static Screen polyplus$shownParent(Screen parent) {
        return ClientPlatform.INSTANCE.currentScreen() instanceof OneClientPauseScreen shown ? shown : parent;
    }

    @TargetHandler(mixin = "dev.microcontrollers.confirmdisconnect.mixin.PauseScreenMixin", name = "tickDelay")
    @ModifyExpressionValue(
            method = "@MixinSquared:Handler",
            at = @At(
                    //? if >= 26.2 {
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;"
                    //?} else {
                    /*value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"
                    *///?}
            ),
            require = 0,
            expect = 0
    )
    private static Screen polyplus$vanillaPauseScreenForTick(Screen shown) {
        return shown instanceof OneClientPauseScreen pause ? pause.getVanilla() : shown;
    }
}
//?}
