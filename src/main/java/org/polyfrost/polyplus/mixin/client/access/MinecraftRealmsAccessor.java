package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftRealmsAccessor {
    @Mutable
    @Accessor("realmsDataFetcher")
    void polyplus$setRealmsDataFetcher(RealmsDataFetcher fetcher);
}
//?}
