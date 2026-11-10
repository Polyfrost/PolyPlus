package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 26.3 {
import com.mojang.blaze3d.pipeline.PipelineCache
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.ShaderManager
import net.minecraft.client.renderer.oit.OitPipelineSet
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.mixin.client.nameplate.PipelineCacheAccessor
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object NameplateMaterialPipelines {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val entries = ConcurrentHashMap<String, Entry>()
    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mismatched: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var generation: Generation? = null
    private var generations = 0

    fun register(program: NameplateShaderProgram): NameplateShaderStatus {
        val entry = entries.computeIfAbsent(program.hash) {
            NameplateShaderSources.add(program)
            Entry(program)
        }
        if (entry.program !== program && entry.program.fragmentSource != program.fragmentSource && mismatched.add(program.hash)) {
            LOGGER.warn("Nameplate material {} was registered twice with different shader source; keeping the first", program.hash)
        }
        onRenderThread { compile(entry) }
        onRenderThread { NameplateIris.assign(entry.pipelines, program.emissive || program.blend == Material.Blend.ADDITIVE) }
        return entry.status
    }

    fun status(hash: String): NameplateShaderStatus? = entries[hash]?.status

    fun ready(hash: String): Entry? = entries[hash]?.takeIf { it.status == NameplateShaderStatus.READY }

    fun release(program: NameplateShaderProgram) {
        val entry = entries[program.hash]?.takeIf { it.program === program } ?: return
        entries.remove(program.hash)
        mismatched.remove(program.hash)
        entry.generation = RELEASED
        entry.status = NameplateShaderStatus.PENDING
        generation?.let { current ->
            val compiled = (current.cache as PipelineCacheAccessor).`polyplus$cache`()
            for (pipeline in entry.pipelines) {
                compiled.remove(pipeline)?.close()
            }
        }
        NameplateShaderSources.remove(program)
    }

    internal fun compileNow(pipeline: RenderPipeline): CompiledRenderPipeline? {
        val current = generation ?: return null
        current.acquire()
        try {
            return RenderSystem.getDevice().compilePipeline(pipeline, current.source, Util.backgroundExecutor()).join().finishCompile()
        } finally {
            current.release()
        }
    }

    internal fun parityPipeline(program: NameplateShaderProgram): RenderPipeline = shared(program, materialLayout(program))
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withLocation(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pipeline/nameplate_material_${program.hash}_parity"))
        .withShaderDefine(PARITY)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .build()

    @JvmStatic
    fun onShadersApplied(cache: PipelineCache) {
        NameplateMaterialAssets.onResourcesReloaded()
        generation?.retire()
        val includes = ShaderManager.listAllIncludes(Minecraft.getInstance().resourceManager)
        val next = Generation(++generations, cache, NameplateShaderSource(includes))
        generation = next
        for (entry in entries.values) {
            if (entry.status != NameplateShaderStatus.FAILED) {
                entry.status = NameplateShaderStatus.PENDING
            }
            compile(entry)
        }
    }

    private fun compile(entry: Entry) {
        val current = generation ?: return
        if (entry.status == NameplateShaderStatus.FAILED || entry.generation == current.id || entry.generation == RELEASED) {
            return
        }
        entry.generation = current.id
        current.acquire()
        val device = RenderSystem.getDevice()
        val pipelines = entry.pipelines
        val futures = pipelines.map { device.compilePipeline(it, current.source, Util.backgroundExecutor()) }
        CompletableFuture.allOf(*futures.toTypedArray()).whenCompleteAsync({ _, _ ->
            try {
                finish(entry, current, futures)
            } finally {
                current.release()
            }
        }, Minecraft.getInstance())
    }

    private fun finish(entry: Entry, owner: Generation, futures: List<CompletableFuture<CompiledRenderPipeline.Pending>>) {
        var error: Throwable? = null
        val compiled = futures.map { future ->
            try {
                future.join().finishCompile()
            } catch (failure: RuntimeException) {
                error = error ?: failure
                null
            }
        }
        if (owner !== generation || entry.generation != owner.id) {
            compiled.forEach { it?.close() }
            return
        }
        if (compiled.any { it == null }) {
            compiled.forEach { it?.close() }
            fail(entry, compiled, error)
            return
        }
        for ((i, pipeline) in entry.pipelines.withIndex()) {
            owner.cache.insert(pipeline, requireNotNull(compiled[i]))
        }
        entry.status = NameplateShaderStatus.READY
    }

    private fun fail(entry: Entry, compiled: List<CompiledRenderPipeline?>, error: Throwable?) {
        entry.status = NameplateShaderStatus.FAILED
        val hash = entry.program.hash
        if (!reported.add(hash)) {
            return
        }
        val device = RenderSystem.getDevice()
        val info = device.deviceInfo
        val failed = entry.pipelines.filterIndexed { i, _ -> compiled[i] == null }.map { it.location.toString() }
        val driver = mapOf(
            "name" to info.name(),
            "vendor" to info.vendorName(),
            "driver" to info.driverInfo(),
            "backend" to info.backendName(),
            "type" to info.type().toString(),
        )
        LOGGER.error(
            "Nameplate material {} failed to compile ({}) on {} / {} / {} ({}); the CPU fallback draws it",
            hash, failed, info.name(), info.vendorName(), info.driverInfo(), info.backendName(), error,
        )
        NameplateShaderFailures.report(NameplateShaderFailure(hash, failed, driver, device.lastDebugMessages.toList(), error))
    }

    private fun onRenderThread(block: () -> Unit) {
        if (RenderSystem.isOnRenderThread()) {
            block()
        } else {
            Minecraft.getInstance().execute { block() }
        }
    }

    class Entry internal constructor(val program: NameplateShaderProgram) {
        val pipeline: RenderPipeline
        val oit: OitPipelineSet
        val depthPipeline: RenderPipeline?
        val blocks: Int = (MAX_UNIFORM_BLOCK_BYTES / program.uniformBytes).coerceIn(1, MAX_BLOCKS)

        @Volatile
        var status: NameplateShaderStatus = NameplateShaderStatus.PENDING
            internal set

        internal var generation = 0

        internal val pipelines: List<RenderPipeline>

        init {
            val material = materialLayout(program)
            val location = "nameplate_material_${program.hash}"
            pipeline = shared(program, material)
                .withShaderDefine(BLOCKS, blocks)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pipeline/$location"))
                .withColorTargetState(ColorTargetState(blend(program.blend)))
                .withDepthStencilState(TRANSLUCENT_DEPTH)
                .build()
            depthPipeline = if (program.blend == Material.Blend.ALPHA) {
                shared(program, material)
                    .withShaderDefine(BLOCKS, blocks)
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withLocation(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pipeline/${location}_depth"))
                    .withShaderDefine(DEPTH_ONLY)
                    .withColorTargetState(DEPTH_ONLY_TARGET)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build()
            } else {
                null
            }
            val oitBase = shared(program, material).withShaderDefine(BLOCKS, blocks)
            if (program.blend == Material.Blend.ADDITIVE) {
                oitBase.withShaderDefine(OIT_ADDITIVE)
            }
            oit = OitPipelineSet.builder("${PolyPlusConstants.ID}_$location", oitBase).build()
            pipelines = listOfNotNull(pipeline, oit.depthBoundsPipeline(), oit.transmittancePipeline(), oit.accumulatePipeline(), depthPipeline)
        }
    }

    private const val OIT_ADDITIVE = "OIT_ADDITIVE"
    private const val PARITY = "NAMEPLATE_PARITY"
    private const val RELEASED = -1
    private const val DEPTH_ONLY = "NAMEPLATE_DEPTH_ONLY"
    private const val BLOCKS = "NAMEPLATE_BLOCKS"
    private const val MAX_UNIFORM_BLOCK_BYTES = 16384
    private const val MAX_BLOCKS = 127

    internal val UNEXTENDED_VERTEX_FORMAT = DefaultVertexFormat.PARTICLE

    private val TRANSLUCENT_DEPTH = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)
    private val DEPTH_ONLY_TARGET = ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE)

    private fun materialLayout(program: NameplateShaderProgram): BindGroupLayout {
        val builder = BindGroupLayout.builder().withUniform(NameplateShaderProgram.UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
        for (sampler in program.samplers) {
            builder.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER)
        }
        return builder.build()
    }

    private fun shared(program: NameplateShaderProgram, material: BindGroupLayout): RenderPipeline.Builder = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
        .withBindGroupLayout(material)
        .withVertexShader(program.vertexShader)
        .withFragmentShader(program.fragmentShader)
        .withVertexBinding(0, UNEXTENDED_VERTEX_FORMAT)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)

    private fun blend(blend: Material.Blend): BlendFunction = when (blend) {
        Material.Blend.ALPHA -> BlendFunction.TRANSLUCENT
        Material.Blend.ADDITIVE -> BlendFunction.LIGHTNING
    }

    private class Generation(val id: Int, val cache: PipelineCache, val source: NameplateShaderSource) {
        private val holds = AtomicInteger(1)

        fun acquire() {
            holds.incrementAndGet()
        }

        fun release() {
            if (holds.decrementAndGet() == 0) {
                source.close()
            }
        }

        fun retire() {
            release()
        }
    }
}
//?} elif = 26.2 {
/*import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

object NameplateMaterialPipelines {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val entries = ConcurrentHashMap<String, Entry>()
    private val parked = ConcurrentHashMap<String, Entry>()
    private val stages = ConcurrentHashMap<Stage, String>()
    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mismatched: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val queue = ArrayDeque<Entry>()
    private val source = ShaderSource { id, type -> shader(id, type) }

    private var generation = 1

    fun register(program: NameplateShaderProgram): NameplateShaderStatus {
        val entry = entries.computeIfAbsent(program.hash) { hash ->
            addSources(program)
            parked.remove(hash)?.takeIf { it.program.fragmentSource == program.fragmentSource }?.also { it.program = program }
                ?: Entry(program)
        }
        if (entry.program !== program && entry.program.fragmentSource != program.fragmentSource && mismatched.add(program.hash)) {
            LOGGER.warn("Nameplate material {} was registered twice with different shader source; keeping the first", program.hash)
        }
        onRenderThread { schedule(entry) }
        onRenderThread { NameplateIris.assign(entry.pipelines, program.emissive || program.blend == Material.Blend.ADDITIVE) }
        return entry.status
    }

    fun status(hash: String): NameplateShaderStatus? = entries[hash]?.status

    fun ready(hash: String): Entry? = entries[hash]?.takeIf { it.status == NameplateShaderStatus.READY }

    fun release(program: NameplateShaderProgram) {
        val entry = entries[program.hash]?.takeIf { it.program === program } ?: return
        entries.remove(program.hash)
        mismatched.remove(program.hash)
        if (entry.status == NameplateShaderStatus.READY && entry.generation == generation) {
            parked[program.hash] = entry
        } else {
            removeSources(program)
        }
    }

    @JvmStatic
    fun shader(id: Identifier, type: ShaderType): String? =
        if (id.namespace == PolyPlusConstants.ID) stages[Stage(id, type)] else null

    @JvmStatic
    fun onShadersApplied() {
        NameplateMaterialAssets.onResourcesReloaded()
        generation++
        for (entry in parked.values) {
            removeSources(entry.program)
        }
        parked.clear()
        queue.clear()
        for (entry in entries.values) {
            if (entry.status != NameplateShaderStatus.FAILED) {
                entry.status = NameplateShaderStatus.PENDING
            }
            entry.compiled = 0
            entry.queued = false
            schedule(entry)
        }
    }

    @JvmStatic
    fun compileNext() {
        var entry = queue.removeFirstOrNull()
        while (entry != null && !compilable(entry)) {
            entry.queued = false
            entry = queue.removeFirstOrNull()
        }
        if (entry != null) {
            entry.queued = false
            compileStep(entry)
        }
    }

    private fun compilable(entry: Entry): Boolean =
        entries[entry.program.hash] === entry && entry.status == NameplateShaderStatus.PENDING

    private fun schedule(entry: Entry) {
        if (entry.queued || !compilable(entry)) {
            return
        }
        if (entry.generation != generation) {
            entry.generation = generation
            entry.compiled = 0
        }
        entry.queued = true
        queue.addLast(entry)
    }

    private fun compileStep(entry: Entry) {
        val pipeline = entry.pipelines[entry.compiled]
        var error: RuntimeException? = null
        val valid = try {
            RenderSystem.getDevice().precompilePipeline(pipeline, source).isValid
        } catch (failure: RuntimeException) {
            error = failure
            false
        }
        if (!valid) {
            fail(entry, pipeline, error)
            return
        }
        entry.compiled++
        if (entry.compiled >= entry.pipelines.size) {
            entry.status = NameplateShaderStatus.READY
        } else {
            entry.queued = true
            queue.addFirst(entry)
        }
    }

    private fun fail(entry: Entry, pipeline: RenderPipeline, error: Throwable?) {
        entry.status = NameplateShaderStatus.FAILED
        val hash = entry.program.hash
        if (!reported.add(hash)) {
            return
        }
        val device = RenderSystem.getDevice()
        val info = device.deviceInfo
        val failed = listOf(pipeline.location.toString())
        val driver = mapOf(
            "name" to info.name(),
            "vendor" to info.vendorName(),
            "driver" to info.driverInfo(),
            "backend" to info.backendName(),
            "type" to info.type().toString(),
        )
        LOGGER.error(
            "Nameplate material {} failed to compile ({}) on {} / {} / {} ({}); the CPU fallback draws it",
            hash, failed, info.name(), info.vendorName(), info.driverInfo(), info.backendName(), error,
        )
        NameplateShaderFailures.report(NameplateShaderFailure(hash, failed, driver, device.lastDebugMessages.toList(), error))
    }

    private fun addSources(program: NameplateShaderProgram) {
        stages[Stage(program.vertexShader, ShaderType.VERTEX)] = program.vertexSource
        stages[Stage(program.fragmentShader, ShaderType.FRAGMENT)] = program.fragmentSource
    }

    private fun removeSources(program: NameplateShaderProgram) {
        if (entries[program.hash] == null) {
            stages.remove(Stage(program.fragmentShader, ShaderType.FRAGMENT))
        }
    }

    private fun onRenderThread(block: () -> Unit) {
        if (RenderSystem.isOnRenderThread()) {
            block()
        } else {
            Minecraft.getInstance().execute { block() }
        }
    }

    class Entry internal constructor(program: NameplateShaderProgram) {
        @Volatile
        var program: NameplateShaderProgram = program
            internal set

        val pipeline: RenderPipeline
        val depthPipeline: RenderPipeline?

        @Volatile
        var status: NameplateShaderStatus = NameplateShaderStatus.PENDING
            internal set

        internal var generation = 0
        internal var compiled = 0
        internal var queued = false

        internal val pipelines: List<RenderPipeline>

        init {
            val material = materialLayout(program)
            val location = "nameplate_material_${program.hash}"
            pipeline = shared(program, material)
                .withLocation(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pipeline/$location"))
                .withColorTargetState(ColorTargetState(blend(program.blend)))
                .withDepthStencilState(TRANSLUCENT_DEPTH)
                .build()
            depthPipeline = if (program.blend == Material.Blend.ALPHA) {
                shared(program, material)
                    .withLocation(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pipeline/${location}_depth"))
                    .withShaderDefine(DEPTH_ONLY)
                    .withColorTargetState(DEPTH_ONLY_TARGET)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .build()
            } else {
                null
            }
            pipelines = listOfNotNull(pipeline, depthPipeline)
        }
    }

    private const val DEPTH_ONLY = "NAMEPLATE_DEPTH_ONLY"

    private val UNEXTENDED_VERTEX_FORMAT = DefaultVertexFormat.PARTICLE

    private val TRANSLUCENT_DEPTH = DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)
    private val DEPTH_ONLY_TARGET = ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE)

    private fun materialLayout(program: NameplateShaderProgram): BindGroupLayout {
        val builder = BindGroupLayout.builder().withUniform(NameplateShaderProgram.UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
        for (sampler in program.samplers) {
            builder.withSampler(sampler)
        }
        return builder.build()
    }

    private fun shared(program: NameplateShaderProgram, material: BindGroupLayout): RenderPipeline.Builder = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
        .withBindGroupLayout(material)
        .withVertexShader(program.vertexShader)
        .withFragmentShader(program.fragmentShader)
        .withVertexBinding(0, UNEXTENDED_VERTEX_FORMAT)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)

    private fun blend(blend: Material.Blend): BlendFunction = when (blend) {
        Material.Blend.ALPHA -> BlendFunction.TRANSLUCENT
        Material.Blend.ADDITIVE -> BlendFunction.LIGHTNING
    }

    private data class Stage(val id: Identifier, val type: ShaderType)
}
*///?} elif >= 1.21.8 {
/*import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.render.NameplateLayer
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
//? if < 26.1 {
/^import com.mojang.blaze3d.platform.DepthTestFunction
^///?} elif < 26.2 {
/^import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.platform.DestFactor
import com.mojang.blaze3d.platform.SourceFactor
import java.util.Optional
^///?}
//? if >= 1.21.11 && < 26.2 {
/^import net.minecraft.client.renderer.rendertype.RenderSetup
import org.polyfrost.polyplus.mixin.client.nameplate.RenderTypeInvoker
^///?}

object NameplateMaterialPipelines {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val entries = ConcurrentHashMap<String, Entry>()
    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mismatched: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val queue = ArrayDeque<Entry>()

    private var shadersReady = false
    private var generation = 0

    fun register(program: NameplateShaderProgram): NameplateShaderStatus {
        val entry = entries.computeIfAbsent(program.hash) {
            NameplateShaderSources.add(program)
            Entry(program)
        }
        if (entry.program !== program && entry.program.fragmentSource != program.fragmentSource && mismatched.add(program.hash)) {
            LOGGER.warn("Nameplate material {} was registered twice with different shader source; keeping the first", program.hash)
        }
        onRenderThread { enqueue(entry) }
        onRenderThread { NameplateIris.assign(entry.pipelines, program.emissive || program.blend == Material.Blend.ADDITIVE) }
        return entry.status
    }

    fun status(hash: String): NameplateShaderStatus? = entries[hash]?.status

    fun ready(hash: String): Entry? = entries[hash]?.takeIf { it.status == NameplateShaderStatus.READY }

    fun release(program: NameplateShaderProgram) {
        val entry = entries[program.hash]?.takeIf { it.program === program } ?: return
        entries.remove(program.hash)
        mismatched.remove(program.hash)
        entry.generation = RELEASED
        entry.status = NameplateShaderStatus.PENDING
        queue.remove(entry)
        NameplateShaderSources.remove(program)
    }

    @JvmStatic
    fun onShadersApplied() {
        NameplateMaterialAssets.onResourcesReloaded()
        shadersReady = true
        generation++
        queue.clear()
        for (entry in entries.values) {
            if (entry.status != NameplateShaderStatus.FAILED) {
                entry.status = NameplateShaderStatus.PENDING
            }
            enqueue(entry)
        }
    }

    @JvmStatic
    fun compileNext() {
        if (!shadersReady) {
            return
        }
        while (true) {
            val entry = queue.peekFirst() ?: return
            if (entry.status == NameplateShaderStatus.FAILED || entry.generation != generation) {
                queue.pollFirst()
                continue
            }
            if (compileOne(entry)) {
                queue.pollFirst()
            }
            return
        }
    }

    internal fun renderType(entry: Entry, name: String, depth: Boolean, textures: List<Identifier>): NameplateLayer =
        createRenderType(if (depth) requireNotNull(entry.depthPipeline) else entry.pipeline, entry.program, name, textures)

    private fun enqueue(entry: Entry) {
        if (entry.status == NameplateShaderStatus.FAILED || entry.generation == RELEASED || entry.generation == generation) {
            return
        }
        entry.generation = generation
        entry.next = 0
        queue.addLast(entry)
    }

    private fun compileOne(entry: Entry): Boolean {
        val pipeline = entry.pipelines[entry.next]
        val compiled: CompiledRenderPipeline = try {
            RenderSystem.getDevice().precompilePipeline(pipeline)
        } catch (e: RuntimeException) {
            fail(entry, pipeline, e)
            return true
        }
        if (!compiled.isValid) {
            fail(entry, pipeline, null)
            return true
        }
        entry.next++
        if (entry.next < entry.pipelines.size) {
            return false
        }
        entry.status = NameplateShaderStatus.READY
        return true
    }

    private fun fail(entry: Entry, pipeline: RenderPipeline, error: Throwable?) {
        entry.status = NameplateShaderStatus.FAILED
        val hash = entry.program.hash
        if (!reported.add(hash)) {
            return
        }
        val device = RenderSystem.getDevice()
        val failed = listOf(pipeline.location.toString())
        val driver = mapOf(
            "name" to device.renderer,
            "vendor" to device.vendor,
            "driver" to device.version,
            "backend" to device.backendName,
        )
        LOGGER.error(
            "Nameplate material {} failed to compile ({}) on {} / {} / {} ({}); the CPU fallback draws it",
            hash, failed, device.renderer, device.vendor, device.version, device.backendName, error,
        )
        NameplateShaderFailures.report(NameplateShaderFailure(hash, failed, driver, device.lastDebugMessages.toList(), error))
    }

    private fun onRenderThread(block: () -> Unit) {
        if (RenderSystem.isOnRenderThread()) {
            block()
        } else {
            Minecraft.getInstance().execute { block() }
        }
    }

    class Entry internal constructor(val program: NameplateShaderProgram) {
        val pipeline: RenderPipeline
        val depthPipeline: RenderPipeline?

        @Volatile
        var status: NameplateShaderStatus = NameplateShaderStatus.PENDING
            internal set

        internal var generation = 0
        internal var next = 0

        internal val pipelines: List<RenderPipeline>

        init {
            val location = "nameplate_material_${program.hash}"
            pipeline = blended(base(program, location), blend(program.blend)).build()
            depthPipeline = if (program.blend == Material.Blend.ALPHA) {
                depthOnly(base(program, "${location}_depth").withShaderDefine(DEPTH_ONLY)).build()
            } else {
                null
            }
            pipelines = listOfNotNull(pipeline, depthPipeline)
        }
    }

    private const val DEPTH_ONLY = "NAMEPLATE_DEPTH_ONLY"
    private const val RELEASED = -1
    private const val LIGHTMAP = "Sampler2"
    private val BLOCKS = listOf("DynamicTransforms", "Projection", "Fog", "Globals", NameplateShaderProgram.UNIFORM_BLOCK)

    private fun base(program: NameplateShaderProgram, location: String): RenderPipeline.Builder {
        val builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "pipeline/$location"))
            .withVertexShader(program.vertexShader)
            .withFragmentShader(program.fragmentShader)
            .withSampler(LIGHTMAP)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, VertexFormat.Mode.QUADS)
        for (block in BLOCKS) {
            builder.withUniform(block, UniformType.UNIFORM_BUFFER)
        }
        for (sampler in program.samplers) {
            builder.withSampler(sampler)
        }
        return builder
    }

    private fun blend(blend: Material.Blend): BlendFunction = when (blend) {
        Material.Blend.ALPHA -> BlendFunction.TRANSLUCENT
        Material.Blend.ADDITIVE -> BlendFunction.LIGHTNING
    }
}
//? if < 26.1 {
/^private fun blended(builder: RenderPipeline.Builder, blend: BlendFunction): RenderPipeline.Builder = builder
    .withBlend(blend)
    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
    .withDepthWrite(false)

private fun depthOnly(builder: RenderPipeline.Builder): RenderPipeline.Builder = builder
    .withoutBlend()
    .withColorWrite(false, false)
    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
    .withDepthWrite(true)
^///?} elif < 26.2 {
/^private fun blended(builder: RenderPipeline.Builder, blend: BlendFunction): RenderPipeline.Builder = builder
    .withColorTargetState(ColorTargetState(blend))
    .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))

private fun depthOnly(builder: RenderPipeline.Builder): RenderPipeline.Builder = builder
    .withColorTargetState(ColorTargetState(Optional.of(BlendFunction(SourceFactor.ZERO, DestFactor.ONE)), ColorTargetState.WRITE_NONE))
    .withDepthStencilState(DepthStencilState.DEFAULT)
^///?}
//? if < 1.21.11 {
/^private fun createRenderType(pipeline: RenderPipeline, program: NameplateShaderProgram, name: String, textures: List<Identifier>): NameplateLayer =
    NameplateMaterialRenderType(name, pipeline, textures, program.samplers)
^///?} elif < 26.2 {
/^private fun createRenderType(pipeline: RenderPipeline, program: NameplateShaderProgram, name: String, textures: List<Identifier>): NameplateLayer {
    val setup = RenderSetup.builder(pipeline).useLightmap()
    for ((i, texture) in textures.withIndex()) {
        setup.withTexture(program.samplers[i], texture)
    }
    return RenderTypeInvoker.`polyplus$create`(name, setup.createRenderSetup())
}
^///?}
*///?} elif >= 1.21.1 {
/*import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.nameplate.render.NameplateLayer
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

object NameplateMaterialPipelines {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val entries = ConcurrentHashMap<String, Entry>()
    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mismatched: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val queue = ArrayDeque<Entry>()

    private var shadersReady = false
    private var generation = 0

    fun register(program: NameplateShaderProgram): NameplateShaderStatus {
        val entry = entries.computeIfAbsent(program.hash) { Entry(program) }
        if (entry.program !== program && entry.program.fragmentSource != program.fragmentSource && mismatched.add(program.hash)) {
            LOGGER.warn("Nameplate material {} was registered twice with different shader source; keeping the first", program.hash)
        }
        onRenderThread { enqueue(entry) }
        //? if = 1.21.5 {
        /^onRenderThread { NameplateIris.assign(entry.pipelines.map { it.pipeline }, program.emissive || program.blend == Material.Blend.ADDITIVE) }
        ^///?}
        return entry.status
    }

    fun status(hash: String): NameplateShaderStatus? = entries[hash]?.status

    fun ready(hash: String): Entry? = entries[hash]?.takeIf { it.status == NameplateShaderStatus.READY }

    fun release(program: NameplateShaderProgram) {
        val entry = entries[program.hash]?.takeIf { it.program === program } ?: return
        entries.remove(program.hash)
        mismatched.remove(program.hash)
        entry.generation = RELEASED
        entry.status = NameplateShaderStatus.PENDING
        queue.remove(entry)
        onRenderThread { entry.close() }
    }

    @JvmStatic
    fun onShadersApplied() {
        NameplateMaterialAssets.onResourcesReloaded()
        shadersReady = true
        generation++
        queue.clear()
        for (entry in entries.values) {
            entry.close()
            if (entry.status != NameplateShaderStatus.FAILED) {
                entry.status = NameplateShaderStatus.PENDING
            }
            enqueue(entry)
        }
    }

    @JvmStatic
    fun compileNext() {
        if (!shadersReady) {
            return
        }
        while (true) {
            val entry = queue.peekFirst() ?: return
            if (entry.status == NameplateShaderStatus.FAILED || entry.generation != generation) {
                queue.pollFirst()
                continue
            }
            if (compileOne(entry)) {
                queue.pollFirst()
            }
            return
        }
    }

    internal fun renderType(entry: Entry, name: String, depth: Boolean, textures: List<Identifier>): NameplateLayer =
        (if (depth) requireNotNull(entry.depthPipeline) else entry.pipeline).renderType(name, textures)

    private fun enqueue(entry: Entry) {
        if (entry.status == NameplateShaderStatus.FAILED || entry.generation == RELEASED || entry.generation == generation) {
            return
        }
        entry.generation = generation
        entry.next = 0
        queue.addLast(entry)
    }

    private fun compileOne(entry: Entry): Boolean {
        val variant = entry.pipelines[entry.next]
        val error = try {
            variant.compile()
        } catch (e: RuntimeException) {
            e
        }
        if (error != null) {
            entry.close()
            fail(entry, variant, error)
            return true
        }
        entry.next++
        if (entry.next < entry.pipelines.size) {
            return false
        }
        entry.status = NameplateShaderStatus.READY
        return true
    }

    private fun fail(entry: Entry, variant: NameplateFlatVariant, error: Throwable) {
        entry.status = NameplateShaderStatus.FAILED
        val hash = entry.program.hash
        if (!reported.add(hash)) {
            return
        }
        val driver = NameplateFlatVariant.driver()
        LOGGER.error(
            "Nameplate material {} failed to compile ({}) on {} / {} / {} ({}); the CPU fallback draws it",
            hash, listOf(variant.label), driver["name"], driver["vendor"], driver["driver"], driver["backend"], error,
        )
        NameplateShaderFailures.report(NameplateShaderFailure(hash, listOf(variant.label), driver, NameplateFlatVariant.debugMessages(), error))
    }

    private fun onRenderThread(block: () -> Unit) {
        if (RenderSystem.isOnRenderThread()) {
            block()
        } else {
            Minecraft.getInstance().execute { block() }
        }
    }

    class Entry internal constructor(val program: NameplateShaderProgram) {
        internal val pipeline = NameplateFlatVariant(program, false)
        internal val depthPipeline = if (program.blend == Material.Blend.ALPHA) NameplateFlatVariant(program, true) else null

        @Volatile
        var status: NameplateShaderStatus = NameplateShaderStatus.PENDING
            internal set

        internal var generation = 0
        internal var next = 0

        internal val pipelines: List<NameplateFlatVariant> = listOfNotNull(pipeline, depthPipeline)

        internal fun close() {
            pipelines.forEach(NameplateFlatVariant::close)
        }
    }

    private const val RELEASED = -1
}
*///?}
