package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RealmsMainScreen.class)
public interface RealmsMainScreenAccessor {
    @Accessor("lastScreen")
    Screen polyplus$lastScreen();
}
//?}
