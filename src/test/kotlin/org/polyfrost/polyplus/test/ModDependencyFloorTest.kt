package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class ModDependencyFloorTest {

    private val manifest: String by lazy {
        val candidates = javaClass.classLoader.getResources("fabric.mod.json").toList()
        val ours = candidates
            .map { it.openStream().bufferedReader().use { reader -> reader.readText() } }
            .filter { Regex(""""id"\s*:\s*"polyplus"""").containsMatchIn(it) }
        assertEquals(
            1, ours.size,
            "expected exactly one PolyPlus fabric.mod.json on the test classpath, found ${ours.size} " +
                "among ${candidates.size} candidates",
        )
        ours.single()
    }

    private fun floorOf(modId: String): String? =
        Regex(""""${Regex.escape(modId)}"\s*:\s*">=([^"]+)"""").find(manifest)?.groupValues?.get(1)

    @Test
    fun `every manifest placeholder is expanded`() {
        assertTrue(
            !manifest.contains("\${"),
            "fabric.mod.json still contains an unexpanded placeholder:\n$manifest",
        )
    }

    @Test
    fun `oneconfig floor matches the version we compile against`() {
        val compiledAgainst = System.getProperty("polyplus.oneconfig.version")
        assumeTrue(!compiledAgainst.isNullOrBlank(), "polyplus.oneconfig.version was not supplied")

        assertEquals(
            compiledAgainst,
            floorOf("oneconfigv1"),
            "the oneconfigv1 floor in fabric.mod.json has drifted from the OneConfig version on the " +
                "compile classpath; an older OneConfig would load and then fail at class load",
        )
    }

    @Test
    fun `the manifest declares the dependencies polyplus cannot start without`() {
        for (modId in listOf("oneconfigv1", "fabric-language-kotlin", "fabricloader")) {
            assertNotNull(floorOf(modId), "fabric.mod.json declares no minimum version for $modId")
        }
    }
}
