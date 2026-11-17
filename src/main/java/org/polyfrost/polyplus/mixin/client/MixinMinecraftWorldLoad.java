package org.polyfrost.polyplus.mixin.client;

//? if >= 26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
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
        Optional<GameRules> gameRules,
        boolean newWorld,
        CallbackInfo ci
    ) {
        TerrainCard.noteWorldLoad(stem.worldDataAndGenSettings().data().getLevelName());
    }

    @WrapOperation(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;disconnectWithProgressScreen()V"))
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
*///?} else {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public class MixinMinecraftWorldLoad {
}
*///?}
