package org.polyfrost.polyplus.client.features

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DefaultModOrder {
    private val logger = LogManager.getLogger("PolyPlus/DefaultModOrder")

    private const val RESOURCE = "/assets/polyplus/mod-order"
    private const val FILE_NAME = "mod-order"

    fun initialize() {
        val defaults = bundledOrder()
        if (defaults.isEmpty()) {
            logger.warn("Bundled mod order is missing or empty, leaving OneConfig's order alone")
            return
        }

        val path = orderFile()
        val current = readOrder(path)
        val merged = merge(current, defaults)
        if (merged == current) return

        write(path, merged)
        logger.info("Seeded {} mod order entries into {}", merged.size - current.size, path)
    }

    internal fun merge(current: List<String>, defaults: List<String>): List<String> {
        if (current.isEmpty()) return defaults

        val result = current.toMutableList()
        defaults.forEachIndexed { index, id ->
            if (id in result) return@forEachIndexed

            val after = defaults.take(index).asReversed().firstNotNullOfOrNull { previous ->
                result.indexOf(previous).takeIf { it >= 0 }?.plus(1)
            }
            val before = after ?: defaults.drop(index + 1).firstNotNullOfOrNull { next ->
                result.indexOf(next).takeIf { it >= 0 }
            }
            result.add(before ?: result.size, id)
        }
        return result
    }

    internal fun bundledOrder(): List<String> =
        runCatching {
            javaClass.getResourceAsStream(RESOURCE)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readLines() }
                .orEmpty()
                .clean()
        }.onFailure { logger.error("Could not read the bundled mod order", it) }.getOrDefault(emptyList())

    private fun readOrder(path: Path): List<String> =
        runCatching {
            if (Files.exists(path)) Files.readAllLines(path, StandardCharsets.UTF_8).clean() else emptyList()
        }.onFailure { logger.error("Could not read {}", path, it) }.getOrDefault(emptyList())

    private fun List<String>.clean(): List<String> = map(String::trim).filter(String::isNotEmpty).distinct()

    private fun write(path: Path, order: List<String>) {
        runCatching {
            Files.createDirectories(path.parent)
            Files.write(path, order.joinToString("\n").toByteArray(StandardCharsets.UTF_8))
        }.onFailure { logger.error("Could not write {}", path, it) }
    }

    private fun orderFile(): Path =
        runCatching { ConfigManager.internal().folder }
            .getOrElse { Paths.get("oneconfig") }
            .resolve(FILE_NAME)
}
