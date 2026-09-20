package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
//?} else {
/*import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Session;
import net.minecraft.client.render.pipeline.RenderTarget;
*///?}
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.CompletableFuture;

//? if >= 26.3 {
import com.mojang.authlib.services.ProfileResult;
//?}

//? if < 26.3 && > 1.8.9 {
/*import com.mojang.authlib.yggdrasil.ProfileResult;
*///?}

//? if < 1.21.5 && > 1.8.9 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
*///?}

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    //? if > 1.8.9 {
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

    //?} else {
    /*@Mutable
    @Accessor("session")
    void setSession(Session session);

    @Accessor("profileProperties")
    PropertyMap polyplus$getProfileProperties();

    @Accessor("renderTarget")
    void polyplus$setMainRenderTarget(RenderTarget target);
    *///?}

    //? if < 1.21.5 && > 1.8.9 {
    /*@Mutable
    @Accessor("mainRenderTarget")
    void polyplus$setMainRenderTarget(RenderTarget target);
    *///?}
}
