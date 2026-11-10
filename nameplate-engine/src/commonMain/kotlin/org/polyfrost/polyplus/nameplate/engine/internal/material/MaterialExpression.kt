package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.molang.MolangIssue
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame

internal fun interface MaterialMolang {
    fun compile(source: String, issues: MutableList<MolangIssue>): MaterialExpression?
}

internal fun interface MaterialExpression {
    fun evaluate(frame: MaterialFrame): Double
}
