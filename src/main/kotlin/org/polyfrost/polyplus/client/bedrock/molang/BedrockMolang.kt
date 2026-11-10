package org.polyfrost.polyplus.client.bedrock.molang

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.polyfrost.polyplus.molang.MolangCompiler
import org.polyfrost.polyplus.molang.MolangEnvironment
import org.polyfrost.polyplus.molang.MolangProgram
import org.polyfrost.polyplus.molang.MolangRuntime

enum class BedrockQuery(val id: String) {
    ANIM_TIME("anim_time"),
    LIFE_TIME("life_time"),
    DELTA_TIME("delta_time"),
    TIME_OF_DAY("time_of_day"),
    IS_MOVING("is_moving"),
    GROUND_SPEED("ground_speed"),
    VERTICAL_SPEED("vertical_speed"),
    IS_ON_GROUND("is_on_ground"),
    IS_IN_WATER("is_in_water"),
    IS_JUMPING("is_jumping"),
    IS_SNEAKING("is_sneaking"),
    IS_SPRINTING("is_sprinting"),
    YAW_SPEED("yaw_speed"),
    ;

    internal companion object {
        val all: Array<BedrockQuery> = entries.toTypedArray()
    }
}

class BedrockMolang {
    private val environment = MolangEnvironment(BedrockQuery.all.map { it.id }, floatVariables = true)
    private val runtime = MolangRuntime(environment)
    private var keys: Array<String> = emptyArray()
    private var loaded: DoubleArray = DoubleArray(0)
    private var boundContext: MolangContext? = null

    fun expression(source: String): MolangScalar {
        val program = compile(source)
        return if (program.isConstant) MolangScalar.Constant(program.constant) else MolangScalar.Expression(this, program)
    }

    fun script(source: String): MolangScript? =
        if (source.all { it.isWhitespace() || it == ';' }) null else MolangScript(this, compile(source))

    internal fun evaluate(program: MolangProgram, context: MolangContext): Double {
        bind(context)
        return program.evaluate(runtime)
    }

    internal fun execute(program: MolangProgram, context: MolangContext) {
        bind(context)
        program.evaluate(runtime)
        val variables = context.variables
        for (i in keys.indices) {
            val value = runtime.variable(i)
            if (value.toRawBits() != loaded[i].toRawBits()) {
                variables[keys[i]] = value.toFloat()
            }
        }
    }

    private fun compile(source: String): MolangProgram {
        val result = MolangCompiler.compile(source, environment, "molang")
        return result.program
            ?: throw IllegalArgumentException("Invalid Molang '$source': ${result.issues.joinToString("; ") { it.message }}")
    }

    private fun bind(context: MolangContext) {
        if (keys.size != environment.variableCount) {
            keys = environment.variableNames.toTypedArray()
            loaded = DoubleArray(keys.size)
        }
        if (boundContext !== context) {
            val queries = BedrockQuery.all
            for (i in queries.indices) {
                runtime.queries[i] = context.query(queries[i])
            }
            boundContext = context
        }
        val variables = context.variables
        for (i in keys.indices) {
            val value = variables[keys[i]]?.toDouble() ?: 0.0
            loaded[i] = value
            runtime.setVariable(i, value)
        }
        runtime.startFrame()
    }
}

sealed class MolangScalar {
    abstract fun evaluate(context: MolangContext): Double

    class Constant(val value: Double) : MolangScalar() {
        override fun evaluate(context: MolangContext): Double = value
    }

    class Expression internal constructor(
        private val molang: BedrockMolang,
        private val program: MolangProgram,
    ) : MolangScalar() {
        override fun evaluate(context: MolangContext): Double = molang.evaluate(program, context)
    }

    companion object {
        val ZERO: MolangScalar = Constant(0.0)
        val ONE: MolangScalar = Constant(1.0)
    }
}

class MolangScript internal constructor(
    private val molang: BedrockMolang,
    private val program: MolangProgram,
) {
    fun execute(context: MolangContext) {
        molang.execute(program, context)
    }
}

fun List<MolangScript>.executeAll(context: MolangContext) {
    for (i in indices) {
        this[i].execute(context)
    }
}

internal fun JsonObject.molangScripts(field: String, molang: BedrockMolang): List<MolangScript> =
    when (val element = get(field)) {
        is JsonPrimitive if element.isString -> listOfNotNull(molang.script(element.asString))
        is JsonArray -> element.mapNotNull { item ->
            if (item.isJsonPrimitive && item.asJsonPrimitive.isString) molang.script(item.asString) else null
        }

        else -> emptyList()
    }
