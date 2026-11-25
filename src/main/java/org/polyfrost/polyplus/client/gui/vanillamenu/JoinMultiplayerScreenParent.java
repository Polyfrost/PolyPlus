package org.polyfrost.polyplus.client.gui.vanillamenu;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen;

/** Reads JoinMultiplayerScreen's parent, so the Friends and Discover tabs go back to the same place. */
public interface JoinMultiplayerScreenParent {
    Screen polyplus$lastScreen();
}
//?}
