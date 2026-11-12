package org.polyfrost.polyplus.client.gui.pausescreen;

public interface ClientTranslationsAccess {
    // what a key translates to without server or world resource packs, or null if only those packs translate it
    String polyplus$clientTranslation(String key);
}
