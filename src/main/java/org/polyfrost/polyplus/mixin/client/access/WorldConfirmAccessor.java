package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConfirmScreen.class)
public interface WorldConfirmAccessor {
    @Accessor("message")
    Component polyplusMessage();

    //? if >= 1.21.8 {
    @Accessor("yesButtonComponent")
    //?} else {
    /*@Accessor("yesButton")
    *///?}
    Component polyplusYes();

    //? if >= 1.21.8 {
    @Accessor("noButtonComponent")
    //?} else {
    /*@Accessor("noButton")
    *///?}
    Component polyplusNo();

    @Accessor("callback")
    BooleanConsumer polyplusCallback();
}
//?}
