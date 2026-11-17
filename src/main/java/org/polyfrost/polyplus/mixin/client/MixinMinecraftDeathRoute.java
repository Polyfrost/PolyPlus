package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.polyfrost.polyplus.client.gui.screens.Routed;
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class MixinMinecraftDeathRoute {
    @ModifyVariable(method = "openScreen", at = @At(value = "STORE", ordinal = 1), argsOnly = true)
    private Screen polyplus$routeDeathScreen(Screen screen) {
        Routed routed = ScreenRouter.route(screen);
        return routed.getConsumed() ? screen : routed.getScreen();
    }
}
*///?}
