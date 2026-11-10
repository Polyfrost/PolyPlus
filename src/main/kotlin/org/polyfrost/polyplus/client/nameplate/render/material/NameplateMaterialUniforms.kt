package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import net.minecraft.client.renderer.DynamicGpuDataStorage
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialUniforms
import java.nio.ByteBuffer

object NameplateMaterialUniforms {
    private const val INITIAL_CAPACITY = 16

    private val storages = HashMap<Int, DynamicGpuDataStorageMapped<Binding>>()
    private val bindings = HashMap<String, Binding>()
    private val assigned = ArrayList<Binding>()
    private val staged = ArrayList<Binding>()
    private val pending = ArrayList<Binding>()
    internal var batching = false
        private set

    class Binding internal constructor(private val blocks: Int, private val blockWords: Int) : DynamicGpuDataStorage.DynamicGpuData {
        internal var slice: GpuBufferSlice? = null
        internal var assigned = false
        internal var staged = false
        private val words = arrayOfNulls<IntArray>(blocks)
        private val clocks = IntArray(blocks)
        private var filled = 0

        internal val bytes: Int
            get() = blocks * blockWords * Integer.BYTES

        internal fun put(index: Int, values: IntArray, frameIndex: Int) {
            words[index] = values
            clocks[index] = frameIndex
            filled = maxOf(filled, index + 1)
        }

        internal fun reset() {
            slice = null
            assigned = false
            staged = false
            words.fill(null)
            filled = 0
        }

        override fun write(byteBuffer: ByteBuffer) {
            val start = byteBuffer.position()
            for (b in 0 until filled) {
                val values = words[b]
                byteBuffer.position(start + b * blockWords * Integer.BYTES)
                for (i in 0 until blockWords) {
                    byteBuffer.putInt(
                        when {
                            i == MaterialUniforms.CLOCK -> clocks[b]
                            values != null && i < values.size -> values[i]
                            else -> 0
                        },
                    )
                }
            }
        }
    }

    internal fun binding(renderType: String, blocks: Int, blockBytes: Int): Binding =
        bindings.getOrPut(renderType) { Binding(blocks, blockBytes / Integer.BYTES) }

    internal fun share(renderType: String, binding: Binding) {
        bindings[renderType] = binding
    }

    internal fun stage(binding: Binding, index: Int, words: IntArray, frameIndex: Int) {
        binding.put(index, words, frameIndex)
        if (!binding.assigned) {
            binding.assigned = true
            assigned += binding
        }
        if (!batching) {
            binding.slice = storage(binding.bytes).writeData(binding)
        } else if (!binding.staged) {
            binding.staged = true
            staged += binding
        }
    }

    @JvmStatic
    fun beginBatch() {
        batching = true
    }

    @JvmStatic
    fun flushBatch() {
        batching = false
        if (staged.isEmpty()) {
            return
        }
        staged.sortBy { it.bytes }
        var start = 0
        while (start < staged.size) {
            val bytes = staged[start].bytes
            var end = start + 1
            while (end < staged.size && staged[end].bytes == bytes) {
                end++
            }
            pending.clear()
            for (i in start until end) {
                pending += staged[i]
            }
            val slices = storage(bytes).writeData(pending.toTypedArray())
            for (i in slices.indices) {
                pending[i].slice = slices[i]
                pending[i].staged = false
            }
            start = end
        }
        pending.clear()
        staged.clear()
    }

    internal fun release(prefix: String) {
        bindings.keys.removeIf { it.startsWith(prefix) }
    }

    @JvmStatic
    fun bind(renderType: String, pass: RenderPass) {
        val slice = bindings[renderType]?.slice ?: return
        pass.setUniform(NameplateShaderProgram.UNIFORM_BLOCK, slice)
    }

    internal fun endFrame() {
        for (storage in storages.values) {
            storage.endFrame()
        }
        for (binding in assigned) {
            binding.reset()
        }
        assigned.clear()
        staged.clear()
        pending.clear()
        batching = false
    }

