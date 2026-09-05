package org.polyfrost.polyplus.client.featured

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.logging.log4j.LogManager
import java.net.URI
import java.time.Instant
import java.util.Collections

sealed interface OutlineStyle {
    data class Solid(val argb: Int) : OutlineStyle
    data object Rainbow : OutlineStyle
    data object None : OutlineStyle
    data class Unsupported(val raw: JsonElement) : OutlineStyle
}

object OutlineStyleSerializer : KSerializer<OutlineStyle> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): OutlineStyle {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("OutlineStyle can only be decoded from JSON")
        return decode(jsonDecoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: OutlineStyle) {
        val raw = when (value) {
            is OutlineStyle.Solid -> JsonPrimitive("#%06X".format(value.argb and 0xFFFFFF))
            OutlineStyle.Rainbow -> JsonPrimitive("rainbow")
            OutlineStyle.None -> JsonPrimitive("none")
            is OutlineStyle.Unsupported -> value.raw
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), raw)
    }

    fun decode(raw: JsonElement): OutlineStyle {
        val text = (raw as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
        if (text == "rainbow") return OutlineStyle.Rainbow
        if (text == "none") return OutlineStyle.None
        if (text != null && HEX_COLOR.matches(text)) {
            return OutlineStyle.Solid(0xFF000000.toInt() or text.substring(1).toInt(16))
        }
        UnknownOutlineStyles.report(raw)
        return OutlineStyle.Unsupported(raw)
    }

    private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")
}

private object UnknownOutlineStyles {
    private val logger = LogManager.getLogger("PolyPlus/FeaturedServers")
    private val reported = Collections.synchronizedSet(mutableSetOf<String>())

    fun report(raw: JsonElement) {
        val key = raw.toString()
        if (reported.add(key)) logger.warn("Unsupported featured-server outline style {}; using #A0A0A0", key)
    }
}

data class FeaturedCampaign(
    val campaignId: String,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val title: String,
    val description: String,
    val ctaLabel: String,
    val imageUrl: String?,
) {
    fun isActive(nowMillis: Long): Boolean = nowMillis >= startsAtMillis && nowMillis < endsAtMillis
}

data class FeaturedServer(
    val id: String,
    val name: String,
    val address: String,
    val outlineStyle: OutlineStyle,
    val featured: FeaturedCampaign?,
)

data class FeaturedServersSnapshot(
    val servers: List<FeaturedServer> = emptyList(),
    val mainMenuDismissedCampaignIds: Set<String> = emptySet(),
    val multiplayerDismissedCampaignIds: Set<String> = emptySet(),
    val expiresAtMillis: Long = 0L,
    val revision: Long = 0L,
) {
    fun visibleServers(nowMillis: Long = System.currentTimeMillis()): List<FeaturedServer> =
        if (nowMillis < expiresAtMillis) servers else emptyList()

    fun mainMenuFeaturedServers(nowMillis: Long = System.currentTimeMillis()): List<FeaturedServer> =
        activeFeaturedServers(nowMillis, mainMenuDismissedCampaignIds)

    fun featuredServers(nowMillis: Long = System.currentTimeMillis()): List<FeaturedServer> =
        activeFeaturedServers(nowMillis, multiplayerDismissedCampaignIds)

    private fun activeFeaturedServers(nowMillis: Long, dismissedCampaignIds: Set<String>): List<FeaturedServer> =
        visibleServers(nowMillis).filter { server ->
            val campaign = server.featured
            campaign != null && campaign.isActive(nowMillis) && campaign.campaignId !in dismissedCampaignIds
        }

    fun sponsoredServers(nowMillis: Long = System.currentTimeMillis()): List<FeaturedServer> {
        val featuredIds = featuredServers(nowMillis).asSequence().map(FeaturedServer::id).toHashSet()
        return visibleServers(nowMillis).filterNot { it.id in featuredIds }
    }
}

data class DecodedFeaturedServers(
    val servers: List<FeaturedServer>,
    val warnings: List<String>,
)

object FeaturedServerCatalogCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun decode(body: String): DecodedFeaturedServers {
        val root = json.parseToJsonElement(body).jsonObject
        require(root["schema_version"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported featured-server schema_version" }
        val elements = root["servers"]?.jsonArray ?: error("Featured-server catalog has no servers array")
        val warnings = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        val servers = elements.mapIndexedNotNull { index, element ->
            runCatching { json.decodeFromJsonElement(ServerWire.serializer(), element) }
                .mapCatching { wire -> wire.toDomain(index, warnings) }
                .onFailure { warnings += "servers[$index] skipped: ${it.message ?: it.javaClass.simpleName}" }
                .getOrNull()
                ?.takeIf { server ->
                    if (ids.add(server.id)) true else {
                        warnings += "servers[$index] skipped: duplicate id ${server.id}"
                        false
                    }
                }
        }
        return DecodedFeaturedServers(servers, warnings)
    }

    private fun ServerWire.toDomain(index: Int, warnings: MutableList<String>): FeaturedServer {
        require(id.isNotBlank()) { "id is blank" }
        require(name.isNotBlank()) { "name is blank" }
        require(address.isNotBlank() && address.none(Char::isWhitespace)) { "address is invalid" }

        val campaign = featured?.let { wire ->
            runCatching { wire.toDomain() }
                .onFailure { warnings += "servers[$index].featured ignored: ${it.message ?: it.javaClass.simpleName}" }
                .getOrNull()
        }
        return FeaturedServer(id.trim(), name.trim(), address.trim(), outlineColor, campaign)
    }

    private fun CampaignWire.toDomain(): FeaturedCampaign {
        require(campaignId.isNotBlank()) { "campaign_id is blank" }
        require(title.isNotBlank()) { "title is blank" }
        require(description.isNotBlank()) { "description is blank" }
        require(ctaLabel.isNotBlank()) { "cta_label is blank" }
        val start = Instant.parse(startsAt).toEpochMilli()
        val end = Instant.parse(endsAt).toEpochMilli()
        require(end > start) { "ends_at must be later than starts_at" }
        val imageUrlText = (imageUrl as? JsonPrimitive)?.takeIf { it.isString }?.content
        val safeImageUrl = imageUrlText?.takeIf(::isHttpsUrl)
        return FeaturedCampaign(campaignId.trim(), start, end, title.trim(), description.trim(), ctaLabel.trim(), safeImageUrl)
    }

    private fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    @Serializable
    private data class ServerWire(
        val id: String = "",
        val name: String = "",
        val address: String = "",
        @SerialName("outline_color")
        @Serializable(with = OutlineStyleSerializer::class)
        val outlineColor: OutlineStyle = OutlineStyle.Unsupported(JsonNull),
        val featured: CampaignWire? = null,
    )

    @Serializable
    private data class CampaignWire(
        @SerialName("campaign_id") val campaignId: String = "",
        @SerialName("starts_at") val startsAt: String = "",
        @SerialName("ends_at") val endsAt: String = "",
        val title: String = "",
        val description: String = "",
        @SerialName("cta_label") val ctaLabel: String = "",
        @SerialName("image_url") val imageUrl: JsonElement? = null,
    )
}

object FeaturedServerColors {
    const val FALLBACK_ARGB: Int = 0xFFA0A0A0.toInt()
    const val RAINBOW_PERIOD_MILLIS: Long = 4_000L

    fun colorAt(style: OutlineStyle, perimeterFraction: Float, nowMillis: Long): Int = when (style) {
        is OutlineStyle.Solid -> style.argb
        OutlineStyle.Rainbow -> rainbowAt(perimeterFraction, nowMillis)
        OutlineStyle.None -> 0x00000000
        is OutlineStyle.Unsupported -> FALLBACK_ARGB
    }

    fun rainbowAt(perimeterFraction: Float, nowMillis: Long): Int {
        val time = Math.floorMod(nowMillis, RAINBOW_PERIOD_MILLIS).toFloat() / RAINBOW_PERIOD_MILLIS
        val hue = ((perimeterFraction % 1f) + 1f + time) % 1f
        return hsv(hue)
    }

    private fun hsv(hue: Float): Int {
        val h = hue * 6f
        val sector = h.toInt().coerceIn(0, 5)
        val fraction = h - sector
        val saturation = 0.85f
        val value = 1f
        val p = value * (1f - saturation)
        val q = value * (1f - saturation * fraction)
        val t = value * (1f - saturation * (1f - fraction))
        val (r, g, b) = when (sector) {
            0 -> Triple(value, t, p)
            1 -> Triple(q, value, p)
            2 -> Triple(p, value, t)
            3 -> Triple(p, q, value)
            4 -> Triple(t, p, value)
            else -> Triple(value, p, q)
        }
        return 0xFF000000.toInt() or
            ((r * 255f).toInt() shl 16) or
            ((g * 255f).toInt() shl 8) or
            (b * 255f).toInt()
    }
}

fun normalizeServerAddress(address: String): String {
    val value = address.trim().lowercase()
    if (value.startsWith("[")) {
        val close = value.indexOf(']')
        if (close > 0) {
            val host = value.substring(1, close).trimEnd('.')
            val port = value.substring(close + 1).removePrefix(":").toIntOrNull() ?: 25565
            return "[$host]:$port"
        }
    }
    val colon = value.lastIndexOf(':')
    val explicitPort = colon > 0 && value.indexOf(':') == colon
    val host = (if (explicitPort) value.substring(0, colon) else value).trimEnd('.')
    val port = if (explicitPort) value.substring(colon + 1).toIntOrNull() ?: 25565 else 25565
    return "$host:$port"
}
