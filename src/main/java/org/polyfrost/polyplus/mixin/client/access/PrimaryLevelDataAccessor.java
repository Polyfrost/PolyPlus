package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 && < 26.2 {
/*import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PrimaryLevelData.class)
public interface PrimaryLevelDataAccessor {
    @Accessor("settings")
    LevelSettings polyplus$getSettings();

    @Mutable
    @Accessor("settings")
    void polyplus$setSettings(LevelSettings settings);
}
*///?}
