package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.server.IntegratedServer;
//?} else
//import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;

//? if = 26.2 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
*///?}

@Mixin(IntegratedServer.class)
public class MixinIntegratedServer {
    // 26.3 no longer touches the client player from the server thread here
    //? if = 26.2 {
    /*@WrapMethod(method = "updatePermissionAndChatAbilities")
    private void polyplus$deferToRenderThread(LocalPlayer player, Operation<Void> original) {
        if (RenderSystem.isOnRenderThread()) {
            original.call(player);
        } else {
            Minecraft.getInstance().execute(() -> original.call(player));
        }
    }
    *///?}
}
