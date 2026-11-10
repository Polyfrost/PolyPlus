package org.polyfrost.polyplus.client.nameplate.render.material

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
//? if = 1.8.9 {
/*import java.lang.reflect.Field
import java.lang.reflect.Modifier
*///?}

object NameplateIris {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    enum class Materials { CPU, SHADER, PACK }

    private const val IRIS_ID = "iris"
    private const val MATERIALS_PROPERTY = "polyplus.nameplate.irisMaterials"
    private const val TRANSLUCENT = "ENTITIES_TRANSLUCENT"
    private const val EMISSIVE = "EMISSIVE_ENTITIES"
    private const val SHADOW = "SHADOW_ENTITIES"

    private val assigned: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    val materials: Materials = materials(System.getProperty(MATERIALS_PROPERTY))

    private val probe: Probe? = detect()

    fun shadowPass(): Boolean = probe?.shadowPass() ?: false

    fun shaderPackInUse(): Boolean = probe?.shaderPackInUse() ?: false

    fun gpuMaterials(): Boolean {
        val api = probe ?: return true
        if (!api.shaderPackInUse()) {
            return true
        }
        return when (materials) {
            Materials.CPU -> false
            Materials.SHADER -> api.rawPrograms && redirects()
            Materials.PACK -> api.assigns
        }
    }

    private fun redirects(): Boolean {
        //? if >= 1.21.1 {
        return NameplateIrisTarget.ready()
        //?} else {
        /*return false
        *///?}
    }

    fun assign(pipelines: List<Any>, emissive: Boolean) {
        val api = probe
        if (materials != Materials.PACK || api == null || !api.assigns) {
            return
        }
        for (pipeline in pipelines) {
            if (assigned.add(pipeline)) {
                api.assign(pipeline, if (emissive) EMISSIVE else TRANSLUCENT, SHADOW)
            }
        }
    }

    private fun detect(): Probe? {
        if (FabricLoader.getInstance().isModLoaded(IRIS_ID)) {
            return IrisProbe.create() ?: Blind("Iris", rawPrograms = true)
        }
        //? if = 1.8.9 {
        /*return OptiFineProbe.create()
        *///?} else {
        return null
        //?}
    }

    private fun materials(value: String?): Materials {
        if (value.isNullOrBlank()) {
            return Materials.SHADER
        }
        return Materials.entries.firstOrNull { it.name == value.trim().uppercase(Locale.ROOT) } ?: run {
            LOGGER.warn("Unknown {} value '{}'; effects keep their own shaders under shader packs where they can", MATERIALS_PROPERTY, value)
            Materials.SHADER
        }
    }

    private abstract class Probe(val mod: String, val rawPrograms: Boolean) {
        @Volatile
        private var broken = false

        open val assigns: Boolean = false

        protected abstract fun readPackInUse(): Boolean

        protected abstract fun readShadowPass(): Boolean

        open fun assign(pipeline: Any, program: String, shadowProgram: String) = Unit

        fun shaderPackInUse(): Boolean = guarded("shaderPackInUse") { readPackInUse() }

        fun shadowPass(): Boolean = guarded("shadowPass") { readShadowPass() }

        private inline fun guarded(query: String, read: () -> Boolean): Boolean {
            if (broken) {
                return true
            }
            return try {
                read()
            } catch (e: ReflectiveOperationException) {
                broken = true
                LOGGER.warn("{} query {} failed; nameplates stay vanilla while {} is loaded", mod, query, mod, e)
                true
            }
        }
    }

    private class Blind(mod: String, rawPrograms: Boolean) : Probe(mod, rawPrograms) {
        override fun readPackInUse(): Boolean = true

        override fun readShadowPass(): Boolean = true
    }

