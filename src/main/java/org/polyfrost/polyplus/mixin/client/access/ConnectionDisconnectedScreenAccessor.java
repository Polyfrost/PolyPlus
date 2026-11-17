package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DisconnectedScreen.class)
public interface ConnectionDisconnectedScreenAccessor {
    @Accessor("parent")
    Screen polyplusParent();

    @Accessor("details")
    DisconnectionDetails polyplusDetails();

    @Accessor("buttonText")
    Component polyplusButtonText();
}
//?}
