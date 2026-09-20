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

    @JvmStatic
    fun serverData(server: FeaturedServer): ServerData =
        //? if > 1.8.9 {
        ServerData(server.name, server.address, ServerData.Type.OTHER)
        //?} else {
        /*ServerData(server.name, server.address, false)
        *///?}
}
