package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.molang.MolangCompiler
import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.molang.MolangIssue
import org.polyfrost.polyplus.molang.MolangProgram
import org.polyfrost.polyplus.molang.MolangRuntime
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialExpression
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialMolang
import org.polyfrost.polyplus.nameplate.engine.internal.query.MolangUse
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQueries
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQuery

internal class NameplateMaterialMolang(params: Collection<String>, private val loop: Int, private val fps: Int) : MaterialMolang {
    private val environment = NameplateQueries.environment(MolangUse.CHANNEL, params)

    override fun compile(source: String, issues: MutableList<MolangIssue>): MaterialExpression? {
        val result = MolangCompiler.compile(source, environment, EXPR)
        issues += result.issues
        return result.program?.let { MolangMaterialExpression(it, environment, loop, fps) }
    }

    private companion object {
        const val EXPR = "expr"
    }
}

internal class MolangMaterialExpression(
    private val program: MolangProgram,
    environment: MolangEnvironment,
    private val loop: Int,
    private val fps: Int,
) : MaterialExpression {
    private val runtime = MolangRuntime(environment)
    private val slots = IntArray(NameplateQuery.entries.size) { environment.querySlot(NameplateQuery.entries[it].id) }
    private val params = environment.paramNames

    val cost: Int
        get() = program.cost

    val source: String
        get() = program.source

    override fun evaluate(frame: MaterialFrame): Double {
        val placement = frame.placement
        val background = placement?.background
        set(NameplateQuery.WIDTH, placement?.width ?: 0)
        set(NameplateQuery.HEIGHT, placement?.height ?: 0)
        set(NameplateQuery.LINE_COUNT, placement?.lineCount ?: 0)
        set(NameplateQuery.NAME_X, placement?.nameX ?: 0)
        set(NameplateQuery.NAME_Y, placement?.nameY ?: 0)
        set(NameplateQuery.NAME_WIDTH, placement?.nameWidth ?: 0)
        set(NameplateQuery.PREFIX_WIDTH, placement?.prefixWidth ?: 0)
        set(NameplateQuery.BG_X0, background?.x0 ?: 0)
        set(NameplateQuery.BG_Y0, background?.y0 ?: 0)
        set(NameplateQuery.BG_X1, background?.x1 ?: 0)
        set(NameplateQuery.BG_Y1, background?.y1 ?: 0)
        set(NameplateQuery.FRAME, (frame.frame * frame.step).mod(loop))
        set(NameplateQuery.LOOP, loop)
        set(NameplateQuery.FPS, fps)
        for (i in params.indices) {
            runtime.params[i] = frame.params[params[i]] ?: 0.0
        }
        runtime.startFrame()
        runtime.clearVariables()
        val value = program.evaluate(runtime)
        return if (runtime.failed || !value.isFinite()) 0.0 else value
    }

    private fun set(query: NameplateQuery, value: Int) {
        val slot = slots[query.ordinal]
        if (slot != MolangEnvironment.NONE) {
            runtime.queries[slot] = value.toDouble()
        }
    }
}
