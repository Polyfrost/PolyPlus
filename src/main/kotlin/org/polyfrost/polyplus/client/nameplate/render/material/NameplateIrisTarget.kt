package org.polyfrost.polyplus.client.nameplate.render.material

//? if >= 1.21.5 {
//? if >= 26.3 {
import com.mojang.renderpearl.backend.opengl.GlProgram
import com.mojang.renderpearl.backend.opengl.GlStateManager
import com.mojang.renderpearl.backend.opengl.GlTexture
//?} else {
/*import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.pipeline.RenderPipeline
*///?}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL33C
import org.polyfrost.polyplus.PolyPlusConstants
import java.lang.reflect.Field
import java.lang.reflect.Method

object NameplateIrisTarget {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val PATH = "pipeline/nameplate_material_"
    private const val PREFIX = "${PolyPlusConstants.ID}:$PATH"
    private const val PARITY = "_parity"
    private const val MAX_TARGETS = 4

    @JvmField
    val enabled: Boolean = FabricLoader.getInstance().isModLoaded("iris")

    private val access: Access? by lazy { Access.create() }
    private val targets = ArrayList<Target>(MAX_TARGETS)

    @Volatile
    private var broken = false

    fun ready(): Boolean = enabled && !broken && access != null

    //? if >= 26.3 {
    @JvmStatic
    fun beforeBind(program: GlProgram) {
        if (owns(program.debugLabel)) {
            unlockBlend()
        }
    }

    @JvmStatic
    fun onBind(program: GlProgram) {
        if (owns(program.debugLabel)) {
            redirect()
        }
    }

    private fun owns(label: String): Boolean = !broken && label.startsWith(PREFIX) && !label.endsWith(PARITY)
    //?} else {
    /*@JvmStatic
    fun beforeApply(pipeline: RenderPipeline) {
        if (owns(pipeline)) {
            unlockBlend()
        }
    }

    @JvmStatic
    fun onSetup(pipeline: RenderPipeline?) {
        if (pipeline != null && owns(pipeline)) {
            redirect()
        }
    }

    private fun owns(pipeline: RenderPipeline): Boolean {
        if (broken) {
            return false
        }
        val location = pipeline.location
        val path = location.path
        return location.namespace == PolyPlusConstants.ID && path.startsWith(PATH) && !path.endsWith(PARITY)
    }
    *///?}

    //? if >= 26.2 {
    private fun mainDepth(): GlTexture? = Minecraft.getInstance().gameRenderer.mainRenderTarget().depthTexture as? GlTexture
    //?} else {
    /*private fun mainDepth(): GlTexture? = Minecraft.getInstance().mainRenderTarget.depthTexture as? GlTexture
    *///?}

    private fun unlockBlend() {
        val api = access ?: return
        try {
            api.unlockBlend()
        } catch (e: ReflectiveOperationException) {
            fail(e)
        } catch (e: RuntimeException) {
            fail(e)
        }
    }

    private fun redirect(): Boolean {
        val api = access ?: return false
        try {
            val framebuffer = api.framebuffer() ?: return false
            val color = api.color(framebuffer)
            val depth = mainDepth()
            if (color <= 0 || depth == null) {
                return false
            }
            val target = targets.firstOrNull { it.matches(framebuffer, color, depth) } ?: create(framebuffer, color, depth) ?: return false
            GlStateManager._glBindFramebuffer(GL33C.GL_FRAMEBUFFER, target.fbo)
            return true
        } catch (e: ReflectiveOperationException) {
            fail(e)
        } catch (e: RuntimeException) {
            fail(e)
        }
        return false
    }

