package org.polyfrost.polyplus.nameplate.engine.query

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.polyfrost.polyplus.molang.MolangCompiler
import org.polyfrost.polyplus.molang.MolangProblem
import org.polyfrost.polyplus.nameplate.engine.internal.query.MolangUse
import org.polyfrost.polyplus.nameplate.engine.internal.query.NameplateQueries

class NameplateMolangStrictTest {
    @Test
    fun bedrockOnlyNamesAreRejectedInNameplates() {
        for (use in MolangUse.entries) {
            val environment = NameplateQueries.environment(use)
            assertFalse(environment.rules.lenient)
            val result = MolangCompiler.compile(BEDROCK_EXPRESSION, environment, "manifest.json")
            assertNull(result.program)
            val problems = result.issues.map { it.problem }
            assertTrue(MolangProblem.UNKNOWN_QUERY in problems, problems.toString())
            assertTrue(MolangProblem.UNKNOWN_MATH in problems, problems.toString())
        }
    }

    @Test
    fun nameplateQueriesStillCompile() {
        val environment = NameplateQueries.environment(MolangUse.CHANNEL)
        assertNotNull(MolangCompiler.compile("math.sin(q.width * 4) + v.never_set", environment, "manifest.json").program)
    }

    private companion object {
        const val BEDROCK_EXPRESSION = "q.head_y_rotation * 0.5 + math.random(0, 10) + q.some_future_query + v.never_declared"
    }
}
