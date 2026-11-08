package org.polyfrost.polyplus.mixin.client.lobby;

//? if >= 1.21.4 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
public class MixinShaderManagerLobby {
    @WrapWithCondition(method = "getPostChain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderManager;tryTriggerRecovery(Ljava/lang/Exception;)V"))
    private boolean polyplus$keepPacksOnLobbyFailure(ShaderManager manager, Exception exception, @Local(argsOnly = true) Identifier id) {
        return !LobbySky.ownsPostEffect(id);
    }
}
//?}
