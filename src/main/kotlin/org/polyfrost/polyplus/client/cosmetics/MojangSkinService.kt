package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import com.google.common.hash.Hashing
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.libs.ktor.client.call.body
import org.polyfrost.polyplus.libs.ktor.client.request.accept
import org.polyfrost.polyplus.libs.ktor.client.request.forms.MultiPartFormDataContent
import org.polyfrost.polyplus.libs.ktor.client.request.forms.formData
import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.request.post
import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.client.statement.HttpResponse
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.Headers
import org.polyfrost.polyplus.libs.ktor.http.HttpHeaders
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.PROFILE_URL
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.accessToken
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.authorize
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitException
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
    private const val BLOCKED_MESSAGE = "Minecraft blocked this skin change. Try again later."
    private const val ACCOUNT_CHANGED_MESSAGE = "Your account changed, so your skin wasn't updated."
    private const val UNREACHABLE_MESSAGE = "Couldn't reach Minecraft's servers."
    private const val READBACK_ATTEMPTS = 5
    private const val READBACK_DELAY_MS = 1_000L
    private const val UPLOAD_GRACE_MS = 60_000L
    private const val TEXTURE_WAIT_MS = 30_000L
    private const val TEXTURE_POLL_MS = 250L
    private const val MAX_LOGGED_BODY = 500
    private val TEXTURE_HASH = Regex("^[0-9a-f]{32,64}$")
    private const val DEFAULT_SKIN_PREFIX = "minecraft:textures/entity/"

    private val requestLock = Mutex()

    private class SkinOverride(
        val owner: UUID,
        val skin: MojangSkin,
        stale: Set<String>,
        val uploadedAt: Long = System.currentTimeMillis(),
    ) {
        val stale: Set<String> = stale - setOfNotNull(skin.textureHash)
        // Modern versions name downloaded skins "skins/<sha1(hash)>", 1.8.9 uses "skins/<hash>".
        val replaceablePaths: Set<String> = (this.stale + setOfNotNull(skin.textureHash)).flatMapTo(HashSet()) {
            listOf("minecraft:skins/$it", "minecraft:skins/" + Hashing.sha1().hashUnencodedChars(it).toString())
        }
    }

    // Writes to override and overrideChecks happen under overrideLock; bumping overrideChecks drops in-flight join checks.
    private val overrideLock = Any()

    @Volatile
    private var override: SkinOverride? = null
    private var overrideChecks = 0

    var changedSkinVersion by mutableIntStateOf(0)
        private set

    suspend fun setSkin(url: String, model: SkinModel, owner: UUID): Result<MojangSkin?> =
        change(model, owner) { SkinLibrary.fetchSkin(secure(url)).getOrThrow() }

    suspend fun uploadSkin(png: ByteArray, model: SkinModel, owner: UUID): Result<MojangSkin?> =
        change(model, owner) { png }

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
        is MojangSkinException, is OutfitException -> error.message ?: SIGNED_OUT_MESSAGE
        else -> UNREACHABLE_MESSAGE
    }

    private fun tokenFor(owner: UUID): String {
        val token = accessToken() ?: throw MojangSkinException(SIGNED_OUT_MESSAGE)
        if (ClientPlatform.localPlayerUuid() != owner) throw MojangSkinException(ACCOUNT_CHANGED_MESSAGE)
        return token
    }

    private suspend fun change(model: SkinModel, owner: UUID, png: suspend () -> ByteArray): Result<MojangSkin?> = requestLock.withLock {
        runSuspendCatching {
            tokenFor(owner)
            val file = png()
            val token = tokenFor(owner)
            val previous = runSuspendCatching { readProfile(token) }.getOrNull()?.textureHash
            val response = post(token, file, model)
            ensureSkinChanged(response)
            val reported = runSuspendCatching { response.body<ServicesProfile>() }.getOrNull()?.let(::activeSkin)
            val skin = reported ?: readBack(token, previous)
            if (skin != null) {
                synchronized(overrideLock) {
                    override = SkinOverride(owner, skin, staleHashes(owner) + listOfNotNull(previous))
                    overrideChecks++
                }
                changedSkinVersion++
            }
            skin
        }.onFailure { LOGGER.warn("Minecraft skin change failed", it) }
    }

    private suspend fun post(token: String, png: ByteArray, model: SkinModel): HttpResponse =
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

    @JvmStatic
    fun localPreviewSkin(playerId: UUID): PreviewSkin? {
        val current = override ?: return null
        if (playerId != current.owner || playerId != ClientPlatform.localPlayerUuid()) return null
        val skin = current.skin
        val hash = skin.textureHash ?: return null
        val texture = SkinLibrary.texture(hash, skin.url) ?: return null
        return PreviewSkin(texture, skin.model == SkinModel.Slim)
    }

    @JvmStatic
    fun isStaleLocalSkin(playerId: UUID, textureId: String?): Boolean {
        val current = override ?: return false
        if (textureId == null || current.owner != playerId) return false
        return textureId.startsWith(DEFAULT_SKIN_PREFIX) || textureId in current.replaceablePaths
    }

    fun refreshLocalOverride() {
        val owner = ClientPlatform.localPlayerUuid()
        val check = synchronized(overrideLock) {
            val current = override
            if (current != null && current.owner != owner) {
                clearLocalOverride()
            } else if (current != null && System.currentTimeMillis() - current.uploadedAt < UPLOAD_GRACE_MS) {
                return
            }
            ++overrideChecks
        }
        val token = accessToken() ?: return
        PolyPlusClient.SCOPE.launch {
            val active = runSuspendCatching { readProfile(token) }.getOrNull() ?: return@launch
            val current = override?.takeIf { it.owner == owner }
            if (current != null && active.textureHash == current.skin.textureHash && active.model == current.skin.model) return@launch
            if (current == null && LocalAccountSkin.launchSkin()?.let { active.textureHash == it.textureHash && active.model == it.model } == true) return@launch
            if (!textureLoaded(active)) return@launch
            synchronized(overrideLock) {
                if (check != overrideChecks || owner != ClientPlatform.localPlayerUuid()) return@launch
                override = SkinOverride(owner, active, staleHashes(owner), uploadedAt = 0L)
            }
            changedSkinVersion++
        }
    }

    private fun staleHashes(owner: UUID): Set<String> {
        val current = override?.takeIf { it.owner == owner }
        return buildSet {
            current?.let { addAll(it.stale); it.skin.textureHash?.let(::add) }
            LocalAccountSkin.launchSkin()?.textureHash?.let(::add)
        }
    }

    fun clearLocalOverride() {
        synchronized(overrideLock) {
            if (override == null) return
            override = null
            overrideChecks++
        }
        changedSkinVersion++
    }

    private suspend fun textureLoaded(skin: MojangSkin): Boolean {
        val hash = skin.textureHash ?: return false
        return withTimeoutOrNull(TEXTURE_WAIT_MS) {
            while (ClientPlatform.runOnMainSync { SkinLibrary.texture(hash, skin.url) } == null) delay(TEXTURE_POLL_MS)
            true
        } ?: false
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
        return activeSkin(response.body<ServicesProfile>())
    }

    private fun activeSkin(profile: ServicesProfile): MojangSkin? {
        val active = profile.skins
            .firstOrNull { it.state.equals("ACTIVE", ignoreCase = true) && it.url.isNotBlank() }
            ?: return null
        return MojangSkin(secure(active.url), SkinModel.fromSerialized(active.variant))
    }

    private suspend fun ensureSkinChanged(response: HttpResponse) {
        val status = response.status
        if (status.isSuccess()) return
        val body = runSuspendCatching { response.bodyAsText() }.getOrDefault("")
        LOGGER.warn("Minecraft rejected a skin change with {}: {}", status.value, body.take(MAX_LOGGED_BODY))
        throw MojangSkinException(rejectionMessage(status))
    }

    internal fun rejectionMessage(status: HttpStatusCode): String = when (status) {
        HttpStatusCode.Unauthorized -> SIGNED_OUT_MESSAGE
        HttpStatusCode.Forbidden -> BLOCKED_MESSAGE
        HttpStatusCode.TooManyRequests -> "Minecraft is limiting skin changes right now. Try again in a minute."
        HttpStatusCode.BadRequest -> "Minecraft couldn't use that skin."
        else -> "Minecraft's servers couldn't change your skin right now."
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
