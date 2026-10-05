package org.polyfrost.polyplus.client.privacy;

public interface UnblockedTranslationsAccess {
    // what a blocked key translates to without the blocked mods' own translations, or null if nothing else translates it
    String polyplus$unblockedTranslation(String key);
}
