package org.polyfrost.polyplus.client.lobby

import org.polyfrost.polyplus.compat.Mods

object LobbyVulkanFlip {
    private const val NAMESPACE = "polyplus"
    private const val PREFIX = "post/lobby"
    private val HEADER = Regex("^[ \t]*#(version|extension)\b.*$", RegexOption.MULTILINE)
    private const val FLIP = "\n#define texture(s, uv) texture(s, vec2((uv).x, 1.0 - (uv).y))\n#define textureLod(s, uv, lod) textureLod(s, vec2((uv).x, 1.0 - (uv).y), lod)\n"

    @JvmStatic
    fun patch(namespace: String, path: String, fragment: Boolean, source: String?): String? {
        if (source == null || !fragment || !Mods.vulkanMod || namespace != NAMESPACE || !path.startsWith(PREFIX)) return source
        val end = HEADER.findAll(source).lastOrNull()?.range?.last ?: return source
        return source.substring(0, end + 1) + FLIP + source.substring(end + 1)
    }
}
