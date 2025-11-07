package org.polyfrost.polyplus.client.network.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

suspend inline fun HttpClient.requestAuthorized(noinline block: HttpRequestBuilder.() -> Unit): HttpResponse {
    val response = request {
        // Authorize
        bearerAuth(PolyAuthorization.current())

        // Apply the user's customizations to the request
        apply(block)
    }

    if (response.status == HttpStatusCode.Unauthorized) {
        return request {
            // Refresh token and re-authorize
            bearerAuth(PolyAuthorization.refresh())

            apply(block)
        }
    }

    return response
}

suspend inline fun HttpClient.requestAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized {
        this.url(url)
        apply(block)
    }
}

suspend inline fun <reified T> HttpClient.requestBodyAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return runCatching {
        val response = requestAuthorized(url, block)
        response.body<T>()
    }
}

suspend inline fun HttpClient.requestAuthorized(url: String, method: HttpMethod, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized {
        this.url(url)
        this.method = method
        apply(block)
    }
}

suspend inline fun <reified T> HttpClient.requestBodyAuthorized(url: String, method: HttpMethod, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return runCatching {
        val response = requestAuthorized(url, method, block)
        response.body<T>()
    }
}

suspend inline fun HttpClient.getAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized(url, HttpMethod.Get, block)
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

suspend inline fun <reified T> HttpClient.putBodyAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return requestBodyAuthorized<T>(url, HttpMethod.Put, block)
}

suspend inline fun HttpClient.patchAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized(url, HttpMethod.Patch, block)
}

suspend inline fun <reified T> HttpClient.patchBodyAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return requestBodyAuthorized<T>(url, HttpMethod.Patch, block)
}

suspend inline fun HttpClient.deleteAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    return requestAuthorized(url, HttpMethod.Delete, block)
}

suspend inline fun <reified T> HttpClient.deleteBodyAuthorized(url: String, noinline block: HttpRequestBuilder.() -> Unit = {}): Result<T> {
    return requestBodyAuthorized<T>(url, HttpMethod.Delete, block)
}
