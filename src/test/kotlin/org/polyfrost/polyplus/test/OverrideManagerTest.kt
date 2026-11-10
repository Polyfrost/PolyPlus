package org.polyfrost.polyplus.test

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.server.Binding
import org.polyfrost.polyplus.server.OptionValue
import org.polyfrost.polyplus.server.OverrideManager
import java.nio.file.Files
import java.nio.file.Path

class OverrideManagerTest {
    private class FakeBinding(override val key: String, override val target: String, var value: Int) : Binding {
        override val mod = "test"
        override val type = "number"
        var lockState = false
        var journalSeenOnApply: String? = null
        var journal: Path? = null

        override fun snapshot(): JsonElement = JsonPrimitive(value)
        override fun apply(value: OptionValue): Boolean {
            journalSeenOnApply = journal?.let { if (Files.exists(it)) Files.readString(it) else null }
            this.value = (value as? OptionValue.IntV)?.value ?: return false
            return true
        }
        override fun restore(snapshot: JsonElement) {
            value = (snapshot as JsonPrimitive).int
        }
        override fun setLocked(locked: Boolean) {
            lockState = locked
        }
    }

    @Test
    fun `journal is written before the first override and cleared on reset`(@TempDir dir: Path) {
        val journal = dir.resolve("server-overrides.json")
        val binding = FakeBinding("fov.default-fov", "t:fov", 70).also { it.journal = journal }
        val manager = OverrideManager(journal) { if (it == binding.key) listOf(binding) else emptyList() }

        manager.set(binding.key, OptionValue.IntV(90))
        assertTrue(binding.journalSeenOnApply!!.contains("70"))
        assertEquals(90, binding.value)
        assertTrue(binding.lockState)

        manager.set(binding.key, OptionValue.IntV(100))
        assertEquals(100, binding.value)
        assertTrue(Files.readString(journal).contains("70"))

        manager.reset(binding.key)
        assertEquals(70, binding.value)
        assertFalse(binding.lockState)
        assertFalse(Files.exists(journal))
    }

    @Test
    fun `crash leaves the journal and the next launch restores the user's value`(@TempDir dir: Path) {
        val journal = dir.resolve("server-overrides.json")
        val before = FakeBinding("fov.default-fov", "t:fov", 70)
        OverrideManager(journal) { listOf(before) }.set(before.key, OptionValue.IntV(90))
        assertEquals(90, before.value)

        val afterCrash = FakeBinding("fov.default-fov", "t:fov", 90)
        val manager = OverrideManager(journal) { listOf(afterCrash) }
        manager.loadJournal()
        assertEquals(setOf("t:fov"), manager.pendingTargets())
        manager.replay("t:fov", afterCrash::restore)
        assertEquals(70, afterCrash.value)
        assertTrue(manager.pendingTargets().isEmpty())
        assertFalse(Files.exists(journal))
    }

    @Test
    fun `override before replay keeps the journaled value as the original`(@TempDir dir: Path) {
        val journal = dir.resolve("server-overrides.json")
        OverrideManager(journal) { listOf(FakeBinding("k", "t", 70)) }.set("k", OptionValue.IntV(90))

        val binding = FakeBinding("k", "t", 90)
        val manager = OverrideManager(journal) { listOf(binding) }
        manager.loadJournal()
        manager.set("k", OptionValue.IntV(120))
        manager.resetAll()
        assertEquals(70, binding.value)
        assertFalse(Files.exists(journal))
    }

    @Test
    fun `shared target is restored only after its last key resets`(@TempDir dir: Path) {
        val journal = dir.resolve("server-overrides.json")
        val a = FakeBinding("a", "shared", 1)
        val b = FakeBinding("b", "shared", 1)
        val manager = OverrideManager(journal) { key -> listOf(if (key == "a") a else b) }
        manager.set("a", OptionValue.IntV(5))
        b.value = a.value
        manager.set("b", OptionValue.IntV(6))
        manager.reset("a")
        assertEquals(6, b.value)
        manager.reset("b")
        assertEquals(1, b.value)
    }

    @Test
    fun `rejected value leaves nothing behind`(@TempDir dir: Path) {
        val journal = dir.resolve("server-overrides.json")
        val binding = FakeBinding("k", "t", 3)
        val manager = OverrideManager(journal) { listOf(binding) }
        manager.set("k", OptionValue.Str("nope"))
        assertEquals(3, binding.value)
        assertFalse(binding.lockState)
        assertFalse(manager.isOverridden("k"))
        assertFalse(Files.exists(journal))
    }

    @Test
    fun `corrupt journal is kept aside`(@TempDir dir: Path) {
        val journal = dir.resolve("server-overrides.json")
        Files.writeString(journal, "{not json")
        val manager = OverrideManager(journal) { emptyList() }
        manager.loadJournal()
        assertTrue(manager.pendingTargets().isEmpty())
        assertTrue(Files.exists(dir.resolve("server-overrides.json.corrupt")))
    }
}
