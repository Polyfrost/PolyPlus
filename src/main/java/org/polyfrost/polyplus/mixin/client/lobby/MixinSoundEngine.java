package org.polyfrost.polyplus.mixin.client.lobby;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.polyfrost.polyplus.client.lobby.LobbySoundGate;
import org.polyfrost.polyplus.mixin.client.access.ChannelHandleAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {
    @WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object polyplus$bindLobbyChannel(Map<Object, Object> map, Object key, Object value, Operation<Object> original) {
        if (key instanceof SoundInstance sound && value instanceof ChannelAccess.ChannelHandle handle) {
            LobbySoundGate.bind(sound, ((ChannelHandleAccessor) handle).polyplus$channel());
        }
        return original.call(map, key, value);
    }
}
//?}
