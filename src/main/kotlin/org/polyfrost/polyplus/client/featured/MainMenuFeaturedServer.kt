package org.polyfrost.polyplus.client.featured

import net.minecraft.client.multiplayer.ServerData

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

    // Reused across menu displays so the card keeps its ping results instead of re-pinging every time.
    private var cachedServerData: ServerData? = null

    @JvmStatic
    fun serverData(server: FeaturedServer): ServerData =
        cachedServerData?.takeIf { it.name == server.name && it.ip == server.address }
            ?: ServerData(server.name, server.address, ServerData.Type.OTHER).also { cachedServerData = it }
}
