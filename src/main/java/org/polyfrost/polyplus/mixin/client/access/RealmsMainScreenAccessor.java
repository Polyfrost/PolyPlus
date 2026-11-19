package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.gui.RealmsServerList;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RealmsMainScreen.class)
public interface RealmsMainScreenAccessor {
    @Accessor("lastScreen")
    Screen polyplus$lastScreen();

    @Accessor("serverList")
    RealmsServerList polyplus$serverList();

    @Invoker("refreshListAndLayout")
    void polyplus$refreshListAndLayout();
}
//?}
