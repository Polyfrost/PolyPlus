package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.libs.ktor.client.request.HttpRequestBuilder
import org.polyfrost.polyplus.libs.ktor.client.request.accept
import org.polyfrost.polyplus.libs.ktor.client.request.header
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.HttpHeaders
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
