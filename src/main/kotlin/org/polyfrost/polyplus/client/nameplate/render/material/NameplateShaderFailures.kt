package org.polyfrost.polyplus.client.nameplate.render.material

data class NameplateShaderFailure(
    val hash: String,
    val pipelines: List<String>,
    val device: Map<String, String>,
    val debugMessages: List<String>,
    val error: Throwable?,
)

object NameplateShaderFailures {
    fun interface Reporter {
        fun report(failure: NameplateShaderFailure)
    }

    @Volatile
    var reporter: Reporter = Reporter { }

    fun report(failure: NameplateShaderFailure) {
        reporter.report(failure)
    }
}
