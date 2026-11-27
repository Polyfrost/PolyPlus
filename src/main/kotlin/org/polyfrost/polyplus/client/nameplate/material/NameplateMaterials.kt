package org.polyfrost.polyplus.client.nameplate.material

import org.apache.logging.log4j.LogManager
import org.polyfrost.plusrenderer.NameplateAsset
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.libs.sentry.SentryLevel

class NameplatePrograms internal constructor(
    val shaders: Array<NameplateShader?>,
    val programs: Array<Any?>,
    val textures: Array<Any?>,
    val gpu: Boolean,
) {
    fun shader(material: Int): NameplateShader? = shaders.getOrNull(material)

    fun program(material: Int): Any? = programs.getOrNull(material)

    fun texture(index: Int): Any? = textures.getOrNull(index)

    companion object {
        val NONE: NameplatePrograms = NameplatePrograms(emptyArray(), emptyArray(), emptyArray(), false)
    }
}

object NameplateMaterials {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private const val NO_DIALECT = -1

    private class Shared(val program: Any) {
        var refs = 0
    }

    @JvmStatic
    val backend: NameplateMaterialBackend? = try {
        NameplateMaterialBackend
    } catch (e: LinkageError) {
        LOGGER.error("Nameplate effects can't use the GPU on this game version", e)
        null
    }

    @JvmStatic
    val dialect: Int = backend?.dialect ?: NO_DIALECT

    @JvmStatic
    val packTarget: NameplatePackTarget? = backend

    private val shared = HashMap<String, Shared>()
    private val failed = HashSet<String>()
    private val reported = HashSet<String>()

    fun shaders(asset: NameplateAsset): NameplateShaderSet? {
        if (dialect == NO_DIALECT) return null
        return NameplateShaderSet.parse(dialect, asset.shadersJson(dialect))
    }

    fun install(name: String, asset: NameplateAsset, set: NameplateShaderSet?): NameplatePrograms {
        val backend = backend ?: return NameplatePrograms.NONE
        if (set == null || set.materialCount == 0) return NameplatePrograms(emptyArray(), emptyArray(), emptyArray(), set != null)
        val shaders = arrayOfNulls<NameplateShader>(set.materialCount)
        val programs = arrayOfNulls<Any>(set.materialCount)
        var gpu = true
        for (shader in set.shaders) {
            shaders[shader.material] = shader
            if (!shader.supported) {
                gpu = false
                continue
            }
            val program = acquire(backend, name, shader)
            if (program == null) gpu = false
            programs[shader.material] = program
        }
        val textures = arrayOfNulls<Any>(if (gpu) textureCount(asset) else 0)
        if (gpu && !upload(backend, name, asset, textures)) gpu = false
        val installed = NameplatePrograms(shaders, programs, textures, gpu)
        if (!gpu) release(installed)
        return if (gpu) installed else NameplatePrograms(shaders, arrayOfNulls(0), arrayOfNulls(0), false)
    }

    fun release(installed: NameplatePrograms) {
        val backend = backend ?: return
        for (i in installed.programs.indices) {
            val program = installed.programs[i] ?: continue
            installed.programs[i] = null
            val key = installed.shaders[i]?.key ?: continue
            val entry = shared[key] ?: continue
            if (--entry.refs > 0) continue
            shared.remove(key)
            safely("release a nameplate effect program") { backend.release(entry.program) }
        }
        for (i in installed.textures.indices) {
            val texture = installed.textures[i] ?: continue
            installed.textures[i] = null
            safely("release a nameplate effect texture") { backend.releaseTexture(texture) }
        }
    }

    fun reload() {
        failed.clear()
        val backend = backend ?: return
        if (shared.isNotEmpty()) {
            for (entry in shared.values) safely("release a nameplate effect program") { backend.release(entry.program) }
            shared.clear()
        }
        safely("reload nameplate effects") { backend.reload() }
    }

    private fun acquire(backend: NameplateMaterialBackend, name: String, shader: NameplateShader): Any? {
        shared[shader.key]?.let {
            it.refs++
            return it.program
        }
        if (shader.key in failed) return null
        val program = try {
            backend.compile(shader)
        } catch (e: Exception) {
            failed += shader.key
            compileFailed(backend, name, shader, e)
            return null
        } catch (e: LinkageError) {
            failed += shader.key
            compileFailed(backend, name, shader, e)
            return null
        }
        shared[shader.key] = Shared(program).also { it.refs = 1 }
        return program
    }

    private fun textureCount(asset: NameplateAsset): Int = try {
        asset.textureCount().coerceAtLeast(0)
    } catch (e: RuntimeException) {
        0
    }

    private fun upload(backend: NameplateMaterialBackend, name: String, asset: NameplateAsset, out: Array<Any?>): Boolean {
        val size = IntArray(2)
        for (i in out.indices) {
            try {
                val bytes = asset.texture(i, size, null)
                if (bytes <= 0 || size[0] <= 0 || size[1] <= 0) continue
                val rgba = ByteArray(bytes)
                asset.texture(i, size, rgba)
                out[i] = backend.texture("$name/$i", size[0], size[1], rgba)
            } catch (e: RuntimeException) {
                LOGGER.warn("Nameplate {} texture {} could not be uploaded; its effects draw on the CPU", name, i, e)
                return false
            }
        }
        return true
    }

    private fun compileFailed(backend: NameplateMaterialBackend, name: String, shader: NameplateShader, e: Throwable) {
        if (!reported.add(shader.key)) return
        LOGGER.warn("Nameplate {} effect {} did not compile; it draws on the CPU instead", name, shader.material, e)
        val vendor = runCatching { backend.vendor() }.getOrDefault("unknown")
        val api = runCatching { backend.api() }.getOrDefault("unknown")
        try {
            PolyPlusSentry.activeHub()?.captureMessage(
                "Nameplate effect shader did not compile: ${e.javaClass.name}: ${e.message}",
                SentryLevel.WARNING,
            ) { scope ->
                scope.setTag("gpu_vendor", vendor)
                scope.setTag("gpu_backend", api)
                scope.setTag("nameplate_dialect", dialect.toString())
            }
        } catch (ignored: RuntimeException) {
            LOGGER.debug("Could not report to Sentry", ignored)
        }
    }

    private inline fun safely(what: String, action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            LOGGER.warn("Could not {}", what, e)
        }
    }
}
