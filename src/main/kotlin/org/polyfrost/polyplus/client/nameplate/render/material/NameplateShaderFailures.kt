package org.polyfrost.polyplus.client.nameplate.render.material

import org.apache.logging.log4j.LogManager

data class NameplateShaderFailure(
    val hash: String,
    val pipelines: List<String>,
    val device: Map<String, String>,
    val debugMessages: List<String>,
    val error: Throwable?,
)

object NameplateShaderFailures {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    fun interface Reporter {
        fun report(failure: NameplateShaderFailure)
    }

    @Volatile
    var reporter: Reporter = Reporter { }

    fun report(hash: String, pipelines: List<String>, device: Map<String, String>, debugMessages: List<String>, error: Throwable?) {
        LOGGER.error(
            "Nameplate material {} failed to compile ({}) on {} / {} / {} ({}); the CPU fallback draws it",
            hash, pipelines, device["name"], device["vendor"], device["driver"], device["backend"], error,
        )
        reporter.report(NameplateShaderFailure(hash, pipelines, device, debugMessages, error))
    }
}
