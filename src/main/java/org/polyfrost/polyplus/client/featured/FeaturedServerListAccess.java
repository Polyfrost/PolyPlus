package org.polyfrost.polyplus.client.featured;

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;

public interface FeaturedServerListAccess {
    void polyplus$rebuildFeaturedServers();
    void polyplus$saveFeaturedServer(FeaturedServerRowRegistry.Row row, int moveDirection);
    void polyplus$moveSavedRow(ServerSelectionList.OnlineServerEntry moved, ServerData neighbor);
}
//?} else {
/*import java.util.List;

public interface FeaturedServerListAccess {
    void polyplus$rebuildFeaturedServers();
    void polyplus$saveFeaturedServer(FeaturedServerRowRegistry.Row row);
    List<String> polyplus$folders();
}
*///?}
