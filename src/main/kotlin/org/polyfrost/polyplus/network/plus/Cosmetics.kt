package org.polyfrost.polyplus.network.plus

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.ResourceLocation
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.impl.Subscribe
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.polyplus.PolyPlus
import org.polyfrost.polyplus.PolyPlus.Companion.logger
import org.polyfrost.polyplus.client.Config
import org.polyfrost.polyplus.network.plus.Auth.authRequest
import org.polyfrost.polyplus.network.plus.responses.CosmeticList
import org.polyfrost.polyplus.network.plus.responses.PlayerCosmetics
import org.polyfrost.polyplus.network.plus.responses.PutCosmetics
import org.polyfrost.polyplus.utils.PlayerUtils
import java.util.UUID

object Cosmetics {
    val players = HashMap<UUID, HashMap<String, Int>>()

    fun getOwned() = PolyPlus.scope.async {
        val cosmetics: Result<PlayerCosmetics> = PolyPlus.client.authRequest(HttpMethod.Get, "${Config.apiUrl}cosmetics/player")
        val owned = cosmetics.onFailure { logger.warning("Failed to fetch owned cosmetics: ${it.message}") }.getOrElse { return@async }
        owned.owned.forEach { cosmetic ->
            players[PlayerUtils.uuid] = players.getOrPut(PlayerUtils.uuid) { HashMap() }.apply {
                this[cosmetic.type] = cosmetic.id
                println("set ${PlayerUtils.uuid} cosmetic ${cosmetic.type} to ${cosmetic.id}")
            }
        }
    }

    fun setOwned(cosmetics: PutCosmetics) = PolyPlus.scope.launch {
        val response = PolyPlus.client.authRequest(HttpMethod.Put, "${Config.apiUrl}cosmetics/player") {
            contentType(ContentType.Application.Json)
            setBody(cosmetics)
        }.onFailure { logger.warning("Failed to set owned cosmetic: ${it.message}") }.getOrNull() ?: return@launch

        if (response.status != HttpStatusCode.OK) logger.warning("Failed to set owned cosmetic: ${response.status}, ${response.bodyAsText()}")
    }

    fun getAll() = PolyPlus.scope.async {
        val cosmetics: Result<CosmeticList> = runCatching { PolyPlus.client.get("${Config.apiUrl}cosmetics") }.map { it.body() }
        cosmetics.onFailure { logger.warning("Failed to fetch all cosmetics: ${it.message}") }
    }
}