package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = PartyVoiceMode.Serializer::class)
enum class PartyVoiceMode(val serializedName: String) {
    Party("party"),
    Proximity("proximity"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PartyVoiceMode> by ApiEnumSerializer(
        "PartyVoiceMode",
        PartyVoiceMode.entries,
        Unknown,
        PartyVoiceMode::serializedName,
    )
}

@Serializable(with = PartyInvitePolicy.Serializer::class)
enum class PartyInvitePolicy(val serializedName: String) {
    Leader("leader"),
    Members("members"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PartyInvitePolicy> by ApiEnumSerializer(
        "PartyInvitePolicy",
        PartyInvitePolicy.entries,
        Unknown,
        PartyInvitePolicy::serializedName,
    )
}

@Serializable(with = VoiceTransport.Serializer::class)
enum class VoiceTransport(val serializedName: String) {
    Rtc("rtc"),
    P2p("p2p"),
    Unknown("unknown");

    internal object Serializer : KSerializer<VoiceTransport> by ApiEnumSerializer(
        "VoiceTransport",
        VoiceTransport.entries,
        Unknown,
        VoiceTransport::serializedName,
    )
}

@Serializable(with = LocationKind.Serializer::class)
enum class LocationKind(val serializedName: String) {
    Menu("menu"),
    Singleplayer("singleplayer"),
    Server("server"),
    HostedWorld("hosted_world"),
    Unknown("unknown");

    internal object Serializer : KSerializer<LocationKind> by ApiEnumSerializer(
        "LocationKind",
        LocationKind.entries,
        Unknown,
        LocationKind::serializedName,
    )
}

@Serializable(with = PartyLeftReason.Serializer::class)
enum class PartyLeftReason(val serializedName: String) {
    Left("left"),
    Kicked("kicked"),
    Disbanded("disbanded"),
    Expired("expired"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PartyLeftReason> by ApiEnumSerializer(
        "PartyLeftReason",
        PartyLeftReason.entries,
        Unknown,
        PartyLeftReason::serializedName,
    )
}

@Serializable(with = PartyInviteStatus.Serializer::class)
enum class PartyInviteStatus(val serializedName: String) {
    Pending("pending"),
    Accepted("accepted"),
    Declined("declined"),
    Cancelled("cancelled"),
    Expired("expired"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PartyInviteStatus> by ApiEnumSerializer(
        "PartyInviteStatus",
        PartyInviteStatus.entries,
        Unknown,
        PartyInviteStatus::serializedName,
    )
}

@Serializable
data class MemberLocation(
    val kind: LocationKind = LocationKind.Menu,
    val key: String? = null,
    val display: String? = null,
)

@Serializable
data class MemberVoice(
    @SerialName("in_voice") val inVoice: Boolean = false,
    val muted: Boolean = false,
    val deafened: Boolean = false,
    @SerialName("mic_blocked") val micBlocked: Boolean = false,
    val capabilities: List<VoiceTransport> = emptyList(),
    val transport: VoiceTransport? = null,
)

@Serializable(with = SceneKind.Serializer::class)
enum class SceneKind(val serializedName: String) {
    Bundled("bundled"),
    Capture("capture"),
    Unknown("unknown");

