package org.polyfrost.polyplus.client.resourcepack

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.network.eos.EosProductUserId

object PackHttpBridge {
    private val LOGGER = LogManager.getLogger("PolyPlus/PackBridge")

    const val PLACEHOLDER_HOST = "polyplus-p2p.invalid"
    private const val PATH_PREFIX = "/polyplus/pack/"

    private const val HEADER_TIMEOUT_MS = 10_000L
    private const val HEADER_ATTEMPTS = 3
    private const val CHUNK_TIMEOUT_MS = 30_000L

    private val threadIds = AtomicInteger()

    @Volatile private var server: HttpServer? = null

    @Volatile private var packSource: EosProductUserId? = null

    fun setPackSource(remote: EosProductUserId?) {
        packSource = remote
    }

    fun placeholderUrl(sha1Hex: String): String = "http://$PLACEHOLDER_HOST$PATH_PREFIX$sha1Hex"

    fun rewrite(url: String): String? {
        val sha1Hex = url.substringAfter("http://$PLACEHOLDER_HOST$PATH_PREFIX", "")
            .takeIf { it.isNotEmpty() && it.length == 40 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
            ?: return null

        val port = ensureStarted() ?: return null
        return "http://127.0.0.1:$port$PATH_PREFIX$sha1Hex"
    }

    @Synchronized
    private fun ensureStarted(): Int? {
        server?.let { return it.address.port }

        return runCatching {
            val created = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            created.createContext(PATH_PREFIX, ::handle)
            created.executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "PolyPlus Pack Bridge ${threadIds.incrementAndGet()}").apply { isDaemon = true }
            }
            created.start()
            server = created
            LOGGER.info("Resource pack bridge listening on 127.0.0.1:{}", created.address.port)
            created.address.port
        }.onFailure { LOGGER.error("Couldn't start the local resource pack bridge", it) }.getOrNull()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.handleSafely {
            val sha1Hex = exchange.requestURI.path.removePrefix(PATH_PREFIX)
            val remote = packSource
            if (remote == null) {
                LOGGER.warn("Resource pack requested for {} but we aren't connected to a P2P host", sha1Hex)
                exchange.respondEmpty(404)
                return@handleSafely
            }

            var download: P2PPackTransport.Download? = null
            var totalBytes: Int? = null
            for (attempt in 1..HEADER_ATTEMPTS) {
                download?.close()
                download = P2PPackTransport.request(remote, sha1Hex) ?: break
                totalBytes = download.awaitTotalBytes(HEADER_TIMEOUT_MS)
                if (totalBytes != null || download.failed != null) break
                LOGGER.info("No answer from {} for resource pack {} (attempt {}/{})", remote, sha1Hex, attempt, HEADER_ATTEMPTS)
            }

            val active = download
            if (active == null) {
                exchange.respondEmpty(503)
                return@handleSafely
            }

            try {
                if (totalBytes == null) {
                    LOGGER.warn("{} never sent us the header for resource pack {} ({})", remote, sha1Hex, active.failed ?: "timed out")
                    exchange.respondEmpty(504)
                    return@handleSafely
                }
                stream(exchange, active, totalBytes, sha1Hex, remote)
            } finally {
                active.close()
            }
        }
    }

    private fun stream(
        exchange: HttpExchange,
        download: P2PPackTransport.Download,
        totalBytes: Int,
        sha1Hex: String,
        remote: EosProductUserId,
    ) {
        exchange.responseHeaders.add("Content-Type", "application/zip")
        exchange.sendResponseHeaders(200, totalBytes.toLong())

        var written = 0L
        exchange.responseBody.use { out ->
            while (written < totalBytes) {
                val chunk = download.nextChunk(CHUNK_TIMEOUT_MS)
                if (chunk == null) {
                    LOGGER.warn(
                        "Resource pack stream from {} ended early at {}/{} bytes ({})",
                        remote,
                        written,
                        totalBytes,
                        download.failed ?: "no more data",
                    )
                    return
                }
                out.write(chunk)
                written += chunk.size
            }
            out.flush()
        }
        LOGGER.info("Served {} of resource pack {} to vanilla's downloader", SharedResourcePack.humanSize(written), sha1Hex)
    }

    private fun HttpExchange.respondEmpty(status: Int) {
        runCatching {
            sendResponseHeaders(status, -1)
            responseBody.close()
        }
    }

               // receiving client reuse its cached download instead of pulling the pack again.
 private inline fun HttpExchange.handleSafely(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            LOGGER.warn("Resource pack bridge request failed", e)
        } finally {
            close()
        }
    }
}
