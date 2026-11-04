package org.polyfrost.polyplus.client.featured;

//? if > 1.8.9 {
public interface FeaturedServerListAccess {
    void polyplus$rebuildFeaturedServers();
    void polyplus$saveFeaturedServer(FeaturedServerRowRegistry.Row row, int moveDirection);
    void polyplus$followSavedOrder();
}
//?} else {
/*public interface FeaturedServerListAccess {
    void polyplus$rebuildFeaturedServers();
    void polyplus$saveFeaturedServer(FeaturedServerRowRegistry.Row row);
}
*///?}
