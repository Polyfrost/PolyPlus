package org.polyfrost.polyplus.mixin.client.access;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("user")
    void setUser(User user);

    @Mutable
    @Accessor("profileFuture")
    void setProfileFuture(CompletableFuture<ProfileResult> profileFuture);

    @Mutable
    @Accessor("userApiService")
    void setUserApiService(UserApiService userApiService);

    @Mutable
    @Accessor("userPropertiesFuture")
    void setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> userPropertiesFuture);

    @Mutable
    @Accessor("profileKeyPairManager")
    void setProfileKeyPairManager(ProfileKeyPairManager profileKeyPairManager);

    //? if < 1.21.5 {
    /*@Mutable
    @Accessor("mainRenderTarget")
    void polyplus$setMainRenderTarget(com.mojang.blaze3d.pipeline.RenderTarget target);
    *///?}
}
