package org.polyfrost.polyplus.mixin.client.access;

import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(User.class)
public interface UserAccessor {
    @Mutable
    @Accessor("name")
    void setName(String name);

    @Mutable
    @Accessor("uuid")
    void setUuid(UUID uuid);

    @Mutable
    @Accessor("accessToken")
    void setAccessToken(String accessToken);
}
