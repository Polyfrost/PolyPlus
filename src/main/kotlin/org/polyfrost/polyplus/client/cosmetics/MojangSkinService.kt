package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.PROFILE_URL
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.accessToken
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.authorize
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinModel
import org.polyfrost.polyplus.client.gui.preview.PreviewSkin
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class MojangSkin(val url: String, val model: SkinModel) {
    val textureHash: String? get() = MojangSkinService.textureHash(url)
}

class MojangSkinException(message: String) : Exception(message)

object MojangSkinService {
    private val LOGGER = LogManager.getLogger("polyplus/mojang-skins")

    private const val SKINS_URL = "$PROFILE_URL/skins"
    private const val SESSION_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile"
    private const val USERNAME_URL = "https://api.mojang.com/users/profiles/minecraft"
    private const val SIGNED_OUT_MESSAGE = "Sign in with a Microsoft account to change your Minecraft skin."
    private const val UNREACHABLE_MESSAGE = "Couldn't reach Minecraft's servers."
    private const val READBACK_ATTEMPTS = 5
    private const val READBACK_DELAY_MS = 1_000L
    private val TEXTURE_HASH = Regex("^[0-9a-f]{32,64}$")

    private val requestLock = Mutex()

    @Volatile
    private var knownTexture: String? = null

    @Volatile
    private var changedSkin: MojangSkin? = null

    @Volatile
    private var changedFor: UUID? = null

    var changedSkinVersion by mutableIntStateOf(0)
        private set

