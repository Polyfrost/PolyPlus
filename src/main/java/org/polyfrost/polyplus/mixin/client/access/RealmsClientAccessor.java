package org.polyfrost.polyplus.mixin.client.access;

//? if >= 1.21.5 {
import com.mojang.realmsclient.client.RealmsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RealmsClient.class)
public interface RealmsClientAccessor {
    @Accessor("realmsClientInstance")
    static void polyplus$setInstance(RealmsClient client) {
        throw new AssertionError();
    }
}
//?}
