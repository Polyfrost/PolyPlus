package org.polyfrost.polyplus.client.party

import org.polyfrost.polyplus.client.network.http.PartyApiException

object PartyErrors {
    const val ALREADY_IN_PARTY = "already_in_party"
    const val NOT_IN_PARTY = "not_in_party"
    const val TRANSPORT_P2P = "transport_p2p"
    const val EOS_NOT_LINKED = "eos_not_linked"
    const val VOICE_UNAVAILABLE = "voice_unavailable"
    const val RATE_LIMITED = "rate_limited"

    private val MESSAGES = mapOf(
        ALREADY_IN_PARTY to "You're already in a party",
        NOT_IN_PARTY to "You're not in a party",
        "not_leader" to "Only the party leader can do that",
        "member_not_found" to "That player isn't in your party",
        "cannot_kick_self" to "You can't remove yourself",
        "not_allowed_to_invite" to "Only the party leader can invite",
        "not_friends" to "You can only invite friends",
        "blocked" to "You can't invite that player",
        "already_member" to "They're already in your party",
        "already_invited" to "You already invited them",
        "party_full" to "The party is full",
        "invite_not_found" to "That invite is gone",
        "invite_expired" to "That invite has expired",
        EOS_NOT_LINKED to "Voice isn't ready yet. Try again in a moment",
        VOICE_UNAVAILABLE to "Voice is unavailable right now",
        RATE_LIMITED to "Too many tries. Wait a moment",
        "session_not_found" to "That world has closed",
        "own_session" to "That's your own world",
        "host_not_in_party" to "The host isn't in your party",
        "host_offline" to "The host is offline",
        "already_in_session" to "You're already in that world",
        "already_invited_to_session" to "You already have an invite to that world",
        "join_request_pending" to "You already asked to join",
        "join_request_not_found" to "That join request is gone",
        "not_host" to "Only the host can do that",
    )

    fun message(error: Throwable, fallback: String): String {
        val api = error as? PartyApiException ?: return fallback
        api.code?.let(MESSAGES::get)?.let { return it }
        return when (api.status) {
            401, 403 -> "You can't do that"
            404 -> "You're not in a party"
            429 -> MESSAGES.getValue(RATE_LIMITED)
            else -> fallback
        }
    }

    fun code(error: Throwable): String? = (error as? PartyApiException)?.code
}
