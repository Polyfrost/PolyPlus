package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(Session.class)
public class MixinSession {
    @Unique
    private String polyplus$uuidString;
    @Unique
    private UUID polyplus$uuid;

    @WrapOperation(method = "getProfile", at = @At(value = "INVOKE", target = "Lcom/mojang/util/UUIDTypeAdapter;fromString(Ljava/lang/String;)Ljava/util/UUID;"))
    private UUID polyplus$cacheUuid(String input, Operation<UUID> original) {
        UUID uuid = this.polyplus$uuid;
        if (uuid == null || !input.equals(this.polyplus$uuidString)) {
            uuid = original.call(input);
            this.polyplus$uuidString = input;
            this.polyplus$uuid = uuid;
        }
        return uuid;
    }
}
*///?}
