package org.polyfrost.polyplus.client.featured;

public interface FeaturedServerListAccess {
    void polyplus$rebuildFeaturedServers();
    void polyplus$saveFeaturedServer(FeaturedServerRowRegistry.Row row, int moveDirection);
}
