package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import net.minecraft.client.Minecraft

internal object MojangAuth {
    const val PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"

    fun accessToken(): String? = runCatching {
        //? if > 1.8.9 {
        Minecraft.getInstance().user.accessToken
        //?} else {
        /*Minecraft.getInstance().session.accessToken
        *///?}
    }.getOrNull()?.takeIf { it.isNotBlank() && it != "0" }

    fun HttpRequestBuilder.authorize(token: String) {
        accept(ContentType.Application.Json)
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}
