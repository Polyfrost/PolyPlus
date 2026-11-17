package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionsSubScreen.class)
public interface SettingsOptionsSubScreenAccessor {
    @Accessor("lastScreen")
    Screen polyplus$lastScreen();
}
//?}