    private fun storage(bytes: Int): DynamicGpuDataStorageMapped<Binding> = storages.getOrPut(bytes) {
        DynamicGpuDataStorageMapped("PolyPlus nameplate material UBO", bytes, GpuBuffer.USAGE_UNIFORM, INITIAL_CAPACITY)
    }
}
//?} elif >= 1.21.8 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import net.minecraft.client.renderer.DynamicUniformStorage
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialUniforms
import java.nio.ByteBuffer

object NameplateMaterialUniforms {
    private const val INITIAL_CAPACITY = 16
    //? if = 26.2 {
    /^private const val PREFIX = "${PolyPlusConstants.ID}:nameplate_material/"
    ^///?}

    private val storages = HashMap<Int, DynamicUniformStorage<Block>>()
    private val bindings = HashMap<String, Binding>()
    private val assigned = ArrayList<Binding>()

    class Binding internal constructor() {
        internal var slice: GpuBufferSlice? = null
    }

    internal fun binding(renderType: String): Binding = bindings.getOrPut(renderType) { Binding() }

    internal fun share(renderType: String, binding: Binding) {
        bindings[renderType] = binding
    }

    internal fun write(binding: Binding, bytes: Int, words: IntArray, frameIndex: Int) {
        val storage = storages.getOrPut(bytes) {
            DynamicUniformStorage("PolyPlus nameplate material UBO", bytes, INITIAL_CAPACITY)
        }
        binding.slice = storage.writeUniform(Block(words, bytes / Integer.BYTES, frameIndex))
        assigned += binding
    }

    internal fun release(prefix: String) {
        bindings.keys.removeIf { it.startsWith(prefix) }
    }

    //? if = 26.2 {
    /^@JvmStatic
    fun owns(renderType: String): Boolean = renderType.startsWith(PREFIX)

    @JvmStatic
    fun bind(renderType: String?, pass: RenderPass) {
        val slice = renderType?.let { bindings[it] }?.slice ?: return
        pass.setUniform(NameplateShaderProgram.UNIFORM_BLOCK, slice)
    }
    ^///?} elif < 26.2 {
    /^@JvmStatic
    fun bind(renderType: String, pass: RenderPass) {
        val slice = bindings[renderType]?.slice ?: return
        pass.setUniform(NameplateShaderProgram.UNIFORM_BLOCK, slice)
    }
    ^///?}

    internal fun endFrame() {
        for (storage in storages.values) {
            storage.endFrame()
        }
        for (binding in assigned) {
            binding.slice = null
        }
        assigned.clear()
    }

    private class Block(private val words: IntArray, private val count: Int, private val frameIndex: Int) : DynamicUniformStorage.DynamicUniform {
        override fun write(byteBuffer: ByteBuffer) {
            for (i in 0 until count) {
                byteBuffer.putInt(
                    when {
                        i == MaterialUniforms.CLOCK -> frameIndex
                        i < words.size -> words[i]
                        else -> 0
                    },
                )
            }
        }
    }
}
*///?} elif >= 1.21.1 {
/*import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialUniforms

object NameplateMaterialUniforms {
    private val bindings = HashMap<String, Binding>()
    private val assigned = ArrayList<Binding>()

    class Binding internal constructor() {
        internal var words = IntArray(0)
        internal var written = false
    }

    internal fun binding(renderType: String): Binding = bindings.getOrPut(renderType) { Binding() }

    internal fun share(renderType: String, binding: Binding) {
        bindings[renderType] = binding
    }

    internal fun write(binding: Binding, bytes: Int, words: IntArray, frameIndex: Int) {
        val count = bytes / Integer.BYTES
        if (binding.words.size != count) {
            binding.words = IntArray(count)
        }
        for (i in 0 until count) {
            binding.words[i] = when {
                i == MaterialUniforms.CLOCK -> frameIndex
                i < words.size -> words[i]
                else -> 0
            }
        }
        if (!binding.written) {
            binding.written = true
            assigned += binding
        }
    }

    internal fun release(prefix: String) {
        bindings.keys.removeIf { it.startsWith(prefix) }
    }

    internal fun words(renderType: String): IntArray? = bindings[renderType]?.takeIf { it.written }?.words

    internal fun endFrame() {
        for (binding in assigned) {
            binding.written = false
        }
        assigned.clear()
    }
}
*///?}
