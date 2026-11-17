package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
//? if >= 26.1 {
import java.util.Optional;
import net.minecraft.world.level.gamerules.GameRules;
//?}
import org.polyfrost.polyplus.client.gui.screens.connection.TerrainCard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftWorldLoad {
    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void polyplus$noteWorldLoad(
        LevelStorageSource.LevelStorageAccess access,
        PackRepository packs,
        WorldStem stem,
        //? if >= 26.1
        Optional<GameRules> gameRules,
        boolean newWorld,
        CallbackInfo ci
    ) {
        //? if >= 26.1 {
        TerrainCard.noteWorldLoad(stem.worldDataAndGenSettings().data().getLevelName());
        //?} else {
        /*TerrainCard.noteWorldLoad(stem.worldData().getLevelName());
        *///?}
    }

    //? if >= 1.21.8 {
    @WrapOperation(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnectWithProgressScreen()V"))
    //?} else {
    /*@WrapOperation(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnect()V"))
    *///?}
    private void polyplus$preparingCard(Minecraft minecraft, Operation<Void> original) {
        if (TerrainCard.worldPending()) minecraft.disconnect(new GenericMessageScreen(TerrainCard.PREPARING), false);
        else original.call(minecraft);
    }
}
//?} elif = 1.8.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import org.polyfrost.polyplus.client.gui.screens.connection.TerrainCard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftWorldLoad {
    @Inject(method = "startGame", at = @At("HEAD"))
    private void polyplus$noteWorldLoad(String folder, String name, WorldSettings settings, CallbackInfo ci) {
        TerrainCard.noteWorldLoad(name != null ? name : folder);
    }
}
*///?}
