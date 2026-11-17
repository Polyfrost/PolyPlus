package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(OnlineOptionsScreen.class)
public interface SettingsGeneralOnlineAccessor {
    @Invoker("applyFriendSettings")
    static void polyplus$applyFriendSettings(Minecraft minecraft, boolean friendsListEnabled, boolean allowFriendRequests, Consumer<Boolean> onResult) {
        throw new AssertionError();
    }

    @Accessor("FRIENDS_CONFIRM_TITLE")
    static Component polyplus$confirmTitle() {
        throw new AssertionError();
    }

    @Accessor("FRIENDS_CONFIRM_MESSAGE")
    static Component polyplus$confirmMessage() {
        throw new AssertionError();
    }

    @Accessor("FRIENDS_CONFIRM_TURN_ON")
    static Component polyplus$confirmTurnOn() {
        throw new AssertionError();
    }

    @Accessor("FRIENDS_CONFIRM_TURN_OFF")
    static Component polyplus$confirmTurnOff() {
        throw new AssertionError();
    }
}
//?}
