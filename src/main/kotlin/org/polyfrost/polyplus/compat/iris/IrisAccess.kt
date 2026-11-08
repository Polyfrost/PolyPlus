package org.polyfrost.polyplus.compat.iris

import com.mojang.blaze3d.systems.RenderSystem
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder
import net.irisshaders.iris.pipeline.IrisRenderingPipeline
import net.irisshaders.iris.pipeline.PipelineManager
import net.irisshaders.iris.pipeline.WorldRenderingPipeline
import net.irisshaders.iris.pipeline.programs.ShaderKey
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId
import org.polyfrost.polyplus.compat.Reflect
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

object IrisAccess {
    private val pipelinesField: Field by lazy { Reflect.declared(PipelineManager::class.java, "pipelinesPerDimension") }
    private val pipelineField: Field by lazy { Reflect.declared(PipelineManager::class.java, "pipeline") }
    private val buffersField: Field by lazy { Reflect.declared(IrisRenderingPipeline::class.java, "shaderStorageBufferHolder") }
    private val activeField: Field by lazy {
        Reflect.declared(ShaderStorageBufferHolder::class.java, "ACTIVE_BUFFERS").also { check(Modifier.isStatic(it.modifiers)) { "ACTIVE_BUFFERS is not static" } }
    }
    private val overridesField: Field? by lazy {
        RenderSystem::class.java.declaredFields
            .firstOrNull { Modifier.isStatic(it.modifiers) && it.name.endsWith("overrides") && Map::class.java.isAssignableFrom(it.type) }
            ?.also { it.isAccessible = true }
    }

    fun resolvePipelineSlot() {
        pipelinesField
        pipelineField
    }

    fun resolveBufferGuard() {
        buffersField
        activeBuffers().size
    }

    @Suppress("UNCHECKED_CAST")
    fun pipelines(manager: PipelineManager): MutableMap<NamespacedId, WorldRenderingPipeline> =
        pipelinesField.get(manager) as MutableMap<NamespacedId, WorldRenderingPipeline>

    fun pipeline(manager: PipelineManager): WorldRenderingPipeline? = pipelineField.get(manager) as WorldRenderingPipeline?

    fun setPipeline(manager: PipelineManager, pipeline: WorldRenderingPipeline?) {
        pipelineField.set(manager, pipeline)
    }

    fun storageBuffers(pipeline: IrisRenderingPipeline): ShaderStorageBufferHolder? =
        buffersField.get(pipeline) as ShaderStorageBufferHolder?

    @Suppress("UNCHECKED_CAST")
    fun activeBuffers(): MutableList<ShaderStorageBuffer> = activeField.get(null) as MutableList<ShaderStorageBuffer>

    fun evictOverrides(pipeline: IrisRenderingPipeline): Int {
        val overrides = overridesField?.get(null) as? Map<*, *> ?: return 0
        val shaders = pipeline.shaderMap
        val programs: MutableSet<Any?> = Collections.newSetFromMap(IdentityHashMap())
        ShaderKey.values().forEach { programs.add(shaders.getShader(it)) }
        programs.remove(null)
        var evicted = 0
        for (byProgram in overrides.values) {
            val entries = (byProgram as? MutableMap<*, *>)?.entries?.iterator() ?: continue
            while (entries.hasNext()) {
                val entry = entries.next()
                if (entry.key !in programs) continue
                (entry.value as? Map<*, *>)?.values?.forEach { closeVertexArray(it) }
                entries.remove()
                evicted++
            }
        }
        return evicted
    }

    private fun closeVertexArray(compiled: Any?) {
        val backend = compiled?.javaClass?.getMethod("backendRenderPipeline")?.invoke(compiled) ?: return
        (backend.javaClass.getMethod("vertexArray").invoke(backend) as? AutoCloseable)?.close()
    }
}
