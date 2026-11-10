package org.polyfrost.polyplus.nameplate.engine.internal.material.load

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures
import org.polyfrost.polyplus.nameplate.engine.api.shader.TexturePixels
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.material.ColorBytes
import org.polyfrost.polyplus.nameplate.engine.internal.material.CpuCompiler
import org.polyfrost.polyplus.nameplate.engine.internal.material.CpuProgram
import org.polyfrost.polyplus.nameplate.engine.internal.material.CpuShared
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput

internal class MaterialEvaluator(material: Material, private val textures: MaterialTextures = MaterialTextures.NONE) {
    private val spec = material.spec
    private val graph = spec.graph
    private val shared = CpuShared(graph.inputs.size, graph.textures.size)
    private val program = CpuCompiler(shared).compile(graph.output)
    private val reaching by lazy { CpuCompiler(shared).compile(graph.output, coverageOnly = true) }
    private val slots = graph.outputsSlots
    private val grow = if (spec.outline != null) 1 else 0
    private var cells = IntArray(0)
    private var x0 = 0
    private var y0 = 0
    private var width = 0
    private var height = 0
    private var outlineValue = 0

    fun evaluate(frame: MaterialFrame, grid: PixelGrid): PixelImage {
        render(frame)
        val canvas = if (slots) grid.paletteCanvas() else grid.canvas()
        paint(canvas)
        return canvas.compile()
    }

    fun render(frame: MaterialFrame) {
        sweep(program, frame) { at, n -> pack(at, n) }
    }

    fun reach(frame: MaterialFrame): Int {
        val r = reaching.registers
        val covers = reaching.coverage
        sweep(reaching, frame) { at, n ->
            for (i in 0 until n) {
                cells[at + i] = if (r[covers + i] > 0.0) 1 else 0
            }
        }
        val outline = spec.outline != null
        var count = 0
        for (j in grow until height - grow) {
            for (i in grow until width - grow) {
                val at = j * width + i
                if (cells[at] != 0 || outline && (cells[at - 1] or cells[at + 1] or cells[at - width] or cells[at + width]) != 0) {
                    count++
                }
            }
        }
        return count
    }

    private inline fun sweep(run: CpuProgram, frame: MaterialFrame, store: (Int, Int) -> Unit) {
        prepare(frame)
        run.begin(frame.frame.toDouble())
        val r = run.registers
        val xs = run.x
        val ys = run.y
        val total = width * height
        var col = 0
        var row = 0
        var at = 0
        while (at < total) {
            val n = minOf(CpuProgram.LANES, total - at)
            for (i in 0 until n) {
                if (xs >= 0) {
                    r[xs + i] = x0 + col + HALF
                }
                if (ys >= 0) {
                    r[ys + i] = y0 + row + HALF
                }
                if (++col == width) {
                    col = 0
                    row++
                }
            }
            run.run(n)
            store(at, n)
            at += n
        }
    }

    fun snapshot(): IntArray = cells.copyOf(width * height)

    private fun paint(canvas: PixelCanvas) {
        val outline = spec.outline != null
        for (j in grow until height - grow) {
            for (i in grow until width - grow) {
                val at = j * width + i
                if (covered(cells[at])) {
                    canvas[x0 + i, y0 + j] = cells[at]
                } else if (outline && edge(at)) {
                    canvas[x0 + i, y0 + j] = outlineValue
                }
            }
        }
    }

    private fun edge(at: Int): Boolean =
        covered(cells[at - 1]) || covered(cells[at + 1]) || covered(cells[at - width]) || covered(cells[at + width])

    private fun covered(value: Int): Boolean = if (slots) value >= 0 else value ushr ALPHA_SHIFT != 0

    private fun pack(at: Int, count: Int) {
        val r = program.registers
        val coverage = program.coverage
        val color = program.color
        if (slots) {
            val refs = color[0]
            for (i in 0 until count) {
                val ref = r[refs + i].toInt()
                cells[at + i] = if (r[coverage + i] > 0.0 && ref >= 0) ref else -1
            }
        } else {
            val red = color[0]
            val green = color[1]
            val blue = color[2]
            val alpha = color[ALPHA_INDEX]
            for (i in 0 until count) {
                cells[at + i] = if (r[coverage + i] > 0.0) ColorBytes.pack(r[red + i], r[green + i], r[blue + i], r[alpha + i]) else 0
            }
        }
    }

    private fun prepare(frame: MaterialFrame) {
        val body = frame.body
        val m = spec.margins
        shared.areaX0 = -m.left.toDouble()
        shared.areaY0 = -m.top.toDouble()
        shared.areaWidth = (body.width + m.left + m.right).toDouble()
        shared.areaHeight = (body.height + m.top + m.bottom).toDouble()
        shared.bodyX0 = body.x0.toDouble()
        shared.bodyY0 = body.y0.toDouble()
        shared.bodyX1 = body.x1.toDouble()
        shared.bodyY1 = body.y1.toDouble()
        shared.fps = frame.fps.toDouble()
        shared.step = frame.step.toDouble()
        shared.palette = frame.palette
        for ((k, input) in graph.inputs.withIndex()) {
            shared.inputs[k] = when (input) {
                is MaterialInput.Param -> frame.params[input.name] ?: 0.0
                is MaterialInput.Expression -> input.expression.evaluate(frame)
                is MaterialInput.BodyEdgeInput -> input.value(frame.edge)
            }
        }
        val tick = frame.frame.toLong() * frame.step * TexturePixels.TICKS_PER_SECOND / maxOf(1, frame.fps)
        for ((k, texture) in graph.textures.withIndex()) {
            val pixels = textures.pixels(texture)
            shared.textures[k] = pixels
            shared.textureOffsets[k] = pixels?.offset(tick) ?: 0
        }
        outlineValue = spec.outline?.let { if (slots) it else frame.palette.color(it) } ?: 0
        x0 = -m.left - grow
        y0 = -m.top - grow
        width = body.width + m.left + m.right + 2 * grow
        height = body.height + m.top + m.bottom + 2 * grow
        if (cells.size < width * height) {
            cells = IntArray(width * height)
        }
    }

    private companion object {
        const val HALF = 0.5
        const val ALPHA_SHIFT = 24
        const val ALPHA_INDEX = 3
    }
}
