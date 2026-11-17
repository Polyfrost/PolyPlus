package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatOptionsScreen.class)
public interface SettingsGeneralChatAccessor {
    @Invoker("options")
    static OptionInstance<?>[] polyplus$options(Options options) {
        throw new AssertionError();
    }
}
//?}
