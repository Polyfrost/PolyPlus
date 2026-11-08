package org.polyfrost.polyplus.mixin.client.lobby;

//? if >= 1.21.4 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ShaderManager;
//? if >= 1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import org.polyfrost.polyplus.client.lobby.LobbySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if = 1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.ShaderProgram;
import org.polyfrost.polyplus.client.lobby.LobbyPipelines;
*///?}

@Mixin(ShaderManager.class)
public class MixinShaderManagerLobby {
    @WrapWithCondition(method = "getPostChain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderManager;tryTriggerRecovery(Ljava/lang/Exception;)V"))
    //? if >= 1.21.11 {
    private boolean polyplus$keepPacksOnLobbyFailure(ShaderManager manager, Exception exception, @Local(argsOnly = true) Identifier id) {
    //?} else {
    /*private boolean polyplus$keepPacksOnLobbyFailure(ShaderManager manager, Exception exception, @Local(argsOnly = true) ResourceLocation id) {
    *///?}
        return !LobbySky.ownsPostEffect(id);
    }

    //? if = 1.21.4 {
    /*@WrapWithCondition(method = "getProgram", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderManager;tryTriggerRecovery(Ljava/lang/Exception;)V"))
    private boolean polyplus$keepPacksOnLobbyProgramFailure(ShaderManager manager, Exception exception, @Local(argsOnly = true) ShaderProgram program) {
        return !LobbyPipelines.ownsProgram(program);
    }

    @WrapOperation(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<?> polyplus$skipLobbyPreload(Map<?, ?> chains, Operation<Collection<?>> original) {
        return chains.entrySet().stream()
                .filter(entry -> !LobbySky.INSTANCE.getPOST_EFFECT().equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
    *///?}
}
//?}
