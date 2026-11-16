package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsAvailability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.CompletableFuture;

@Mixin(RealmsAvailability.class)
public interface RealmsAvailabilityAccessor {
    @Accessor("future")
    static void polyplus$setFuture(CompletableFuture<RealmsAvailability.Result> future) {
        throw new AssertionError();
    }
}
//?}
