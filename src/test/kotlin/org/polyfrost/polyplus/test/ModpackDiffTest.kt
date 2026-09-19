package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.ModpackDiff

class ModpackDiffTest {

    @Test
    fun `mods are matched by hash, not by name`() {
        val pack = mapOf("aaa" to "sodium.jar", "bbb" to "iris.jar", "ccc" to "lithium.jar")
        val loaded = mapOf("aaa" to "sodium renamed", "ccc" to "lithium", "ddd" to "jei")
        assertEquals(
            ModpackDiff.Diff(removed = listOf("iris.jar"), added = listOf("jei")),
            ModpackDiff.diff(pack, loaded),
        )
    }
}
