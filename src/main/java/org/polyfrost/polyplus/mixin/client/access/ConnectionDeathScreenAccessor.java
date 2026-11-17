package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import net.minecraft.client.gui.screens.DeathScreen;
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

    @Accessor("player")
    LocalPlayer polyplusPlayer();

    @Invoker("handleExitToTitleScreen")
    void polyplusHandleExitToTitleScreen();
}
//?}