    private fun create(framebuffer: Any, color: Int, depth: GlTexture): Target? {
        if (targets.size >= MAX_TARGETS) {
            GlStateManager._glDeleteFramebuffers(targets.removeAt(0).fbo)
        }
        val fbo = GlStateManager.glGenFramebuffers()
        GlStateManager._glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo)
        GlStateManager._glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, color, 0)
        GlStateManager._glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL33C.GL_TEXTURE_2D, depth.glId(), 0)
        GL33C.glDrawBuffers(GL33C.GL_COLOR_ATTACHMENT0)
        val status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER)
        if (status != GL33C.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._glDeleteFramebuffers(fbo)
            fail(IllegalStateException("framebuffer status 0x${Integer.toHexString(status)}"))
            return null
        }
        return Target(framebuffer, color, depth, fbo).also { targets += it }
    }

    private fun fail(error: Exception) {
        broken = true
        LOGGER.warn("Nameplate effects can't draw into the shader pack's targets; they use the CPU fallback under shader packs", error)
    }

    private class Target(val framebuffer: Any, val color: Int, val depth: GlTexture, val fbo: Int) {
        fun matches(framebuffer: Any, color: Int, depth: GlTexture): Boolean =
            this.framebuffer === framebuffer && this.color == color && this.depth === depth
    }

    private class Access(
        private val manager: Method,
        private val current: Method,
        private val rendering: Class<*>,
        private val overrides: Method,
        private val before: Field,
        private val shaderMap: Method,
        private val shader: Method,
        private val text: Any,
        private val attachment: Method,
        private val restoreBlend: Method,
    ) {
        private val writers = HashMap<Class<*>, Pair<Field, Field>?>()

        fun framebuffer(): Any? {
            val pipeline = current.invoke(manager.invoke(null) ?: return null) ?: return null
            if (!rendering.isInstance(pipeline) || overrides.invoke(pipeline) != true) {
                return null
            }
            val program = shader.invoke(shaderMap.invoke(pipeline), text) ?: return null
            val (beforeTranslucent, afterTranslucent) = writers.getOrPut(program.javaClass) { writers(program.javaClass) } ?: return null
            return (if (before.getBoolean(pipeline)) beforeTranslucent else afterTranslucent).get(program)
        }

        fun color(framebuffer: Any): Int = attachment.invoke(framebuffer, 0) as Int

        fun unlockBlend() {
            restoreBlend.invoke(null)
        }

        private fun writers(type: Class<*>): Pair<Field, Field>? {
            val before = field(type, BEFORE) ?: return null
            val after = field(type, AFTER) ?: return null
            return before to after
        }

        private fun field(type: Class<*>, name: String): Field? {
            var current: Class<*>? = type
            while (current != null) {
                current.declaredFields.firstOrNull { it.name == name }?.let {
                    it.isAccessible = true
                    return it
                }
                current = current.superclass
            }
            return null
        }

        companion object {
            private const val BEFORE = "writingToBeforeTranslucent"
            private const val AFTER = "writingToAfterTranslucent"

            fun create(): Access? {
                if (!enabled) {
                    return null
                }
                return try {
                    val iris = load("net.irisshaders.iris.Iris")
                    val manager = iris.getMethod("getPipelineManager")
                    val current = load("net.irisshaders.iris.pipeline.PipelineManager").getMethod("getPipelineNullable")
                    val rendering = load("net.irisshaders.iris.pipeline.IrisRenderingPipeline")
                    val keys = load("net.irisshaders.iris.pipeline.programs.ShaderKey")
                    val text = keys.enumConstants.first { (it as Enum<*>).name == "TEXT" }
                    Access(
                        manager,
                        current,
                        rendering,
                        rendering.getMethod("shouldOverrideShaders"),
                        rendering.getField("isBeforeTranslucent"),
                        rendering.getMethod("getShaderMap"),
                        load("net.irisshaders.iris.pipeline.programs.ShaderMap").getMethod("getShader", keys),
                        text,
                        load("net.irisshaders.iris.gl.framebuffer.GlFramebuffer").getMethod("getColorAttachment", Int::class.javaPrimitiveType),
                        load("net.irisshaders.iris.gl.blending.BlendModeStorage").getMethod("restoreBlend"),
                    )
                } catch (e: ReflectiveOperationException) {
                    LOGGER.warn("Iris internals not found; nameplate effects use the CPU fallback under shader packs", e)
                    null
                } catch (e: NoSuchElementException) {
                    LOGGER.warn("Iris internals not found; nameplate effects use the CPU fallback under shader packs", e)
                    null
                } catch (e: LinkageError) {
                    LOGGER.warn("Iris internals not found; nameplate effects use the CPU fallback under shader packs", e)
                    null
                }
            }

            private fun load(name: String): Class<*> = Class.forName(name, false, NameplateIrisTarget::class.java.classLoader)
        }
    }
}
//?}
//? if >= 1.21.1 && < 1.21.5 {
/*import com.mojang.blaze3d.platform.GlStateManager
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL30C
//? if = 1.21.4 {
/^import java.lang.invoke.MethodHandle
^///?}
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

object NameplateIrisTarget {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val SHADER_PREFIX = "polyplus_nameplate/material_"
    private const val MAX_TARGETS = 4

    @JvmField
    val enabled: Boolean = FabricLoader.getInstance().isModLoaded("iris")

    private val access: Access? by lazy { Access.create() }
    private val targets = ArrayList<Target>(MAX_TARGETS)
    private val programs: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())
    private var redirected = false

    @Volatile
    private var broken = false

    fun ready(): Boolean = enabled && !broken && access != null

    @JvmStatic
    fun track(program: Any, vertex: Any) {
        if (vertex == NameplateShaderProgram.VERTEX) {
            programs.add(program)
            //? if = 1.21.4 {
            /^setIrisSkip(program)
            ^///?}
        }
    }

    //? if = 1.21.4 {
    /^private fun setIrisSkip(program: Any) {
        try {
            val loader = NameplateIrisTarget::class.java.classLoader
            val always = Class.forName("net.irisshaders.iris.compat.SkipList", true, loader).getField("ALWAYS").get(null)
            Class.forName("net.irisshaders.iris.mixinterface.ShaderInstanceInterface", false, loader)
                .getMethod("setShouldSkip", MethodHandle::class.java)
                .invoke(program, always)
        } catch (e: ReflectiveOperationException) {
            fail(e)
        } catch (e: RuntimeException) {
            fail(e)
        }
    }
    ^///?}

    @JvmStatic
    fun owns(program: Any): Boolean = !broken && program in programs

    @JvmStatic
    fun ownsName(name: String): Boolean = !broken && name.startsWith(SHADER_PREFIX)

    @JvmStatic
    fun beforeDraw() {
        val api = access ?: return
        try {
            val framebuffer = api.framebuffer() ?: return
            val color = api.color(framebuffer)
            val depth = Minecraft.getInstance().mainRenderTarget.depthTextureId
            if (color <= 0 || depth <= 0) {
                return
            }
            val target = targets.firstOrNull { it.matches(framebuffer, color, depth) } ?: create(framebuffer, color, depth) ?: return
            api.unlock()
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, target.fbo)
            redirected = true
        } catch (e: ReflectiveOperationException) {
            fail(e)
        } catch (e: RuntimeException) {
            fail(e)
        }
    }

    @JvmStatic
    fun afterDraw() {
        if (!redirected) {
            return
        }
        redirected = false
        Minecraft.getInstance().mainRenderTarget.bindWrite(false)
    }

    private fun create(framebuffer: Any, color: Int, depth: Int): Target? {
        if (targets.size >= MAX_TARGETS) {
            GlStateManager._glDeleteFramebuffers(targets.removeAt(0).fbo)
        }
        val fbo = GlStateManager.glGenFramebuffers()
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo)
        GlStateManager._glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_TEXTURE_2D, color, 0)
        GlStateManager._glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, depth, 0)
        GL30C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0)
        val status = GlStateManager.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER)
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._glDeleteFramebuffers(fbo)
            Minecraft.getInstance().mainRenderTarget.bindWrite(false)
            fail(IllegalStateException("framebuffer status 0x${Integer.toHexString(status)}"))
            return null
        }
        return Target(framebuffer, color, depth, fbo).also { targets += it }
    }

    private fun fail(error: Exception) {
        broken = true
        LOGGER.warn("Nameplate effects can't draw into the shader pack's targets; they use the CPU fallback under shader packs", error)
    }

    private class Target(val framebuffer: Any, val color: Int, val depth: Int, val fbo: Int) {
        fun matches(framebuffer: Any, color: Int, depth: Int): Boolean =
            this.framebuffer === framebuffer && this.color == color && this.depth == depth
    }

    private class Access(
        private val manager: Method,
        private val current: Method,
        private val rendering: Class<*>,
        private val overrides: Method,
        private val before: Field,
        private val shaderMap: Method,
        private val shader: Method,
        private val text: Any,
        private val attachment: Method,
        private val unlockDepthColor: Method,
        private val restoreBlend: Method,
    ) {
        private val writers = HashMap<Class<*>, Pair<Field, Field>?>()

        fun framebuffer(): Any? {
            val pipeline = current.invoke(manager.invoke(null) ?: return null) ?: return null
            if (!rendering.isInstance(pipeline) || overrides.invoke(pipeline) != true) {
                return null
            }
            val program = shader.invoke(shaderMap.invoke(pipeline), text) ?: return null
            val (beforeTranslucent, afterTranslucent) = writers.getOrPut(program.javaClass) { writers(program.javaClass) } ?: return null
            return (if (before.getBoolean(pipeline)) beforeTranslucent else afterTranslucent).get(program)
        }

        fun color(framebuffer: Any): Int = attachment.invoke(framebuffer, 0) as Int

        fun unlock() {
            unlockDepthColor.invoke(null)
            restoreBlend.invoke(null)
        }

        private fun writers(type: Class<*>): Pair<Field, Field>? {
            val before = field(type, BEFORE) ?: return null
            val after = field(type, AFTER) ?: return null
            return before to after
        }

        private fun field(type: Class<*>, name: String): Field? {
            var current: Class<*>? = type
            while (current != null) {
                current.declaredFields.firstOrNull { it.name == name }?.let {
                    it.isAccessible = true
                    return it
                }
                current = current.superclass
            }
            return null
        }

        companion object {
            private const val BEFORE = "writingToBeforeTranslucent"
            private const val AFTER = "writingToAfterTranslucent"

            fun create(): Access? {
                if (!enabled) {
                    return null
                }
                return try {
                    val iris = load("net.irisshaders.iris.Iris")
                    val manager = iris.getMethod("getPipelineManager")
                    val current = load("net.irisshaders.iris.pipeline.PipelineManager").getMethod("getPipelineNullable")
                    val rendering = load("net.irisshaders.iris.pipeline.IrisRenderingPipeline")
                    val keys = load("net.irisshaders.iris.pipeline.programs.ShaderKey")
                    val text = keys.enumConstants.first { (it as Enum<*>).name == "TEXT" }
                    Access(
                        manager,
                        current,
                        rendering,
                        rendering.getMethod("shouldOverrideShaders"),
                        rendering.getField("isBeforeTranslucent"),
                        rendering.getMethod("getShaderMap"),
                        load("net.irisshaders.iris.pipeline.programs.ShaderMap").getMethod("getShader", keys),
                        text,
                        load("net.irisshaders.iris.gl.framebuffer.GlFramebuffer").getMethod("getColorAttachment", Int::class.javaPrimitiveType),
                        load("net.irisshaders.iris.gl.blending.DepthColorStorage").getMethod("unlockDepthColor"),
                        load("net.irisshaders.iris.gl.blending.BlendModeStorage").getMethod("restoreBlend"),
                    )
                } catch (e: ReflectiveOperationException) {
                    LOGGER.warn("Iris internals not found; nameplate effects use the CPU fallback under shader packs", e)
                    null
                } catch (e: NoSuchElementException) {
                    LOGGER.warn("Iris internals not found; nameplate effects use the CPU fallback under shader packs", e)
                    null
                } catch (e: LinkageError) {
                    LOGGER.warn("Iris internals not found; nameplate effects use the CPU fallback under shader packs", e)
                    null
                }
            }

            private fun load(name: String): Class<*> = Class.forName(name, false, NameplateIrisTarget::class.java.classLoader)
        }
    }
}
*///?}
