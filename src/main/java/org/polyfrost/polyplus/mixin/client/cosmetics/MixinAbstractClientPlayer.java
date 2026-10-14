package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.polyfrost.polyplus.client.cosmetics.CapeResolver;
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin;
//?}

//? if < 1.21.10 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

@Mixin(value = AbstractClientPlayer.class, priority = 1500)
public abstract class MixinAbstractClientPlayer implements PlayerEmotesAccess, PlayerCosmeticsAccess {
    @Shadow
    private PlayerInfo playerInfo;

    @Unique
    private EmoteController polyplus$emoteController;

    @Unique
    private CosmeticEquipment polyplus$cosmeticEquipment;

    @Override
    public EmoteController polyplus$emoteController() {
        EmoteController controller = polyplus$emoteController;
        if (controller == null) {
            controller = new EmoteController();
            polyplus$emoteController = controller;
        }
        return controller;
    }

    @Override
    public CosmeticEquipment polyplus$cosmeticEquipment() {
        CosmeticEquipment equipment = polyplus$cosmeticEquipment;
        if (equipment == null) {
            equipment = new CosmeticEquipment();
            polyplus$cosmeticEquipment = equipment;
        }
        return equipment;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$tickEmote(CallbackInfo ci) {
        polyplus$emoteController().tick((AbstractClientPlayer) (Object) this);
    }

    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin polyplus$replaceCapeTexture(PlayerSkin original) {
        if (this.playerInfo == null) {
            return original;
        }

        GameProfile profile = this.playerInfo.getProfile();
        //~ if >= 1.21.10 'getId' -> 'id'
        return CapeResolver.applyCape(profile.id(), profile, original);
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.World;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.cosmetics.CapeResolver;
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache;
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment;
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = ClientPlayerEntity.class, priority = 1500)
public abstract class MixinAbstractClientPlayer extends PlayerEntity implements PlayerEmotesAccess, PlayerCosmeticsAccess {
    @Unique
    private EmoteController polyplus$emoteController;

    @Unique
    private CosmeticEquipment polyplus$cosmeticEquipment;

    private MixinAbstractClientPlayer(World world, GameProfile profile) {
        super(world, profile);
    }

    @Shadow
    protected abstract PlayerInfo getInfo();

    @Override
    public EmoteController polyplus$emoteController() {
        EmoteController controller = polyplus$emoteController;
        if (controller == null) {
            controller = new EmoteController();
            polyplus$emoteController = controller;
        }
        return controller;
    }

    @Override
    public CosmeticEquipment polyplus$cosmeticEquipment() {
        CosmeticEquipment equipment = polyplus$cosmeticEquipment;
        if (equipment == null) {
            equipment = new CosmeticEquipment();
            polyplus$cosmeticEquipment = equipment;
        }
        return equipment;
    }

    @Override
    public void tick() {
        super.tick();
        polyplus$emoteController().tick((ClientPlayerEntity) (Object) this);
    }

    @ModifyReturnValue(method = "getCapeTextureLocation", at = @At("RETURN"))
    private Identifier polyplus$replaceCapeTexture(Identifier original) {
        UUID playerId = ((ClientPlayerEntity) (Object) this).getUuid();
        switch (CapeResolver.decide(playerId, original == null ? null : original.getNamespace())) {
            case POLYPLUS:
                return polyplus$orOriginal(CosmeticAssetCache.getCapeTexture(playerId), original);
            case MOJANG:
                return polyplus$orOriginal(MojangCapeService.localCapeTexture(), original);
            case VANILLA:
                PlayerInfo info = getInfo();
                return info == null ? null : info.getCapeTexture();
            case NONE:
                return null;
            default:
                return original;
        }
    }

    @Unique
    private static Identifier polyplus$orOriginal(Identifier cape, Identifier original) {
        return cape != null ? cape : original;
    }

    @Inject(method = "getFovModifier", at = @At("HEAD"), cancellable = true)
    private void polyplus$disableDynamicFov(CallbackInfoReturnable<Float> cir) {
        if (!PolyPlusConfig.getDynamicFov()) cir.setReturnValue(1.0F);
    }
}
*///?}
