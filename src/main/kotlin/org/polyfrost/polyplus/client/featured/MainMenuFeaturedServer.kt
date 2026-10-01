package org.polyfrost.polyplus.client.featured

//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ServerData
//?} else {
/*import net.minecraft.client.options.ServerListEntry as ServerData
*///?}

object MainMenuFeaturedServer {
    @JvmStatic
    @JvmOverloads
    fun current(
        snapshot: FeaturedServersSnapshot = FeaturedServers.snapshot(),
        nowMillis: Long = System.currentTimeMillis(),
    ): FeaturedServer? = snapshot.mainMenuFeaturedServers(nowMillis).firstOrNull()

    @JvmStatic
    fun isDismissible(server: FeaturedServer): Boolean = server.featured?.dismissibleInMainMenu == true

    @JvmStatic
    fun dismiss(server: FeaturedServer) {
        val campaign = server.featured ?: return
        if (!campaign.dismissibleInMainMenu) return
        FeaturedServers.dismissMainMenu(campaign.campaignId)
    }

    // reused across menu displays so the card keeps its ping results instead of re-pinging every time
    private var cachedServerData: ServerData? = null
    private var cachedKey: Pair<String, String>? = null

    @JvmStatic
    fun serverData(server: FeaturedServer): ServerData {
        val key = server.name to server.address
        cachedServerData?.takeIf { cachedKey == key }?.let { return it }
        //? if > 1.8.9 {
        val data = ServerData(server.name, server.address, ServerData.Type.OTHER)
        //?} else {
        /*val data = ServerData(server.name, server.address, false)
        *///?}
        cachedServerData = data
        cachedKey = key
        return data
    }
}
