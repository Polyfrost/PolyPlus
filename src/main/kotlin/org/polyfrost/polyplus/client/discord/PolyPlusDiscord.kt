package org.polyfrost.polyplus.client.discord

import de.jcm.discordgamesdk.DiscordEventAdapter

/**
 * TODO: Implement Discord event handling. f.ex, join events, spectate events, etc.
 */
class PolyPlusDiscord : DiscordEventAdapter() {
    override fun onActivityJoin(secret: String?) {
        println("Joined with secret: $secret")
    }
}
