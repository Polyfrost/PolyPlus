package org.polyfrost.polyplus.client.gui.news

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import org.w3c.dom.Element
import java.io.StringReader
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal class NewsPost(
    val title: String,
    val summary: String,
    val link: String,
    val published: Long,
    val image: String?,
)

internal object NewsFeed {
    const val BLOG_URL = "https://polyfrost.org/blog"
    private const val FEED_URL = "https://polyfrost.org/rss.xml"
    private const val REFRESH_MS = 30 * 60 * 1000L
    private const val MAX_POSTS = 30

    private val LOGGER = LogManager.getLogger()

    var posts by mutableStateOf<List<NewsPost>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var seen by mutableStateOf(PolyPlusMainMenuConfig.lastSeenNewsMillis)

    private var fetchedAt = 0L
    private var job: Job? = null

    val unread: Boolean
        get() = (posts.firstOrNull()?.published ?: 0L) > seen

    fun refresh(force: Boolean = false) {
        if (job?.isActive == true) return
        if (!force && loaded && !failed && System.currentTimeMillis() - fetchedAt < REFRESH_MS) return
        loading = true
        job = PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val result = runCatching {
                val response = PolyPlusClient.HTTP.get(FEED_URL)
                check(response.status.isSuccess()) { "News feed returned ${response.status}" }
                parse(response.bodyAsText())
            }
            result.exceptionOrNull()?.let { LOGGER.warn("Failed to load news", it) }
            ClientPlatform.runOnMain {
                result.getOrNull()?.let {
                    posts = it
                    fetchedAt = System.currentTimeMillis()
                }
                failed = result.isFailure
                loading = false
                loaded = true
            }
        }
    }

    fun markSeen() {
        val newest = posts.firstOrNull()?.published ?: return
        if (newest <= seen) return
        seen = newest
        PolyPlusMainMenuConfig.lastSeenNewsMillis = newest
        PolyPlusMainMenuConfig.save()
    }

    fun open(post: NewsPost) = ClientPlatform.openUri(post.link)

    fun openBlog() = ClientPlatform.openUri(BLOG_URL)

    private fun parse(xml: String): List<NewsPost> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val items = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).getElementsByTagName("item")
        val base = URI(FEED_URL)
        return (0 until items.length)
            .mapNotNull { index -> (items.item(index) as? Element)?.let { post(it, base) } }
            .distinctBy { it.link }
            .sortedByDescending { it.published }
            .take(MAX_POSTS)
    }

    private fun post(item: Element, base: URI): NewsPost? {
        val title = item.text("title") ?: return null
        val link = item.text("link")?.let { web(base, it) } ?: return null
        val published = item.text("pubDate")?.let {
            runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        } ?: 0L
        val summary = item.text("description")?.replace(TAG, " ")?.replace(SPACE, " ")?.trim().orEmpty()
        return NewsPost(title, summary, link, published, image(item, base))
    }

    private fun image(item: Element, base: URI): String? {
        val enclosures = item.getElementsByTagName("enclosure")
        for (index in 0 until enclosures.length) {
            val enclosure = enclosures.item(index) as? Element ?: continue
            if (enclosure.getAttribute("type").startsWith("image/")) web(base, enclosure.getAttribute("url"))?.let { return it }
        }
        for (name in MEDIA_TAGS) {
            val media = item.getElementsByTagNameNS("*", name)
            for (index in 0 until media.length) {
                web(base, (media.item(index) as? Element)?.getAttribute("url").orEmpty())?.let { return it }
            }
        }
        return null
    }

    private fun Element.text(name: String): String? =
        getElementsByTagName(name).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun web(base: URI, value: String): String? = value.trim().takeIf { it.isNotEmpty() }?.let { trimmed ->
        runCatching { base.resolve(trimmed).takeIf { it.scheme == "https" }?.toString() }.getOrNull()
    }

    private val MEDIA_TAGS = listOf("thumbnail", "content")
    private val TAG = Regex("<[^>]*>")
    private val SPACE = Regex("\\s+")
}
