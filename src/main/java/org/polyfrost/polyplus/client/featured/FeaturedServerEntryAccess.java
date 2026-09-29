package org.polyfrost.polyplus.client.featured;

public interface FeaturedServerEntryAccess {
    FeaturedServerRowRegistry.Row polyplus$featuredRow();
    void polyplus$setFeaturedRow(FeaturedServerRowRegistry.Row row);
}
