package org.polyfrost.polyplus.client.lobby.shader

import java.util.concurrent.ConcurrentHashMap

object LobbyRevealGlsl {
    const val UNIFORM = "polyplus_LobbyWave"
    const val VARYING = "polyplus_waveFront"

    private const val ROOT = "/assets/polyplus/shaders/iris/"
    private const val CACHE_SIZE = 256
    private const val FIRST_IN_OUT_VERSION = 130
    private const val FIRST_CORE_VERSION = 150
    private const val LOOK_BEHIND = 32

    private val VERSION = Regex("""^[ \t]*#[ \t]*version[ \t]+(\d+)(?:[ \t]+(\w+))?""", RegexOption.MULTILINE)
    private val DIRECTIVE = Regex("""^[ \t]*#[ \t]*(\w*)""")
    private val MAIN = Regex("""\bvoid\s+main\s*\(\s*(?:void\s*)?\)\s*\{""")
    private val GL_VERTEX = Regex("""\bgl_Vertex\b""")
    private val FTRANSFORM = Regex("""\bftransform\s*\(\s*\)""")
    private val VA_POSITION = Regex("""\bvaPosition\b""")
    private val VEC3_BEFORE = Regex("""\bvec3\s*$""")
    private val CHUNK_OFFSET = Regex("""\buniform\s+(?:(?:highp|mediump|lowp)\s+)?vec3\s+chunkOffset\s*;""")

    class Plan internal constructor(
        val vertex: String?,
        val fragment: String?,
        val rise: Boolean,
        val discard: Boolean,
        val miss: String?,
    ) {
        val hit: Boolean get() = miss == null
    }

    private data class Key(
        val vertex: String?,
        val geometry: String?,
        val tessControl: String?,
        val tessEval: String?,
        val fragment: String?,
    )

    private class Edit(val start: Int, val end: Int, val text: String)

    private class Source(val text: String) {
        val masked: String = mask(text)
        val version: Int?
        val core: Boolean

        init {
            val match = VERSION.find(masked)
            version = match?.groupValues?.get(1)?.toIntOrNull()
            val profile = match?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
            core = version != null && (profile == "core" || (profile == null && version >= FIRST_CORE_VERSION))
        }

        val varyingOut: String get() = if ((version ?: 0) < FIRST_IN_OUT_VERSION) "varying" else "out"
        val varyingIn: String get() = if ((version ?: 0) < FIRST_IN_OUT_VERSION) "varying" else "in"

        fun header(): Int {
            var at = 0
            var after = -1
            var depth = 0
            var pending = false
            while (at < masked.length) {
                var end = at
                do {
                    end = masked.indexOf('\n', end).let { if (it < 0) masked.length else it + 1 }
                } while (end < masked.length && masked.substring(at, end).trimEnd('\n', '\r').endsWith('\\'))
                val line = masked.substring(at, end)
                at = end
                val name = DIRECTIVE.find(line)?.groupValues?.get(1)
                if (name == null) {
                    if (line.isNotBlank()) break
                    continue
                }
                when (name) {
                    "if", "ifdef", "ifndef" -> depth++
                    "endif" -> {
                        if (depth > 0) depth--
                        if (depth == 0 && pending) {
                            after = end
                            pending = false
                        }
                    }
                    "version", "extension" -> if (depth == 0) after = end else pending = true
                }
            }
            return if (pending) -1 else after
        }

        fun mainBody(): Int? = MAIN.findAll(masked).toList().singleOrNull()?.range?.last?.plus(1)
    }

    private val plans = lru<Key, Plan>()
    private val origins = lru<String, String>()
    private val snippets = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun plan(vertex: String?, geometry: String?, tessControl: String?, tessEval: String?, fragment: String?): Plan {
        val key = Key(origin(vertex), geometry, tessControl, tessEval, origin(fragment))
        synchronized(plans) { plans[key] }?.let { return it }
        val plan = compute(key)
        synchronized(plans) { plans[key] = plan }
        synchronized(origins) {
            if (plan.vertex != null && key.vertex != null && plan.vertex !== key.vertex) origins[plan.vertex] = key.vertex
            if (plan.fragment != null && key.fragment != null && plan.fragment !== key.fragment) origins[plan.fragment] = key.fragment
        }
        return plan
    }

    private fun origin(source: String?): String? = source?.let { synchronized(origins) { origins[it] } ?: it }

    private fun compute(key: Key): Plan {
        val staged = key.geometry != null || key.tessControl != null || key.tessEval != null
        val vertex = key.vertex?.let(::Source)
        val fragment = key.fragment?.let(::Source)
        val reason = when {
            vertex == null -> "no vertex stage"
            fragment == null -> "no fragment stage"
            vertex.version == null -> "vertex has no #version"
            !staged && fragment.version == null -> "fragment has no #version"
            else -> null
        }
        if (reason != null || vertex == null || fragment == null) return miss(key, reason ?: "no source")
        val risen = patchVertex(vertex, !staged) ?: return miss(key, vertexMiss(vertex))
        if (staged) return Plan(risen, key.fragment, rise = true, discard = false, miss = null)
        val revealed = patchFragment(fragment) ?: return miss(key, "fragment main() not found")
        return Plan(risen, revealed, rise = true, discard = true, miss = null)
    }

