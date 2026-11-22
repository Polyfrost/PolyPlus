package org.polyfrost.polyplus.molang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MolangLimitsTest {
    private fun environment(): MolangEnvironment = MolangEnvironment(listOf("x"))

    private fun problems(source: String, environment: MolangEnvironment = environment()): List<MolangProblem> =
        MolangCompiler.compile(source, environment, "test").issues.map { it.problem }

    private fun program(source: String, environment: MolangEnvironment = environment()): MolangProgram {
        val result = MolangCompiler.compile(source, environment, "test")
        return assertNotNull(result.program, result.issues.toString())
    }

    private fun fbmSum(count: Int): String = List(count) { "math.fbm(q.x, q.x, 8)" }.joinToString(" + ")

    @Test
    fun programsOverTheInstructionCapAreRejected() {
        val result = MolangCompiler.compile(fbmSum(40), environment(), "test")
        assertNull(result.program)
        val issue = result.issues.single()
        assertEquals(MolangProblem.COST, issue.problem)
        assertEquals(MolangLimits.MAX_INSTRUCTIONS, issue.values["limit"])
        assertTrue(issue.values.getValue("cost") > MolangLimits.MAX_INSTRUCTIONS)
    }

    @Test
    fun programsUnderTheInstructionCapCompile() {
        assertTrue(program(fbmSum(20)).cost <= MolangLimits.MAX_INSTRUCTIONS)
    }

    @Test
    fun tooManyNodesAreRejected() {
        assertEquals(listOf(MolangProblem.TOO_BIG), problems(List(200) { "q.x" }.joinToString(" + ")))
    }

    @Test
    fun deepNestingIsRejected() {
        val depth = MolangLimits.MAX_DEPTH + 1
        assertEquals(listOf(MolangProblem.TOO_DEEP), problems("(".repeat(depth) + "q.x" + ")".repeat(depth)))
    }

    @Test
    fun longSourcesAreRejected() {
        assertEquals(listOf(MolangProblem.TOO_LONG), problems("q.x" + " ".repeat(MolangLimits.MAX_SOURCE_CHARS)))
    }

    @Test
    fun tooManyVariablesAreRejected() {
        val source = (0..MolangLimits.MAX_VARIABLES).joinToString("; ") { "v.a$it = 1" } + ";"
        assertTrue(MolangProblem.VARIABLES in problems(source))
        val fits = (0 until MolangLimits.MAX_VARIABLES).joinToString("; ") { "v.a$it = 1" } + ";"
        assertTrue(problems(fits).isEmpty())
    }

    @Test
    fun fuelRunsOutAcrossEvaluationsInOneFrame() {
        val program = program(fbmSum(16))
        val runtime = MolangRuntime(program.environment)
        runtime.queries[0] = 3.5
        runtime.startFrame()
        val expected = program.evaluate(runtime)
        val runs = MolangLimits.CPU_BUDGET / program.cost
        repeat(runs - 1) {
            assertEquals(expected, program.evaluate(runtime))
            assertFalse(runtime.failed)
        }
        assertEquals(0.0, program.evaluate(runtime))
        assertTrue(runtime.failed)
        assertEquals(0.0, program.evaluate(runtime))

        runtime.startFrame()
        assertEquals(expected, program.evaluate(runtime))
        assertFalse(runtime.failed)
    }

    @Test
    fun aSmallBudgetStopsAnExpensiveProgram() {
        val program = program(fbmSum(4))
        val runtime = MolangRuntime(program.environment)
        runtime.startFrame(program.cost - 1)
        assertEquals(0.0, program.evaluate(runtime))
        assertTrue(runtime.failed)
    }
}
