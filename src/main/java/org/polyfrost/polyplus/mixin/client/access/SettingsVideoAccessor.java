package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VideoSettingsScreen.class)
public interface SettingsVideoAccessor {
    @Invoker("displayOptions")
    static OptionInstance<?>[] polyplus$displayOptions(Options options) {
        throw new AssertionError();
    }

    @Invoker("qualityOptions")
    static OptionInstance<?>[] polyplus$qualityOptions(Options options) {
        throw new AssertionError();
    }

    @Invoker("preferenceOptions")
    static OptionInstance<?>[] polyplus$preferenceOptions(Options options) {
        throw new AssertionError();
    }
}
//?}
