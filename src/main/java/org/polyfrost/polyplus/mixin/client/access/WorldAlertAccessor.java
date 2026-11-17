package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AlertScreen.class)
public interface WorldAlertAccessor {
    @Accessor("messageText")
    Component polyplusMessage();

    @Accessor("okButton")
    Component polyplusOk();

    @Accessor("callback")
    Runnable polyplusCallback();
}
//?}
