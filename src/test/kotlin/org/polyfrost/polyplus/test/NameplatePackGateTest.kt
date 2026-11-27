package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.material.NameplateOwnedPipelines
import org.polyfrost.polyplus.client.nameplate.material.NameplatePackGate
import org.polyfrost.polyplus.client.nameplate.material.NameplatePackSurface
import org.polyfrost.polyplus.client.nameplate.material.NameplatePackTarget

class NameplatePackGateTest {
    private class FakeTarget(var ready: Boolean = true) : NameplatePackTarget {
        val owned = NameplateOwnedPipelines()
        var resolveCalls = 0
        var resolveError: Throwable? = null

        override fun resolve(): Boolean {
            resolveCalls++
            resolveError?.let { throw it }
            return ready
        }

        override fun owns(pipeline: Any): Boolean = pipeline in owned
    }

    private class FakeSurface : NameplatePackSurface {
        var ready = true
        var overriding = true
        var redirectError: Throwable? = null
        val calls = ArrayList<String>()

        override fun resolve(): Boolean = ready

        override fun active(): Boolean = overriding

        override fun unlockBlend() {
            calls += "unlock"
        }

        override fun redirect(): Boolean {
            redirectError?.let { throw it }
            if (!overriding) return false
            calls += "redirect"
            return true
        }

        override fun restore() {
            calls += "restore"
        }
    }

    private class Pipeline(val name: String) {
        override fun equals(other: Any?): Boolean = other is Pipeline && other.name == name

        override fun hashCode(): Int = name.hashCode()
    }

    private val target = FakeTarget()
    private val surface = FakeSurface()
    private val failures = ArrayList<Throwable>()
    private val gate = NameplatePackGate({ target }, { surface }) { failures += it }

    @Test
    fun `no pack keeps the gpu on without asking the target`() {
        gate.update(false)
        assertTrue(gate.gpuAllowed())
        assertFalse(gate.redirecting())
        assertEquals(0, target.resolveCalls)
    }

    @Test
    fun `a ready target lets materials draw on the gpu under a pack`() {
        gate.update(true)
        assertTrue(gate.gpuAllowed())
        assertTrue(gate.redirecting())
    }

    @Test
    fun `a pack pipeline iris cannot drive keeps materials on the cpu`() {
        surface.ready = false
        gate.update(true)
        assertFalse(gate.gpuAllowed())
        assertFalse(gate.redirecting())
    }

    @Test
    fun `no surface leaves the decision to the target`() {
        val legacy = NameplatePackGate({ target }, { null }) { failures += it }
        legacy.update(true)
        assertTrue(legacy.gpuAllowed())
        target.ready = false
        legacy.update(true)
        assertFalse(legacy.gpuAllowed())
    }

    @Test
    fun `a linkage error while resolving latches the cpu path for the session`() {
        target.resolveError = NoSuchMethodError("bindDefault")
        gate.update(true)
        assertFalse(gate.gpuAllowed())
        target.resolveError = null
        gate.update(true)
        assertFalse(gate.gpuAllowed())
        assertEquals(1, failures.size)
        gate.update(false)
        assertTrue(gate.gpuAllowed())
    }

    @Test
    fun `only owned pipelines are redirected`() {
        val ours = Pipeline("polyplus:nameplate")
        target.owned.add(ours)
        gate.update(true)
        gate.redirect(Pipeline("polyplus:nameplate"))
        gate.unlockBlend(Pipeline("polyplus:nameplate"))
        assertEquals(emptyList<String>(), surface.calls)
        gate.unlockBlend(ours)
        gate.redirect(ours)
        assertEquals(listOf("unlock", "redirect"), surface.calls)
    }

    @Test
    fun `restore runs once and only after a redirect`() {
        val ours = Pipeline("a")
        target.owned.add(ours)
        gate.update(true)
        gate.restore()
        assertEquals(emptyList<String>(), surface.calls)
        gate.redirect(ours)
        gate.restore()
        gate.restore()
        assertEquals(listOf("redirect", "restore"), surface.calls)
    }

    @Test
    fun `a second redirect restores the first one before binding again`() {
        val ours = Pipeline("a")
        target.owned.add(ours)
        gate.update(true)
        gate.redirect(ours)
        gate.redirect(Pipeline("b"))
        gate.restore()
        assertEquals(listOf("redirect", "restore"), surface.calls)
    }

    @Test
    fun `draws outside the world are left alone`() {
        val ours = Pipeline("a")
        target.owned.add(ours)
        gate.update(true)
        surface.overriding = false
        gate.redirect(ours)
        gate.restore()
        assertFalse(gate.fogOff())
        assertEquals(emptyList<String>(), surface.calls)
    }

    @Test
    fun `a failing redirect latches the cpu path`() {
        val ours = Pipeline("a")
        target.owned.add(ours)
        gate.update(true)
        surface.redirectError = IllegalStateException("framebuffer")
        gate.redirect(ours)
        assertFalse(gate.redirecting())
        assertFalse(gate.gpuAllowed())
        surface.redirectError = null
        gate.redirect(ours)
        assertEquals(emptyList<String>(), surface.calls)
        assertEquals(1, failures.size)
    }

    @Test
    fun `fog turns off only while redirecting in the world`() {
        assertFalse(gate.fogOff())
        gate.update(true)
        assertTrue(gate.fogOff())
    }

    @Test
    fun `owned pipelines compare by identity`() {
        val owned = NameplateOwnedPipelines()
        val a = Pipeline("x")
        owned.add(a)
        owned.add(null)
        assertTrue(a in owned)
        assertFalse(Pipeline("x") in owned)
        assertFalse(null in owned)
        owned.remove(Pipeline("x"))
        assertTrue(a in owned)
        owned.remove(a)
        assertFalse(a in owned)
        owned.add(a)
        owned.clear()
        assertFalse(a in owned)
    }
}
