package org.polyfrost.polyplus.client.emotes.duo

import org.polyfrost.polyplus.client.network.http.responses.SharedEmotePrivacy

enum class DuoEmoteRequests(internal val privacy: SharedEmotePrivacy) {
    EVERYONE(SharedEmotePrivacy.Everyone),
    FRIENDS(SharedEmotePrivacy.Friends),
    NOBODY(SharedEmotePrivacy.Nobody);

    companion object {
        fun of(privacy: SharedEmotePrivacy): DuoEmoteRequests = entries.firstOrNull { it.privacy == privacy } ?: EVERYONE
    }
}
