@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ChangelogSource
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.features.ModpackDiff
import org.polyfrost.polyplus.libs.ktor.client.request.get
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import java.util.concurrent.CompletableFuture

internal object ModpackChangelog : ChangelogSource() {
    private const val URL = "https://raw.githubusercontent.com/Polyfrost/DataStorageV2/main/data/oneclient/MODPACK_CHANGELOG.md"
    private val HEADING = Regex("""^##\s+\[v?(\d+)\.(\d+)\.(\d+)]""", RegexOption.MULTILINE)

    private val ORDER = compareBy<Triple<Int, Int, Int>>({ it.first }, { it.second }, { it.third })

    @Volatile
    private var releases: List<Triple<Int, Int, Int>> = emptyList()

    override fun load(): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            runCatching {
                val response = PolyPlusClient.HTTP.get(URL)
                check(response.status.isSuccess()) { "Modpack changelog returned ${response.status}" }
                response.bodyAsText()
            }.onSuccess { text ->
                releases = HEADING.findAll(text).map { it.version() }.toList()
                future.complete(text)
            }.onFailure(future::completeExceptionally)
        }
        return future
    }

    override fun installedVersion(): String? {
        val installed = ModpackDiff.installedVersion?.let { HEADING.find("## [$it]") }?.version() ?: return null
        return releases
            .filter { ORDER.compare(it, installed) <= 0 }
            .maxWithOrNull(ORDER)
            ?.let { "${it.first}.${it.second}.${it.third}" }
    }

    private fun MatchResult.version(): Triple<Int, Int, Int> =
        Triple(groupValues[1].toInt(), groupValues[2].toInt(), groupValues[3].toInt())
}
