package org.polyfrost.polyplus.client.lobby

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object LobbySodiumWave {
    enum class Flavour(val declarations: String, val fragmentAnchor: String, val color: String) {
        GL("gl", "color *= v_Color;", "color"),
        GL_DIFFUSE("gl", "diffuseColor *= v_Color;", "diffuseColor"),
        PIPELINE("pipeline", "color *= v_Color;", "color"),
        CELERITAS("gl", "diffuseColor.rgb *= m_color.a;\n#endif", "diffuseColor"),
    }

    private const val ROOT = "/assets/polyplus/shaders/sodium/"
    private const val MAIN = "void main()"
    private const val MARKER = "polyplus_waveFront"
    private const val VERTEX_ANCHOR = "vec3 position = _vert_position + translation;"
    private const val VERTEX_CALL = "    position = polyplus_lobbyWave(position);"
    private const val LOCATED_VERTEX = "layout(location = 0) out vec4 v_Color;"
    private const val LOCATED_FRAGMENT = "layout(location = 0) in vec4 v_Color;"
    private const val IRIS_API = "net.irisshaders.iris.api.v0.IrisApi"

    private val snippets = ConcurrentHashMap<String, String>()
    private val reported = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var broken = false

    private val irisPack: Pair<Any, Method>? by lazy {
        runCatching {
            val api = Class.forName(IRIS_API)
            requireNotNull(api.getMethod("getInstance").invoke(null)) to api.getMethod("isShaderPackInUse")
        }.getOrNull()
    }

    @JvmStatic
    fun active(): Boolean = LobbyWaveState.active() && !broken && !irisPackInUse()

    @JvmStatic
    fun irisPackInUse(): Boolean {
        val (api, query) = irisPack ?: return false
        return runCatching { query.invoke(api) == true }.getOrDefault(false)
    }

    @JvmStatic
    fun seconds(): Float = LobbyWaveState.seconds()

    @JvmStatic
    fun aim(): Float = LobbyWaveState.aim()

    @JvmStatic
    fun fail(what: String, error: Throwable?) {
        broken = true
        if (reported.add("fail/$what")) LOBBY_LOGGER.warn("Sodium lobby reveal disabled: {}", what, error)
    }

    @JvmStatic
    fun patchVertex(source: String, flavour: Flavour): String? {
        val text = source.replace("\r\n", "\n")
        if (MARKER in text) return text
        val located = flavour == Flavour.PIPELINE && LOCATED_VERTEX in text
        val declarations = declarations(flavour, "vertex", located) ?: return miss(flavour, "vertex snippet")
        return splice(text, declarations, VERTEX_ANCHOR, VERTEX_CALL) ?: miss(flavour, "vertex anchor")
    }

    @JvmStatic
    fun patchFragment(source: String, flavour: Flavour): String? {
        val text = source.replace("\r\n", "\n")
        if (MARKER in text) return text
        val located = flavour == Flavour.PIPELINE && LOCATED_FRAGMENT in text
        val declarations = declarations(flavour, "fragment", located) ?: return miss(flavour, "fragment snippet")
        val call = "    ${flavour.color} = polyplus_lobbyReveal(${flavour.color});"
        return splice(text, declarations, flavour.fragmentAnchor, call) ?: miss(flavour, "fragment anchor")
    }

    private fun declarations(flavour: Flavour, stage: String, located: Boolean): String? {
        val head = snippet("${flavour.declarations}_$stage${if (located) "_located" else ""}") ?: return null
        val body = snippet("wave_$stage") ?: return null
        return head + body
    }

    private fun splice(text: String, declarations: String, anchor: String, call: String): String? {
        val at = text.indexOf(anchor)
        val main = text.indexOf(MAIN)
        val unique = at >= 0 && text.indexOf(anchor, at + anchor.length) < 0
        if (!unique || main !in 0..at) return null
        val lineEnd = text.indexOf('\n', at + anchor.length).takeIf { it >= 0 } ?: text.length
        return buildString(text.length + declarations.length + call.length + 2) {
            append(text, 0, main)
            append(declarations)
            append('\n')
            append(text, main, lineEnd)
            append('\n')
            append(call)
            append(text, lineEnd, text.length)
        }
    }

    private fun snippet(name: String): String? = snippets[name] ?: LobbySodiumWave::class.java
        .getResourceAsStream("$ROOT$name.glsl")
        ?.use { it.readBytes().decodeToString().replace("\r\n", "\n") }
        ?.also { snippets[name] = it }

    private fun miss(flavour: Flavour, what: String): String? {
        broken = true
        if (reported.add("$flavour/$what")) LOBBY_LOGGER.warn("Sodium {} {} not found, lobby reveal uses Sodium's own program", flavour, what)
        return null
    }
}