    private class IrisProbe(
        private val api: Any,
        private val inUse: Method,
        private val shadow: Method,
        private val assignMain: Method?,
        private val assignShadow: Method?,
    ) : Probe("Iris", rawPrograms = true) {
        override val assigns: Boolean = assignMain != null

        override fun readPackInUse(): Boolean = inUse.invoke(api) as Boolean

        override fun readShadowPass(): Boolean = shadow.invoke(api) as Boolean

        override fun assign(pipeline: Any, program: String, shadowProgram: String) {
            invoke(assignMain, pipeline, program)
            invoke(assignShadow, pipeline, shadowProgram)
        }

        private fun invoke(method: Method?, pipeline: Any, program: String) {
            if (method == null || !method.parameterTypes[0].isInstance(pipeline)) {
                return
            }
            val constant = method.parameterTypes[1].enumConstants?.firstOrNull { (it as Enum<*>).name == program } ?: return
            try {
                method.invoke(api, pipeline, constant)
            } catch (e: ReflectiveOperationException) {
                LOGGER.warn("Iris {} failed for nameplate pipeline {}", method.name, pipeline, e)
            }
        }

        companion object {
            private const val API = "net.irisshaders.iris.api.v0.IrisApi"

            fun create(): Probe? = try {
                val type = Class.forName(API)
                val api = type.getMethod("getInstance").invoke(null)
                val revision = type.getMethod("getMinorApiRevision").invoke(api)
                val assignMain = assignMethod(type, "assignPipeline")
                val assignShadow = assignMethod(type, "assignPipelineShadow")
                LOGGER.info(
                    "Iris API v0.{} found: nameplates skip the shadow pass, effects {} under shader packs, pipeline assignment {}",
                    revision,
                    effects(assignMain != null),
                    if (assignMain == null) "unavailable" else if (materials == Materials.PACK) "on" else "off",
                )
                IrisProbe(api, type.getMethod("isShaderPackInUse"), type.getMethod("isRenderingShadowPass"), assignMain, assignShadow)
            } catch (e: ReflectiveOperationException) {
                LOGGER.warn("Iris API not found; nameplates stay vanilla while Iris is loaded", e)
                null
            } catch (e: LinkageError) {
                LOGGER.warn("Iris API not found; nameplates stay vanilla while Iris is loaded", e)
                null
            }

            private fun effects(assigns: Boolean): String = when (materials) {
                Materials.CPU -> "use the CPU fallback"
                Materials.SHADER -> if (redirects()) "keep their own shaders" else "use the CPU fallback"
                Materials.PACK -> if (assigns) "draw with the pack's entity programs" else "use the CPU fallback"
            }

            private fun assignMethod(type: Class<*>, name: String): Method? =
                type.methods.firstOrNull { it.name == name && it.parameterCount == 2 && it.parameterTypes[1].isEnum }
        }
    }

    //? if = 1.8.9 {
    /*private class OptiFineProbe(private val loaded: Field, private val shadow: Field) : Probe("OptiFine", rawPrograms = false) {
        override fun readPackInUse(): Boolean = loaded.getBoolean(null)

        override fun readShadowPass(): Boolean = shadow.getBoolean(null)

        companion object {
            private val SHADERS = listOf("net.optifine.shaders.Shaders", "shadersmod.client.Shaders")

            fun create(): Probe? {
                val type = SHADERS.firstNotNullOfOrNull(::find) ?: return null
                return try {
                    val probe = OptiFineProbe(flag(type, "shaderPackLoaded"), flag(type, "isShadowPass"))
                    LOGGER.info("OptiFine shaders found ({}): nameplates skip the shadow pass, effects use the CPU fallback under shader packs", type.name)
                    probe
                } catch (e: ReflectiveOperationException) {
                    LOGGER.warn("OptiFine shaders not readable; nameplates stay vanilla while OptiFine is loaded", e)
                    Blind("OptiFine", rawPrograms = false)
                }
            }

            private fun find(name: String): Class<*>? = try {
                Class.forName(name, false, NameplateIris::class.java.classLoader)
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: LinkageError) {
                null
            }

            private fun flag(type: Class<*>, name: String): Field {
                val field = type.getDeclaredField(name)
                if (!Modifier.isStatic(field.modifiers) || field.type != Boolean::class.javaPrimitiveType) {
                    throw NoSuchFieldException("${type.name}.$name")
                }
                field.isAccessible = true
                return field
            }
        }
    }
    *///?}
}
