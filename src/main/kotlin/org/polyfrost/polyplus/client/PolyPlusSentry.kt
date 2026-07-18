package org.polyfrost.polyplus.client

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.sentry.Sentry
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.PolyPlusConstants
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PolyPlusSentry {
    private const val DSN =
        "https://8aad59841c698c55f86ec3992b853628@o4511714343124992.ingest.us.sentry.io/4511714567979008"

    private val started = AtomicBoolean(false)

    private val seen: MutableSet<Throwable> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    fun initialize() {
        if (!started.compareAndSet(false, true)) return

        val dev = FabricLoader.getInstance().isDevelopmentEnvironment

        Sentry.init { options ->
            options.dsn = DSN
            options.release = "${PolyPlusConstants.ID}@${PolyPlusConstants.VERSION}"
            options.environment = if (dev) "development" else "production"
            // Verbose SDK logging only in dev.
            options.isDebug = dev
        }
    }

    @JvmStatic
    fun capture(throwable: Throwable) {
        initialize()
        if (isTransientNetworkFailure(throwable)) return
        if (!seen.add(throwable)) return
        Sentry.captureException(throwable)
    }

    private fun isTransientNetworkFailure(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            when (cause) {
                is ServerResponseException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException,
                is SocketTimeoutException,
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.net.UnknownHostException,
                is java.net.SocketException,
                -> return true
                is ClientRequestException ->
                    if (cause.response.status == HttpStatusCode.Unauthorized) return true
            }
            val next = cause.cause
            if (next === cause) break
            cause = next
        }
        return false
    }

    @JvmStatic
    fun captureMessage(message: String) {
        initialize()
        Sentry.captureMessage(message, io.sentry.SentryLevel.ERROR)
    }

    @JvmStatic
    fun captureFatal(throwable: Throwable) {
        initialize()
        if (!Sentry.isEnabled()) return
        if (!seen.add(throwable)) return
        Sentry.captureException(throwable)
        Sentry.flush(5_000)
    }
}
