package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache;
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
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
//?}

//? if < 1.21.10 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

@Mixin(AbstractClientPlayer.class)
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

        //~ if >= 1.21.10 'getId' -> 'id'
        var capeLocation = CosmeticAssetCache.getCapeTexture(this.playerInfo.getProfile().id());
        if (capeLocation == null) {
            return original;
        }

        return new PlayerSkin(
                // FIXME: how to simplify this
                //? if >= 1.21.10 {
                original.body(),
                new ClientAsset.ResourceTexture(capeLocation),
                original.elytra(),
                original.model(),
                original.secure()
                //?} else {
                /*original.texture(),
                original.textureUrl(),
                capeLocation,
                original.elytraTexture(),
                original.model(),
                original.secure()
                *///?}
        );
    }
}
//?} else {
/*import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.World;
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache;
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess;
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess;
import org.polyfrost.polyplus.client.emotes.playback.EmoteController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinAbstractClientPlayer extends PlayerEntity implements PlayerEmotesAccess, PlayerCosmeticsAccess {
    @Unique
    private EmoteController polyplus$emoteController;

    @Unique
    private CosmeticEquipment polyplus$cosmeticEquipment;

    private MixinAbstractClientPlayer(World world, GameProfile profile) {
        super(world, profile);
    }

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

    @Inject(method = "getCapeTextureLocation", at = @At("HEAD"), cancellable = true)
    private void polyplus$replaceCapeTexture(CallbackInfoReturnable<Identifier> cir) {
        Identifier cape = CosmeticAssetCache.getCapeTexture(((ClientPlayerEntity) (Object) this).getUuid());
        if (cape != null) cir.setReturnValue(cape);
    }
}
*///?}
