package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f

internal abstract class RenderBlocksDiorama(private val label: String) : RenderBlocksContent {
    protected abstract val scene: RenderBlocksScene

    val viewProjection = Matrix4f()
    val eye = Vector3f()

    private var mesh: RenderBlocksMesh? = null
    private var builtKey: Any? = null
    private var built = false
    private val startNanos = System.nanoTime()
    private var lastNanos = 0L

    protected open fun meshKey(time: Float): Any? = null

    protected open fun buildMesh(mesh: RenderBlocksMesh) {
        mesh.rebuild(scene)
    }

    protected open fun animate(dt: Float, time: Float) = Unit

    protected abstract fun view(time: Float, aspect: Float): RenderBlocksView

    protected open fun lighting(time: Float) = RenderBlocksLighting(brightness = 1f)

    protected open fun extraDraws(time: Float): List<RenderBlocksDraw> = emptyList()

    protected open fun backdrop(target: RenderTarget, aspect: Float, time: Float): Boolean = false

    protected open fun overlay(target: RenderTarget, aspect: Float, time: Float) = Unit

    protected open fun releaseExtra(): List<AutoCloseable> = emptyList()

    final override fun render(
        renderer: RenderBlocksRenderer,
        target: RenderTarget,
        width: Int,
        height: Int,
        aspect: Float,
        nowNanos: Long,
    ): Boolean {
        val dt = if (lastNanos == 0L) 0f else ((nowNanos - lastNanos) / NANOS_PER_SECOND).toFloat().coerceAtMost(MAX_STEP)
        lastNanos = nowNanos
        val time = ((nowNanos - startNanos) / NANOS_PER_SECOND).toFloat()
        animate(dt, time)

        val main = mesh ?: RenderBlocksMesh("$label terrain").also { mesh = it }
        val key = meshKey(time)
        if (!built || key != builtKey) {
            buildMesh(main)
            builtKey = key
            built = true
        }

        val view = view(time, aspect)
        val draws = listOf(RenderBlocksDraw(main, IDENTITY)) + extraDraws(time)
        val backed = backdrop(target, aspect, time)
        if (!renderer.render(target, aspect, view, lighting(time), draws, clearColor = !backed)) return false
        view.viewProjection(aspect, viewProjection)
        view.eye(eye)
        overlay(target, aspect, time)
        return true
    }

    final override fun release(): List<AutoCloseable> {
        val owned = listOfNotNull(mesh) + releaseExtra()
        mesh = null
        built = false
        builtKey = null
        lastNanos = 0L
        return owned
    }

    private companion object {
        val IDENTITY: Matrix4fc = Matrix4f()
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MAX_STEP = 0.1f
    }
}
//?}