    suspend fun setSkin(url: String, model: SkinModel): Result<MojangSkin?> = change { token ->
        PolyPlusClient.HTTP.post(SKINS_URL) {
            authorize(token)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("variant", model.serializedName)
                    put("url", url)
                },
            )
        }
    }

    suspend fun uploadSkin(png: ByteArray, model: SkinModel): Result<MojangSkin?> = change { token ->
        PolyPlusClient.HTTP.post(SKINS_URL) {
            authorize(token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("variant", model.serializedName)
                        append(
                            "file",
                            png,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"skin.png\"")
                            },
                        )
                    },
                ),
            )
        }
    }

    suspend fun activeSkin(): Result<MojangSkin?> = runSuspendCatching {
        val token = accessToken() ?: throw MojangSkinException(SIGNED_OUT_MESSAGE)
        readProfile(token)
    }

    suspend fun profileSkin(uuid: UUID): Result<MojangSkin?> = runSuspendCatching {
        val id = uuid.toString().replace("-", "")
        val response = PolyPlusClient.HTTP.get("$SESSION_PROFILE_URL/$id") {
            accept(ContentType.Application.Json)
        }
        when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.NoContent, HttpStatusCode.NotFound -> return@runSuspendCatching null
            HttpStatusCode.TooManyRequests ->
                throw MojangSkinException("Minecraft is limiting lookups right now. Try again in a minute.")
            else -> throw MojangSkinException(UNREACHABLE_MESSAGE)
        }
        val texturesValue = response.body<SessionProfile>().properties
            .firstOrNull { it.name == "textures" }
            ?.value
            ?: return@runSuspendCatching null
        val decoded = String(Base64.getDecoder().decode(texturesValue), StandardCharsets.UTF_8)
        val skin = PolyPlusClient.JSON.decodeFromString(TexturesPayload.serializer(), decoded).textures["SKIN"]
            ?: return@runSuspendCatching null
        if (skin.url.isBlank()) return@runSuspendCatching null
        MojangSkin(secure(skin.url), SkinModel.fromSerialized(skin.metadata["model"]))
    }

    suspend fun uuidForName(username: String): Result<UUID?> = runSuspendCatching {
        val response = PolyPlusClient.HTTP.get("$USERNAME_URL/$username") {
            accept(ContentType.Application.Json)
        }
        when {
            response.status == HttpStatusCode.OK -> parseUndashed(response.body<NameLookup>().id)
            response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.NotFound -> null
            response.status == HttpStatusCode.TooManyRequests ->
                throw MojangSkinException("Minecraft is limiting lookups right now. Try again in a minute.")
            else -> throw MojangSkinException(UNREACHABLE_MESSAGE)
        }
    }

    fun textureHash(url: String): String? =
        url.substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .substringAfterLast('/')
            .lowercase()
            .takeIf { TEXTURE_HASH.matches(it) }

    fun failureMessage(error: Throwable): String = when (error) {
        is MojangSkinException -> error.message ?: SIGNED_OUT_MESSAGE
        else -> UNREACHABLE_MESSAGE
    }

    private suspend fun change(call: suspend (String) -> HttpResponse): Result<MojangSkin?> = requestLock.withLock {
        runSuspendCatching {
            val token = accessToken() ?: throw MojangSkinException(SIGNED_OUT_MESSAGE)
            val previous = knownTexture ?: runSuspendCatching { readProfile(token) }.getOrNull()?.textureHash
            val response = call(token)
            ensureSkinChanged(response)
            val reported = runSuspendCatching { response.body<ServicesProfile>() }.getOrNull()?.let(::activeSkin)
            val skin = if (reported != null) {
                knownTexture = reported.textureHash
                reported
            } else {
                readBack(token, previous)
            }
            if (skin != null) {
                changedSkin = skin
                changedFor = ClientPlatform.localPlayerUuid()
                changedSkinVersion++
            }
            skin
        }.onFailure { LOGGER.warn("Minecraft skin change failed", it) }
    }

    @JvmStatic
    fun localPreviewSkin(playerId: UUID): PreviewSkin? {
        val skin = changedSkin ?: return null
        if (playerId != changedFor || playerId != ClientPlatform.localPlayerUuid()) return null
        val hash = skin.textureHash ?: return null
        val texture = SkinLibrary.texture(hash, skin.url) ?: return null
        return PreviewSkin(texture, skin.model == SkinModel.Slim)
    }

    private suspend fun readBack(token: String, previous: String?): MojangSkin? {
        repeat(READBACK_ATTEMPTS) {
            delay(READBACK_DELAY_MS)
            val skin = runSuspendCatching { readProfile(token) }.getOrNull()
            if (skin != null && skin.textureHash != previous) return skin
        }
        return null
    }

    private suspend fun readProfile(token: String): MojangSkin? {
        val response = PolyPlusClient.HTTP.get(PROFILE_URL) { authorize(token) }
        val status = response.status
        if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound) {
            return null
        }
        if (!status.isSuccess()) throw MojangSkinException(UNREACHABLE_MESSAGE)
        return activeSkin(response.body<ServicesProfile>()).also { knownTexture = it?.textureHash }
    }

    private fun activeSkin(profile: ServicesProfile): MojangSkin? {
        val active = profile.skins
            .firstOrNull { it.state.equals("ACTIVE", ignoreCase = true) && it.url.isNotBlank() }
            ?: return null
        return MojangSkin(secure(active.url), SkinModel.fromSerialized(active.variant))
    }

    private fun ensureSkinChanged(response: HttpResponse) {
        val status = response.status
        if (status.isSuccess()) return
        throw MojangSkinException(
            when (status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> SIGNED_OUT_MESSAGE
                HttpStatusCode.TooManyRequests -> "Minecraft is limiting skin changes right now. Try again in a minute."
                HttpStatusCode.BadRequest -> "Minecraft couldn't use that skin."
                else -> "Minecraft's servers couldn't change your skin right now."
            },
        )
    }

    private fun parseUndashed(id: String): UUID? = runCatching {
        if (id.length != 32) return@runCatching UUID.fromString(id)
        UUID.fromString(
            "${id.substring(0, 8)}-${id.substring(8, 12)}-${id.substring(12, 16)}-${id.substring(16, 20)}-${id.substring(20)}",
        )
    }.getOrNull()

    private fun secure(url: String): String =
        if (url.startsWith("http://textures.minecraft.net/")) "https://" + url.removePrefix("http://") else url

    @Serializable
    private data class SessionProfile(val properties: List<ProfileProperty> = emptyList())

    @Serializable
    private data class ProfileProperty(val name: String = "", val value: String = "")

    @Serializable
    private data class TexturesPayload(val textures: Map<String, TextureEntry> = emptyMap())

    @Serializable
    private data class TextureEntry(val url: String = "", val metadata: Map<String, String> = emptyMap())

    @Serializable
    private data class ServicesProfile(val skins: List<ServicesSkin> = emptyList())

    @Serializable
    private data class ServicesSkin(val state: String = "", val url: String = "", val variant: String = "")

    @Serializable
    private data class NameLookup(val id: String = "")
}
