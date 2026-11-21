package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PauseScreen.class)
public interface PauseScreenAccessor {
    @Accessor("disconnectButton")
    Button polyplus$disconnectButton();
}
//?}
