package org.polyfrost.polyplus.network.plus

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.PolyPlus
import org.polyfrost.polyplus.PolyPlus.Companion.logger
import org.polyfrost.polyplus.client.Config
import org.polyfrost.polyplus.network.plus.Auth.authRequest
import org.polyfrost.polyplus.network.plus.responses.Cosmetic
import org.polyfrost.polyplus.network.plus.responses.PlayerCosmetics
import org.polyfrost.polyplus.network.plus.responses.PutCosmetics

object Cosmetics {
    fun getOwned() = PolyPlus.scope.async {
        val cosmetics: Result<PlayerCosmetics> = PolyPlus.client.authRequest(HttpMethod.Get, "${Config.apiUrl}/cosmetics/player")
        cosmetics.onFailure { logger.warning("Failed to fetch owned cosmetics: ${it.message}") }
        // put on player probably?
    }

    fun setOwned(cosmetics: PutCosmetics) = PolyPlus.scope.launch {
        val response = PolyPlus.client.authRequest(HttpMethod.Put, "${Config.apiUrl}/cosmetics/player") {
            contentType(ContentType.Application.Json)
            setBody(cosmetics)
        }.onFailure { logger.warning("Failed to set owned cosmetic: ${it.message}") }.getOrNull() ?: return@launch

        if (response.status != HttpStatusCode.OK) logger.warning("Failed to set owned cosmetic: ${response.status}, ${response.bodyAsText()}")
    }

    fun getAll() = PolyPlus.scope.async {
        val cosmetics: Result<List<Cosmetic>> = runCatching { PolyPlus.client.get("${Config.apiUrl}/cosmetics") }.map { it.body() }
        cosmetics.onFailure { logger.warning("Failed to fetch all cosmetics: ${it.message}") }
        // store somewhere/do something with them
    }
}