    internal object Serializer : KSerializer<SceneKind> by ApiEnumSerializer(
        "SceneKind",
        SceneKind.entries,
        Unknown,
        SceneKind::serializedName,
    )
}

@Serializable
data class MemberScene(
    val kind: SceneKind,
    val id: String,
    val fallback: String = "",
    val clock: Int = 0,
    @SerialName("mc_version") val mcVersion: String = "",
)

@Serializable
data class MemberState(
    val location: MemberLocation = MemberLocation(),
    val voice: MemberVoice = MemberVoice(),
    @SerialName("svc_connected") val svcConnected: Boolean = false,
    val scene: MemberScene? = null,
)

@Serializable(with = PlayDestinationKind.Serializer::class)
enum class PlayDestinationKind(val serializedName: String) {
    Server("server"),
    World("world"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PlayDestinationKind> by ApiEnumSerializer(
        "PlayDestinationKind",
        PlayDestinationKind.entries,
        Unknown,
        PlayDestinationKind::serializedName,
    )
}

@Serializable(with = PlayResponse.Serializer::class)
enum class PlayResponse(val serializedName: String) {
    Joining("joining"),
    Cancelled("cancelled"),
    Busy("busy"),
    Incompatible("incompatible"),
    Joined("joined"),
    Failed("failed"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PlayResponse> by ApiEnumSerializer(
        "PlayResponse",
        PlayResponse.entries,
        Unknown,
        PlayResponse::serializedName,
    )
}

@Serializable(with = PlayReason.Serializer::class)
enum class PlayReason(val serializedName: String) {
    Version("version"),
    Unreachable("unreachable"),
    Mods("mods"),
    Full("full"),
    Kicked("kicked"),
    Other("other"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PlayReason> by ApiEnumSerializer(
        "PlayReason",
        PlayReason.entries,
        Unknown,
        PlayReason::serializedName,
    )
}

@Serializable(with = PlayEndReason.Serializer::class)
enum class PlayEndReason(val serializedName: String) {
    Replaced("replaced"),
    Expired("expired"),
    LeaderLeft("leader_left"),
    Cancelled("cancelled"),
    Unknown("unknown");

    internal object Serializer : KSerializer<PlayEndReason> by ApiEnumSerializer(
        "PlayEndReason",
        PlayEndReason.entries,
        Unknown,
        PlayEndReason::serializedName,
    )
}

@Serializable
data class PlayDestination(
    val kind: PlayDestinationKind,
    val address: String? = null,
    val name: String,
    @SerialName("mc_version") val mcVersion: String? = null,
    val loader: String? = null,
    val mods: List<String> = emptyList(),
)

@Serializable
data class PartySettings(
    @SerialName("voice_mode") val voiceMode: PartyVoiceMode = PartyVoiceMode.Party,
    @SerialName("invite_policy") val invitePolicy: PartyInvitePolicy = PartyInvitePolicy.Leader,
)

@Serializable
data class PartyVoiceInfo(
    @SerialName("room_name") val roomName: String? = null,
    val transport: VoiceTransport = VoiceTransport.P2p,
    @SerialName("rtc_available") val rtcAvailable: Boolean = false,
)

@Serializable
data class PartyMember(
    val player: String,
    @SerialName("joined_at") val joinedAt: String? = null,
    val online: Boolean = false,
    @SerialName("eos_product_user_id") val eosProductUserId: String? = null,
    @SerialName("voice_hard_muted") val voiceHardMuted: Boolean = false,
    val state: MemberState? = null,
)

@Serializable
data class Party(
    val id: String,
    val leader: String,
    @SerialName("created_at") val createdAt: String? = null,
    val settings: PartySettings = PartySettings(),
    @SerialName("chat_group_id") val chatGroupId: Int? = null,
    val voice: PartyVoiceInfo = PartyVoiceInfo(),
    val members: List<PartyMember> = emptyList(),
) {
    fun member(player: String): PartyMember? = members.firstOrNull { it.player.equals(player, ignoreCase = true) }
}

@Serializable
data class PartyInvite(
    val id: String,
    @SerialName("party_id") val partyId: String,
    val sender: String,
    val recipient: String,
    val status: PartyInviteStatus = PartyInviteStatus.Pending,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("party_size") val partySize: Int = 1,
)

@Serializable
data class VoiceToken(
    @SerialName("room_name") val roomName: String,
    @SerialName("client_base_url") val clientBaseUrl: String,
    @SerialName("participant_token") val participantToken: String,
    @SerialName("issued_at") val issuedAt: String? = null,
)

@Serializable
data class VoicePass(
    val pass: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class PartySettingsRequest(
    @SerialName("voice_mode") val voiceMode: PartyVoiceMode? = null,
    @SerialName("invite_policy") val invitePolicy: PartyInvitePolicy? = null,
)

@Serializable
data class PartyPlayerRequest(val player: String)

@Serializable
data class PartyHardMuteRequest(@SerialName("hard_muted") val hardMuted: Boolean)

@Serializable
data class PartyAcceptRequest(@SerialName("leave_current") val leaveCurrent: Boolean = false)

@Serializable
data class ApiErrorBody(val error: String, val message: String? = null)
