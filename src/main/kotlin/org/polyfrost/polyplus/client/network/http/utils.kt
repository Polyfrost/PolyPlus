package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.libs.ktor.client.HttpClient
import org.polyfrost.polyplus.libs.ktor.client.call.body
import org.polyfrost.polyplus.libs.ktor.client.plugins.ClientRequestException
import org.polyfrost.polyplus.libs.ktor.client.plugins.ResponseException
import org.polyfrost.polyplus.libs.ktor.client.plugins.ServerResponseException
import org.polyfrost.polyplus.libs.ktor.client.request.HttpRequestBuilder
import org.polyfrost.polyplus.libs.ktor.client.request.bearerAuth
import org.polyfrost.polyplus.libs.ktor.client.request.request
import org.polyfrost.polyplus.libs.ktor.client.request.url
import org.polyfrost.polyplus.libs.ktor.client.statement.HttpResponse
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.HttpMethod
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import org.polyfrost.polyplus.client.utils.runSuspendCatching

suspend inline fun HttpClient.requestAuthorized(noinline block: HttpRequestBuilder.() -> Unit): HttpResponse {
    val response = request {
        bearerAuth(PolyAuthorization.current())
        apply(block)
    }

    if (response.status == HttpStatusCode.Unauthorized) {
        return request {
            bearerAuth(PolyAuthorization.refresh())
            apply(block)
        }
    }

    return response
}

suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (!status.isSuccess()) {
        val text = bodyAsText()
        throw when {
            status.value >= 500 -> ServerResponseException(this, text)
            status.value >= 400 -> ClientRequestException(this, text)
            else -> ResponseException(this, text)
        }
    }
    return body()
}

suspend inline fun HttpClient.requestAuthorized(url: String, method: HttpMethod, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized {
        this.url(url)
        this.method = method
        apply(block)
    }
}

suspend inline fun <reified T> HttpClient.requestBodyAuthorized(url: String, method: HttpMethod, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return runSuspendCatching {
        val response = requestAuthorized(url, method, block)
        response.bodyOrThrow<T>()
    }
}

suspend inline fun <reified T> HttpClient.getBodyAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return requestBodyAuthorized<T>(url, HttpMethod.Get, block)
}

suspend inline fun HttpClient.postAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized(url, HttpMethod.Post, block)
}

suspend inline fun <reified T> HttpClient.postBodyAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return requestBodyAuthorized<T>(url, HttpMethod.Post, block)
}

suspend inline fun HttpClient.putAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized(url, HttpMethod.Put, block)
}

suspend inline fun <reified T> HttpClient.patchBodyAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return requestBodyAuthorized<T>(url, HttpMethod.Patch, block)
}

suspend inline fun HttpClient.deleteAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized(url, HttpMethod.Delete, block)
}
