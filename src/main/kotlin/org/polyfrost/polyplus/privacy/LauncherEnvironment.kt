package org.polyfrost.polyplus.privacy

import net.fabricmc.loader.api.FabricLoader
import java.io.File

object LauncherEnvironment {
    private const val BRAND_PROPERTY = "minecraft.launcher.brand"
    private const val BRAND = "OneClient"

    private const val MARKER_PROPERTY = "polyplus.launcher"
    private const val MARKER_ENV = "POLYPLUS_LAUNCHER"

    private const val MAX_WALK_UP = 6

    val launcherDir: File? by lazy { findLauncherDir() }

    val isOneClient: Boolean by lazy {
        when (override()) {
            "oneclient" -> true
            "standalone" -> false
            else -> brandIsOneClient() || launcherDir != null
        }
    }

    fun launcherAcceptedTerms(): Boolean? {
        val settings = launcherDir?.let { File(it, "settings.json") }?.takeIf { it.isFile } ?: return null
        val text = runCatching { settings.readText() }.getOrNull() ?: return null
        val terms = intField(text, "accepted_tos_version") ?: return null
        return terms > 0
    }

    private fun override(): String? =
        (System.getProperty(MARKER_PROPERTY) ?: System.getenv(MARKER_ENV))?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun brandIsOneClient(): Boolean =
        System.getProperty(BRAND_PROPERTY)?.trim().equals(BRAND, ignoreCase = true)

    private fun findLauncherDir(): File? {
        var dir: File? = runCatching { FabricLoader.getInstance().gameDir.toFile().absoluteFile }.getOrNull()
        var depth = 0
        while (dir != null && depth <= MAX_WALK_UP) {
            if (isLauncherDir(dir)) return dir
            dir = dir.parentFile
            depth++
        }
        return null
    }

    private fun isLauncherDir(dir: File): Boolean {
        val settings = File(dir, "settings.json")
        if (settings.isFile) {
            val text = runCatching { settings.readText() }.getOrNull()
            if (text != null && text.contains("accepted_tos_version")) return true
        }
        return File(dir, "auth.json").isFile && File(dir, "clusters").isDirectory
    }

    private fun intField(text: String, name: String): Int? =
        Regex("\"$name\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
}
