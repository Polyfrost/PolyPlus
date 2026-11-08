package org.polyfrost.polyplus.compat

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object Reflect {
    fun find(name: String, loader: ClassLoader? = Reflect::class.java.classLoader): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    fun method(owner: Class<*>, name: String, vararg params: Class<*>): Method? =
        runCatching { owner.getMethod(name, *params) }.getOrNull()

    fun methodByArity(owner: Class<*>, name: String, parameters: Int): Method =
        owner.methods.first { it.name == name && it.parameterCount == parameters }

    fun declared(owner: Class<*>, name: String): Field = owner.getDeclaredField(name).also { it.isAccessible = true }

    fun field(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            val found = runCatching { declared(current, name) }.getOrNull()
            if (found != null) return found
            current = current.superclass
        }
        return null
    }
}

internal class ReflectiveHook(private val logger: Logger, private val level: Level = Level.WARN) {
    private val failed = AtomicBoolean()

    val broken: Boolean get() = failed.get()

    fun fail(message: String, error: Throwable) {
        if (failed.compareAndSet(false, true)) logger.log(level, message, error)
    }

    inline fun <T> attempt(message: String, fallback: T, action: () -> T): T =
        try {
            action()
        } catch (e: Throwable) {
            fail(message, e)
            fallback
        }
}
