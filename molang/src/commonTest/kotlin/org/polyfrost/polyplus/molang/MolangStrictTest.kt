package org.polyfrost.polyplus.molang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MolangStrictTest {
    private val environment = MolangEnvironment(listOf("anim_time"))

    private fun problems(source: String): List<MolangProblem> {
        val result = MolangCompiler.compile(source, MolangEnvironment(listOf("anim_time")), "test")
        if (result.issues.isEmpty()) assertNotNull(result.program)
        return result.issues.map { it.problem }
    }

    @Test
    fun knownQueriesAndMathCompileAndEvaluate() {
        val program = assertNotNull(MolangCompiler.compile("math.sin(q.anim_time * 90) + math.clamp(2, 0, 1)", environment, "test").program)
        val runtime = MolangRuntime(environment)
        runtime.queries[0] = 1.0
        runtime.startFrame()
        assertEquals(2.0, program.evaluate(runtime), 1e-9)
    }

    @Test
    fun unknownQueriesAreRejected() {
        assertEquals(listOf(MolangProblem.UNKNOWN_QUERY), problems("q.head_y_rotation"))
    }

    @Test
    fun unknownMathFunctionsAreRejected() {
        assertEquals(listOf(MolangProblem.UNKNOWN_MATH), problems("math.random(0, 1)"))
    }

    @Test
    fun unknownFunctionsAndNamesAreRejected() {
        assertEquals(listOf(MolangProblem.UNKNOWN_FUNCTION), problems("q.is_item_name_any('slot.weapon.mainhand', 'minecraft:bow')"))
        assertEquals(listOf(MolangProblem.UNKNOWN_NAME), problems("c.item_slot"))
    }

    @Test
    fun wrongArgumentCountsAreRejected() {
        assertEquals(listOf(MolangProblem.ARGUMENTS), problems("math.sin(1, 2)"))
    }

    @Test
    fun undeclaredVariablesReadAsZero() {
        val program = assertNotNull(MolangCompiler.compile("v.never_set + 1", environment, "test").program)
        val runtime = MolangRuntime(environment)
        runtime.startFrame()
        assertEquals(1.0, program.evaluate(runtime))
    }

    @Test
    fun syntaxErrorsAreReportedNotThrown() {
        val result = MolangCompiler.compile("1 +", environment, "test")
        assertNull(result.program)
        assertEquals(MolangProblem.UNEXPECTED, result.issues.single().problem)
    }
}
