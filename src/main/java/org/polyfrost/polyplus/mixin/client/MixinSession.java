package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Session;
import org.polyfrost.polyplus.client.utils.SessionUuidCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(Session.class)
public class MixinSession {
    @Unique
    private final SessionUuidCache polyplus$uuidCache = new SessionUuidCache();

    @WrapOperation(method = "getProfile", at = @At(value = "INVOKE", target = "Lcom/mojang/util/UUIDTypeAdapter;fromString(Ljava/lang/String;)Ljava/util/UUID;"))
    private UUID polyplus$cacheUuid(String input, Operation<UUID> original) {
        UUID uuid = this.polyplus$uuidCache.lookup(input);
        if (uuid != null) {
            return uuid;
        }
        try {
            uuid = original.call(input);
        } catch (IllegalArgumentException error) {
            this.polyplus$uuidCache.storeFailure(input, error);
            throw error;
        }
        this.polyplus$uuidCache.store(input, uuid);
        return uuid;
    }
}
*///?}
