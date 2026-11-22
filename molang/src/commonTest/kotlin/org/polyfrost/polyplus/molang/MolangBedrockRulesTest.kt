package org.polyfrost.polyplus.molang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MolangBedrockRulesTest {
    private fun bedrock(): MolangEnvironment = MolangEnvironment(listOf("anim_time"), floatVariables = true, rules = MolangRules.BEDROCK)

    private fun strict(): MolangEnvironment = MolangEnvironment(listOf("anim_time"), floatVariables = true)

    private fun evaluate(source: String, animTime: Double = 0.0): Double {
        val environment = bedrock()
        val result = MolangCompiler.compile(source, environment, "test")
        val program = assertNotNull(result.program, result.issues.toString())
        assertTrue(result.issues.isEmpty())
        val runtime = MolangRuntime(environment)
        runtime.queries[0] = animTime
        runtime.startFrame()
        val value = program.evaluate(runtime)
        assertFalse(runtime.failed)
        return value
    }

    @Test
    fun unknownQueriesFunctionsAndVariablesReadAsZero() {
        val source = "q.head_y_rotation * 0.5 + math.random(0, 10) + q.some_future_query + v.never_declared + math.sin(q.anim_time)"
        assertEquals(1.0, evaluate(source, animTime = 90.0), 1e-9)
    }

    @Test
    fun theSameExpressionIsRejectedUnderStrictRules() {
        val source = "q.head_y_rotation * 0.5 + math.random(0, 10) + q.some_future_query + v.never_declared + math.sin(q.anim_time)"
        val result = MolangCompiler.compile(source, strict(), "test")
        assertNull(result.program)
        val problems = result.issues.map { it.problem }
        assertTrue(MolangProblem.UNKNOWN_QUERY in problems, problems.toString())
        assertTrue(MolangProblem.UNKNOWN_MATH in problems, problems.toString())
    }

    @Test
    fun otherUnknownNamesReadAsZero() {
        assertEquals(0.0, evaluate("q.is_item_name_any('slot.weapon.mainhand', 'minecraft:bow')"))
        assertEquals(0.0, evaluate("query.head_x_rotation(0)"))
        assertEquals(0.0, evaluate("c.item_slot"))
        assertEquals(0.0, evaluate("math.pi"))
        assertEquals(0.0, evaluate("q.a.b"))
    }

    @Test
    fun scriptsWithUnknownNamesStillRunAndStoreVariables() {
        val environment = bedrock()
        val program = assertNotNull(
            MolangCompiler.compile("v.speed = q.modified_move_speed + 2; v.swing = math.cos(0) * v.speed;", environment, "test").program,
        )
        val runtime = MolangRuntime(environment)
        runtime.startFrame()
        program.evaluate(runtime)
        assertEquals(2.0, runtime.variable(environment.variableSlot("v.speed")))
        assertEquals(2.0, runtime.variable(environment.variableSlot("v.swing")))
    }

    @Test
    fun otherRootsActAsVariables() {
        assertEquals(3.0, evaluate("c.scale = 3; return c.scale;"))
    }

    @Test
    fun wrongArgumentCountsArePaddedOrTrimmed() {
        assertEquals(1.0, evaluate("math.sin(90, 45)"), 1e-9)
        assertEquals(0.0, evaluate("math.clamp(5, 0)"))
        assertEquals(4.0, evaluate("math.max(4)"))
    }

    @Test
    fun syntaxErrorsAreStillReported() {
        val result = MolangCompiler.compile("1 +", bedrock(), "test")
        assertNull(result.program)
        assertEquals(MolangProblem.UNEXPECTED, result.issues.single().problem)
    }

    @Test
    fun bedrockLimitsFitLargeAnimations() {
        val terms = List(1_000) { "math.sin(q.anim_time + $it)" }.joinToString(" + ")
        assertTrue(MolangCompiler.compile(terms, bedrock(), "test").issues.isEmpty())
        assertTrue(MolangCompiler.compile(terms, strict(), "test").issues.isNotEmpty())

        val environment = bedrock()
        val variables = (0 until 300).joinToString("; ") { "v.a$it = $it" } + ";"
        assertTrue(MolangCompiler.compile(variables, environment, "test").issues.isEmpty())
        assertEquals(300, environment.variableCount)
    }

    @Test
    fun largeBedrockProgramsRunWithinTheirBudget() {
        val source = List(500) { "math.sin(q.anim_time + $it)" }.joinToString(" + ")
        val environment = bedrock()
        val program = assertNotNull(MolangCompiler.compile(source, environment, "test").program)
        val runtime = MolangRuntime(environment)
        runtime.startFrame()
        program.evaluate(runtime)
        assertFalse(runtime.failed)
    }
}
