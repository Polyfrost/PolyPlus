package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConfirmScreen.class)
public interface WorldConfirmAccessor {
    @Accessor("message")
    Component polyplusMessage();

    @Accessor("yesButtonComponent")
    Component polyplusYes();

    @Accessor("noButtonComponent")
    Component polyplusNo();

    @Accessor("callback")
    BooleanConsumer polyplusCallback();
}
//?}