    private fun vertexMiss(vertex: Source): String = when {
        vertex.mainBody() == null -> "vertex main() not found"
        vertex.header() < 0 -> "vertex header not found"
        vertex.core -> "core vertex has no vaPosition"
        else -> "vertex has no gl_Vertex or ftransform()"
    }

    private fun miss(key: Key, reason: String): Plan = Plan(key.vertex, key.fragment, rise = false, discard = false, miss = reason)

    private fun patchVertex(source: Source, varying: Boolean): String? {
        val header = source.header().takeIf { it >= 0 } ?: return null
        val main = source.mainBody() ?: return null
        val edits = if (source.core) coreEdits(source) else compatEdits(source)
        if (edits.isEmpty()) return null
        val wave = snippet("wave") ?: return null
        val body = snippet(if (source.core) "vertex_core" else "vertex_compat") ?: return null
        val position = if (source.core) "vaPosition + chunkOffset" else "gl_Vertex.xyz"
        val declarations = buildString {
            append("uniform vec4 ").append(UNIFORM).append(";\n")
            if (source.core && !CHUNK_OFFSET.containsMatchIn(source.masked)) append("uniform vec3 chunkOffset;\n")
            if (varying) append(source.varyingOut).append(" float ").append(VARYING).append(";\n")
            append("float polyplus_front(vec3 polyplus_position);\n")
            append(if (source.core) "vec3 polyplus_vaPosition();\n" else "vec4 polyplus_vertex();\n")
        }
        edits += Edit(header, header, declarations)
        if (varying) edits += Edit(main, main, "\n    $VARYING = polyplus_front($position);")
        edits += Edit(source.text.length, source.text.length, "\n$wave\n$body")
        return apply(source.text, edits)
    }

    private fun compatEdits(source: Source): MutableList<Edit> {
        val edits = FTRANSFORM.findAll(source.masked)
            .map { Edit(it.range.first, it.range.last + 1, "(gl_ModelViewProjectionMatrix * polyplus_vertex())") }
            .toMutableList()
        GL_VERTEX.findAll(source.masked).mapTo(edits) { Edit(it.range.first, it.range.last + 1, "polyplus_vertex()") }
        return edits
    }

    private fun coreEdits(source: Source): MutableList<Edit> = VA_POSITION.findAll(source.masked)
        .filterNot { VEC3_BEFORE.containsMatchIn(source.masked.substring((it.range.first - LOOK_BEHIND).coerceAtLeast(0), it.range.first)) }
        .map { Edit(it.range.first, it.range.last + 1, "polyplus_vaPosition()") }
        .toMutableList()

    private fun patchFragment(source: Source): String? {
        val header = source.header().takeIf { it >= 0 } ?: return null
        val main = source.mainBody() ?: return null
        val edits = listOf(
            Edit(header, header, "${source.varyingIn} float $VARYING;\n"),
            Edit(main, main, "\n    if ($VARYING <= 0.0) discard;"),
        )
        return apply(source.text, edits)
    }

    private fun apply(text: String, edits: List<Edit>): String {
        val sorted = edits.sortedWith(compareBy({ it.start }, { it.end }))
        return buildString(text.length + sorted.sumOf { it.text.length }) {
            var at = 0
            for (edit in sorted) {
                append(text, at, edit.start)
                append(edit.text)
                at = edit.end
            }
            append(text, at, text.length)
        }
    }

    private fun mask(text: String): String {
        val out = text.toCharArray()
        var i = 0
        while (i < out.size) {
            i = when {
                text.startsWith("//", i) -> blank(out, i, text.indexOf('\n', i).let { if (it < 0) out.size else it })
                text.startsWith("/*", i) -> blank(out, i, text.indexOf("*/", i + 2).let { if (it < 0) out.size else it + 2 })
                else -> i + 1
            }
        }
        return String(out)
    }

    private fun blank(out: CharArray, from: Int, to: Int): Int {
        for (j in from until to) if (out[j] != '\n') out[j] = ' '
        return to
    }

    private fun snippet(name: String): String? = snippets[name] ?: LobbyRevealGlsl::class.java
        .getResourceAsStream("$ROOT$name.glsl")
        ?.use { it.readBytes().decodeToString().replace("\r\n", "\n") }
        ?.also { snippets[name] = it }

    private fun <K, V> lru(): MutableMap<K, V> = object : LinkedHashMap<K, V>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > CACHE_SIZE
    }
}
