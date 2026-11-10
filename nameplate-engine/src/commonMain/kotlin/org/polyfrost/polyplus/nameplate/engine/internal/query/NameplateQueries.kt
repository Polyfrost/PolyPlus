package org.polyfrost.polyplus.nameplate.engine.internal.query

import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.molang.MolangRuntime
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock

internal enum class MolangUse(val label: String) {
    CHANNEL("channels"),
    SLOT_COLOR("slot colors"),
    FIELD("fields"),
    TEXT("text effects"),
}

internal enum class NameplateQuery(val id: String, private val only: MolangUse? = null) {
    WIDTH("width"),
    HEIGHT("height"),
    LINE_COUNT("line_count"),
    FRAME("frame"),
    LOOP("loop"),
    FPS("fps"),
    X("x", MolangUse.FIELD),
    Y("y", MolangUse.FIELD),
    NAME_X("name_x"),
    NAME_Y("name_y"),
    NAME_WIDTH("name_width"),
    PREFIX_WIDTH("prefix_width"),
    BG_X0("bg_x0"),
    BG_Y0("bg_y0"),
    BG_X1("bg_x1"),
    BG_Y1("bg_y1"),
    GLYPH_INDEX("glyph_index", MolangUse.TEXT),
    GLYPH_COUNT("glyph_count", MolangUse.TEXT),
    GLYPH_X("glyph_x", MolangUse.TEXT),
    GLYPH_WIDTH("glyph_width", MolangUse.TEXT),
    ;

    fun availableIn(use: MolangUse): Boolean = only == null || only == use

    val place: String
        get() = only?.label.toString()
}

internal object NameplateQueries {
    private val names: List<String> = NameplateQuery.entries.map { it.id }

    fun environment(use: MolangUse, params: Collection<String> = emptyList()): MolangEnvironment =
        MolangEnvironment(
            queries = names,
            params = params,
            unavailable = NameplateQuery.entries.filter { !it.availableIn(use) }.associate { it.id to it.place },
        )

    fun bindBlock(
        runtime: MolangRuntime,
        block: TagBlock,
        background: PixelRect = block.background,
    ) {
        set(runtime, NameplateQuery.WIDTH, block.width)
        set(runtime, NameplateQuery.HEIGHT, block.height)
        set(runtime, NameplateQuery.LINE_COUNT, block.lineCount)
        set(runtime, NameplateQuery.NAME_X, block.nameX)
        set(runtime, NameplateQuery.NAME_Y, block.nameLineY)
        set(runtime, NameplateQuery.NAME_WIDTH, block.nameWidth)
        set(runtime, NameplateQuery.PREFIX_WIDTH, block.prefixWidth)
        set(runtime, NameplateQuery.BG_X0, background.x0)
        set(runtime, NameplateQuery.BG_Y0, background.y0)
        set(runtime, NameplateQuery.BG_X1, background.x1)
        set(runtime, NameplateQuery.BG_Y1, background.y1)
    }

    fun bindFrame(runtime: MolangRuntime, frame: Int, loop: Int, fps: Int) {
        set(runtime, NameplateQuery.FRAME, frame)
        set(runtime, NameplateQuery.LOOP, loop)
        set(runtime, NameplateQuery.FPS, fps)
    }

    fun bindPixel(runtime: MolangRuntime, x: Int, y: Int) {
        set(runtime, NameplateQuery.X, x)
        set(runtime, NameplateQuery.Y, y)
    }

    fun bindGlyph(runtime: MolangRuntime, index: Int, count: Int, x: Int, width: Int) {
        set(runtime, NameplateQuery.GLYPH_INDEX, index)
        set(runtime, NameplateQuery.GLYPH_COUNT, count)
        set(runtime, NameplateQuery.GLYPH_X, x)
        set(runtime, NameplateQuery.GLYPH_WIDTH, width)
    }

    fun bindParams(runtime: MolangRuntime, params: Map<String, Double>) {
        val names = runtime.environment.paramNames
        for (i in names.indices) {
            runtime.params[i] = params[names[i]] ?: 0.0
        }
    }

    private fun set(runtime: MolangRuntime, query: NameplateQuery, value: Int) {
        runtime.queries[query.ordinal] = value.toDouble()
    }
}
