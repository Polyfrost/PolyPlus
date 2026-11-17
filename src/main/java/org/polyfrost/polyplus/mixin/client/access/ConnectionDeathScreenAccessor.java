package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.DeathScreen;
//? if >= 1.21.11
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DeathScreen.class)
public interface ConnectionDeathScreenAccessor {
    @Accessor("causeOfDeath")
    Component polyplusCauseOfDeath();

    @Accessor("hardcore")
    boolean polyplusHardcore();

    //? if >= 1.21.11 {
    @Accessor("player")
    LocalPlayer polyplusPlayer();
    //?}

    @Invoker("handleExitToTitleScreen")
    void polyplusHandleExitToTitleScreen();
}
//?}
